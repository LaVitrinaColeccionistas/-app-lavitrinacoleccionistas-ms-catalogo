package py.com.ms1lvccatalogo.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;

@SuppressWarnings("JpaQlInspection")
public interface IOfertaRepository extends IBaseRepository<Oferta, Long> {
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
