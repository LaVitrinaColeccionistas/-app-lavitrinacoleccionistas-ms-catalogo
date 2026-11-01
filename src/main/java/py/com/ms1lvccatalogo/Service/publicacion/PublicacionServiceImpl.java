package py.com.ms1lvccatalogo.Service.publicacion;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.*;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.ms1lvccatalogo.Config.RedisCacheConfig;
import py.com.ms1lvccatalogo.Repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.Repository.IPublicacionRepository;
import py.com.ms1lvccatalogo.Repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.Service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.Service.detallepublicacion.DetallePublicacionValidator;
import py.com.ms1lvccatalogo.Service.producto.IProductoService;
import py.com.ms1lvccatalogo.exception.BadRequestException;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.filtro.PublicacionFiltro;
import py.com.ms1lvccatalogo.mapper.DetallePublicacionMapper;
import py.com.ms1lvccatalogo.mapper.PublicacionMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PublicacionServiceImpl
        extends BaseServiceImpl<Publicacion, PublicacionCreateDTO, PublicacionUpdateDTO, PublicacionDTO, PublicacionFiltro>
        implements IPublicacionService {

    /**
     * Transiciones que puede pedir el usuario por PATCH /estado.
     * ELIMINADA se asigna con DELETE y VENDIDA la asigna el sistema al confirmar la venta.
     */
    private static final Map<EstadoPublicacion, Set<EstadoPublicacion>> TRANSICIONES = Map.of(
            EstadoPublicacion.ACTIVA, EnumSet.of(EstadoPublicacion.PAUSADA),
            EstadoPublicacion.PAUSADA, EnumSet.of(EstadoPublicacion.ACTIVA),
            EstadoPublicacion.VENDIDA, EnumSet.noneOf(EstadoPublicacion.class)
    );

    private final IPublicacionRepository publicacionRepository;
    private final IDetallePublicacionRepository detalleRepository;
    private final IReservaStockRepository reservaRepository;
    private final IProductoService productoService;
    private final DetallePublicacionValidator validator;
    private final CacheManager cacheManager;
    private final DetallePublicacionMapper detalleMapper;

    public PublicacionServiceImpl(IPublicacionRepository publicacionRepository, PublicacionMapper mapper,
                                  IDetallePublicacionRepository detalleRepository,
                                  IReservaStockRepository reservaRepository,
                                  IProductoService productoService, DetallePublicacionValidator validator,
                                  CacheManager cacheManager, DetallePublicacionMapper detalleMapper) {
        super(publicacionRepository, mapper, "Publicacion");
        this.publicacionRepository = publicacionRepository;
        this.detalleRepository = detalleRepository;
        this.reservaRepository = reservaRepository;
        this.productoService = productoService;
        this.validator = validator;
        this.cacheManager = cacheManager;
        this.detalleMapper = detalleMapper;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#id")
    public PublicacionDTO obtener(Long id) {
        return super.obtener(id);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#result.id")
    public PublicacionDTO crear(PublicacionCreateDTO dto) {
        if (dto.getDetalles() == null || dto.getDetalles().isEmpty()) {
            throw new BadRequestException("La publicacion necesita al menos un detalle");
        }
        log.debug("Creando publicacion con {} detalles", dto.getDetalles().size());
        Publicacion p = mapper.toEntity(dto);
        sincronizarDetalles(p, dto.getDetalles());
        return mapper.toDTO(repository.save(p));
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#result.id")
    public PublicacionDTO actualizar(Long id, PublicacionUpdateDTO dto) {
        Publicacion p = buscarPorId(id);
        if (p.getEstadoPublicacion() == EstadoPublicacion.VENDIDA) {
            throw new ConflictException("La publicacion " + id + " esta VENDIDA y no admite modificaciones");
        }
        log.debug("Actualizando publicacion {}", id);
        mapper.actualizarEntity(dto, p);
        if (dto.getDetalles() != null && !dto.getDetalles().isEmpty()) {
            sincronizarDetalles(p, dto.getDetalles());
        }
        return mapper.toDTO(repository.save(p));
    }

    private void sincronizarDetalles(Publicacion pub, List<DetallePublicacionCreateDTO> items) {
        Map<Long, DetallePublicacion> existentes = pub.getDetallePublicacion().stream()
                .collect(Collectors.toMap(d -> d.getProducto().getId(), Function.identity()));
        Set<Long> vistos = new HashSet<>();

        // Orden fijo por producto: evita deadlocks entre publicaciones concurrentes
        List<DetallePublicacionCreateDTO> ordenados = items.stream()
                .sorted(Comparator.comparing(DetallePublicacionCreateDTO::getIdProducto))
                .toList();

        for (DetallePublicacionCreateDTO item : ordenados) {
            if (!vistos.add(item.getIdProducto())) {
                throw new ConflictException("Producto repetido en los detalles: " + item.getIdProducto());
            }
            Producto prod = productoService.obtenerEntidadBloqueada(item.getIdProducto());
            validator.validar(pub, prod, item.getCantidadPublicada());

            DetallePublicacion d = existentes.remove(item.getIdProducto());
            if (d == null) {
                d = new DetallePublicacion();
                d.setPublicacion(pub);
                d.setProducto(prod);
                pub.getDetallePublicacion().add(d);
            } else {
                validator.validarCantidadContraReservas(d, item.getCantidadPublicada());
            }
            d.setPrecioUnitario(BigDecimal.valueOf(item.getPrecioUnitario()));
            d.setCantidadPublicada(item.getCantidadPublicada());
        }
        existentes.values().forEach(validator::validarSinReservas);
        pub.getDetallePublicacion().removeAll(existentes.values());
    }

    @Override
    protected Publicacion buscarPorId(Long id) {
        Publicacion p = super.buscarPorId(id);
        if (p.getEstadoPublicacion() == EstadoPublicacion.ELIMINADA) {
            throw new EntityNotFoundException(nombreEntidad + " no encontrado: " + id);
        }
        return p;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#id")
    public void eliminar(Long id) {
        Publicacion p = buscarPorId(id);
        if (p.getEstadoPublicacion() == EstadoPublicacion.VENDIDA) {
            throw new ConflictException("La publicacion " + id + " esta VENDIDA (historica) y no se puede eliminar");
        }
        exigirSinReservasVigentes(p);
        rechazarOfertasPendientes(p);
        log.debug("Eliminando (logico) publicacion {}", id);
        p.setEstadoPublicacion(EstadoPublicacion.ELIMINADA);
        repository.save(p);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#result.id")
    public PublicacionDTO actualizarEstado(Long id, PublicacionEstadoUpdateDTO dto) {
        Publicacion p = super.buscarPorId(id);
        EstadoPublicacion actual = p.getEstadoPublicacion();
        EstadoPublicacion nuevo = EstadoPublicacion.valueOf(dto.getEstadoPublicacion().name());

        if (actual == EstadoPublicacion.ELIMINADA) {
            throw new ConflictException("La publicacion " + id + " esta ELIMINADA y no admite cambios de estado");
        }
        if (nuevo == EstadoPublicacion.ELIMINADA) {
            throw new BadRequestException("Para eliminar una publicacion use DELETE /publicaciones/" + id);
        }
        if (nuevo == EstadoPublicacion.VENDIDA) {
            throw new BadRequestException("El estado VENDIDA lo asigna el sistema al confirmar la venta");
        }
        if (!TRANSICIONES.get(actual).contains(nuevo)) {
            throw new ConflictException("Transicion no permitida: " + actual + " -> " + nuevo);
        }

        log.debug("Publicacion {}: {} ; {}", id, actual, nuevo);
        if (nuevo == EstadoPublicacion.ACTIVA) {
            validarStockAlReactivar(p);
        }
        p.setEstadoPublicacion(nuevo);
        return mapper.toDTO(repository.save(p));
    }

    /** Al reactivar, las cantidades de la publicacion vuelven a competir por el stock del producto. */
    private void validarStockAlReactivar(Publicacion p) {
        p.getDetallePublicacion().stream()
                .sorted(Comparator.comparing(d -> d.getProducto().getId()))
                .forEach(d -> {
                    Producto prod = productoService.obtenerEntidadBloqueada(d.getProducto().getId());
                    validator.validar(p, prod, d.getCantidadPublicada());
                });
    }

    /** Bloquea los detalles para que una reserva nueva no se cuele entre el chequeo y el cambio de estado. */
    private void exigirSinReservasVigentes(Publicacion p) {
        p.getDetallePublicacion().stream()
                .map(DetallePublicacion::getId)
                .sorted()
                .forEach(detalleRepository::findByIdForUpdate);
        if (reservaRepository.existsPorPublicacionYEstado(p.getId(), EstadoReserva.RESERVADA)) {
            throw new ConflictException("La publicacion " + p.getId() + " tiene reservas vigentes (compras en curso)");
        }
    }

    /** Politica: al eliminar, las ofertas PENDIENTES se rechazan automaticamente. */
    private void rechazarOfertasPendientes(Publicacion p) {
        Cache cacheOfertas = cacheManager.getCache(RedisCacheConfig.OFERTAS);
        LocalDateTime ahora = LocalDateTime.now();
        p.getOfertas().stream()
                .filter(o -> o.getEstadoOferta() == EstadoOferta.PENDIENTE)
                .forEach(o -> {
                    log.debug("Oferta {}: PENDIENTE -> RECHAZADA (publicacion {} eliminada)", o.getId(), p.getId());
                    o.setEstadoOferta(EstadoOferta.RECHAZADA);
                    o.setFechaRespuesta(ahora);
                    if (cacheOfertas != null) {
                        cacheOfertas.evict(o.getId());
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public DetallePublicacionPageDTO listarDetalles(Long idPublicacion, Pageable pageable) {
        obtenerEntidad(idPublicacion); // 404 si no existe o esta ELIMINADA
        Page<DetallePublicacionDTO> p = detalleRepository
                .findByPublicacionId(idPublicacion, pageable)
                .map(detalleMapper::toDTO);
        DetallePublicacionPageDTO out = new DetallePublicacionPageDTO();
        out.setContent(p.getContent());
        out.setTotalElements(p.getTotalElements());
        out.setTotalPages(p.getTotalPages());
        out.setSize(p.getSize());
        out.setNumber(p.getNumber());
        return out;
    }

    @Override
    protected Page<Publicacion> buscarEntidades(PublicacionFiltro f, Pageable pageable) {
        return publicacionRepository.buscarConFiltros(
                f.getIdVendedor(), f.getEstadoPublicacion(), f.getTipoVenta(), f.getTitulo(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicacionPageDTO buscarPagina(PublicacionFiltro filtro, Pageable pageable) {
        Page<PublicacionDTO> p = buscar(filtro, pageable);
        PublicacionPageDTO out = new PublicacionPageDTO();
        out.setContent(p.getContent());
        out.setTotalElements(p.getTotalElements());
        out.setTotalPages(p.getTotalPages());
        out.setSize(p.getSize());
        out.setNumber(p.getNumber());
        return out;
    }
}