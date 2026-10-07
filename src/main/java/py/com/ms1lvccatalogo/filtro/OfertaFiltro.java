package py.com.ms1lvccatalogo.filtro;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;

@Getter
@Setter
@ToString
public class OfertaFiltro {
    private Long idPublicacion;
    private Long idComprador;
    private EstadoOferta estadoOferta;
}
