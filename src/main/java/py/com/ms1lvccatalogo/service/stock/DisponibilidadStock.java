package py.com.ms1lvccatalogo.service.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.ms1lvccatalogo.repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.repository.IReservaStockRepository;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisponibilidadStock {

    public static final long SIN_PUBLICACION = -1L;
    public static final List<EstadoPublicacion> PUBLICADO =
            List.of(EstadoPublicacion.ACTIVA, EstadoPublicacion.PAUSADA);

    private final IDetallePublicacionRepository detalleRepository;
    private final IReservaStockRepository reservaRepository;

    //Unidades publicadas del producto, sin contar la publicacion indicada.
    public long publicado(Long idProducto, long excluirPublicacion) {
        return detalleRepository.sumarPublicadoPorProducto(idProducto, PUBLICADO, excluirPublicacion);
    }

    // Publicado mas reservado libre
    public long comprometido(Long idProducto, long excluirPublicacion) {
        long publicado = publicado(idProducto, excluirPublicacion);
        long libres = reservaRepository.sumarLibresPorProducto(idProducto, EstadoReserva.RESERVADA);
        log.debug("Stock producto={} publicado={} reservadoLibre={}", idProducto, publicado, libres);
        return publicado + libres;
    }

    // Lo que queda para publicar o reservar como stock libre
    public long libre(Producto p, long excluirPublicacion) {
        long stock = p.getStock() == null ? 0 : p.getStock();
        return stock - comprometido(p.getId(), excluirPublicacion);
    }

    public long libre(Producto p) {
        return libre(p, SIN_PUBLICACION);
    }
}