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
import py.com.ms1lvccatalogo.Service.reserva.IReservaService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final IReservaService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','COMPRADOR')")
    public ResponseEntity<List<ReservaDTO>> reservar(@Valid @RequestBody ReservaCreateDTO dto) {
        log.info("POST /reservas orden={}", dto.getIdOrden());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reservar(dto));
    }

    @PostMapping("/{idOrden}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    public List<ReservaDTO> confirmar(@PathVariable Long idOrden) {
        log.info("POST /reservas/{}/confirmar", idOrden);
        return service.confirmar(idOrden);
    }

    @PostMapping("/{idOrden}/liberar")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR','COMPRADOR')")
    public List<ReservaDTO> liberar(@PathVariable Long idOrden) {
        log.info("POST /reservas/{}/liberar", idOrden);
        return service.liberar(idOrden);
    }

    @PostMapping("/liberar-vencidas")
    @PreAuthorize("hasRole('ADMIN')")
    public LiberacionReservasDTO liberarVencidas() {
        log.info("POST /reservas/liberar-vencidas");
        return service.liberarVencidas();
    }
}