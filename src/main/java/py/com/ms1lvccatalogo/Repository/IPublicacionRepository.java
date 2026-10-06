package py.com.ms1lvccatalogo.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.TipoVenta;

public interface IPublicacionRepository extends IBaseRepository<Publicacion, Long> {
    @Query("""
       SELECT p FROM Publicacion p
       WHERE p.estadoPublicacion <> py.com.lavitrinacoleccionistas.enums.EstadoPublicacion.CANCELADA
       AND (:idVendedor IS NULL OR p.idVendedor = :idVendedor)
       AND (:estado     IS NULL OR p.estadoPublicacion = :estado)
       AND (:tipoVenta  IS NULL OR p.tipoVenta = :tipoVenta)
       AND (:titulo     IS NULL OR LOWER(p.titulo) LIKE LOWER(CONCAT('%', CAST(:titulo AS string), '%')))
       """)
    Page<Publicacion> buscarConFiltros(@Param("idVendedor") Long idVendedor,
                                       @Param("estado") EstadoPublicacion estado,
                                       @Param("tipoVenta") TipoVenta tipoVenta,
                                       @Param("titulo") String titulo,
                                       Pageable pageable);
}