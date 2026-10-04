package com.ridelink.drivervehicle.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 specification configuration for Driver &amp; Vehicle Service.
 *
 * <p>Configures service metadata and enables interactive Swagger UI with Bearer JWT
 * authentication scheme, enabling the 'Authorize' button in the Swagger UI interface.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI driverVehicleOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink Driver & Vehicle Service API")
                        .description("Manages driver operational readiness, vehicle specifications, availability states, and candidate driver queries. Owned by Member 2.")
                        .version("1.0.0")
                        .contact(new Contact().name("RideLink Team - Member 2")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
