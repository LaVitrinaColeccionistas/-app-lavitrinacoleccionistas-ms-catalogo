package py.com.ms1lvccatalogo.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;

import java.util.List;
import java.util.Optional;

public interface IDetallePublicacionRepository extends IBaseRepository<DetallePublicacion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DetallePublicacion d WHERE d.id = :id")
    Optional<DetallePublicacion> findByIdForUpdate(@Param("id") Long id);

    @Query("""
       SELECT COALESCE(SUM(d.cantidadPublicada), 0)
       FROM DetallePublicacion d
       WHERE d.producto.id = :idProducto
       AND d.publicacion.estadoPublicacion IN :estados
       AND d.publicacion.id <> :idPublicacionExcluida
       """)
    Long sumarPublicadoPorProducto(@Param("idProducto") Long idProducto,
                                   @Param("estados") List<EstadoPublicacion> estados,
                                   @Param("idPublicacionExcluida") Long idPublicacionExcluida);
    Page<DetallePublicacion> findByPublicacionId(Long idPublicacion, Pageable pageable);
}