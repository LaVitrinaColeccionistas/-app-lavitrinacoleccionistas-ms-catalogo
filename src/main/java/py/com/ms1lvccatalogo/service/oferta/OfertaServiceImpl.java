package py.com.ms1lvccatalogo.service.oferta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.OfertaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaPageDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.ms1lvccatalogo.cache.CatalogoCacheInvalidator;
import py.com.ms1lvccatalogo.config.RedisCacheConfig;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.filtro.OfertaFiltro;
import py.com.ms1lvccatalogo.mapper.OfertaMapper;
import py.com.ms1lvccatalogo.repository.IOfertaRepository;
import py.com.ms1lvccatalogo.security.UsuarioActual;
import py.com.ms1lvccatalogo.service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.service.publicacion.IPublicacionService;
import py.com.ms1lvccatalogo.service.reserva.IReservaService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
public class OfertaServiceImpl
        extends BaseServiceImpl<Oferta, OfertaCreateDTO, OfertaUpdateDTO, OfertaDTO, OfertaFiltro>
        implements IOfertaService {

    private final IOfertaRepository ofertaRepository;
    private final OfertaMapper ofertaMapper;
    private final IPublicacionService publicacionService;
    private final IReservaService reservaService;
    private final CatalogoCacheInvalidator cacheInvalidator;

    public OfertaServiceImpl(IOfertaRepository ofertaRepository, OfertaMapper ofertaMapper,
                             IPublicacionService publicacionService, IReservaService reservaService,
                             CatalogoCacheInvalidator cacheInvalidator) {
        super(ofertaRepository, ofertaMapper, "Oferta");
        this.ofertaRepository = ofertaRepository;
        this.ofertaMapper = ofertaMapper;
        this.publicacionService = publicacionService;
        this.reservaService = reservaService;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.OFERTAS, key = "#id")
    public OfertaDTO obtener(Long id) {
        return super.obtener(id);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.OFERTAS, key = "#result.id")
    public OfertaDTO crear(OfertaCreateDTO dto) {
        Publicacion pub = publicacionService.obtenerEntidad(dto.getIdPublicacion());
        exigirOfertable(pub, dto.getIdComprador());

        Oferta oferta = mapper.toEntity(dto);
        oferta.setPublicacion(pub);
        Oferta guardada = repository.save(oferta);
        log.info("Oferta creada id={} publicacion={} comprador={}",
                guardada.getId(), pub.getId(), dto.getIdComprador());
        return mapper.toDTO(guardada);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.OFERTAS, key = "#result.id")
    public OfertaDTO actualizar(Long id, OfertaUpdateDTO dto) {
        Oferta oferta = buscarPorId(id);
        exigirComprador(oferta);
        exigirPendiente(oferta);
        mapper.actualizarEntity(dto, oferta);
        log.info("Oferta actualizada id={}", id);
        return mapper.toDTO(repository.save(oferta));
    }

    //flujo de negocio
    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.OFERTAS, key = "#result.id")
    public OfertaDTO aceptar(Long id) {
        Oferta oferta = buscarPorId(id);
        exigirVendedor(oferta);
        exigirPendiente(oferta);
        exigirPublicacionActiva(oferta.getPublicacion());

        reservaService.reservarParaOferta(oferta);
        marcar(oferta, EstadoOferta.ACEPTADA);
        int rechazadas = rechazarOtrasPendientes(oferta);

        log.info("Oferta aceptada id={} publicacion={} comprador={} rechazadas={}",
                id, oferta.getPublicacion().getId(), oferta.getIdComprador(), rechazadas);
        return mapper.toDTO(oferta);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.OFERTAS, key = "#result.id")
    public OfertaDTO rechazar(Long id) {
        Oferta oferta = buscarPorId(id);
        exigirVendedor(oferta);
        exigirPendiente(oferta);
        marcar(oferta, EstadoOferta.RECHAZADA);
        log.info("Oferta rechazada id={}", id);
        return mapper.toDTO(oferta);
    }

    //Consultas
    @Override
    @Transactional(readOnly = true)
    public OfertaPageDTO buscarPagina(OfertaFiltro filtro, Pageable pageable) {
        return ofertaMapper.toPageDTO(buscar(filtro, pageable));
    }

    @Override
    protected Page<Oferta> buscarEntidades(OfertaFiltro f, Pageable pageable) {
        // Quien no es ADMIN solo ve ofertas en las que participa (como comprador o como vendedor)
        Long idParticipante = UsuarioActual.esAdmin() ? null : UsuarioActual.id();
        LocalDateTime desde = f.getFechaDesde() == null ? null : f.getFechaDesde().atStartOfDay();
        LocalDateTime hasta = f.getFechaHasta() == null ? null : f.getFechaHasta().atTime(LocalTime.MAX);
        return ofertaRepository.buscarConFiltros(
                f.getIdPublicacion(), f.getIdComprador(), f.getIdVendedor(), f.getEstadoOferta(),
                idParticipante, f.getMontoMin(), f.getMontoMax(), desde, hasta, pageable);
    }

    @Override
    public void exigirAcceso(OfertaDTO oferta) {
        if (UsuarioActual.esAdmin()) return;
        Long usuario = UsuarioActual.id();
        if (usuario.equals(oferta.getIdComprador())) return;
        if (!ofertaRepository.existsByIdAndPublicacion_IdVendedor(oferta.getId(), usuario)) {
            throw new AccessDeniedException("No participás en esta oferta");
        }
    }

    // Validaciones

    private void exigirOfertable(Publicacion pub, Long idComprador) {
        if (!UsuarioActual.esAdmin() && !idComprador.equals(UsuarioActual.id())) {
            throw new AccessDeniedException("Solo podés ofertar a tu nombre");
        }
        exigirPublicacionActiva(pub);
        if (pub.getIdVendedor().equals(idComprador)) {
            throw new ConflictException("El vendedor no puede ofertar en su propia publicacion");
        }
        if (ofertaRepository.existsByPublicacionIdAndEstadoOferta(pub.getId(), EstadoOferta.ACEPTADA)) {
            throw new ConflictException("La publicacion ya tiene una oferta aceptada");
        }
    }

    private void exigirPublicacionActiva(Publicacion pub) {
        if (pub.getEstadoPublicacion() != EstadoPublicacion.ACTIVA) {
            throw new ConflictException("La publicacion no esta activa");
        }
    }

    private void exigirPendiente(Oferta oferta) {
        if (oferta.getEstadoOferta() != EstadoOferta.PENDIENTE) {
            throw new ConflictException("La oferta no esta PENDIENTE (estado actual: " + oferta.getEstadoOferta() + ")");
        }
    }

    private void exigirVendedor(Oferta oferta) {
        if (!oferta.getPublicacion().getIdVendedor().equals(UsuarioActual.id())) {
            throw new AccessDeniedException("Solo el vendedor de la publicacion puede responder la oferta");
        }
    }

    private void exigirComprador(Oferta oferta) {
        if (!UsuarioActual.esAdmin() && !oferta.getIdComprador().equals(UsuarioActual.id())) {
            throw new AccessDeniedException("Solo el comprador puede modificar su oferta");
        }
    }

    //Helpers
    private void marcar(Oferta oferta, EstadoOferta estado) {
        log.debug("Oferta {}: {} -> {}", oferta.getId(), oferta.getEstadoOferta(), estado);
        oferta.setEstadoOferta(estado);
        oferta.setFechaRespuesta(LocalDateTime.now());
    }

    private int rechazarOtrasPendientes(Oferta aceptada) {
        List<Oferta> pendientes = ofertaRepository.findByPublicacionIdAndEstadoOfertaAndIdNot(
                aceptada.getPublicacion().getId(), EstadoOferta.PENDIENTE, aceptada.getId());
        pendientes.forEach(o -> marcar(o, EstadoOferta.RECHAZADA));
        cacheInvalidator.evictOfertas(pendientes.stream().map(Oferta::getId).toList());
        return pendientes.size();
    }
}