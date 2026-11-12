package py.com.ms1lvccatalogo.service.reserva;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import py.com.lavitrinacoleccionistas.enums.TipoRol;
import py.com.lavitrinacoleccionistas.enums.TipoVenta;
import py.com.ms1lvccatalogo.cache.CatalogoCacheInvalidator;
import py.com.ms1lvccatalogo.config.RedisCacheConfig;
import py.com.ms1lvccatalogo.exception.BadRequestException;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.mapper.ReservaStockMapper;
import py.com.ms1lvccatalogo.repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.repository.IOfertaRepository;
import py.com.ms1lvccatalogo.repository.IProductoRepository;
import py.com.ms1lvccatalogo.repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.security.UsuarioActual;
import py.com.ms1lvccatalogo.service.producto.IProductoService;
import py.com.ms1lvccatalogo.service.stock.DisponibilidadStock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Reserva de stock : ORDEN, OFERTA o INTERCAMBIO.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements IReservaService {

    private final IReservaStockRepository reservaRepository;
    private final IDetallePublicacionRepository detalleRepository;
    private final IProductoRepository productoRepository;
    private final IOfertaRepository ofertaRepository;
    private final IProductoService productoService;
    private final ReservaStockMapper mapper;
    private final DisponibilidadStock disponibilidad;
    private final CatalogoCacheInvalidator cacheInvalidator;

    @Value("${app.reserva.horas-expiracion:48}")
    private long horasOrden;

    @Value("${app.reserva.horas-oferta:48}")
    private long horasOferta;

    @Value("${app.reserva.horas-intercambio:168}")
    private long horasIntercambio;

    @Override
    @Transactional
    public List<ReservaDTO> reservar(ReservaCreateDTO dto) {
        TipoOrigenReserva origen = origenDe(dto);
        Long idOrigen = idOrigenDe(dto);
        exigirPuedeReservar(origen);
        List<ReservaItem> items = normalizar(origen, dto.getItems());
        return mapper.toDTO(reservarItems(origen, idOrigen, items, true));
    }

    @Override
    @Transactional
    public List<ReservaDTO> reservarParaOferta(Oferta oferta) {
        List<ReservaItem> items = oferta.getPublicacion().getDetallePublicacion().stream()
                .filter(d -> d.getCantidadPublicada() != null && d.getCantidadPublicada() > 0)
                .map(d -> new ReservaItem(d.getId(), null, d.getCantidadPublicada(), null))
                .toList();
        if (items.isEmpty()) {
            throw new ConflictException("La publicacion no tiene unidades disponibles");
        }
        return mapper.toDTO(reservarItems(TipoOrigenReserva.OFERTA, oferta.getId(), items, false));
    }

    //Consultar

    @Override
    @Transactional(readOnly = true)
    public List<ReservaDTO> obtener(TipoOrigenReserva origen, Long idOrigen) {
        log.debug("findByTipoOrigenAndIdOrigen origen={} idOrigen={}", origen, idOrigen);
        return mapper.toDTO(reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen));
    }

    //Transiciones
    @Override
    @Transactional
    public List<ReservaDTO> reasignar(TipoOrigenReserva origen, Long idOrigen, ReservaReasignarDTO dto) {
        TipoOrigenReserva destino = TipoOrigenReserva.valueOf(dto.getOrigen().name());
        exigirReasignable(origen, destino);

        Oferta oferta = ofertaRepository.findById(idOrigen)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada: " + idOrigen));
        exigirCompradorDeOfertaAceptada(oferta);

        List<ReservaStock> reservas = reservasDe(origen, idOrigen);
        exigirTodasReservadas(origen, idOrigen, reservas);
        exigirOrdenSinReservas(destino, dto.getIdOrigen());

        LocalDateTime expiracion = LocalDateTime.now().plusHours(horasOrden);
        reservas.forEach(r -> {
            r.setTipoOrigen(destino);
            r.setIdOrigen(dto.getIdOrigen());
            r.setFechaExpiracion(expiracion);
        });
        log.info("Reserva reasignada OFERTA {} -> ORDEN {} items={}", idOrigen, dto.getIdOrigen(), reservas.size());
        return mapper.toDTO(reservas);
    }

    @Transactional
    public List<ReservaDTO> actualizarReservaIntercambio(Long idIntercambio, ReservaCreateDTO dto) {
        exigirMismoIntercambio(idIntercambio, dto);
        Map<Long, ReservaItemCreateDTO> nuevas = itemsLibresPorProducto(dto.getItems());

        // Mismo orden de bloqueo que reservar/confirmar: productos por id
        Map<Long, Producto> productos = bloquearProductos(nuevas.keySet().stream());
        List<ReservaStock> reservas = reservasDe(TipoOrigenReserva.INTERCAMBIO, idIntercambio);
        exigirTodasReservadas(TipoOrigenReserva.INTERCAMBIO, idIntercambio, reservas);

        Map<Long, ReservaStock> ofrecidas = libresPorProducto(reservas);
        exigirMismosProductos(ofrecidas.keySet(), nuevas.keySet());

        // Primero se valida todo
        Long usuario = UsuarioActual.esAdmin() ? null : UsuarioActual.id();
        ofrecidas.forEach((idProducto, r) ->
                exigirCambioValido(r, productos.get(idProducto), nuevas.get(idProducto), usuario));
        ofrecidas.forEach((idProducto, r) -> r.setCantidad(nuevas.get(idProducto).getCantidad()));

        // Stock libre: no cambia la disponibilidad de ninguna publicacion, no hay cache que invalidar
        log.info("Reserva de intercambio actualizada id={} items={}", idIntercambio, ofrecidas.size());
        return mapper.toDTO(reservas);
    }

    @Override
    @Transactional
    public List<ReservaDTO> confirmar(TipoOrigenReserva origen, Long idOrigen) {
        List<ReservaStock> reservas = reservasDe(origen, idOrigen);
        exigirPuedeConfirmar(origen, reservas);
        exigirNoLiberada(origen, idOrigen, reservas);

        List<ReservaStock> pendientes = soloReservadas(reservas);
        if (pendientes.isEmpty()) {
            log.debug("{} {} ya estaba confirmada (idempotente)", origen, idOrigen);
            return mapper.toDTO(reservas);
        }

        Map<Long, Producto> productos = bloquearProductos(pendientes.stream().map(r -> r.getProducto().getId()));
        Map<Long, DetallePublicacion> detalles = bloquearDetalles(pendientes.stream().map(this::idDetalle));

        Set<Publicacion> publicaciones = new HashSet<>();
        for (ReservaStock r : pendientes) {
            DetallePublicacion det = r.getDetallePublicacion() == null ? null : detalles.get(idDetalle(r));
            descontar(r, productos.get(r.getProducto().getId()), det);
            if (det != null) publicaciones.add(det.getPublicacion());
        }
        publicaciones.forEach(this::marcarVendidaSiAgotada);

        cacheInvalidator.evict(RedisCacheConfig.PRODUCTOS, productos.keySet());
        cacheInvalidator.evictPublicacionesDeProductos(productos.keySet());
        log.info("Reservas confirmadas origen={} idOrigen={} items={}", origen, idOrigen, pendientes.size());
        return mapper.toDTO(reservas);
    }

    @Override
    @Transactional
    public List<ReservaDTO> liberar(TipoOrigenReserva origen, Long idOrigen) {
        List<ReservaStock> reservas = reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen);
        if (reservas.isEmpty()) {
            return List.of();
        }
        exigirPuedeLiberar(origen, idOrigen, reservas);
        exigirNoConfirmada(origen, idOrigen, reservas);

        soloReservadas(reservas).forEach(r -> r.setEstadoReserva(EstadoReserva.LIBERADA));
        if (origen == TipoOrigenReserva.OFERTA) {
            marcarOfertaExpirada(idOrigen);
        }
        cacheInvalidator.evictPublicaciones(idsPublicacion(reservas));
        log.info("Reservas liberadas origen={} idOrigen={} items={}", origen, idOrigen, reservas.size());
        return mapper.toDTO(reservas);
    }

    //Libera todas las reservas RESERVADA con fechaExpiracion pasada
    @Override
    @Transactional
    public List<ReservaDTO> liberarVencidas() {
        List<ReservaStock> vencidas = reservaRepository
                .findByEstadoReservaAndFechaExpiracionBefore(EstadoReserva.RESERVADA, LocalDateTime.now());
        if (vencidas.isEmpty()) {
            log.debug("Sin reservas vencidas");
            return List.of();
        }
        vencidas.forEach(r -> r.setEstadoReserva(EstadoReserva.LIBERADA));
        vencidas.stream()
                .filter(r -> r.getTipoOrigen() == TipoOrigenReserva.OFERTA)
                .map(ReservaStock::getIdOrigen)
                .distinct()
                .forEach(this::marcarOfertaExpirada);
        cacheInvalidator.evictPublicaciones(idsPublicacion(vencidas));
        log.info("Reservas vencidas liberadas: {}", vencidas.size());
        return mapper.toDTO(vencidas);
    }

    // Reservar
    private List<ReservaStock> reservarItems(TipoOrigenReserva origen, Long idOrigen,
                                             List<ReservaItem> items, boolean validarPermiso) {
        Map<Long, Producto> productos = bloquearProductos(items.stream().map(ReservaItem::idProducto));
        Map<Long, DetallePublicacion> detalles = bloquearDetalles(items.stream().map(ReservaItem::idDetalle));

        // Idempotencia por item, leida con los bloqueos ya tomados
        List<ReservaStock> existentes = new ArrayList<>(reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen));
        List<ReservaItem> nuevos = sinReservar(items, existentes);
        if (nuevos.isEmpty()) {
            log.debug("{} {}: items ya reservados (idempotente)", origen, idOrigen);
            return existentes;
        }
        exigirTodasReservadas(origen, idOrigen, existentes);
        if (origen == TipoOrigenReserva.ORDEN) {
            exigirUnSoloVendedor(items, existentes, productos, detalles);
        }

        // usuario == null: llamada interna o ADMIN, no se valida dueño
        Long usuario = validarPermiso && !UsuarioActual.esAdmin() ? UsuarioActual.id() : null;
        LocalDateTime expiracion = LocalDateTime.now().plusHours(horas(origen));

        for (ReservaItem item : nuevos) {
            ReservaStock r = item.conDetalle()
                    ? deDetalle(origen, detalles.get(item.idDetalle()), item, usuario)
                    : deStockLibre(productos.get(item.idProducto()), item, usuario);
            completar(r, origen, idOrigen, item, expiracion);
            existentes.add(reservaRepository.save(r));
            log.debug("{} {}: reservadas {} de {}", origen, idOrigen, item.cantidad(), item.clave());
        }

        // Solo cambia la disponibilidad de los detalles reservados; el stock libre no afecta publicaciones
        cacheInvalidator.evictPublicaciones(detalles.values().stream()
                .map(d -> d.getPublicacion().getId()).distinct().toList());
        log.info("Reserva creada origen={} idOrigen={} items={}", origen, idOrigen, nuevos.size());
        return existentes;
    }

    // Una orden conecta al comprador con un unico vendedor (el pago se acuerda fuera de la plataforma)
    private void exigirUnSoloVendedor(List<ReservaItem> items, List<ReservaStock> existentes,
                                      Map<Long, Producto> productos, Map<Long, DetallePublicacion> detalles) {
        long vendedores = Stream.concat(
                        existentes.stream().map(r -> r.getProducto().getIdVendedor()),
                        items.stream().map(i -> i.conDetalle()
                                ? detalles.get(i.idDetalle()).getPublicacion().getIdVendedor()
                                : productos.get(i.idProducto()).getIdVendedor()))
                .distinct()
                .count();
        if (vendedores > 1) {
            throw new ConflictException("Una orden solo puede incluir productos de un mismo vendedor");
        }
    }

    private ReservaStock deDetalle(TipoOrigenReserva origen, DetallePublicacion det, ReservaItem item, Long usuario) {
        exigirDetalleReservable(origen, det, usuario);
        exigirDisponibleEnDetalle(det, item.cantidad());
        exigirDestinoValido(item, det.getProducto());
        ReservaStock r = new ReservaStock();
        r.setDetallePublicacion(det);
        r.setProducto(det.getProducto());
        return r;
    }

    private ReservaStock deStockLibre(Producto p, ReservaItem item, Long usuario) {
        exigirDuenoDelProducto(p, usuario);
        exigirDisponibleLibre(p, item.cantidad());
        exigirDestinoValido(item, p);
        ReservaStock r = new ReservaStock();
        r.setProducto(p);
        return r;
    }

    private void completar(ReservaStock r, TipoOrigenReserva origen, Long idOrigen,
                           ReservaItem item, LocalDateTime expiracion) {
        r.setTipoOrigen(origen);
        r.setIdOrigen(idOrigen);
        r.setCantidad(item.cantidad());
        r.setIdUsuarioDestino(item.idDestino());
        r.setEstadoReserva(EstadoReserva.RESERVADA);
        r.setFechaExpiracion(expiracion);
    }

    private List<ReservaItem> normalizar(TipoOrigenReserva origen, List<ReservaItemCreateDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new BadRequestException("La reserva necesita al menos un item");
        }
        Set<String> vistos = new HashSet<>();
        List<ReservaItem> items = new ArrayList<>();
        for (ReservaItemCreateDTO dto : dtos) {
            exigirItemValido(origen, dto);
            ReservaItem item = ReservaItem.de(dto);
            if (!vistos.add(item.clave())) {
                throw new ConflictException("Item repetido: " + item.clave());
            }
            items.add(item);
        }
        return items;
    }

    // Confirmar
    private void descontar(ReservaStock r, Producto producto, DetallePublicacion det) {
        int cantidad = r.getCantidad();
        exigirStockParaConfirmar(producto, det, cantidad);
        if (det != null) {
            det.setCantidadPublicada(det.getCantidadPublicada() - cantidad);
        }
        producto.setStock(producto.getStock() - cantidad);
        if (r.getIdUsuarioDestino() != null) {
            productoRepository.save(copiarPara(r.getIdUsuarioDestino(), producto, cantidad));
        }
        r.setEstadoReserva(EstadoReserva.CONFIRMADA);
        log.debug("Reserva {} CONFIRMADA producto={} -{}", r.getId(), producto.getId(), cantidad);
    }

    private void marcarVendidaSiAgotada(Publicacion publicacion) {
        boolean agotada = publicacion.getDetallePublicacion().stream()
                .allMatch(d -> d.getCantidadPublicada() == 0);
        if (agotada) {
            publicacion.setEstadoPublicacion(EstadoPublicacion.VENDIDA);
            log.info("Publicacion VENDIDA id={}", publicacion.getId());
        }
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

    // Validaciones

    private void exigirPuedeReservar(TipoOrigenReserva origen) {
        switch (origen) {
            case OFERTA -> throw new BadRequestException("Las reservas de una oferta se crean al aceptarla");
            case ORDEN -> {
                if (!UsuarioActual.tieneRol(TipoRol.ADMIN, TipoRol.COMPRADOR)) {
                    throw new AccessDeniedException("Solo un comprador puede reservar para una orden");
                }
            }
            case INTERCAMBIO -> { /* se valida por item: solo stock propio */ }
        }
    }

    private void exigirPuedeConfirmar(TipoOrigenReserva origen, List<ReservaStock> reservas) {
        switch (origen) {
            case OFERTA -> throw new ConflictException("Una oferta se confirma al pagar: reasigná la reserva a la orden");
            case ORDEN -> exigirVendedorDeLosProductos(reservas);
            case INTERCAMBIO -> exigirParticipante(reservas);
        }
    }

    // Liberacion manual: ADMIN o alguien que participa en la operacion.
    private void exigirPuedeLiberar(TipoOrigenReserva origen, Long idOrigen, List<ReservaStock> reservas) {
        if (UsuarioActual.esAdmin()) return;
        if (origen == TipoOrigenReserva.OFERTA) {
            exigirParteDeOferta(idOrigen);
        } else {
            exigirParticipante(reservas);
        }
    }

    private void exigirParteDeOferta(Long idOferta) {
        Oferta o = ofertaRepository.findById(idOferta)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada: " + idOferta));
        Long usuario = UsuarioActual.id();
        if (!usuario.equals(o.getIdComprador()) && !usuario.equals(o.getPublicacion().getIdVendedor())) {
            throw new AccessDeniedException("Solo el comprador o el vendedor pueden liberar la oferta");
        }
    }

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

    private void exigirVendedorDeLosProductos(List<ReservaStock> reservas) {
        if (UsuarioActual.esAdmin()) return;
        Long usuario = UsuarioActual.id();
        if (reservas.stream().anyMatch(r -> !usuario.equals(r.getProducto().getIdVendedor()))) {
            throw new AccessDeniedException("Solo el vendedor de los productos confirma la orden");
        }
    }

    private void exigirCompradorDeOfertaAceptada(Oferta oferta) {
        if (!UsuarioActual.esAdmin() && !oferta.getIdComprador().equals(UsuarioActual.id())) {
            throw new AccessDeniedException("Solo el comprador de la oferta puede pagarla");
        }
        if (oferta.getEstadoOferta() != EstadoOferta.ACEPTADA) {
            throw new ConflictException("La oferta no esta ACEPTADA (estado: " + oferta.getEstadoOferta() + ")");
        }
    }

    private void exigirDuenoDelProducto(Producto p, Long usuario) {
        if (usuario != null && !p.getIdVendedor().equals(usuario)) {
            throw new AccessDeniedException("Solo podés reservar stock de tus propios productos");
        }
    }

    private void exigirItemValido(TipoOrigenReserva origen, ReservaItemCreateDTO i) {
        boolean conDetalle = i.getIdDetallePublicacion() != null;
        if (conDetalle == (i.getIdProducto() != null)) {
            throw new BadRequestException("Cada item lleva idDetallePublicacion o idProducto (uno solo)");
        }
        if (origen == TipoOrigenReserva.ORDEN && !conDetalle) {
            throw new BadRequestException("Las ordenes solo reservan detalles de publicacion");
        }
        if (i.getCantidad() == null || i.getCantidad() <= 0) {
            throw new BadRequestException("La cantidad de cada item debe ser mayor a 0");
        }
    }

    private void exigirDetalleReservable(TipoOrigenReserva origen, DetallePublicacion det, Long usuario) {
        Publicacion pub = det.getPublicacion();
        if (pub.getEstadoPublicacion() != EstadoPublicacion.ACTIVA) {
            throw new ConflictException("La publicacion del detalle " + det.getId() + " no esta activa");
        }
        if (origen == TipoOrigenReserva.ORDEN) {
            if (pub.getTipoVenta() == TipoVenta.OFERTA_LIBRE) {
                throw new ConflictException("La publicacion " + pub.getId() + " solo se vende por oferta");
            }
            if (usuario != null && pub.getIdVendedor().equals(usuario)) {
                throw new ConflictException("No podés comprar tu propia publicacion");
            }
        }
        if (origen == TipoOrigenReserva.INTERCAMBIO && usuario != null && !pub.getIdVendedor().equals(usuario)) {
            throw new AccessDeniedException("Solo el vendedor puede reservar su publicacion para un intercambio");
        }
    }

    private void exigirDisponibleEnDetalle(DetallePublicacion det, int cantidad) {
        long reservado = reservaRepository.sumarCantidadPorEstado(det.getId(), EstadoReserva.RESERVADA);
        long disponible = det.getCantidadPublicada() - reservado;
        log.debug("Detalle {} publicado={} reservado={}", det.getId(), det.getCantidadPublicada(), reservado);
        if (cantidad > disponible) {
            throw new ConflictException("Stock insuficiente de \"" + det.getProducto().getNombre()
                    + "\" (disponible: " + disponible + ")");
        }
    }

    private void exigirDisponibleLibre(Producto p, int cantidad) {
        long disponible = disponibilidad.libre(p);
        if (cantidad > disponible) {
            throw new ConflictException("Stock libre insuficiente de \"" + p.getNombre()
                    + "\" (disponible: " + disponible + "; el resto esta publicado o reservado)");
        }
    }

    private void exigirDestinoValido(ReservaItem item, Producto p) {
        if (item.idDestino() != null && item.idDestino().equals(p.getIdVendedor())) {
            throw new BadRequestException("El destino no puede ser el dueño de \"" + p.getNombre() + "\"");
        }
    }

    private void exigirStockParaConfirmar(Producto producto, DetallePublicacion det, int cantidad) {
        if (producto.getStock() == null || producto.getStock() < cantidad) {
            throw new ConflictException("Stock insuficiente de \"" + producto.getNombre() + "\"");
        }
        if (det != null && det.getCantidadPublicada() < cantidad) {
            throw new ConflictException("Cantidad publicada insuficiente en el detalle " + det.getId());
        }
    }

    private void exigirTodasReservadas(TipoOrigenReserva origen, Long idOrigen, List<ReservaStock> reservas) {
        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() != EstadoReserva.RESERVADA)) {
            throw new ConflictException("Las reservas de " + origen + " " + idOrigen + " ya fueron confirmadas o liberadas");
        }
    }

    private void exigirNoLiberada(TipoOrigenReserva origen, Long idOrigen, List<ReservaStock> reservas) {
        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() == EstadoReserva.LIBERADA)) {
            throw new ConflictException("Las reservas de " + origen + " " + idOrigen + " fueron liberadas");
        }
    }

    private void exigirNoConfirmada(TipoOrigenReserva origen, Long idOrigen, List<ReservaStock> reservas) {
        if (reservas.stream().anyMatch(r -> r.getEstadoReserva() == EstadoReserva.CONFIRMADA)) {
            throw new ConflictException(origen + " " + idOrigen + " ya fue confirmado y no se puede liberar");
        }
    }

    private void exigirMismoIntercambio(Long idIntercambio, ReservaCreateDTO dto) {
        if (origenDe(dto) != TipoOrigenReserva.INTERCAMBIO || !idIntercambio.equals(idOrigenDe(dto))) {
            throw new BadRequestException("El cuerpo no corresponde al intercambio " + idIntercambio);
        }
    }

    private void exigirMismosProductos(Set<Long> actuales, Set<Long> nuevos) {
        if (!actuales.equals(nuevos)) {
            throw new BadRequestException("No se pueden agregar ni quitar productos ofrecidos");
        }
    }

    /** Mismo destinatario y cantidad dentro de lo libre (la reserva actual ya cuenta como comprometida). */
    private void exigirCambioValido(ReservaStock r, Producto p, ReservaItemCreateDTO nuevo, Long usuario) {
        exigirDuenoDelProducto(p, usuario);
        if (!Objects.equals(nuevo.getIdUsuarioDestino(), r.getIdUsuarioDestino())) {
            throw new BadRequestException("No se puede cambiar el destinatario de \"" + p.getNombre() + "\"");
        }
        long disponible = disponibilidad.libre(p) + r.getCantidad();
        if (nuevo.getCantidad() > disponible) {
            throw new ConflictException("Stock libre insuficiente de \"" + p.getNombre()
                    + "\" (maximo: " + disponible + ")");
        }
    }

    private void exigirReasignable(TipoOrigenReserva origen, TipoOrigenReserva destino) {
        if (origen != TipoOrigenReserva.OFERTA || destino != TipoOrigenReserva.ORDEN) {
            throw new BadRequestException("Solo se puede reasignar de OFERTA a ORDEN");
        }
    }

    private void exigirOrdenSinReservas(TipoOrigenReserva destino, Long idOrden) {
        if (reservaRepository.existsByTipoOrigenAndIdOrigen(destino, idOrden)) {
            throw new ConflictException("La orden " + idOrden + " ya tiene reservas");
        }
    }

    // helpers

    private TipoOrigenReserva origenDe(ReservaCreateDTO dto) {
        return dto.getOrigen() != null
                ? TipoOrigenReserva.valueOf(dto.getOrigen().name())
                : TipoOrigenReserva.ORDEN;
    }

    private Long idOrigenDe(ReservaCreateDTO dto) {
        Long idOrigen = dto.getIdOrigen() != null ? dto.getIdOrigen() : dto.getIdOrden();
        if (idOrigen == null) {
            throw new BadRequestException("Falta idOrigen");
        }
        return idOrigen;
    }

    private List<ReservaStock> reservasDe(TipoOrigenReserva origen, Long idOrigen) {
        List<ReservaStock> reservas = reservaRepository.findByTipoOrigenAndIdOrigen(origen, idOrigen);
        if (reservas.isEmpty()) {
            throw new EntityNotFoundException(origen + " " + idOrigen + " no tiene reservas");
        }
        return reservas;
    }

    /** Valida los items de la edicion y los indexa por producto. Solo stock libre. */
    private Map<Long, ReservaItemCreateDTO> itemsLibresPorProducto(List<ReservaItemCreateDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Faltan productos ofrecidos");
        }
        Map<Long, ReservaItemCreateDTO> out = new TreeMap<>();
        for (ReservaItemCreateDTO i : items) {
            exigirItemValido(TipoOrigenReserva.INTERCAMBIO, i);
            if (i.getIdProducto() == null) {
                throw new BadRequestException("Solo se editan productos ofrecidos (stock libre)");
            }
            if (out.put(i.getIdProducto(), i) != null) {
                throw new BadRequestException("Producto repetido: " + i.getIdProducto());
            }
        }
        return out;
    }

    private Map<Long, ReservaStock> libresPorProducto(List<ReservaStock> reservas) {
        return reservas.stream()
                .filter(r -> r.getDetallePublicacion() == null)
                .collect(Collectors.toMap(r -> r.getProducto().getId(), r -> r));
    }

    private List<ReservaItem> sinReservar(List<ReservaItem> items, List<ReservaStock> existentes) {
        Set<String> yaReservados = existentes.stream().map(this::clave).collect(Collectors.toSet());
        return items.stream().filter(i -> !yaReservados.contains(i.clave())).toList();
    }

    private List<ReservaStock> soloReservadas(List<ReservaStock> reservas) {
        return reservas.stream().filter(r -> r.getEstadoReserva() == EstadoReserva.RESERVADA).toList();
    }

    /** Bloquea en orden de id (evita deadlocks). Ignora nulls. */
    private Map<Long, Producto> bloquearProductos(Stream<Long> ids) {
        Map<Long, Producto> out = new TreeMap<>();
        ids.filter(Objects::nonNull).distinct().sorted()
                .forEach(id -> out.put(id, productoService.obtenerEntidadBloqueada(id)));
        return out;
    }

    private Map<Long, DetallePublicacion> bloquearDetalles(Stream<Long> ids) {
        Map<Long, DetallePublicacion> out = new TreeMap<>();
        ids.filter(Objects::nonNull).distinct().sorted().forEach(id -> {
            log.debug("findByIdForUpdate DetallePublicacion id={}", id);
            out.put(id, detalleRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("DetallePublicacion no encontrado: " + id)));
        });
        return out;
    }

    private void marcarOfertaExpirada(Long idOferta) {
        ofertaRepository.findById(idOferta)
                .filter(o -> o.getEstadoOferta() == EstadoOferta.ACEPTADA)
                .ifPresent(o -> {
                    o.setEstadoOferta(EstadoOferta.EXPIRADA);
                    o.setFechaRespuesta(LocalDateTime.now());
                    log.info("Oferta EXPIRADA id={} (reserva liberada)", idOferta);
                });
        cacheInvalidator.evictOfertas(List.of(idOferta));
    }

    private Collection<Long> idsPublicacion(List<ReservaStock> reservas) {
        return reservas.stream()
                .filter(r -> r.getDetallePublicacion() != null)
                .map(r -> r.getDetallePublicacion().getPublicacion().getId())
                .distinct().toList();
    }

    private Long idDetalle(ReservaStock r) {
        return r.getDetallePublicacion() == null ? null : r.getDetallePublicacion().getId();
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
}