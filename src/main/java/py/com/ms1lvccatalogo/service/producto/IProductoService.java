package py.com.ms1lvccatalogo.service.producto;

import org.springframework.data.domain.Pageable;
import py.com.lavitrinacoleccionistas.dto.ProductoCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoPageDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.ms1lvccatalogo.filtro.ProductoFiltro;
import py.com.ms1lvccatalogo.service.base.IBaseService;

public interface IProductoService
        extends IBaseService<ProductoCreateDTO, ProductoUpdateDTO, ProductoDTO, ProductoFiltro> {
    void eliminar(Long id);
    ProductoPageDTO buscarPagina(ProductoFiltro filtro, Pageable pageable);
    Producto obtenerEntidad(Long id);
    Producto obtenerEntidadBloqueada(Long id);
}