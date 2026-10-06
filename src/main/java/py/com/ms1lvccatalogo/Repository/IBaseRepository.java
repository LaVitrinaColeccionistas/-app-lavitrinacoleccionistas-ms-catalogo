package py.com.ms1lvccatalogo.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import py.com.lavitrinacoleccionistas.entity.BaseEntity;

/**
 *
 * @param <T>
 * @param <ID>
 */
public interface IBaseRepository<T extends BaseEntity, ID> extends JpaRepository<T, ID> {
}