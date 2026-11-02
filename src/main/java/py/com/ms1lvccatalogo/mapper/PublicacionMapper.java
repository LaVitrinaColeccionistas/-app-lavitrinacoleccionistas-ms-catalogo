package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import py.com.lavitrinacoleccionistas.dto.PublicacionCreateDTO;
import py.com.lavitrinacoleccionistas.dto.PublicacionDTO;
import py.com.lavitrinacoleccionistas.dto.PublicacionUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.ms1lvccatalogo.Config.IBaseMapperConfig;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Mapper(config = IBaseMapperConfig.class, uses = DetallePublicacionMapper.class)
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
    @Mapping(target = "detalles", source = "detallePublicacion")
    @Mapping(target = "precioDesde", expression = "java(precioDesde(entity))")
    @Mapping(target = "precioTotal", expression = "java(precioTotal(entity))")
    @Mapping(target = "ofertable", expression = "java(ofertable(entity))")
    PublicacionDTO toDTO(Publicacion entity);

    // ---- Precios calculados: una sola fuente para catalogo, detalle, ofertas e intercambios.
    //      Usan los detalles ya cargados (cantidadReservada viene del @Formula): sin consultas extra.

    /** Menor precio unitario entre lo que se puede comprar; si todo esta reservado, entre todos. */
    default Double precioDesde(Publicacion p) {
        List<DetallePublicacion> detalles = detallesDe(p);
        return detalles.stream()
                .filter(d -> disponible(d) > 0)
                .map(DetallePublicacion::getPrecioUnitario)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .or(() -> detalles.stream()
                        .map(DetallePublicacion::getPrecioUnitario)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder()))
                .map(BigDecimal::doubleValue)
                .orElse(null);
    }

    /** Valor del lote completo: lo que se reserva al aceptar una oferta. */
    default Double precioTotal(Publicacion p) {
        List<DetallePublicacion> detalles = detallesDe(p);
        if (detalles.isEmpty()) return null;
        return detalles.stream()
                .filter(d -> d.getPrecioUnitario() != null && d.getCantidadPublicada() != null)
                .map(d -> d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidadPublicada())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }

    /** Se puede ofertar si la publicacion esta activa, tiene unidades y nada esta reservado. */
    default Boolean ofertable(Publicacion p) {
        List<DetallePublicacion> detalles = detallesDe(p);
        return p.getEstadoPublicacion() == EstadoPublicacion.ACTIVA
                && detalles.stream().anyMatch(d -> d.getCantidadPublicada() != null && d.getCantidadPublicada() > 0)
                && detalles.stream().allMatch(d -> d.getCantidadReservada() == null || d.getCantidadReservada() == 0);
    }

    private static List<DetallePublicacion> detallesDe(Publicacion p) {
        return p.getDetallePublicacion() == null ? List.of() : p.getDetallePublicacion();
    }

    private static int disponible(DetallePublicacion d) {
        int publicada = d.getCantidadPublicada() == null ? 0 : d.getCantidadPublicada();
        int reservada = d.getCantidadReservada() == null ? 0 : d.getCantidadReservada();
        return publicada - reservada;
    }

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