package py.com.ms1lvccatalogo.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;

import java.time.LocalDateTime;

@SuppressWarnings("JpaQlInspection")
public interface IOfertaRepository extends IBaseRepository<Oferta, Long> {

    /** Al aceptar una oferta, las demas PENDIENTE de la misma publicacion quedan RECHAZADA. */
    @Modifying
    @Query("""
       UPDATE Oferta o
       SET o.estadoOferta = py.com.lavitrinacoleccionistas.enums.EstadoOferta.RECHAZADA,
           o.fechaRespuesta = :fecha
       WHERE o.publicacion.id = :idPublicacion
       AND o.id <> :idAceptada
       AND o.estadoOferta = py.com.lavitrinacoleccionistas.enums.EstadoOferta.PENDIENTE
       """)
    int rechazarPendientesDePublicacion(@Param("idPublicacion") Long idPublicacion,
                                        @Param("idAceptada") Long idAceptada,
                                        @Param("fecha") LocalDateTime fecha);

    @Query("""
       SELECT o FROM Oferta o
       WHERE (:idPublicacion IS NULL OR o.publicacion.id = :idPublicacion)
       AND (:idComprador IS NULL OR o.idComprador = :idComprador)
       AND (:estado IS NULL OR o.estadoOferta = :estado)
       """)
    Page<Oferta> buscarConFiltros(@Param("idPublicacion") Long idPublicacion,
                                  @Param("idComprador") Long idComprador,
                                  @Param("estado") EstadoOferta estado,
                                  Pageable pageable);
}
