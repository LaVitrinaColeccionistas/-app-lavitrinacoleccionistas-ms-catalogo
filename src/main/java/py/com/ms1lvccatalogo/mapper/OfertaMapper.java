package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import py.com.lavitrinacoleccionistas.dto.OfertaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaPageDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.ms1lvccatalogo.config.IBaseMapperConfig;

@Mapper(config = IBaseMapperConfig.class)
public interface OfertaMapper
        extends IBaseMapper<Oferta, OfertaCreateDTO, OfertaUpdateDTO, OfertaDTO> {

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicacion", ignore = true)
    @Mapping(target = "estadoOferta", constant = "PENDIENTE")
    @Mapping(target = "fechaOferta", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "fechaRespuesta", ignore = true)
    Oferta toEntity(OfertaCreateDTO dto);

    @Override
    @Mapping(target = "idPublicacion", source = "publicacion.id")
    OfertaDTO toDTO(Oferta entity);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicacion", ignore = true)
    @Mapping(target = "idComprador", ignore = true)
    @Mapping(target = "estadoOferta", ignore = true)
    @Mapping(target = "fechaOferta", ignore = true)
    @Mapping(target = "fechaRespuesta", ignore = true)
    void actualizarEntity(OfertaUpdateDTO dto, @MappingTarget Oferta entity);

    default OfertaPageDTO toPageDTO(Page<OfertaDTO> page) {
        OfertaPageDTO out = new OfertaPageDTO();
        out.setContent(page.getContent());
        out.setTotalElements(page.getTotalElements());
        out.setTotalPages(page.getTotalPages());
        out.setSize(page.getSize());
        out.setNumber(page.getNumber());
        return out;
    }
}