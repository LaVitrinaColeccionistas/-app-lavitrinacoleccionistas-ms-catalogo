package py.com.ms1lvccatalogo.service.publicacion;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.*;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.ms1lvccatalogo.cache.CatalogoCacheInvalidator;
import py.com.ms1lvccatalogo.config.RedisCacheConfig;
import py.com.ms1lvccatalogo.exception.BadRequestException;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.filtro.PublicacionFiltro;
import py.com.ms1lvccatalogo.mapper.DetallePublicacionMapper;
import py.com.ms1lvccatalogo.mapper.PublicacionMapper;
import py.com.ms1lvccatalogo.repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.repository.IPublicacionRepository;
import py.com.ms1lvccatalogo.repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.security.UsuarioActual;
import py.com.ms1lvccatalogo.service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.service.producto.IProductoService;

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
    private final PublicacionMapper publicacionMapper;
    private final DetallePublicacionMapper detalleMapper;
    private final CatalogoCacheInvalidator cacheInvalidator;

    public PublicacionServiceImpl(IPublicacionRepository publicacionRepository, PublicacionMapper publicacionMapper,
                                  IDetallePublicacionRepository detalleRepository,
                                  IReservaStockRepository reservaRepository,
                                  IProductoService productoService, DetallePublicacionValidator validator,
                                  DetallePublicacionMapper detalleMapper, CatalogoCacheInvalidator cacheInvalidator) {
        super(publicacionRepository, publicacionMapper, "Publicacion");
        this.publicacionRepository = publicacionRepository;
        this.publicacionMapper = publicacionMapper;
        this.detalleRepository = detalleRepository;
        this.reservaRepository = reservaRepository;
        this.productoService = productoService;
        this.validator = validator;
        this.detalleMapper = detalleMapper;
        this.cacheInvalidator = cacheInvalidator;
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
        exigirConDetalles(dto.getDetalles());
        Publicacion p = mapper.toEntity(dto);
        exigirDueno(p);
        sincronizarDetalles(p, dto.getDetalles());
        Publicacion guardada = repository.save(p);
        log.info("Publicacion creada id={} vendedor={} detalles={}",
                guardada.getId(), guardada.getIdVendedor(), dto.getDetalles().size());
        return mapper.toDTO(guardada);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#result.id")
    public PublicacionDTO actualizar(Long id, PublicacionUpdateDTO dto) {
        Publicacion p = buscarPorId(id);
        exigirDueno(p);
        exigirNoVendida(p, "no admite modificaciones");
        mapper.actualizarEntity(dto, p);
        if (dto.getDetalles() != null && !dto.getDetalles().isEmpty()) {
            sincronizarDetalles(p, dto.getDetalles());
        }
        log.info("Publicacion actualizada id={}", id);
        return mapper.toDTO(repository.save(p));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#id")
    public void eliminar(Long id) {
        Publicacion p = buscarPorId(id);
        exigirDueno(p);
        exigirNoVendida(p, "es historica y no se puede eliminar");
        exigirSinReservasVigentes(p);
        int rechazadas = rechazarOfertasPendientes(p);
        p.setEstadoPublicacion(EstadoPublicacion.ELIMINADA);
        log.info("Publicacion eliminada (logico) id={} ofertasRechazadas={}", id, rechazadas);
    }

    //Flujo de negocio

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PUBLICACIONES, key = "#result.id")
    public PublicacionDTO actualizarEstado(Long id, PublicacionEstadoUpdateDTO dto) {
        Publicacion p = super.buscarPorId(id);
        EstadoPublicacion actual = p.getEstadoPublicacion();
        EstadoPublicacion nuevo = EstadoPublicacion.valueOf(dto.getEstadoPublicacion().name());

        exigirDueno(p);
        exigirTransicionValida(id, actual, nuevo);
        if (nuevo == EstadoPublicacion.ACTIVA) {
            exigirStockAlReactivar(p);
        }
        p.setEstadoPublicacion(nuevo);
        log.info("Publicacion id={} estado {} -> {}", id, actual, nuevo);
        return mapper.toDTO(repository.save(p));
    }

    // Consultas
    @Override
    @Transactional(readOnly = true)
    public PublicacionPageDTO buscarPagina(PublicacionFiltro filtro, Pageable pageable) {
        return publicacionMapper.toPageDTO(buscar(filtro, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public DetallePublicacionPageDTO listarDetalles(Long idPublicacion, Pageable pageable) {
        obtenerEntidad(idPublicacion); // 404 si no existe o esta ELIMINADA
        log.debug("findByPublicacionId idPublicacion={} pagina={}", idPublicacion, pageable);
        Page<DetallePublicacionDTO> page = detalleRepository
                .findByPublicacionId(idPublicacion, pageable)
                .map(detalleMapper::toDTO);
        return detalleMapper.toPageDTO(page);
    }

    @Override
    protected Page<Publicacion> buscarEntidades(PublicacionFiltro f, Pageable pageable) {
        return publicacionRepository.buscarConFiltros(
                f.getIdVendedor(), f.getEstadoPublicacion(), f.getTipoVenta(), f.getTitulo(), pageable);
    }

    // Hooks
    @Override
    protected Publicacion buscarPorId(Long id) {
        Publicacion p = super.buscarPorId(id);
        if (p.getEstadoPublicacion() == EstadoPublicacion.ELIMINADA) {
            throw new EntityNotFoundException(nombreEntidad + " no encontrado: " + id);
        }
        return p;
    }

    //Validaciones

    private void exigirDueno(Publicacion p) {
        if (!UsuarioActual.esAdmin() && !UsuarioActual.id().equals(p.getIdVendedor())) {
            throw new AccessDeniedException("Solo el vendedor puede gestionar su publicacion");
        }
    }

    private void exigirConDetalles(List<DetallePublicacionCreateDTO> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            throw new BadRequestException("La publicacion necesita al menos un detalle");
        }
    }

    private void exigirSinRepetidos(List<DetallePublicacionCreateDTO> items) {
        Set<Long> vistos = new HashSet<>();
        items.forEach(i -> {
            if (!vistos.add(i.getIdProducto())) {
                throw new ConflictException("Producto repetido en los detalles: " + i.getIdProducto());
            }
        });
    }

    private void exigirNoVendida(Publicacion p, String motivo) {
        if (p.getEstadoPublicacion() == EstadoPublicacion.VENDIDA) {
            throw new ConflictException("La publicacion " + p.getId() + " esta VENDIDA y " + motivo);
        }
    }

    private void exigirTransicionValida(Long id, EstadoPublicacion actual, EstadoPublicacion nuevo) {
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
    }

    private void exigirStockAlReactivar(Publicacion p) {
        p.getDetallePublicacion().stream()
                .sorted(Comparator.comparing(d -> d.getProducto().getId()))
                .forEach(d -> validator.validar(p,
                        productoService.obtenerEntidadBloqueada(d.getProducto().getId()),
                        d.getCantidadPublicada()));
    }

    private void exigirSinReservasVigentes(Publicacion p) {
        p.getDetallePublicacion().stream()
                .map(DetallePublicacion::getId)
                .sorted()
                .forEach(detalleRepository::findByIdForUpdate);
        if (reservaRepository.existsPorPublicacionYEstado(p.getId(), EstadoReserva.RESERVADA)) {
            throw new ConflictException("La publicacion " + p.getId() + " tiene reservas vigentes (compras en curso)");
        }
    }

    //Helpers
    private void sincronizarDetalles(Publicacion pub, List<DetallePublicacionCreateDTO> items) {
        exigirSinRepetidos(items);
        Map<Long, DetallePublicacion> existentes = pub.getDetallePublicacion().stream()
                .collect(Collectors.toMap(d -> d.getProducto().getId(), Function.identity()));

        // Orden fijo por producto evita deadlocks entre publicaciones concurrentes
        items.stream()
                .sorted(Comparator.comparing(DetallePublicacionCreateDTO::getIdProducto))
                .forEach(item -> aplicarDetalle(pub, item, existentes.remove(item.getIdProducto())));

        // Los que quedaron en el mapa ya no vienen en el request se quitan si no tienen reservas
        existentes.values().forEach(validator::validarSinReservas);
        pub.getDetallePublicacion().removeAll(existentes.values());
    }

    private void aplicarDetalle(Publicacion pub, DetallePublicacionCreateDTO item, DetallePublicacion existente) {
        Producto prod = productoService.obtenerEntidadBloqueada(item.getIdProducto());
        validator.validar(pub, prod, item.getCantidadPublicada());

        DetallePublicacion d = existente;
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

    private int rechazarOfertasPendientes(Publicacion p) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Oferta> pendientes = p.getOfertas().stream()
                .filter(o -> o.getEstadoOferta() == EstadoOferta.PENDIENTE)
                .toList();
        pendientes.forEach(o -> {
            o.setEstadoOferta(EstadoOferta.RECHAZADA);
            o.setFechaRespuesta(ahora);
        });
        cacheInvalidator.evictOfertas(pendientes.stream().map(Oferta::getId).toList());
        return pendientes.size();
    }
}