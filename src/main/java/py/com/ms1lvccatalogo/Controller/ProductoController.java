package py.com.ms1lvccatalogo.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import py.com.lavitrinacoleccionistas.dto.ProductoCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoPageDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoUpdateDTO;
import py.com.ms1lvccatalogo.Service.producto.IProductoService;
import py.com.ms1lvccatalogo.filtro.ProductoFiltro;

@Slf4j
@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final IProductoService service;

    @PostMapping
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoCreateDTO dto) {
        log.info("POST /productos");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(dto));
    }

    @GetMapping("/{id}")
    public ProductoDTO obtener(@PathVariable Long id) {
        log.info("GET /productos/{}", id);
        return service.obtener(id);
    }

    @GetMapping
    public ProductoPageDTO listar(@ParameterObject ProductoFiltro filtro,
                                  @ParameterObject Pageable pageable) {
        log.info("GET /productos filtro={}", filtro);
        return service.buscarPagina(filtro, pageable);
    }

    @PutMapping("/{id}")
    public ProductoDTO actualizar(@PathVariable Long id, @Valid @RequestBody ProductoUpdateDTO dto) {
        log.info("PUT /productos/{}", id);
        return service.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        log.info("DELETE /productos/{}", id);
        service.eliminar(id);
    }
}