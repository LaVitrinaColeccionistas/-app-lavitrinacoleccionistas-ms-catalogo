package py.com.ms1lvccatalogo.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.ReservaStock;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;

import java.time.LocalDateTime;
import java.util.List;

public interface IReservaStockRepository extends IBaseRepository<ReservaStock, Long> {

    List<ReservaStock> findByIdOrden(Long idOrden);

    List<ReservaStock> findByEstadoReservaAndFechaExpiracionBefore(EstadoReserva estadoReserva,
                                                                   LocalDateTime fecha);

    @Query("""
       SELECT COALESCE(SUM(r.cantidad), 0)
       FROM ReservaStock r
       WHERE r.detallePublicacion.id = :idDetalle
       AND r.estadoReserva = :estado
       """)
    Long sumarCantidadPorEstado(@Param("idDetalle") Long idDetalle,
                                @Param("estado") EstadoReserva estado);

    @Query("""
       SELECT COUNT(r) > 0
       FROM ReservaStock r
       WHERE r.detallePublicacion.publicacion.id = :idPublicacion
       AND r.estadoReserva = :estado
       """)
    boolean existsPorPublicacionYEstado(@Param("idPublicacion") Long idPublicacion,
                                        @Param("estado") EstadoReserva estado);

    @Query("SELECT COUNT(r) > 0 FROM ReservaStock r WHERE r.detallePublicacion.id = :idDetalle")
    boolean existsPorDetalle(@Param("idDetalle") Long idDetalle);
}