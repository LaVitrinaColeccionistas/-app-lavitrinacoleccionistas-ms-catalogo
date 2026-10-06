package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionCreateDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.ms1lvccatalogo.Config.IBaseMapperConfig;

@Mapper(config = IBaseMapperConfig.class, uses = ProductoMapper.class)
public interface DetallePublicacionMapper
        extends IBaseMapper<DetallePublicacion, DetallePublicacionCreateDTO,
        DetallePublicacionUpdateDTO, DetallePublicacionDTO> {

    @Override
    @Mapping(target = "idPublicacion", source = "publicacion.id")
    DetallePublicacionDTO toDTO(DetallePublicacion entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicacion", ignore = true)
    @Mapping(target = "producto", ignore = true)
    DetallePublicacion toEntity(DetallePublicacionCreateDTO dto);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicacion", ignore = true)
    @Mapping(target = "producto", ignore = true)
    void actualizarEntity(DetallePublicacionUpdateDTO dto, @MappingTarget DetallePublicacion entity);
}