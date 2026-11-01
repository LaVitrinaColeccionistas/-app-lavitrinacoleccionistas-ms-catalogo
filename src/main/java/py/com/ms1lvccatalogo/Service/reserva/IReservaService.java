package py.com.ms1lvccatalogo.Service.reserva;

import py.com.lavitrinacoleccionistas.dto.LiberacionReservasDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;

import java.util.List;

public interface IReservaService {
    List<ReservaDTO> reservar(ReservaCreateDTO dto);
    List<ReservaDTO> confirmar(Long idOrden);
    List<ReservaDTO> liberar(Long idOrden);
    LiberacionReservasDTO liberarVencidas();
}