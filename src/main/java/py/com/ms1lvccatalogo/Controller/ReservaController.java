package py.com.ms1lvccatalogo.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import py.com.lavitrinacoleccionistas.dto.LiberacionReservasDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaDTO;
import py.com.lavitrinacoleccionistas.dto.ReservaReasignarDTO;
import py.com.lavitrinacoleccionistas.enums.TipoOrigenReserva;
import py.com.ms1lvccatalogo.Service.reserva.IReservaService;

import java.util.List;

/**
 * Reserva generica por (origen, idOrigen). Los permisos finos (quien puede reservar, confirmar
 * o liberar cada origen) se validan en el service porque dependen del origen y de los productos.
 */
@Slf4j
@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final IReservaService service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReservaDTO>> reservar(@Valid @RequestBody ReservaCreateDTO dto) {
        log.info("POST /reservas origen={} id={}", dto.getOrigen(),
                dto.getIdOrigen() != null ? dto.getIdOrigen() : dto.getIdOrden());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reservar(dto));
    }

    @GetMapping("/{origen}/{idOrigen}")
    @PreAuthorize("isAuthenticated()")
    public List<ReservaDTO> obtener(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen) {
        return service.obtener(origen, idOrigen);
    }

    @PostMapping("/{origen}/{idOrigen}/confirmar")
    @PreAuthorize("isAuthenticated()")
    public List<ReservaDTO> confirmar(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen) {
        log.info("POST /reservas/{}/{}/confirmar", origen, idOrigen);
        return service.confirmar(origen, idOrigen);
    }

    @PostMapping("/{origen}/{idOrigen}/liberar")
    @PreAuthorize("isAuthenticated()")
    public List<ReservaDTO> liberar(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen) {
        log.info("POST /reservas/{}/{}/liberar", origen, idOrigen);
        return service.liberar(origen, idOrigen);
    }

    @PostMapping("/{origen}/{idOrigen}/reasignar")
    @PreAuthorize("isAuthenticated()")
    public List<ReservaDTO> reasignar(@PathVariable TipoOrigenReserva origen, @PathVariable Long idOrigen,
                                      @Valid @RequestBody ReservaReasignarDTO dto) {
        log.info("POST /reservas/{}/{}/reasignar -> {} {}", origen, idOrigen, dto.getOrigen(), dto.getIdOrigen());
        return service.reasignar(origen, idOrigen, dto);
    }

    @PostMapping("/liberar-vencidas")
    @PreAuthorize("hasRole('ADMIN')")
    public LiberacionReservasDTO liberarVencidas() {
        log.info("POST /reservas/liberar-vencidas");
        return service.liberarVencidas();
    }

    // ---- Compatibilidad con el MS de ordenes (deprecated) ----

    @Deprecated
    @PostMapping("/{idOrden}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    public List<ReservaDTO> confirmarOrden(@PathVariable Long idOrden) {
        return service.confirmar(TipoOrigenReserva.ORDEN, idOrden);
    }

    @Deprecated
    @PostMapping("/{idOrden}/liberar")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR','COMPRADOR')")
    public List<ReservaDTO> liberarOrden(@PathVariable Long idOrden) {
        return service.liberar(TipoOrigenReserva.ORDEN, idOrden);
    }
}
