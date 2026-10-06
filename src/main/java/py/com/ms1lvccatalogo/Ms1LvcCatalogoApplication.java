package py.com.ms1lvccatalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "py.com.lavitrinacoleccionistas.entity"
})
@EnableJpaRepositories(basePackages = {
        "py.com.ms1lvccatalogo.Repository"
})
public class Ms1LvcCatalogoApplication {

    public static void main(String[] args) {
        SpringApplication.run(Ms1LvcCatalogoApplication.class, args);
    }

}
