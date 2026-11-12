package py.com.ms1lvccatalogo.service.reserva;

import py.com.lavitrinacoleccionistas.dto.ReservaItemCreateDTO;

record ReservaItem(Long idDetalle, Long idProducto, int cantidad, Long idDestino) {

    static ReservaItem de(ReservaItemCreateDTO dto) {
        return new ReservaItem(dto.getIdDetallePublicacion(), dto.getIdProducto(),
                dto.getCantidad(), dto.getIdUsuarioDestino());
    }

    boolean conDetalle() {
        return idDetalle != null;
    }

    String clave() {
        return conDetalle() ? "D" + idDetalle : "P" + idProducto;
    }
}