# Boxing School - Backend

Backend de una aplicación web para la gestión administrativa de una escuela de boxeo.

El sistema permite administrar estudiantes, pagos, facturación y usuarios mediante una API REST segura utilizando autenticación JWT.

Este proyecto fue desarrollado como práctica profesional de backend utilizando Spring Boot y buenas prácticas de arquitectura.

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

- config
- controller
- exception
- mapper
- model (entity, dto, shared)
- repository
- security
- service
- util

## Separacion de responsabilidades

- Controller: endpoints REST
- Service: lógica de negocio
- Repository: acceso a datos
- DTO: transferencia de datos
- Entity: modelos de base de datos

## Seguridad

El sistema implementa autenticacion basada en JWT
Flujo de autenticacion:

Login
  |
Generacion de JWT
  |
Cliente envia token en Authorization Header
  |
JwtAuthenticationFilter valida el token
  |
Acceso permitido segun roles

Roles disponibles:

1. ROLE_ADMIN
2. ROLE_USER


## Base de datos

- PostgreSQL
- Las tablas se generan automáticamente mediante JPA
- ddl-auto=update
- La base de datos debe crearse previamente
- boxing_school

## Configuración

La aplicación utiliza variables de entorno para la conexión a la base de datos y para el Token.

Variables requeridas:

- DB_URL
- DB_USERNAME
- DB_PASSWORD
- JWT_SECRET

## Ejecución

1. Crear la base de datos
2. Configurar variables de entorno
3. Ejecutar:

mvn spring-boot:run

## Autor

- Desarrollado por Santiago Torres
- Proyecto de practica profesional backend.

## Mejoras futuras

- Refresh Token
- Documentacion con Swagger
- Test unitarios
- Docker
- Deploy en la nube



