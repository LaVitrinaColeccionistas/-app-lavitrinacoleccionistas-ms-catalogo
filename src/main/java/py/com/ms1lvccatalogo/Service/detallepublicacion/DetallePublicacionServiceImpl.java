package py.com.ms1lvccatalogo.Service.detallepublicacion;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionCreateDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionPageDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.ms1lvccatalogo.Repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.Service.producto.IProductoService;
import py.com.ms1lvccatalogo.Service.publicacion.IPublicacionService;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.mapper.DetallePublicacionMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetallePublicacionServiceImpl implements IDetallePublicacionService {

    private final IDetallePublicacionRepository repository;
    private final DetallePublicacionMapper mapper;
    private final IPublicacionService publicacionService;
    private final IProductoService productoService;

    @Override
    @Transactional
    public DetallePublicacionDTO crear(Long idPublicacion, DetallePublicacionCreateDTO dto) {
        log.debug("Agregando producto {} a publicacion {}", dto.getIdProducto(), idPublicacion);
        Publicacion publicacion = publicacionService.obtenerEntidad(idPublicacion);
        Producto producto = productoService.obtenerEntidad(dto.getIdProducto());

        if (repository.existsByPublicacionIdAndProductoId(idPublicacion, dto.getIdProducto())) {
            throw new ConflictException("El producto ya esta en la publicacion");
        }
        validarReglas(publicacion, producto, dto.getCantidadPublicada());

        DetallePublicacion detalle = mapper.toEntity(dto);
        detalle.setPublicacion(publicacion);
        detalle.setProducto(producto);
        return mapper.toDTO(repository.save(detalle));
    }

    @Override
    @Transactional
    public DetallePublicacionDTO actualizar(Long idPublicacion, Long idDetalle, DetallePublicacionUpdateDTO dto) {
        log.debug("Actualizando detalle {} de publicacion {}", idDetalle, idPublicacion);
        publicacionService.obtenerEntidad(idPublicacion);
        DetallePublicacion detalle = repository.findByIdAndPublicacionId(idDetalle, idPublicacion)
                .orElseThrow(() -> new EntityNotFoundException("Detalle no encontrado: " + idDetalle));

        mapper.actualizarEntity(dto, detalle);
        validarReglas(detalle.getPublicacion(), detalle.getProducto(), detalle.getCantidadPublicada());
        return mapper.toDTO(repository.save(detalle));
    }

    @Override
    @Transactional(readOnly = true)
    public DetallePublicacionPageDTO listar(Long idPublicacion, Pageable pageable) {
        publicacionService.obtenerEntidad(idPublicacion);
        Page<DetallePublicacionDTO> p = repository.findByPublicacionId(idPublicacion, pageable)
                .map(mapper::toDTO);
        DetallePublicacionPageDTO out = new DetallePublicacionPageDTO();
        out.setContent(p.getContent());
        out.setTotalElements(p.getTotalElements());
        out.setTotalPages(p.getTotalPages());
        out.setSize(p.getSize());
        out.setNumber(p.getNumber());
        return out;
    }

    private void validarReglas(Publicacion pub, Producto prod, Integer cantidad) {
        if (!pub.getIdVendedor().equals(prod.getIdVendedor())) {
            throw new ConflictException("El producto no pertenece al vendedor de la publicacion");
        }
        if (prod.getStock() != null && cantidad > prod.getStock()) {
            throw new ConflictException("La cantidad supera el stock disponible");
        }
    }
}