package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.MappingTarget;

/**
 *
 * @param <E> entidad
 * @param <DC> create DTO
 * @param <DU> update DTO
 * @param <DO> salidad DTO
 */
public interface IBaseMapper<E, DC, DU, DO> {
    E toEntity(DC dto);
    DO toDTO(E entity);
    void actualizarEntity(DU dto, @MappingTarget E entity);
}