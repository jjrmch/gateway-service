# Changelog

Todos los cambios relevantes de este proyecto se documentan en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y este proyecto sigue [Semantic Versioning](https://semver.org/lang/es/).

## [1.0.0] - 2026-10-05

### Añadido

- Enrutado hacia catalog-service, transactions-service, customer-service y auth-service resolviendo instancias por nombre con Spring Cloud LoadBalancer (`lb://`)
- Validación de JWT (HS256) con el `JWT_SECRET` compartido y respuestas 401 en JSON
- Rutas públicas: login, registro, lectura del catálogo, Swagger, health y `/error`
- Agregación de Swagger de los cuatro servicios en `http://localhost:8080/swagger-ui.html`
- Configuración CORS para el frontend de desarrollo
