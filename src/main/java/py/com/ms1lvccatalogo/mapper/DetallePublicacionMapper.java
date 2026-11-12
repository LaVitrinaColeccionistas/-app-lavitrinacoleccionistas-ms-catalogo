package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.mapstruct.Mapping;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionPageDTO;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.ms1lvccatalogo.config.IBaseMapperConfig;

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

    default DetallePublicacionPageDTO toPageDTO(Page<DetallePublicacionDTO> page) {
        DetallePublicacionPageDTO out = new DetallePublicacionPageDTO();
        out.setContent(page.getContent());
        out.setTotalElements(page.getTotalElements());
        out.setTotalPages(page.getTotalPages());
        out.setSize(page.getSize());
        out.setNumber(page.getNumber());
        return out;
    }
}