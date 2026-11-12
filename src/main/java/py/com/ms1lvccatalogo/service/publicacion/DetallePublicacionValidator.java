package py.com.ms1lvccatalogo.service.publicacion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import py.com.lavitrinacoleccionistas.entity.DetallePublicacion;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.lavitrinacoleccionistas.enums.EstadoReserva;
import py.com.ms1lvccatalogo.exception.ConflictException;
import py.com.ms1lvccatalogo.repository.IDetallePublicacionRepository;
import py.com.ms1lvccatalogo.repository.IReservaStockRepository;
import py.com.ms1lvccatalogo.service.stock.DisponibilidadStock;

@Component
@RequiredArgsConstructor
public class DetallePublicacionValidator {

    private final IDetallePublicacionRepository detalleRepository;
    private final IReservaStockRepository reservaRepository;
    private final DisponibilidadStock disponibilidad;

    public void validar(Publicacion pub, Producto prod, Integer cantidad) {
        if (!pub.getIdVendedor().equals(prod.getIdVendedor())) {
            throw new ConflictException("El producto " + prod.getId() + " no pertenece al vendedor de la publicacion");
        }
        long excluir = pub.getId() == null ? DisponibilidadStock.SIN_PUBLICACION : pub.getId();
        long disponible = disponibilidad.libre(prod, excluir);
        if (cantidad > disponible) {
            throw new ConflictException("La cantidad supera el stock disponible para publicar del producto "
                    + prod.getId() + " (disponible: " + disponible + ")");
        }
    }

    public void validarCantidadContraReservas(DetallePublicacion detalle, Integer nuevaCantidad) {
        bloquear(detalle);
        long reservado = reservaRepository.sumarCantidadPorEstado(detalle.getId(), EstadoReserva.RESERVADA);
        if (nuevaCantidad < reservado) {
            throw new ConflictException("El detalle del producto " + detalle.getProducto().getId()
                    + " tiene " + reservado + " unidades reservadas");
        }
    }

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