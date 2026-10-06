package py.com.ms1lvccatalogo.Service.producto;

import org.springframework.data.domain.Pageable;
import py.com.lavitrinacoleccionistas.dto.ProductoCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoPageDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.ms1lvccatalogo.Service.base.IBaseService;
import py.com.ms1lvccatalogo.filtro.ProductoFiltro;

public interface IProductoService extends IBaseService<ProductoCreateDTO, ProductoUpdateDTO, ProductoDTO, ProductoFiltro> {
    ProductoPageDTO buscarPagina(ProductoFiltro filtro, Pageable pageable);
    Producto obtenerEntidad(Long id);
    void eliminar(Long id);
}