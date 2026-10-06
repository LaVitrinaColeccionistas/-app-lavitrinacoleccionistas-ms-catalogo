package py.com.ms1lvccatalogo.Service.base;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import py.com.lavitrinacoleccionistas.entity.BaseEntity;
import py.com.ms1lvccatalogo.Repository.IBaseRepository;
import py.com.ms1lvccatalogo.mapper.IBaseMapper;

@Slf4j
public abstract class BaseServiceImpl<T extends BaseEntity, DC, DU, DO, F>
        implements IBaseService<DC, DU, DO, F> {

    protected final IBaseRepository<T, Long> repository;
    protected final IBaseMapper<T, DC, DU, DO> mapper;
    protected final String nombreEntidad;

    protected BaseServiceImpl(IBaseRepository<T, Long> repository,
                              IBaseMapper<T, DC, DU, DO> mapper, String nombreEntidad) {
        this.repository = repository;
        this.mapper = mapper;
        this.nombreEntidad = nombreEntidad;
    }

    protected abstract Page<T> buscarEntidades(F filtro, Pageable pageable);

    @Override
    @Transactional
    public DO crear(DC dto) {
        log.debug("Creando {}", nombreEntidad);
        return mapper.toDTO(repository.save(mapper.toEntity(dto)));
    }

    @Override
    @Transactional()
    public DO obtener(Long id) {
        log.debug("Obteniendo {} {}", nombreEntidad, id);
        return mapper.toDTO(buscarPorId(id));
    }

    @Override
    @Transactional
    public DO actualizar(Long id, DU dto) {
        T entity = buscarPorId(id);
        log.debug("Actualizando {} {}", nombreEntidad, id);
        mapper.actualizarEntity(dto, entity);
        return mapper.toDTO(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DO> buscar(F filtro, Pageable pageable) {
        log.debug("Buscando {} filtro={}", nombreEntidad, filtro);
        return buscarEntidades(filtro, pageable).map(mapper::toDTO);
    }

    protected T buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(nombreEntidad + " no encontrado: " + id));
    }
}