package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;
import py.com.lavitrinacoleccionistas.entity.ReservaStock;
import py.com.ms1lvccatalogo.config.IBaseMapperConfig;

import java.util.List;

@Mapper(config = IBaseMapperConfig.class)
public interface ReservaStockMapper {

    @Mapping(target = "origen", source = "tipoOrigen")
    @Mapping(target = "idOrden", source = "idOrden")          // getter @Transient de la entidad
    @Mapping(target = "idProducto", source = "producto.id")
    @Mapping(target = "idDetallePublicacion", source = "detallePublicacion.id")
    ReservaDTO toDTO(ReservaStock entity);

    List<ReservaDTO> toDTO(List<ReservaStock> entities);
}
