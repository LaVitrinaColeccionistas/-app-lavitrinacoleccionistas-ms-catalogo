package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import py.com.lavitrinacoleccionistas.dto.PublicacionCreateDTO;
import py.com.lavitrinacoleccionistas.dto.PublicacionDTO;
import py.com.lavitrinacoleccionistas.dto.PublicacionUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.ms1lvccatalogo.Config.IBaseMapperConfig;

@Mapper(config = IBaseMapperConfig.class)
public interface PublicacionMapper
        extends IBaseMapper<Publicacion, PublicacionCreateDTO, PublicacionUpdateDTO, PublicacionDTO> {

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "detallePublicacion", ignore = true)
    @Mapping(target = "ofertas", ignore = true)
    @Mapping(target = "estadoPublicacion", constant = "ACTIVA")
    @Mapping(target = "fechaPublicacion", expression = "java(java.time.LocalDateTime.now())")
    Publicacion toEntity(PublicacionCreateDTO dto);

    @Override
    PublicacionDTO toDTO(Publicacion entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idVendedor", ignore = true)
    @Mapping(target = "estadoPublicacion", ignore = true)
    @Mapping(target = "fechaPublicacion", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "detallePublicacion", ignore = true)
    @Mapping(target = "ofertas", ignore = true)
    void actualizarEntity(PublicacionUpdateDTO dto, @MappingTarget Publicacion entity);
}
