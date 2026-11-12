package py.com.ms1lvccatalogo.service.base;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.entity.BaseEntity;
import py.com.ms1lvccatalogo.mapper.IBaseMapper;
import py.com.ms1lvccatalogo.repository.IBaseRepository;

/**
 * CRUD generico
 *
 * @param <T>  entidad
 * @param <DC> DTO de creacion
 * @param <DU> DTO de actualizacion
 * @param <DO> DTO de salida
 * @param <F>  filtro de busqueda
 */
@Slf4j
public abstract class BaseServiceImpl<T extends BaseEntity, DC, DU, DO, F>
        implements IBaseService<DC, DU, DO, F> {

    protected final IBaseRepository<T, Long> repository;
    protected final IBaseMapper<T, DC, DU, DO> mapper;
    protected final String nombreEntidad;

    protected BaseServiceImpl(IBaseRepository<T, Long> repository,
                              IBaseMapper<T, DC, DU, DO> mapper,
                              String nombreEntidad) {
        this.repository = repository;
        this.mapper = mapper;
        this.nombreEntidad = nombreEntidad;
    }

    @Override
    @Transactional(readOnly = true)
    public DO obtener(Long id) {
        log.debug("Obteniendo {} id={}", nombreEntidad, id);
        return mapper.toDTO(buscarPorId(id));
    }

    @Override
    @Transactional
    public DO crear(DC dto) {
        T guardado = repository.save(mapper.toEntity(dto));
        log.info("{} creado id={}", nombreEntidad, guardado.getId());
        return mapper.toDTO(guardado);
    }

    @Override
    @Transactional
    public DO actualizar(Long id, DU dto) {
        T entity = buscarPorId(id);
        mapper.actualizarEntity(dto, entity);
        log.info("{} actualizado id={}", nombreEntidad, id);
        return mapper.toDTO(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DO> buscar(F filtro, Pageable pageable) {
        log.debug("Buscando {} filtro={} pagina={}", nombreEntidad, filtro, pageable);
        return buscarEntidades(filtro, pageable).map(mapper::toDTO);
    }

    @Transactional(readOnly = true)
    public T obtenerEntidad(Long id) {
        return buscarPorId(id);
    }

    protected abstract Page<T> buscarEntidades(F filtro, Pageable pageable);

    protected T buscarPorId(Long id) {
        log.debug("findById {} id={}", nombreEntidad, id);
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(nombreEntidad + " no encontrado: " + id));
    }
}