package py.com.ms1lvccatalogo.Service.publicacion;

import org.springframework.data.domain.Pageable;
import py.com.lavitrinacoleccionistas.dto.*;
import py.com.lavitrinacoleccionistas.entity.Publicacion;
import py.com.ms1lvccatalogo.Service.base.IBaseService;
import py.com.ms1lvccatalogo.filtro.PublicacionFiltro;

public interface IPublicacionService
        extends IBaseService<PublicacionCreateDTO, PublicacionUpdateDTO, PublicacionDTO, PublicacionFiltro> {
    PublicacionPageDTO buscarPagina(PublicacionFiltro filtro, Pageable pageable);
    PublicacionDTO actualizarEstado(Long id, PublicacionEstadoUpdateDTO dto);
    Publicacion obtenerEntidad(Long id);
    void eliminar(Long id);
}