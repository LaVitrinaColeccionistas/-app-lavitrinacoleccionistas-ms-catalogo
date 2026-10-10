package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionDTO;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.ms1lvccatalogo.Config.IBaseMapperConfig;

@Mapper(config = IBaseMapperConfig.class, uses = ProductoMapper.class)
public interface DetallePublicacionMapper {

    @Mapping(target = "idPublicacion", source = "publicacion.id")
    DetallePublicacionDTO toDTO(DetallePublicacion entity);
}