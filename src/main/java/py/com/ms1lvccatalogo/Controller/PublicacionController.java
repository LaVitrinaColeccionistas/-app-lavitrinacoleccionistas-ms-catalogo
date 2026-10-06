package py.com.ms1lvccatalogo.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import py.com.lavitrinacoleccionistas.dto.*;
import py.com.ms1lvccatalogo.Service.publicacion.IPublicacionService;
import py.com.ms1lvccatalogo.filtro.PublicacionFiltro;

@Slf4j
@RestController
@RequestMapping("/publicaciones")
@RequiredArgsConstructor
public class PublicacionController {

    private final IPublicacionService service;

    @PostMapping
    public ResponseEntity<PublicacionDTO> crear(@Valid @RequestBody PublicacionCreateDTO dto) {
        log.info("POST /publicaciones");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(dto));
    }

    @GetMapping("/{id}")
    public PublicacionDTO obtener(@PathVariable Long id) {
        log.info("GET /publicaciones/{}", id);
        return service.obtener(id);
    }

    @GetMapping
    public PublicacionPageDTO listar(@ParameterObject PublicacionFiltro filtro,
                                     @ParameterObject Pageable pageable) {
        log.info("GET /publicaciones filtro={}", filtro);
        return service.buscarPagina(filtro, pageable);
    }

    @PutMapping("/{id}")
    public PublicacionDTO actualizar(@PathVariable Long id, @Valid @RequestBody PublicacionUpdateDTO dto) {
        log.info("PUT /publicaciones/{}", id);
        return service.actualizar(id, dto);
    }

    @PatchMapping("/{id}/estado")
    public PublicacionDTO actualizarEstado(@PathVariable Long id,
                                           @Valid @RequestBody PublicacionEstadoUpdateDTO dto) {
        log.info("PATCH /publicaciones/{}/estado", id);
        return service.actualizarEstado(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        log.info("DELETE /publicaciones/{}", id);
        service.eliminar(id);
    }
}