package py.com.ms1lvccatalogo.Service.publicacion;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.*;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.ms1lvccatalogo.Repository.IPublicacionRepository;
import py.com.ms1lvccatalogo.Service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.Service.detallepublicacion.DetallePublicacionValidator;
import py.com.ms1lvccatalogo.Service.producto.IProductoService;
import py.com.ms1lvccatalogo.exception.BadRequestException;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.filtro.PublicacionFiltro;
import py.com.ms1lvccatalogo.mapper.PublicacionMapper;

import java.math.BigDecimal;
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

    private final IPublicacionRepository publicacionRepository;
    private final IProductoService productoService;
    private final DetallePublicacionValidator validator;

    public PublicacionServiceImpl(IPublicacionRepository publicacionRepository, PublicacionMapper mapper,
                                  IProductoService productoService, DetallePublicacionValidator validator) {
        super(publicacionRepository, mapper, "Publicacion");
        this.publicacionRepository = publicacionRepository;
        this.productoService = productoService;
        this.validator = validator;
    }

    @Override
    @Transactional
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
    public PublicacionDTO actualizar(Long id, PublicacionUpdateDTO dto) {
        Publicacion p = buscarPorId(id);
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

        for (DetallePublicacionCreateDTO item : items) {
            if (!vistos.add(item.getIdProducto())) {
                throw new ConflictException("Producto repetido en los detalles: " + item.getIdProducto());
            }
            Producto prod = productoService.obtenerEntidad(item.getIdProducto());
            validator.validar(pub, prod, item.getCantidadPublicada());

            DetallePublicacion d = existentes.remove(item.getIdProducto());
            if (d == null) {
                d = new DetallePublicacion();
                d.setPublicacion(pub);
                d.setProducto(prod);
                pub.getDetallePublicacion().add(d);
            }
            d.setPrecioUnitario(BigDecimal.valueOf(item.getPrecioUnitario()));
            d.setCantidadPublicada(item.getCantidadPublicada());
        }
        pub.getDetallePublicacion().removeAll(existentes.values());
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
        log.debug("Publicacion {}: {} ; {}", id, p.getEstadoPublicacion(), dto.getEstadoPublicacion());
        p.setEstadoPublicacion(EstadoPublicacion.valueOf(dto.getEstadoPublicacion().name()));
        return mapper.toDTO(repository.save(p));
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