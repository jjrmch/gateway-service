# Gateway Service

![CI](https://github.com/jjrmch/gateway-service/actions/workflows/ci.yml/badge.svg)

API Gateway de la plataforma de gestión de biblioteca. Es el punto único de entrada: el frontend y cualquier cliente hablan solo con este servicio y él reenvía las peticiones al microservicio correspondiente, resolviendo las instancias por nombre a través de Eureka con balanceo de carga (`lb://`).

Además valida el JWT en cada petición: las rutas de negocio exigen un token válido y solo el login y el registro son públicos.

## Qué hace

- Enrutado de peticiones a catalog-service, transactions-service, customer-service y auth-service
- Validación de JWT (HS256) con el `JWT_SECRET` compartido; sin token válido responde `401` en JSON
- Rutas públicas: `/auth/login`, `/auth/register`, la lectura del catálogo (`GET /libros/**`), Swagger, health y `/error`
- Balanceo de carga entre instancias vía Spring Cloud LoadBalancer
- Agregación de Swagger: la UI muestra los docs de los cuatro servicios en `http://localhost:8080/swagger-ui.html`
- Configuración CORS para permitir al frontend de desarrollo (React/Vite) consumir la API

## Rutas configuradas

| Ruta | Servicio destino |
|---|---|
| `/libros/**` | `lb://catalog-service` |
| `/ventas/**` | `lb://transactions-service` |
| `/alquileres/**` | `lb://transactions-service` |
| `/reservas/**` | `lb://transactions-service` |
| `/multas/**` | `lb://transactions-service` |
| `/clientes/**` | `lb://customer-service` |
| `/auth/**` | `lb://auth-service` |
| `/catalog-service/v3/api-docs` | docs de catalog-service |
| `/transactions-service/v3/api-docs` | docs de transactions-service |
| `/customer-service/v3/api-docs` | docs de customer-service |
| `/auth-service/v3/api-docs` | docs de auth-service |

## Stack

- Java 17
- Spring Boot 4.1
- Spring Cloud Gateway (variante WebMVC, no WebFlux)
- Spring Security (OAuth2 Resource Server) + Nimbus JWT
- Spring Cloud Netflix Eureka (client) + LoadBalancer
- springdoc-openapi (agregación de Swagger)

## Cómo ejecutarlo

Necesitas el discovery-service (Eureka) levantado y los microservicios registrados. Puedes levantar todo el stack con docker-compose desde `biblioteca-deploy`, o ejecutar este servicio solo:

```bash
./mvnw spring-boot:run
```

Configuración por variables de entorno:

| Variable | Descripción |
|---|---|
| `EUREKA_URL` | URL del servidor Eureka (default `http://localhost:8761/eureka/`) |
| `JWT_SECRET` | Secreto compartido para validar los JWT (mínimo 32 caracteres). **Debe ser el mismo que usa auth-service** |

El gateway queda disponible en `http://localhost:8080`.

## Cómo autenticarse

```bash
# 1. Login a través del gateway
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@biblioteca.com","password":"admin1234"}'

# 2. Usar el token en cualquier ruta protegida
curl http://localhost:8080/libros -H "Authorization: Bearer <token>"
```

## Parte de un sistema más grande

La plataforma completa se compone de:

- [discovery-service](https://github.com/jjrmch/discovery-service) — servidor Eureka
- [catalog-service](https://github.com/jjrmch/catalog-service) — catálogo de libros y stock
- [transactions-service](https://github.com/jjrmch/transactions-service) — ventas, alquileres, reservas y multas
- [customer-service](https://github.com/jjrmch/customer-service) — clientes
- [auth-service](https://github.com/jjrmch/auth-service) — registro, login y emisión de JWT
- [biblioteca-frontend](https://github.com/jjrmch/biblioteca-frontend) — panel web en React
- [biblioteca-deploy](https://github.com/jjrmch/biblioteca-deploy) — docker-compose con el stack completo

## Tests

```bash
./mvnw verify
```

7 tests de integración de la cadena de seguridad (`@SpringBootTest` + MockMvc) contra un servicio de destino simulado: no necesitan Eureka ni PostgreSQL. Se ejecutan también en CI en cada push y pull request (badge arriba).

## Por mejorar

- CORS abierto a cualquier origen; habría que restringirlo a los dominios del frontend.
- No hay rate limiting ni renovación de token en el gateway.
- El gateway solo autentica: la autorización por rol (ADMIN, BIBLIOTECARIO, CLIENTE) la aplican los propios servicios.

## Licencia

MIT
