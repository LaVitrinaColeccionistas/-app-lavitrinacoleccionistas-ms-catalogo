package py.com.ms1lvccatalogo.security;

import py.com.lavitrinacoleccionistas.enums.TipoRol;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

//Datos del usuario autenticado tomados del JWT
public final class UsuarioActual {

    private static final String[] CLAIMS_ID = {"uid", "idUsuario", "usuarioId", "userId", "id"};

    private UsuarioActual() {
    }

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            for (String claim : CLAIMS_ID) {
                Object valor = jwt.getClaim(claim);
                if (valor != null) {
                    return Long.valueOf(valor.toString());
                }
            }
        }
        throw new AccessDeniedException("No se pudo identificar al usuario");
    }

    public static boolean tieneRol(TipoRol... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (TipoRol rol : roles) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + rol.name()))) {
                return true;
            }
        }
        return false;
    }

    public static boolean esAdmin() {
        return tieneRol(TipoRol.ADMIN);
    }
}