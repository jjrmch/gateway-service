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
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GatewaySecurityIntegrationTest {

    private static final List<String> PETICIONES = new CopyOnWriteArrayList<>();
    private static final HttpServer DESTINO;
    private static final int PUERTO_DESTINO;

    static {
        try {
            DESTINO = HttpServer.create(new InetSocketAddress(0), 0);
            PUERTO_DESTINO = DESTINO.getAddress().getPort();
            DESTINO.createContext("/", exchange -> {
                String auth = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                PETICIONES.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " auth=" + auth);
                byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
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
    void laLecturaPublicaDelCatalogoLlegaAlServicioDeDestino() throws Exception {
        mockMvc.perform(get("/libros")).andExpect(status().isOk());

        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("GET /libros")));
    }

    @Test
    void sinTokenDevuelve401EnJsonYSinLlegarAlDestino() throws Exception {
        mockMvc.perform(post("/libros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("No autenticado"));

        assertTrue(PETICIONES.isEmpty());
    }

    @Test
    void conTokenInvalidoDevuelve401YSinLlegarAlDestino() throws Exception {
        mockMvc.perform(get("/libros").header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertTrue(PETICIONES.isEmpty());
    }

    @Test
    void conTokenValidoReenviaElHeaderAuthorizationAlDestino() throws Exception {
        String token = tokenAdmin();

        mockMvc.perform(post("/libros")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertTrue(PETICIONES.stream().anyMatch(p -> p.contains("auth=Bearer " + token)));
    }

    @Test
    void elLoginEsPublicoYSeEnruta() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.com","password":"admin1234"}
                                """))
                .andExpect(status().isOk());

        assertTrue(PETICIONES.stream().anyMatch(p -> p.startsWith("POST /auth/login")));
    }

    @Test
    void lasRutasDePersonalExigenToken() throws Exception {
        mockMvc.perform(get("/clientes")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void laDocumentacionDelGatewayEsPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
