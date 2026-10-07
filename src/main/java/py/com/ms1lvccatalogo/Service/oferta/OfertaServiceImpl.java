package py.com.ms1lvccatalogo.Service.oferta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import py.com.ms1lvccatalogo.Repository.IOfertaRepository;
import py.com.ms1lvccatalogo.Service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.Service.publicacion.IPublicacionService;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.filtro.OfertaFiltro;
import py.com.ms1lvccatalogo.mapper.OfertaMapper;

import java.time.LocalDateTime;

@Slf4j
@Service
public class OfertaServiceImpl
        extends BaseServiceImpl<Oferta, OfertaCreateDTO, OfertaUpdateDTO, OfertaDTO, OfertaFiltro>
        implements IOfertaService {

    private final IOfertaRepository ofertaRepository;
    private final IPublicacionService publicacionService;

    public OfertaServiceImpl(IOfertaRepository ofertaRepository, OfertaMapper mapper,
                             IPublicacionService publicacionService) {
        super(ofertaRepository, mapper, "Oferta");
        this.ofertaRepository = ofertaRepository;
        this.publicacionService = publicacionService;
    }

    @Override
    @Transactional
    public OfertaDTO crear(OfertaCreateDTO dto) {
        log.debug("Creando oferta de comprador {} en publicacion {}", dto.getIdComprador(), dto.getIdPublicacion());
        Publicacion pub = publicacionService.obtenerEntidad(dto.getIdPublicacion());

        if (pub.getEstadoPublicacion() != EstadoPublicacion.ACTIVA) {
            throw new ConflictException("La publicacion no esta activa");
        }
        if (pub.getIdVendedor().equals(dto.getIdComprador())) {
            throw new ConflictException("El vendedor no puede ofertar en su propia publicacion");
        }

        Oferta oferta = mapper.toEntity(dto);
        oferta.setPublicacion(pub);
        return mapper.toDTO(repository.save(oferta));
    }

    @Override
    @Transactional
    public OfertaDTO actualizar(Long id, OfertaUpdateDTO dto) {
        Oferta oferta = buscarPorId(id);
        exigirPendiente(oferta);
        log.debug("Actualizando monto de oferta {}", id);
        mapper.actualizarEntity(dto, oferta);
        return mapper.toDTO(repository.save(oferta));
    }

    @Override
    @Transactional
    public OfertaDTO aceptar(Long id) {
        return responder(id, EstadoOferta.ACEPTADA);
    }

    @Override
    @Transactional
    public OfertaDTO rechazar(Long id) {
        return responder(id, EstadoOferta.RECHAZADA);
    }

    private OfertaDTO responder(Long id, EstadoOferta nuevoEstado) {
        Oferta oferta = buscarPorId(id);
        exigirPendiente(oferta);
        log.debug("Oferta {}: PENDIENTE -> {}", id, nuevoEstado);
        oferta.setEstadoOferta(nuevoEstado);
        oferta.setFechaRespuesta(LocalDateTime.now());
        return mapper.toDTO(repository.save(oferta));
    }

    private void exigirPendiente(Oferta oferta) {
        if (oferta.getEstadoOferta() != EstadoOferta.PENDIENTE) {
            throw new ConflictException("La oferta no esta PENDIENTE (estado actual: " + oferta.getEstadoOferta() + ")");
        }
    }

    @Override
    protected Page<Oferta> buscarEntidades(OfertaFiltro f, Pageable pageable) {
        return ofertaRepository.buscarConFiltros(
                f.getIdPublicacion(), f.getIdComprador(), f.getEstadoOferta(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public OfertaPageDTO buscarPagina(OfertaFiltro filtro, Pageable pageable) {
        Page<OfertaDTO> p = buscar(filtro, pageable);
        OfertaPageDTO out = new OfertaPageDTO();
        out.setContent(p.getContent());
        out.setTotalElements(p.getTotalElements());
        out.setTotalPages(p.getTotalPages());
        out.setSize(p.getSize());
        out.setNumber(p.getNumber());
        return out;
    }
}