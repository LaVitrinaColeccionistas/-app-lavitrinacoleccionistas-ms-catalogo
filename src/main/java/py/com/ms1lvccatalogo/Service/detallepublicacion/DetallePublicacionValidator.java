package py.com.ms1lvccatalogo.Service.detallepublicacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.ms1lvccatalogo.Repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.Repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.exception.ConflictException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DetallePublicacionValidator {

    private static final long SIN_PUBLICACION = -1L;
    private static final List<EstadoPublicacion> ESTADOS_QUE_CUENTAN =
            List.of(EstadoPublicacion.ACTIVA, EstadoPublicacion.PAUSADA);

    private final IDetallePublicacionRepository detalleRepository;
    private final IReservaStockRepository reservaRepository;

    /**
     * Regla: la suma de cantidadPublicada de un producto en publicaciones ACTIVA/PAUSADA
     * no puede superar producto.stock. El producto debe venir bloqueado (obtenerEntidadBloqueada).
     */
    public void validar(Publicacion pub, Producto prod, Integer cantidad) {
        if (!pub.getIdVendedor().equals(prod.getIdVendedor())) {
            throw new ConflictException("El producto " + prod.getId() + " no pertenece al vendedor de la publicacion");
        }
        long stock = prod.getStock() == null ? 0 : prod.getStock();
        long idPublicacion = pub.getId() == null ? SIN_PUBLICACION : pub.getId();
        long enOtras = detalleRepository.sumarPublicadoPorProducto(prod.getId(), ESTADOS_QUE_CUENTAN, idPublicacion);
        long disponible = stock - enOtras;
        if (cantidad > disponible) {
            throw new ConflictException("La cantidad supera el stock disponible para publicar del producto "
                    + prod.getId() + " (disponible: " + disponible + ")");
        }
    }

    /** No se puede bajar la cantidad publicada por debajo de lo ya reservado. */
    public void validarCantidadContraReservas(DetallePublicacion detalle, Integer nuevaCantidad) {
        bloquear(detalle);
        long reservado = reservaRepository.sumarCantidadPorEstado(detalle.getId(), EstadoReserva.RESERVADA);
        if (nuevaCantidad < reservado) {
            throw new ConflictException("El detalle del producto " + detalle.getProducto().getId()
                    + " tiene " + reservado + " unidades reservadas");
        }
    }

    /** No se puede quitar un detalle que tiene reservas (vigentes o historicas). */
    public void validarSinReservas(DetallePublicacion detalle) {
        bloquear(detalle);
        if (reservaRepository.existsPorDetalle(detalle.getId())) {
            throw new ConflictException("No se puede quitar el producto " + detalle.getProducto().getId()
                    + ": tiene reservas asociadas");
        }
    }

    private void bloquear(DetallePublicacion detalle) {
        detalleRepository.findByIdForUpdate(detalle.getId());
    }
}