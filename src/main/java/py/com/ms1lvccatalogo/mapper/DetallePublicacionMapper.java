package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionDTO;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.ms1lvccatalogo.Config.IBaseMapperConfig;

@Mapper(config = IBaseMapperConfig.class, uses = ProductoMapper.class)
public interface DetallePublicacionMapper {

    @Mapping(target = "idPublicacion", source = "publicacion.id")
    @Mapping(target = "cantidadDisponible", expression = "java(disponible(entity))")
    DetallePublicacionDTO toDTO(DetallePublicacion entity);

    default Integer disponible(DetallePublicacion d) {
        if (d.getCantidadPublicada() == null) return null;
        int reservada = d.getCantidadReservada() == null ? 0 : d.getCantidadReservada();
        return Math.max(0, d.getCantidadPublicada() - reservada);
    }
}