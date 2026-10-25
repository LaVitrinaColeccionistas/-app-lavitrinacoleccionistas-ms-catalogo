package py.com.ms1lvccatalogo.Service.producto;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.ProductoCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoPageDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.ms1lvccatalogo.Config.RedisCacheConfig;
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
    private final CacheManager cacheManager;

    public ProductoServiceImpl(IProductoRepository productoRepository, ProductoMapper productoMapper, CacheManager cacheManager) {
        super(productoRepository, productoMapper, "Producto");
        this.productoRepository = productoRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = RedisCacheConfig.PRODUCTOS, key = "#id")
    public ProductoDTO obtener(Long id) {
        return super.obtener(id);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PRODUCTOS, key = "#result.id")
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
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PRODUCTOS, key = "#result.id")
    public ProductoDTO actualizar(Long id, ProductoUpdateDTO dto) {
        ProductoDTO actualizado = super.actualizar(id, dto);
        invalidarPublicaciones();
        return actualizado;
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
    @CacheEvict(cacheNames = RedisCacheConfig.PRODUCTOS, key = "#id")
    public void eliminar(Long id) {
        Producto p = buscarPorId(id);
        log.debug("Eliminando producto {}", id);
        p.setActivo(false);
        repository.save(p);
        invalidarPublicaciones();
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

    private void invalidarPublicaciones() {
        Cache cache = cacheManager.getCache(RedisCacheConfig.PUBLICACIONES);
        if (cache != null) {
            cache.clear();
            log.debug("CACHE CLEAR dominio=publicaciones por cambio en producto");
        }
    }
}