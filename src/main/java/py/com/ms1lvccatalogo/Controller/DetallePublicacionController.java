package py.com.ms1lvccatalogo.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionCreateDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionPageDTO;
import py.com.lavitrinacoleccionistas.dto.DetallePublicacionUpdateDTO;
import py.com.ms1lvccatalogo.Service.detallepublicacion.IDetallePublicacionService;

@Slf4j
@RestController
@RequestMapping("/publicaciones/{idPublicacion}/detalles")
@RequiredArgsConstructor
public class DetallePublicacionController {

    private final IDetallePublicacionService service;

    @GetMapping
    public DetallePublicacionPageDTO listar(@PathVariable Long idPublicacion,
                                            @ParameterObject Pageable pageable) {
        log.info("GET /publicaciones/{}/detalles", idPublicacion);
        return service.listar(idPublicacion, pageable);
    }

    @PostMapping
    public ResponseEntity<DetallePublicacionDTO> crear(@PathVariable Long idPublicacion,
                                                       @Valid @RequestBody DetallePublicacionCreateDTO dto) {
        log.info("POST /publicaciones/{}/detalles", idPublicacion);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(idPublicacion, dto));
    }

    @PutMapping("/{idDetalle}")
    public DetallePublicacionDTO actualizar(@PathVariable Long idPublicacion,
                                            @PathVariable Long idDetalle,
                                            @Valid @RequestBody DetallePublicacionUpdateDTO dto) {
        log.info("PUT /publicaciones/{}/detalles/{}", idPublicacion, idDetalle);
        return service.actualizar(idPublicacion, idDetalle, dto);
    }
}