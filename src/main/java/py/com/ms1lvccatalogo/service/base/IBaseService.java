package py.com.ms1lvccatalogo.service.base;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * @param <DC> DTO de creacion
 * @param <DU> DTO de actualizacion
 * @param <DO> DTO de salida
 * @param <F>  filtro de busqueda
 */
public interface IBaseService<DC, DU, DO, F> {
    DO obtener(Long id);
    DO crear(DC dto);
    DO actualizar(Long id, DU dto);
    Page<DO> buscar(F filtro, Pageable pageable);
}