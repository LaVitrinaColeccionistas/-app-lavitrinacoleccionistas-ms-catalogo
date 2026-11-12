package py.com.ms1lvccatalogo.service.producto;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.com.lavitrinacoleccionistas.dto.ProductoCreateDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoPageDTO;
import py.com.lavitrinacoleccionistas.dto.ProductoUpdateDTO;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.ms1lvccatalogo.cache.CatalogoCacheInvalidator;
import py.com.ms1lvccatalogo.config.RedisCacheConfig;
import py.com.ms1lvccatalogo.exception.BadRequestException;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.filtro.ProductoFiltro;
import py.com.ms1lvccatalogo.mapper.ProductoMapper;
import py.com.ms1lvccatalogo.repository.IProductoRepository;
import py.com.ms1lvccatalogo.repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.security.UsuarioActual;
import py.com.ms1lvccatalogo.service.base.BaseServiceImpl;
import py.com.ms1lvccatalogo.service.stock.DisponibilidadStock;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ProductoServiceImpl
        extends BaseServiceImpl<Producto, ProductoCreateDTO, ProductoUpdateDTO, ProductoDTO, ProductoFiltro>
        implements IProductoService {

    private final IProductoRepository productoRepository;
    private final IReservaStockRepository reservaRepository;
    private final ProductoMapper productoMapper;
    private final DisponibilidadStock disponibilidad;
    private final CatalogoCacheInvalidator cacheInvalidator;

    public ProductoServiceImpl(IProductoRepository productoRepository, ProductoMapper productoMapper,
                               IReservaStockRepository reservaRepository, DisponibilidadStock disponibilidad,
                               CatalogoCacheInvalidator cacheInvalidator) {
        super(productoRepository, productoMapper, "Producto");
        this.productoRepository = productoRepository;
        this.productoMapper = productoMapper;
        this.reservaRepository = reservaRepository;
        this.disponibilidad = disponibilidad;
        this.cacheInvalidator = cacheInvalidator;
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
        exigirCamposObligatorios(dto);
        return super.crear(dto);
    }

    @Override
    @Transactional
    @CachePut(cacheNames = RedisCacheConfig.PRODUCTOS, key = "#result.id")
    public ProductoDTO actualizar(Long id, ProductoUpdateDTO dto) {
        Producto p = obtenerEntidadBloqueada(id);
        exigirDueno(p);
        if (dto.getStock() != null) {
            exigirStockSuficiente(p, dto.getStock());
        }
        ProductoDTO actualizado = super.actualizar(id, dto);
        cacheInvalidator.evictPublicacionesDeProducto(id);
        return actualizado;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.PRODUCTOS, key = "#id")
    public void eliminar(Long id) {
        Producto p = obtenerEntidadBloqueada(id);
        exigirDueno(p);
        exigirEliminable(p);
        p.setActivo(false);
        cacheInvalidator.evictPublicacionesDeProducto(id);
        log.info("Producto eliminado id={} vendedor={}", id, p.getIdVendedor());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoPageDTO buscarPagina(ProductoFiltro filtro, Pageable pageable) {
        return productoMapper.toPageDTO(buscar(filtro, pageable));
    }


    @Override
    @Transactional
    public Producto obtenerEntidadBloqueada(Long id) {
        log.debug("findByIdForUpdate Producto id={}", id);
        Producto p = productoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> noEncontrado(id));
        exigirActivo(p);
        return p;
    }

    //Hooks
    @Override
    protected Producto buscarPorId(Long id) {
        Producto p = super.buscarPorId(id);
        exigirActivo(p);
        return p;
    }

    @Override
    protected Page<Producto> buscarEntidades(ProductoFiltro f, Pageable pageable) {
        return productoRepository.buscarConFiltros(
                f.getIdVendedor(), f.getCategoria(), f.getRareza(), f.getNombre(), pageable);
    }

    // Validaciones
    private void exigirDueno(Producto p) {
        if (!UsuarioActual.esAdmin() && !UsuarioActual.id().equals(p.getIdVendedor())) {
            throw new AccessDeniedException("Solo el dueño puede modificar \"" + p.getNombre() + "\"");
        }
    }

    private void exigirCamposObligatorios(ProductoCreateDTO dto) {
        List<String> faltantes = new ArrayList<>();
        if (dto.getIdVendedor() == null) faltantes.add("idVendedor");
        if (dto.getNombre() == null || dto.getNombre().isBlank()) faltantes.add("nombre");
        if (dto.getPrecioReferencia() == null) faltantes.add("precioReferencia");
        if (!faltantes.isEmpty()) {
            throw new BadRequestException("Campos obligatorios: " + String.join(", ", faltantes));
        }
    }

    private void exigirStockSuficiente(Producto p, int nuevoStock) {
        long comprometido = disponibilidad.comprometido(p.getId(), DisponibilidadStock.SIN_PUBLICACION);
        if (nuevoStock < comprometido) {
            throw new ConflictException("El stock no puede ser menor a " + comprometido
                    + " (unidades publicadas o reservadas de \"" + p.getNombre() + "\")");
        }
    }

    private void exigirEliminable(Producto p) {
        if (disponibilidad.publicado(p.getId(), DisponibilidadStock.SIN_PUBLICACION) > 0) {
            throw new ConflictException("No se puede eliminar \"" + p.getNombre()
                    + "\": esta en publicaciones activas o pausadas");
        }
        if (reservaRepository.existsPorProductoYEstado(p.getId(), EstadoReserva.RESERVADA)) {
            throw new ConflictException("No se puede eliminar \"" + p.getNombre() + "\": tiene reservas vigentes");
        }
    }

    private void exigirActivo(Producto p) {
        if (!Boolean.TRUE.equals(p.getActivo())) {
            throw noEncontrado(p.getId());
        }
    }

    private EntityNotFoundException noEncontrado(Long id) {
        return new EntityNotFoundException(nombreEntidad + " no encontrado: " + id);
    }
}