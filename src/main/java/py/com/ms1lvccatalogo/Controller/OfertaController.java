package py.com.ms1lvccatalogo.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import py.com.lavitrinacoleccionistas.dto.OfertaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaPageDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaUpdateDTO;
import py.com.ms1lvccatalogo.Service.oferta.IOfertaService;
import py.com.ms1lvccatalogo.filtro.OfertaFiltro;

@Slf4j
@RestController
@RequestMapping("/ofertas")
@RequiredArgsConstructor
public class OfertaController {

    private final IOfertaService service;

    @PostMapping
    public ResponseEntity<OfertaDTO> crear(@Valid @RequestBody OfertaCreateDTO dto) {
        log.info("POST /ofertas");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(dto));
    }

    @GetMapping("/{id}")
    public OfertaDTO obtener(@PathVariable Long id) {
        log.info("GET /ofertas/{}", id);
        return service.obtener(id);
    }

    @GetMapping
    public OfertaPageDTO listar(@ParameterObject OfertaFiltro filtro,
                                @ParameterObject Pageable pageable) {
        log.info("GET /ofertas filtro={}", filtro);
        return service.buscarPagina(filtro, pageable);
    }

    @PutMapping("/{id}")
    public OfertaDTO actualizar(@PathVariable Long id, @Valid @RequestBody OfertaUpdateDTO dto) {
        log.info("PUT /ofertas/{}", id);
        return service.actualizar(id, dto);
    }

    @PostMapping("/{id}/aceptar")
    public OfertaDTO aceptar(@PathVariable Long id) {
        log.info("POST /ofertas/{}/aceptar", id);
        return service.aceptar(id);
    }

    @PostMapping("/{id}/rechazar")
    public OfertaDTO rechazar(@PathVariable Long id) {
        log.info("POST /ofertas/{}/rechazar", id);
        return service.rechazar(id);
    }
}