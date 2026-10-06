package py.com.ms1lvccatalogo.Service.publicacion;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import py.com.lavitrinacoleccionistas.dto.*;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.ms1lvccatalogo.Repository.IPublicacionRepository;
import py.com.ms1lvccatalogo.Service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.filtro.PublicacionFiltro;
import py.com.ms1lvccatalogo.mapper.PublicacionMapper;

@Slf4j
@Service
public class PublicacionServiceImpl
        extends BaseServiceImpl<Publicacion, PublicacionCreateDTO, PublicacionUpdateDTO, PublicacionDTO, PublicacionFiltro>
        implements IPublicacionService {

    private final IPublicacionRepository publicacionRepository;

    public PublicacionServiceImpl(IPublicacionRepository publicacionRepository, PublicacionMapper mapper) {
        super(publicacionRepository, mapper, "Publicacion");
        this.publicacionRepository = publicacionRepository;
    }

    @Override
    protected Publicacion buscarPorId(Long id) {
        Publicacion p = super.buscarPorId(id);
        if (p.getEstadoPublicacion() == EstadoPublicacion.CANCELADA) {
            throw new EntityNotFoundException(nombreEntidad + " no encontrado: " + id);
        }
        return p;
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Publicacion p = buscarPorId(id);
        log.debug("Cancelando publicacion {}", id);
        p.setEstadoPublicacion(EstadoPublicacion.CANCELADA);
        repository.save(p);
    }

    @Override
    @Transactional
    public PublicacionDTO actualizarEstado(Long id, PublicacionEstadoUpdateDTO dto) {
        Publicacion p = super.buscarPorId(id);
        if (p.getEstadoPublicacion() == EstadoPublicacion.CANCELADA) {
            throw new ConflictException("La publicacion " + id + " esta CANCELADA y no admite cambios de estado");
        }
        log.debug("Publicacion {}: {} -> {}", id, p.getEstadoPublicacion(), dto.getEstadoPublicacion());
        p.setEstadoPublicacion(EstadoPublicacion.valueOf(dto.getEstadoPublicacion().name()));        return mapper.toDTO(repository.save(p));
    }

    @Override
    protected Page<Publicacion> buscarEntidades(PublicacionFiltro f, Pageable pageable) {
        return publicacionRepository.buscarConFiltros(
                f.getIdVendedor(), f.getEstadoPublicacion(), f.getTipoVenta(), f.getTitulo(), pageable);
    }

    @Override
    @Transactional()
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