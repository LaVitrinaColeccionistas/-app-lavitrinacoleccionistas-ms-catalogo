package py.com.ms1lvccatalogo.service.reserva;

import py.com.lavitrinacoleccionistas.dto.ReservaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaReasignarDTO;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.enums.TipoOrigenReserva;

import java.util.List;

//reservar
//reasignar
//confirmar o liberar
public interface IReservaService {
    List<ReservaDTO> reservar(ReservaCreateDTO dto); // Crear para orden e intercambio
    List<ReservaDTO> reservarParaOferta(Oferta oferta);
    List<ReservaDTO> obtener(TipoOrigenReserva origen, Long idOrigen);

    // Transiciones
    List<ReservaDTO> actualizarReservaIntercambio(Long idIntercambio, ReservaCreateDTO dto); //para editar intercambio
    List<ReservaDTO> reasignar(TipoOrigenReserva origen, Long idOrigen, ReservaReasignarDTO destino);
    List<ReservaDTO> confirmar(TipoOrigenReserva origen, Long idOrigen);
    List<ReservaDTO> liberar(TipoOrigenReserva origen, Long idOrigen);
    List<ReservaDTO> liberarVencidas();
}