package py.com.ms1lvccatalogo.Service.producto;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.ProductoCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoPageDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.ms1lvccatalogo.Repository.IProductoRepository;
import py.com.ms1lvccatalogo.Service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.exception.BadRequestException;
import py.com.ms1lvccatalogo.filtro.ProductoFiltro;
import py.com.ms1lvccatalogo.mapper.ProductoMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ProductoServiceImpl
        extends BaseServiceImpl<Producto, ProductoCreateDTO, ProductoUpdateDTO, ProductoDTO, ProductoFiltro>
        implements IProductoService {

    private final IProductoRepository productoRepository;

    public ProductoServiceImpl(IProductoRepository productoRepository, ProductoMapper productoMapper) {
        super(productoRepository, productoMapper, "Producto");
        this.productoRepository = productoRepository;
    }

    @Override
    @Transactional
    public ProductoDTO crear(ProductoCreateDTO dto) {
        List<String> faltantes = new ArrayList<>();
        if (dto.getIdVendedor() == null) faltantes.add("idVendedor");
        if (dto.getNombre() == null || dto.getNombre().isBlank()) faltantes.add("nombre");
        if (dto.getPrecioReferencia() == null) faltantes.add("precioReferencia");
        if (!faltantes.isEmpty()) {
            throw new BadRequestException("Campos obligatorios: " + String.join(", ", faltantes));
        }
        return super.crear(dto);
    }

    @Override
    protected Producto buscarPorId(Long id) {
        Producto p = super.buscarPorId(id);
        if (!Boolean.TRUE.equals(p.getActivo())) {
            throw new EntityNotFoundException(nombreEntidad + " no encontrado: " + id);
        }
        return p;
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Producto p = buscarPorId(id);
        log.debug("Eliminando producto {}", id);
        p.setActivo(false);
        repository.save(p);
    }

    @Override
    protected Page<Producto> buscarEntidades(ProductoFiltro f, Pageable pageable) {
        return productoRepository.buscarConFiltros(
                f.getIdVendedor(), f.getCategoria(), f.getRareza(), f.getNombre(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoPageDTO buscarPagina(ProductoFiltro filtro, Pageable pageable) {
        Page<ProductoDTO> p = buscar(filtro, pageable);
        ProductoPageDTO out = new ProductoPageDTO();
        out.setContent(p.getContent());
        out.setTotalElements(p.getTotalElements());
        out.setTotalPages(p.getTotalPages());
        out.setSize(p.getSize());
        out.setNumber(p.getNumber());
        return out;
    }
}