# Gateway Service

API Gateway de la plataforma de gestión de biblioteca. Es el punto único de entrada: el frontend y cualquier cliente hablan solo con este servicio y él reenvía las peticiones al microservicio correspondiente, resolviendo las instancias por nombre a través de Eureka con balanceo de carga (`lb://`).

También agrega la documentación Swagger de los tres microservicios en una sola UI, de forma que toda la API se consulta desde un único lugar.

## Qué hace

- Enrutado de peticiones a catalog-service, transactions-service y customer-service
- Balanceo de carga entre instancias vía Spring Cloud LoadBalancer
- Agregación de Swagger: la UI muestra los docs de los tres servicios en `http://localhost:8080/swagger-ui.html`
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
| `/catalog-service/v3/api-docs` | docs de catalog-service |
| `/transactions-service/v3/api-docs` | docs de transactions-service |
| `/customer-service/v3/api-docs` | docs de customer-service |

## Stack

- Java 17
- Spring Boot 4.1
- Spring Cloud Gateway (variante WebMVC, no WebFlux)
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

El gateway queda disponible en `http://localhost:8080`.

## Parte de un sistema más grande

La plataforma completa se compone de:

- [discovery-service](https://github.com/jjrmch/discovery-service) — servidor Eureka
- [catalog-service](https://github.com/jjrmch/catalog-service) — catálogo de libros y stock
- [transactions-service](https://github.com/jjrmch/transactions-service) — ventas, alquileres, reservas y multas
- [customer-service](https://github.com/jjrmch/customer-service) — clientes
- [biblioteca-frontend](https://github.com/jjrmch/biblioteca-frontend) — panel web en React
- [biblioteca-deploy](https://github.com/jjrmch/biblioteca-deploy) — docker-compose con el stack completo

## Por mejorar

- CORS abierto a cualquier origen; habría que restringirlo a los dominios del frontend.
- Las rutas no tienen autenticación ni rate limiting; está pensado como capa de enrutado para desarrollo.

## Licencia

MIT
