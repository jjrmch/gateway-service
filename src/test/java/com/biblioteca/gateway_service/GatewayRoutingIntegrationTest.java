package com.biblioteca.gateway_service;

import com.biblioteca.gateway_service.support.TestJwtFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GatewayRoutingIntegrationTest {

    private static final List<String> PETICIONES = new CopyOnWriteArrayList<>();
    private static final HttpServer DESTINO;
    private static final int PUERTO_DESTINO;

    static {
        try {
            DESTINO = HttpServer.create(new InetSocketAddress(0), 0);
            PUERTO_DESTINO = DESTINO.getAddress().getPort();
            DESTINO.createContext("/", exchange -> {
                PETICIONES.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            DESTINO.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void pararDestinoFalso() {
        DESTINO.stop(0);
    }

    @DynamicPropertySource
    static void apuntarLasInstanciasAlDestinoFalso(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + PUERTO_DESTINO;
        registry.add("spring.cloud.discovery.client.simple.instances.catalog-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.customer-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.transactions-service[0].uri", () -> uri);
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void limpiarPeticiones() {
        PETICIONES.clear();
    }

    private String tokenAdmin() {
        return TestJwtFactory.token("admin@test.com", "ADMIN");
    }

    @Test
    void lasRutasDeTransaccionesSeEnrutanALaInstanciaRegistrada() throws Exception {
        for (String ruta : List.of("/ventas", "/alquileres", "/reservas", "/multas")) {
            mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                    .andExpect(status().isOk());
        }

        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("GET /ventas")));
        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("GET /alquileres")));
        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("GET /reservas")));
        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("GET /multas")));
    }

    @Test
    void laRutaDeClientesSeEnrutaAcustomerService() throws Exception {
        mockMvc.perform(get("/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());

        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("GET /clientes")));
    }

    @Test
    void losDocsDeLosServiciosLleganSinElPrefijoDelGateway() throws Exception {
        mockMvc.perform(get("/catalog-service/v3/api-docs")).andExpect(status().isOk());

        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("GET /v3/api-docs")));
    }

    @Test
    void elPreflightCorsDelFrontendNoExigeToken() throws Exception {
        mockMvc.perform(options("/ventas")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void sinTokenLasRutasDeNegocioNoLleganAlDestino() throws Exception {
        mockMvc.perform(get("/ventas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/multas")).andExpect(status().isUnauthorized());

        assertTrue(PETICIONES.isEmpty());
    }
}
