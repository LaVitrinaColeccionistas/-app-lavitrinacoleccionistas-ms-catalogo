package py.com.ms1lvccatalogo.repository;

import org.springframework.data.domain.Page;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.com.lavitrinacoleccionistas.entity.Producto;

import java.util.Optional;

public interface IProductoRepository extends IBaseRepository<Producto, Long> {

    @Query("""
       SELECT p FROM Producto p
       WHERE p.activo = true
       AND (:idVendedor IS NULL OR p.idVendedor = :idVendedor)
       AND (:categoria  IS NULL OR p.categoria = :categoria)
       AND (:rareza     IS NULL OR p.rareza = :rareza)
       AND (:nombre     IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
       """)
    Page<Producto> buscarConFiltros(@Param("idVendedor") Long idVendedor,
                                    @Param("categoria") String categoria,
                                    @Param("rareza") String rareza,
                                    @Param("nombre") String nombre,
                                    Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> findByIdForUpdate(@Param("id") Long id);
}