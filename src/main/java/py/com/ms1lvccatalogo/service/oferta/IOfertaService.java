package py.com.ms1lvccatalogo.service.oferta;

import org.springframework.data.domain.Pageable;
import py.com.lavitrinacoleccionistas.dto.OfertaCreateDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaPageDTO;
import py.com.lavitrinacoleccionistas.dto.OfertaUpdateDTO;
import py.com.ms1lvccatalogo.service.base.IBaseService;
import py.com.ms1lvccatalogo.filtro.OfertaFiltro;

public interface IOfertaService
        extends IBaseService<OfertaCreateDTO, OfertaUpdateDTO, OfertaDTO, OfertaFiltro> {
    void exigirAcceso(OfertaDTO oferta);
    OfertaDTO aceptar(Long id);
    OfertaDTO rechazar(Long id);
    OfertaPageDTO buscarPagina(OfertaFiltro filtro, Pageable pageable);
}