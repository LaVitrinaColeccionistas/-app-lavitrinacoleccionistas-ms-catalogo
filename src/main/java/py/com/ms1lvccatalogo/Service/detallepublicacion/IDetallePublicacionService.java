package py.com.ms1lvccatalogo.Service.detallepublicacion;

import org.springframework.data.domain.Pageable;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionCreateDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionPageDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionUpdateDTO;

public interface IDetallePublicacionService {
    DetallePublicacionDTO crear(Long idPublicacion, DetallePublicacionCreateDTO dto);
    DetallePublicacionDTO actualizar(Long idPublicacion, Long idDetalle, DetallePublicacionUpdateDTO dto);
    DetallePublicacionPageDTO listar(Long idPublicacion, Pageable pageable);
}