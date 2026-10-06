package py.com.ms1lvccatalogo.Config;

import org.mapstruct.MapperConfig;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import py.com.ms1lvccatalogo.mapper.FechaMapper;

@MapperConfig(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = { FechaMapper.class })
public interface IBaseMapperConfig {
}