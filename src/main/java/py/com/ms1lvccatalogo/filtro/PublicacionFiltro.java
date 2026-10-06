package py.com.ms1lvccatalogo.filtro;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import py.com.lavitrinacoleccionistas.enums.EstadoPublicacion;
import py.com.lavitrinacoleccionistas.enums.TipoVenta;

@Getter
@Setter
@ToString
public class PublicacionFiltro {
    private String titulo;
    private Long idVendedor;
    private EstadoPublicacion estadoPublicacion;
    private TipoVenta tipoVenta;
}