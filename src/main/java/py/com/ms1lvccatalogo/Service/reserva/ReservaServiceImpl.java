package py.com.ms1lvccatalogo.Service.reserva;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.LiberacionReservasDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaItemCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaReasignarDTO;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.entity.ReservaStock;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.lavitrinacoleccionistas.enums.TipoOrigenReserva;
import py.com.lavitrinacoleccionistas.enums.TipoVenta;
import py.com.ms1lvccatalogo.Config.RedisCacheConfig;
import py.com.ms1lvccatalogo.Repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.Repository.IOfertaRepository;
import py.com.ms1lvccatalogo.Repository.IProductoRepository;
import py.com.ms1lvccatalogo.Repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.exception.BadRequestException;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.mapper.ReservaStockMapper;
import py.com.ms1lvccatalogo.security.UsuarioActual;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reserva generica de stock por (origen, idOrigen): ORDEN, OFERTA o INTERCAMBIO.
 * <p>
 * Dos bolsas de disponibilidad:
 * <ul>
 *   <li>Con detalle: cantidadPublicada - reservas RESERVADA del detalle.</li>
 *   <li>Sin detalle (stock libre): stock - publicado (ACTIVA/PAUSADA) - reservas libres RESERVADA.</li>
 * </ul>
 * Reservar no toca producto.stock; se descuenta al confirmar. Orden de bloqueo en todas las
 * operaciones: productos por id y luego detalles por id (evita deadlocks).
 * <p>
 * Toda operacion que cambia reservas invalida la cache de publicaciones, porque cada
 * DetallePublicacionDTO cacheado lleva cantidadDisponible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements IReservaService {

    private static final long SIN_PUBLICACION = -1L;
    private static final List<EstadoPublicacion> PUBLICADO =
            List.of(EstadoPublicacion.ACTIVA, EstadoPublicacion.PAUSADA);

    private final IReservaStockRepository reservaRepository;
    private final IDetallePublicacionRepository detalleRepository;
    private final IProductoRepository productoRepository;
    private final IOfertaRepository ofertaRepository;
    private final ReservaStockMapper mapper;
    private final CacheManager cacheManager;

    @Value("${app.reserva.horas-expiracion:48}")
    private long horasOrden;

    @Value("${app.reserva.horas-oferta:48}")
    private long horasOferta;

    @Value("${app.reserva.horas-intercambio:168}")
    private long horasIntercambio;

    /** Item normalizado: exactamente uno de idDetalle / idProducto. */
    private record Item(Long idDetalle, Long idProducto, int cantidad, Long idDestino) {
        String clave() {
            return idDetalle != null ? "D" + idDetalle : "P" + idProducto;
        }
    }

    // ------------------------------------------------------------------ reservar

    @Override
    @Transactional
    public List<ReservaDTO> reservar(ReservaCreateDTO dto) {
        TipoOrigenReserva origen = dto.getOrigen() != null
                ? TipoOrigenReserva.valueOf(dto.getOrigen().name())
                : TipoOrigenReserva.ORDEN;
        Long idOrigen = dto.getIdOrigen() != null ? dto.getIdOrigen() : dto.getIdOrden();
        if (idOrigen == null) {
            throw new BadRequestException("Falta idOrigen");
        }

        switch (origen) {
            case OFERTA -> throw new BadRequestException("Las reservas de una oferta se crean al aceptarla");
            case ORDEN -> {
                if (!UsuarioActual.tieneRol("ADMIN", "COMPRADOR")) {
                    throw new AccessDeniedException("Solo un comprador puede reservar para una orden");
                }
            }
            case INTERCAMBIO -> { /* el permiso se valida por item: solo stock propio */ }
        }

        List<Item> items = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (ReservaItemCreateDTO i : dto.getItems()) {
            boolean conDetalle = i.getIdDetallePublicacion() != null;
            boolean conProducto = i.getIdProducto() != null;
            if (conDetalle == conProducto) {
                throw new BadRequestException("Cada item lleva idDetallePublicacion o idProducto (uno solo)");
            }
            if (origen == TipoOrigenReserva.ORDEN && !conDetalle) {
                throw new BadRequestException("Las ordenes solo reservan detalles de publicacion");
            }
            Item item = new Item(i.getIdDetallePublicacion(), i.getIdProducto(),
                    i.getCantidad(), i.getIdUsuarioDestino());
            if (!vistos.add(item.clave())) {
                throw new ConflictException("Item repetido: " + item.clave());
            }
            items.add(item);
        }

        return mapper.toDTO(reservarItems(origen, idOrigen, items, true));
    }

    @Override
    @Transactional
    public List<ReservaDTO> reservarParaOferta(Oferta oferta) {
        List<Item> items = oferta.getPublicacion().getDetallePublicacion().stream()
                .filter(d -> d.getCantidadPublicada() != null && d.getCantidadPublicada() > 0)
                .map(d -> new Item(d.getId(), null, d.getCantidadPublicada(), null))
                .toList();
        if (items.isEmpty()) {
            throw new ConflictException("La publicacion no tiene unidades disponibles");
        }
        return mapper.toDTO(reservarItems(TipoOrigenReserva.OFERTA, oferta.getId(), items, false));
    }

    private List<ReservaStock> reservarItems(TipoOrigenReserva origen, Long idOrigen,
                                             List<Item> items, boolean validarPermiso) {
        // 1) Bloqueos en orden fijo: productos de stock libre por id, luego detalles por id
        Map<Long, Producto> productos = new TreeMap<>();
        items.stream().map(Item::idProducto).filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> productos.put(id, bloquearProducto(id)));

        Map<Long, DetallePublicacion> detalles = new TreeMap<>();
        items.stream().map(Item::idDetalle).filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> detalles.put(id, bloquearDetalle(id)));

        // 2) Idempotencia por item, leida con los bloqueos ya tomados
        List<ReservaStock> existentes = new ArrayList<>(reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen));
        Set<String> yaReservados = existentes.stream().map(this::clave).collect(Collectors.toSet());
        List<Item> nuevos = items.stream().filter(i -> !yaReservados.contains(i.clave())).toList();
        if (nuevos.isEmpty()) {
            log.debug("{} {}: items ya reservados (idempotente)", origen, idOrigen);
            return existentes;
        }
        if (existentes.stream().anyMatch(r -> r.getEstadoReserva() != EstadoReserva.RESERVADA)) {
            throw new ConflictException("Las reservas de " + origen + " " + idOrigen + " ya fueron confirmadas o liberadas");
        }

        Long usuario = validarPermiso ? UsuarioActual.id() : null;
        boolean admin = validarPermiso && UsuarioActual.esAdmin();
        LocalDateTime expiracion = LocalDateTime.now().plusHours(horas(origen));

        // 3) Validar disponibilidad y crear
        for (Item item : nuevos) {
            ReservaStock r = new ReservaStock();
            Producto producto;

            if (item.idDetalle() != null) {
                DetallePublicacion det = detalles.get(item.idDetalle());
                Publicacion pub = det.getPublicacion();
                producto = det.getProducto();

                if (pub.getEstadoPublicacion() != EstadoPublicacion.ACTIVA) {
                    throw new ConflictException("La publicacion del detalle " + det.getId() + " no esta activa");
                }
                if (origen == TipoOrigenReserva.ORDEN && pub.getTipoVenta() == TipoVenta.OFERTA_LIBRE) {
                    throw new ConflictException("La publicacion " + pub.getId() + " solo se vende por oferta");
                }
                if (origen == TipoOrigenReserva.INTERCAMBIO && validarPermiso && !admin
                        && !pub.getIdVendedor().equals(usuario)) {
                    throw new AccessDeniedException("Solo el vendedor puede reservar su publicacion para un intercambio");
                }

                long reservado = reservaRepository.sumarCantidadPorEstado(det.getId(), EstadoReserva.RESERVADA);
                long disponible = det.getCantidadPublicada() - reservado;
                if (item.cantidad() > disponible) {
                    throw new ConflictException("Stock insuficiente de \"" + producto.getNombre()
                            + "\" (disponible: " + disponible + ")");
                }
                r.setDetallePublicacion(det);
            } else {
                producto = productos.get(item.idProducto());

                if (validarPermiso && !admin && !producto.getIdVendedor().equals(usuario)) {
                    throw new AccessDeniedException("Solo podés reservar stock de tus propios productos");
                }

                long stock = producto.getStock() == null ? 0 : producto.getStock();
                long publicado = detalleRepository.sumarPublicadoPorProducto(producto.getId(), PUBLICADO, SIN_PUBLICACION);
                long libresReservadas = reservaRepository.sumarLibresPorProducto(producto.getId(), EstadoReserva.RESERVADA);
                long disponible = stock - publicado - libresReservadas;
                if (item.cantidad() > disponible) {
                    throw new ConflictException("Stock libre insuficiente de \"" + producto.getNombre()
                            + "\" (disponible: " + disponible + "; el resto esta publicado o reservado)");
                }
            }

            if (item.idDestino() != null && item.idDestino().equals(producto.getIdVendedor())) {
                throw new BadRequestException("El destino no puede ser el dueño de \"" + producto.getNombre() + "\"");
            }

            r.setTipoOrigen(origen);
            r.setIdOrigen(idOrigen);
            r.setProducto(producto);
            r.setCantidad(item.cantidad());
            r.setIdUsuarioDestino(item.idDestino());
            r.setEstadoReserva(EstadoReserva.RESERVADA);
            r.setFechaExpiracion(expiracion);
            existentes.add(reservaRepository.save(r));
            log.debug("{} {}: reservadas {} de {}", origen, idOrigen, item.cantidad(), item.clave());
        }

        // La disponibilidad de los detalles cambio
        invalidarPublicaciones();
        return existentes;
    }

    // ------------------------------------------------------------------ consultar

    @Override
    @Transactional(readOnly = true)
    public List<ReservaDTO> obtener(TipoOrigenReserva origen, Long idOrigen) {
        return mapper.toDTO(reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen));
    }

    // ------------------------------------------------------------------ confirmar

    @Override
    @Transactional
    public List<ReservaDTO> confirmar(TipoOrigenReserva origen, Long idOrigen) {
        List<ReservaStock> reservas = reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen);
        if (reservas.isEmpty()) {
            throw new EntityNotFoundException(origen + " " + idOrigen + " no tiene reservas");
        }

        switch (origen) {
            case OFERTA -> throw new ConflictException("Una oferta se confirma al pagar: reasigná la reserva a la orden");
            case ORDEN -> {
                if (!UsuarioActual.tieneRol("ADMIN", "VENDEDOR")) {
                    throw new AccessDeniedException("Solo el vendedor confirma una orden");
                }
            }
            case INTERCAMBIO -> exigirParticipante(reservas);
        }

        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() == EstadoReserva.LIBERADA)) {
            throw new ConflictException("Las reservas de " + origen + " " + idOrigen + " fueron liberadas o vencieron");
        }
        List<ReservaStock> pendientes = reservas.stream()
                .filter(r -> r.getEstadoReserva() == EstadoReserva.RESERVADA)
                .toList();
        if (pendientes.isEmpty()) {
            log.debug("{} {} ya estaba confirmada (idempotente)", origen, idOrigen);
            return mapper.toDTO(reservas);
        }

        Map<Long, Producto> productos = new TreeMap<>();
        pendientes.stream().map(r -> r.getProducto().getId()).distinct().sorted()
                .forEach(id -> productos.put(id, bloquearProducto(id)));

        Map<Long, DetallePublicacion> detalles = new TreeMap<>();
        pendientes.stream().filter(r -> r.getDetallePublicacion() != null)
                .map(r -> r.getDetallePublicacion().getId()).distinct().sorted()
                .forEach(id -> detalles.put(id, bloquearDetalle(id)));

        Set<Publicacion> publicaciones = new HashSet<>();
        for (ReservaStock r : pendientes) {
            Producto producto = productos.get(r.getProducto().getId());
            int cantidad = r.getCantidad();

            if (producto.getStock() == null || producto.getStock() < cantidad) {
                throw new ConflictException("Stock insuficiente de \"" + producto.getNombre() + "\"");
            }
            if (r.getDetallePublicacion() != null) {
                DetallePublicacion det = detalles.get(r.getDetallePublicacion().getId());
                if (det.getCantidadPublicada() < cantidad) {
                    throw new ConflictException("Cantidad publicada insuficiente en el detalle " + det.getId());
                }
                det.setCantidadPublicada(det.getCantidadPublicada() - cantidad);
                publicaciones.add(det.getPublicacion());
            }
            producto.setStock(producto.getStock() - cantidad);

            if (r.getIdUsuarioDestino() != null) {
                productoRepository.save(copiarPara(r.getIdUsuarioDestino(), producto, cantidad));
            }
            r.setEstadoReserva(EstadoReserva.CONFIRMADA);
            log.debug("{} {}: producto {} CONFIRMADA (-{})", origen, idOrigen, producto.getId(), cantidad);
        }

        publicaciones.forEach(this::marcarVendidaSiAgotada);
        invalidarCaches(productos.keySet());
        return mapper.toDTO(reservas);
    }

    // ------------------------------------------------------------------ liberar

    @Override
    @Transactional
    public List<ReservaDTO> liberar(TipoOrigenReserva origen, Long idOrigen) {
        List<ReservaStock> reservas = reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen);
        if (reservas.isEmpty()) {
            return List.of();
        }

        switch (origen) {
            case ORDEN -> {
                if (!UsuarioActual.tieneRol("ADMIN", "VENDEDOR", "COMPRADOR")) {
                    throw new AccessDeniedException("Sin permiso para liberar la orden");
                }
            }
            case OFERTA -> {
                if (!UsuarioActual.esAdmin()) {
                    throw new AccessDeniedException("La reserva de una oferta se libera por vencimiento");
                }
            }
            case INTERCAMBIO -> exigirParticipante(reservas);
        }

        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() == EstadoReserva.CONFIRMADA)) {
            throw new ConflictException(origen + " " + idOrigen + " ya fue confirmado y no se puede liberar");
        }
        reservas.stream()
                .filter(r -> r.getEstadoReserva() == EstadoReserva.RESERVADA)
                .forEach(r -> r.setEstadoReserva(EstadoReserva.LIBERADA));

        if (origen == TipoOrigenReserva.OFERTA) {
            expirarOfertas(Set.of(idOrigen));
        }
        invalidarPublicaciones();
        log.debug("{} {}: reservas LIBERADA", origen, idOrigen);
        return mapper.toDTO(reservas);
    }

    // ------------------------------------------------------------------ reasignar

    @Override
    @Transactional
    public List<ReservaDTO> reasignar(TipoOrigenReserva origen, Long idOrigen, ReservaReasignarDTO dto) {
        TipoOrigenReserva destino = TipoOrigenReserva.valueOf(dto.getOrigen().name());
        if (origen != TipoOrigenReserva.OFERTA || destino != TipoOrigenReserva.ORDEN) {
            throw new BadRequestException("Solo se puede reasignar de OFERTA a ORDEN");
        }

        Oferta oferta = ofertaRepository.findById(idOrigen)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada: " + idOrigen));
        if (!UsuarioActual.esAdmin() && !oferta.getIdComprador().equals(UsuarioActual.id())) {
            throw new AccessDeniedException("Solo el comprador de la oferta puede pagarla");
        }
        if (oferta.getEstadoOferta() != EstadoOferta.ACEPTADA) {
            throw new ConflictException("La oferta no esta ACEPTADA (estado: " + oferta.getEstadoOferta() + ")");
        }

        List<ReservaStock> reservas = reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen);
        if (reservas.isEmpty()) {
            throw new EntityNotFoundException("La oferta " + idOrigen + " no tiene reservas vigentes");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() != EstadoReserva.RESERVADA
                || r.getFechaExpiracion().isBefore(ahora))) {
            throw new ConflictException("La reserva de la oferta vencio o ya no esta vigente");
        }
        if (reservaRepository.existsByTipoOrigenAndIdOrigen(destino, dto.getIdOrigen())) {
            throw new ConflictException("La orden " + dto.getIdOrigen() + " ya tiene reservas");
        }

        LocalDateTime expiracion = ahora.plusHours(horasOrden);
        reservas.forEach(r -> {
            r.setTipoOrigen(destino);
            r.setIdOrigen(dto.getIdOrigen());
            r.setFechaExpiracion(expiracion);
        });
        log.debug("OFERTA {} -> ORDEN {}", idOrigen, dto.getIdOrigen());
        return mapper.toDTO(reservas);
    }

    // ------------------------------------------------------------------ vencidas

    @Override
    @Transactional
    public LiberacionReservasDTO liberarVencidas() {
        List<ReservaStock> vencidas = reservaRepository.findByEstadoReservaAndFechaExpiracionBefore(
                EstadoReserva.RESERVADA, LocalDateTime.now());
        vencidas.forEach(r -> r.setEstadoReserva(EstadoReserva.LIBERADA));

        Set<Long> ofertas = vencidas.stream()
                .filter(r -> r.getTipoOrigen() == TipoOrigenReserva.OFERTA)
                .map(ReservaStock::getIdOrigen)
                .collect(Collectors.toSet());
        expirarOfertas(ofertas);
        if (!vencidas.isEmpty()) {
            invalidarPublicaciones();
        }
        log.debug("Reservas vencidas liberadas: {} (ofertas expiradas: {})", vencidas.size(), ofertas.size());

        LiberacionReservasDTO out = new LiberacionReservasDTO();
        out.setLiberadas(vencidas.size());
        return out;
    }

    // ------------------------------------------------------------------ helpers

    /** Participante = dueño de algun producto reservado o destinatario de alguno. */
    private void exigirParticipante(List<ReservaStock> reservas) {
        if (UsuarioActual.esAdmin()) return;
        Long usuario = UsuarioActual.id();
        boolean participa = reservas.stream().anyMatch(r ->
                usuario.equals(r.getProducto().getIdVendedor()) || usuario.equals(r.getIdUsuarioDestino()));
        if (!participa) {
            throw new AccessDeniedException("No participás en esta operacion");
        }
    }

    private void expirarOfertas(Set<Long> ids) {
        if (ids.isEmpty()) return;
        LocalDateTime ahora = LocalDateTime.now();
        ofertaRepository.findAllById(ids).stream()
                .filter(o -> o.getEstadoOferta() == EstadoOferta.ACEPTADA)
                .forEach(o -> {
                    o.setEstadoOferta(EstadoOferta.EXPIRADA);
                    o.setFechaRespuesta(ahora);
                });
        Cache cache = cacheManager.getCache(RedisCacheConfig.OFERTAS);
        if (cache != null) ids.forEach(cache::evict);
    }

    private Producto copiarPara(Long idUsuario, Producto origen, int cantidad) {
        Producto p = new Producto();
        p.setIdVendedor(idUsuario);
        p.setNombre(origen.getNombre());
        p.setCategoria(origen.getCategoria());
        p.setEstadoConservacion(origen.getEstadoConservacion());
        p.setRareza(origen.getRareza());
        p.setPrecioReferencia(origen.getPrecioReferencia());
        p.setUrlImagen(origen.getUrlImagen());
        p.setStock(cantidad);
        p.setActivo(true);
        return p;
    }

    private String clave(ReservaStock r) {
        return r.getDetallePublicacion() != null
                ? "D" + r.getDetallePublicacion().getId()
                : "P" + r.getProducto().getId();
    }

    private long horas(TipoOrigenReserva origen) {
        return switch (origen) {
            case ORDEN -> horasOrden;
            case OFERTA -> horasOferta;
            case INTERCAMBIO -> horasIntercambio;
        };
    }

    private Producto bloquearProducto(Long id) {
        Producto p = productoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + id));
        if (!Boolean.TRUE.equals(p.getActivo())) {
            throw new EntityNotFoundException("Producto no encontrado: " + id);
        }
        return p;
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
        invalidarPublicaciones();
    }

    /** Los DetallePublicacionDTO cacheados llevan cantidadDisponible: cualquier cambio de reservas la altera. */
    private void invalidarPublicaciones() {
        Cache publicaciones = cacheManager.getCache(RedisCacheConfig.PUBLICACIONES);
        if (publicaciones != null) {
            publicaciones.clear();
            log.debug("CACHE CLEAR dominio=publicaciones por cambio de reservas");
        }
    }
}