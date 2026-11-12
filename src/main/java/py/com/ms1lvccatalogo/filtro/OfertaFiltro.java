package py.com.ms1lvccatalogo.filtro;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import py.com.lavitrinacoleccionistas.enums.EstadoOferta;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@ToString
public class OfertaFiltro {
    private Long idPublicacion;
    private Long idComprador;
    private EstadoOferta estadoOferta;
    private Long idVendedor; // vendedor de la publicacion
    private BigDecimal montoMin;
    private BigDecimal montoMax;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaDesde;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaHasta;
}
