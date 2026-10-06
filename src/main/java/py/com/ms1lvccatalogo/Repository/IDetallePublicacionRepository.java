package py.com.ms1lvccatalogo.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;

import java.util.Optional;

public interface IDetallePublicacionRepository extends IBaseRepository<DetallePublicacion, Long> {
    Page<DetallePublicacion> findByPublicacionId(Long idPublicacion, Pageable pageable);
    Optional<DetallePublicacion> findByIdAndPublicacionId(Long id, Long idPublicacion);
    boolean existsByPublicacionIdAndProductoId(Long idPublicacion, Long idProducto);
}