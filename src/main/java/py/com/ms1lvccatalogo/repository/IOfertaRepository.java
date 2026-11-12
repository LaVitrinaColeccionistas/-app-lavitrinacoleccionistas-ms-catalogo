package py.com.ms1lvccatalogo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@SuppressWarnings("JpaQlInspection")
public interface IOfertaRepository extends IBaseRepository<Oferta, Long> {

    boolean existsByPublicacionIdAndEstadoOferta(Long idPublicacion, EstadoOferta estado);
    List<Oferta> findByPublicacionIdAndEstadoOfertaAndIdNot(Long idPublicacion, EstadoOferta estado, Long idExcluida);

    boolean existsByIdAndPublicacion_IdVendedor(Long id, Long idVendedor);

    @Query("""
       SELECT o FROM Oferta o
       WHERE (:idPublicacion IS NULL OR o.publicacion.id = :idPublicacion)
       AND (:idComprador IS NULL OR o.idComprador = :idComprador)
       AND (:idVendedor IS NULL OR o.publicacion.idVendedor = :idVendedor)
       AND (:estado IS NULL OR o.estadoOferta = :estado)
       AND (:idParticipante IS NULL OR o.idComprador = :idParticipante
            OR o.publicacion.idVendedor = :idParticipante)
       AND o.montoOfertado >= COALESCE(:montoMin, o.montoOfertado)
       AND o.montoOfertado <= COALESCE(:montoMax, o.montoOfertado)
       AND o.fechaOferta >= COALESCE(:desde, o.fechaOferta)
       AND o.fechaOferta <= COALESCE(:hasta, o.fechaOferta)
   """)
    Page<Oferta> buscarConFiltros(@Param("idPublicacion") Long idPublicacion,
                                  @Param("idComprador") Long idComprador,
                                  @Param("idVendedor") Long idVendedor,
                                  @Param("estado") EstadoOferta estado,
                                  @Param("idParticipante") Long idParticipante,
                                  @Param("montoMin") BigDecimal montoMin,
                                  @Param("montoMax") BigDecimal montoMax,
                                  @Param("desde") LocalDateTime desde,
                                  @Param("hasta") LocalDateTime hasta,
                                  Pageable pageable);
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