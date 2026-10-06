package py.com.ms1lvccatalogo.Service.base;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 *
 * @param <DC>
 * @param <DU>
 * @param <DO>
 * @param <F>
 */
public interface IBaseService<DC, DU, DO, F> {
    DO crear(DC dto);
    DO actualizar(Long id, DU dto);
    DO obtener(Long id);
    Page<DO> buscar(F filtro, Pageable pageable);
}