package org.generation.italy.demoxchange.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.uploads")
public record AppUploadsProperties(
        String dir
) {}
