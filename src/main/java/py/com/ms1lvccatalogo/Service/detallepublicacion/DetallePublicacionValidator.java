package py.com.ms1lvccatalogo.Service.detallepublicacion;

import org.springframework.stereotype.Component;
import py.com.lavitrinacoleccionistas.entity.Producto;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.ms1lvccatalogo.exception.ConflictException;

@Component
public class DetallePublicacionValidator {

    public void validar(Publicacion pub, Producto prod, Integer cantidad) {
        if (!pub.getIdVendedor().equals(prod.getIdVendedor())) {
            throw new ConflictException("El producto " + prod.getId() + " no pertenece al vendedor de la publicacion");
        }
        if (prod.getStock() != null && cantidad > prod.getStock()) {
            throw new ConflictException("La cantidad supera el stock disponible del producto " + prod.getId());
        }
    }
}