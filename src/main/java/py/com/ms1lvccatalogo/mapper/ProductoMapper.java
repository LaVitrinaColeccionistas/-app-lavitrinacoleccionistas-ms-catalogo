package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import py.com.lavitrinacoleccionistas.dto.ProductoCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.ms1lvccatalogo.Config.IBaseMapperConfig;

@Mapper(config = IBaseMapperConfig.class, uses = { FechaMapper.class })
public interface ProductoMapper
        extends IBaseMapper<Producto, ProductoCreateDTO, ProductoUpdateDTO, ProductoDTO> {

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "detallesPublicacion", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "stock", defaultValue = "0")
    Producto toEntity(ProductoCreateDTO dto);

    @Override
    ProductoDTO toDTO(Producto entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idVendedor", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "detallesPublicacion", ignore = true)
    void actualizarEntity(ProductoUpdateDTO dto, @MappingTarget Producto entity);
}