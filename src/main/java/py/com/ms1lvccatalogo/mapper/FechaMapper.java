package py.com.ms1lvccatalogo.mapper;

import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring")
public class FechaMapper {

    private static final ZoneId ZONA = ZoneId.of("America/Asuncion");

    public OffsetDateTime toOffset(LocalDateTime fecha) {
        return fecha == null ? null : fecha.atZone(ZONA).toOffsetDateTime();
    }

    public LocalDateTime toLocal(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZONA).toLocalDateTime();
    }
}