package py.com.ms1lvccatalogo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import py.com.lavitrinacoleccionistas.dto.ReservaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaReasignarDTO;
import py.com.lavitrinacoleccionistas.enums.TipoOrigenReserva;
import py.com.ms1lvccatalogo.service.reserva.IReservaService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final IReservaService service;

    @PostMapping
    public ResponseEntity<List<ReservaDTO>> reservar(@Valid @RequestBody ReservaCreateDTO dto) {
        log.info("POST /reservas origen={} id={}", dto.getOrigen(),
                dto.getIdOrigen() != null ? dto.getIdOrigen() : dto.getIdOrden());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reservar(dto));
    }

    @GetMapping("/{origen}/{idOrigen}")
    public List<ReservaDTO> obtener(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen) {
        return service.obtener(origen, idOrigen);
    }

    @PutMapping("/INTERCAMBIO/{idIntercambio}")
    public List<ReservaDTO> actualizarReservaIntercambio(@PathVariable Long idIntercambio,
                                                         @Valid @RequestBody ReservaCreateDTO dto) {
        log.info("PUT /reservas/INTERCAMBIO/{}", idIntercambio);
        return service.actualizarReservaIntercambio(idIntercambio, dto);
    }

    @PostMapping("/{origen}/{idOrigen}/confirmar")
    public List<ReservaDTO> confirmar(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen) {
        log.info("POST /reservas/{}/{}/confirmar", origen, idOrigen);
        return service.confirmar(origen, idOrigen);
    }

    @PostMapping("/{origen}/{idOrigen}/liberar")
    public List<ReservaDTO> liberar(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen) {
        log.info("POST /reservas/{}/{}/liberar", origen, idOrigen);
        return service.liberar(origen, idOrigen);
    }

    // Libera todas las reservas con plazo vencido (manual hasta que exista el cron)
    @PostMapping("/liberar-vencidas")
    public List<ReservaDTO> liberarVencidas() {
        log.info("POST /reservas/liberar-vencidas");
        return service.liberarVencidas();
    }


    @PostMapping("/{origen}/{idOrigen}/reasignar")
    public List<ReservaDTO> reasignar(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen,
                                      @Valid @RequestBody ReservaReasignarDTO dto) {
        log.info("POST /reservas/{}/{}/reasignar -> {} {}", origen, idOrigen, dto.getOrigen(), dto.getIdOrigen());
        return service.reasignar(origen, idOrigen, dto);
    }
}