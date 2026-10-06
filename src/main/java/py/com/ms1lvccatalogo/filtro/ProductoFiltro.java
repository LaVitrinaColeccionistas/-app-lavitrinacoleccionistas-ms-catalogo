package py.com.ms1lvccatalogo.filtro;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ProductoFiltro {
    private String nombre;
    private String categoria;
    private String rareza;
    private Long idVendedor;
}