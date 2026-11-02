package py.com.ms1lvccatalogo.Service.reserva;

import py.com.lavitrinacoleccionistas.dto.LiberacionReservasDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaReasignarDTO;
import py.com.lavitrinacoleccionistas.entity.Oferta;
import py.com.lavitrinacoleccionistas.enums.TipoOrigenReserva;

import java.util.List;

public interface IReservaService {

    /** Endpoint publico: ORDEN e INTERCAMBIO. */
    List<ReservaDTO> reservar(ReservaCreateDTO dto);

    /** Uso interno de OfertaServiceImpl.aceptar(): reserva todos los detalles de la publicacion. */
    List<ReservaDTO> reservarParaOferta(Oferta oferta);

    List<ReservaDTO> obtener(TipoOrigenReserva origen, Long idOrigen);

    List<ReservaDTO> confirmar(TipoOrigenReserva origen, Long idOrigen);

    List<ReservaDTO> liberar(TipoOrigenReserva origen, Long idOrigen);

    List<ReservaDTO> reasignar(TipoOrigenReserva origen, Long idOrigen, ReservaReasignarDTO destino);

    LiberacionReservasDTO liberarVencidas();
}
