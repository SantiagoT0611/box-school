# Box School - Backend

Backend de una aplicación web para la gestión administrativa de una escuela de boxeo.
El sistema permite manejar estudiantes, pagos, facturación y usuarios.

Proyecto desarrollado con Java y Spring Boot, enfocado en buenas prácticas backend y
estructura profesional.

## Tecnologías

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- Maven
- Lombok

## Estructura del proyecto

Arquitectura en capas:

- controller
- service
- repository
- model (entity, dto, shared)
- config

## Base de datos

- PostgreSQL
- Las tablas se generan automáticamente mediante JPA
- La base de datos debe crearse previamente

## Configuración

La aplicación utiliza variables de entorno para la conexión a la base de datos.

Variables requeridas:

- DB_URL
- DB_USERNAME
- DB_PASSWORD

## Ejecución

1. Crear la base de datos
2. Configurar variables de entorno
3. Ejecutar:

```bash
mvn spring-boot:run
