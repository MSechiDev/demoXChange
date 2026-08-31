package org.generation.italy.demoxchange.config;

import org.generation.italy.demoxchange.security.AppUploadsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Espone il contenuto della cartella upload sotto l'URL pubblico /files/**,
 * cosi' che un ItemImage.url tipo "/files/items/5/ab12.jpg" sia raggiungibile dal browser.
 */
@Configuration
public class StaticResourcesConfig implements WebMvcConfigurer {
    private final AppUploadsProperties props;

    public StaticResourcesConfig(AppUploadsProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(props.dir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(uploadDir.toUri().toString());
    }
}
