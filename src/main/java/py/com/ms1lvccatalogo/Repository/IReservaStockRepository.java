package py.com.ms1lvccatalogo.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.ReservaStock;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.lavitrinacoleccionistas.enums.TipoOrigenReserva;

import java.time.LocalDateTime;
import java.util.List;

public interface IReservaStockRepository extends IBaseRepository<ReservaStock, Long> {

    List<ReservaStock> findByTipoOrigenAndIdOrigen(TipoOrigenReserva tipoOrigen, Long idOrigen);

    boolean existsByTipoOrigenAndIdOrigen(TipoOrigenReserva tipoOrigen, Long idOrigen);

    List<ReservaStock> findByEstadoReservaAndFechaExpiracionBefore(EstadoReserva estadoReserva,
                                                                   LocalDateTime fecha);

    /** Unidades reservadas de un detalle de publicacion. */
    @Query("""
       SELECT COALESCE(SUM(r.cantidad), 0)
       FROM ReservaStock r
       WHERE r.detallePublicacion.id = :idDetalle
       AND r.estadoReserva = :estado
       """)
    Long sumarCantidadPorEstado(@Param("idDetalle") Long idDetalle,
                                @Param("estado") EstadoReserva estado);

    /** Unidades reservadas del stock libre (no publicado) de un producto. */
    @Query("""
       SELECT COALESCE(SUM(r.cantidad), 0)
       FROM ReservaStock r
       WHERE r.producto.id = :idProducto
       AND r.detallePublicacion IS NULL
       AND r.estadoReserva = :estado
       """)
    Long sumarLibresPorProducto(@Param("idProducto") Long idProducto,
                                @Param("estado") EstadoReserva estado);

    @Query("""
       SELECT COUNT(r) > 0
       FROM ReservaStock r
       WHERE r.producto.id = :idProducto
       AND r.estadoReserva = :estado
       """)
    boolean existsPorProductoYEstado(@Param("idProducto") Long idProducto,
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
