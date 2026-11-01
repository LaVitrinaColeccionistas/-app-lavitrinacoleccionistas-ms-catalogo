package py.com.ms1lvccatalogo.Service.reserva;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.LiberacionReservasDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaItemCreateDTO;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.entity.ReservaStock;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.ms1lvccatalogo.Config.RedisCacheConfig;
import py.com.ms1lvccatalogo.Repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.Repository.IProductoRepository;
import py.com.ms1lvccatalogo.Repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.mapper.ReservaStockMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Reserva de stock. La reserva no toca producto.stock: solo aparta unidades del detalle
 * (cantidadPublicada - reservas RESERVADA). El stock se descuenta recien al confirmar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements IReservaService {

    private final IReservaStockRepository reservaRepository;
    private final IDetallePublicacionRepository detalleRepository;
    private final IProductoRepository productoRepository;
    private final ReservaStockMapper mapper;
    private final CacheManager cacheManager;

    @Value("${app.reserva.horas-expiracion:48}")
    private long horasExpiracion;

    @Override
    @Transactional
    public List<ReservaDTO> reservar(ReservaCreateDTO dto) {
        List<ReservaStock> existentes = reservaRepository.findByIdOrden(dto.getIdOrden());
        if (!existentes.isEmpty()) {
            log.debug("Orden {} ya tiene reservas (idempotente)", dto.getIdOrden());
            return mapper.toDTO(existentes);
        }

        Set<Long> vistos = new HashSet<>();
        for (ReservaItemCreateDTO item : dto.getItems()) {
            if (!vistos.add(item.getIdDetallePublicacion())) {
                throw new ConflictException("Detalle repetido en la orden: " + item.getIdDetallePublicacion());
            }
        }

        // Orden fijo por id: evita deadlocks entre ordenes concurrentes
        List<ReservaItemCreateDTO> items = dto.getItems().stream()
                .sorted(Comparator.comparing(ReservaItemCreateDTO::getIdDetallePublicacion))
                .toList();

        LocalDateTime expiracion = LocalDateTime.now().plusHours(horasExpiracion);
        List<ReservaStock> creadas = new ArrayList<>();

        for (ReservaItemCreateDTO item : items) {
            DetallePublicacion detalle = bloquearDetalle(item.getIdDetallePublicacion());

            if (detalle.getPublicacion().getEstadoPublicacion() != EstadoPublicacion.ACTIVA) {
                throw new ConflictException("La publicacion del detalle " + detalle.getId() + " no esta activa");
            }

            long reservado = reservaRepository.sumarCantidadPorEstado(detalle.getId(), EstadoReserva.RESERVADA);
            long disponible = detalle.getCantidadPublicada() - reservado;
            if (item.getCantidad() > disponible) {
                throw new ConflictException("Stock insuficiente en el detalle " + detalle.getId()
                        + " (disponible: " + disponible + ")");
            }

            ReservaStock reserva = new ReservaStock();
            reserva.setIdOrden(dto.getIdOrden());
            reserva.setDetallePublicacion(detalle);
            reserva.setCantidad(item.getCantidad());
            reserva.setEstadoReserva(EstadoReserva.RESERVADA);
            reserva.setFechaExpiracion(expiracion);
            creadas.add(reservaRepository.save(reserva));
            log.debug("Orden {}: reservadas {} unidades del detalle {}", dto.getIdOrden(), item.getCantidad(), detalle.getId());
        }
        return mapper.toDTO(creadas);
    }

    @Override
    @Transactional
    public List<ReservaDTO> confirmar(Long idOrden) {
        List<ReservaStock> reservas = reservaRepository.findByIdOrden(idOrden);
        if (reservas.isEmpty()) {
            throw new EntityNotFoundException("La orden " + idOrden + " no tiene reservas");
        }
        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() == EstadoReserva.LIBERADA)) {
            throw new ConflictException("Las reservas de la orden " + idOrden + " fueron liberadas o vencieron");
        }

        List<ReservaStock> pendientes = reservas.stream()
                .filter(r -> r.getEstadoReserva() == EstadoReserva.RESERVADA)
                .sorted(Comparator.comparing(r -> r.getDetallePublicacion().getId()))
                .toList();
        if (pendientes.isEmpty()) {
            log.debug("Orden {} ya estaba confirmada (idempotente)", idOrden);
            return mapper.toDTO(reservas);
        }

        // Mismo orden de bloqueo que al publicar: primero productos (por id), luego detalles (por id)
        Map<Long, Producto> productos = new TreeMap<>();
        pendientes.stream()
                .map(r -> r.getDetallePublicacion().getProducto().getId())
                .distinct()
                .sorted()
                .forEach(idProducto -> productos.put(idProducto, productoRepository.findByIdForUpdate(idProducto)
                        .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + idProducto))));

        Set<Publicacion> publicaciones = new HashSet<>();
        for (ReservaStock reserva : pendientes) {
            DetallePublicacion detalle = bloquearDetalle(reserva.getDetallePublicacion().getId());
            Producto producto = productos.get(detalle.getProducto().getId());
            int cantidad = reserva.getCantidad();

            if (detalle.getCantidadPublicada() < cantidad) {
                throw new ConflictException("Cantidad publicada insuficiente en el detalle " + detalle.getId());
            }
            if (producto.getStock() == null || producto.getStock() < cantidad) {
                throw new ConflictException("Stock insuficiente del producto " + producto.getId());
            }

            detalle.setCantidadPublicada(detalle.getCantidadPublicada() - cantidad);
            producto.setStock(producto.getStock() - cantidad);
            reserva.setEstadoReserva(EstadoReserva.CONFIRMADA);
            publicaciones.add(detalle.getPublicacion());
            log.debug("Orden {}: detalle {} CONFIRMADA (-{})", idOrden, detalle.getId(), cantidad);
        }

        publicaciones.forEach(this::marcarVendidaSiAgotada);
        invalidarCaches(productos.keySet());
        return mapper.toDTO(reservas);
    }

    @Override
    @Transactional
    public List<ReservaDTO> liberar(Long idOrden) {
        List<ReservaStock> reservas = reservaRepository.findByIdOrden(idOrden);
        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() == EstadoReserva.CONFIRMADA)) {
            throw new ConflictException("La orden " + idOrden + " ya fue confirmada y no se puede liberar");
        }
        reservas.stream()
                .filter(r -> r.getEstadoReserva() == EstadoReserva.RESERVADA)
                .forEach(r -> {
                    log.debug("Orden {}: detalle {} RESERVADA -> LIBERADA", idOrden, r.getDetallePublicacion().getId());
                    r.setEstadoReserva(EstadoReserva.LIBERADA);
                });
        return mapper.toDTO(reservas);
    }

    @Override
    @Transactional
    public LiberacionReservasDTO liberarVencidas() {
        List<ReservaStock> vencidas = reservaRepository.findByEstadoReservaAndFechaExpiracionBefore(
                EstadoReserva.RESERVADA, LocalDateTime.now());
        vencidas.forEach(r -> r.setEstadoReserva(EstadoReserva.LIBERADA));
        log.debug("Reservas vencidas liberadas: {}", vencidas.size());

        LiberacionReservasDTO out = new LiberacionReservasDTO();
        out.setLiberadas(vencidas.size());
        return out;
    }

    private DetallePublicacion bloquearDetalle(Long id) {
        return detalleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("DetallePublicacion no encontrado: " + id));
    }

    private void marcarVendidaSiAgotada(Publicacion publicacion) {
        boolean agotada = publicacion.getDetallePublicacion().stream()
                .allMatch(d -> d.getCantidadPublicada() == 0);
        if (agotada) {
            log.debug("Publicacion {} -> VENDIDA", publicacion.getId());
            publicacion.setEstadoPublicacion(EstadoPublicacion.VENDIDA);
        }
    }

    /** Confirmar cambia stock y cantidades publicadas, que viajan dentro de los DTO cacheados. */
    private void invalidarCaches(Set<Long> idsProducto) {
        Cache productos = cacheManager.getCache(RedisCacheConfig.PRODUCTOS);
        if (productos != null) {
            idsProducto.forEach(productos::evict);
        }
        Cache publicaciones = cacheManager.getCache(RedisCacheConfig.PUBLICACIONES);
        if (publicaciones != null) {
            publicaciones.clear();
            log.debug("CACHE CLEAR dominio=publicaciones por confirmacion de reserva");
        }
    }
}