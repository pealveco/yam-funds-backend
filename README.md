# YAM Funds Backend

Backend para una plataforma de gestión de fondos de inversión, desarrollado como parte de una prueba técnica.

La aplicación permite a los clientes administrar sus vinculaciones a fondos de inversión mediante una API REST.

## Funcionalidades

- Suscripción a fondos de inversión.
- Cancelación de suscripciones.
- Consulta del historial de transacciones.
- Notificación por email o SMS al realizar una suscripción.
- Validación de saldo disponible.
- Manejo centralizado de errores.

## Stack tecnológico

- Java 21
- Spring Boot 3
- Spring WebFlux
- Gradle
- Lombok
- AWS DynamoDB
- AWS SNS / SES
- AWS CloudFormation
- JUnit 5
- Mockito
- JaCoCo

## Arquitectura

El proyecto utiliza **Clean Architecture / Hexagonal Architecture**, generado a partir del scaffold de Clean Architecture de Bancolombia.

El objetivo es mantener el dominio independiente de frameworks, mecanismos de persistencia y servicios externos.

```text
yam-funds-backend/
├── applications/
│   └── app-service/
├── domain/
│   ├── model/
│   └── usecase/
├── infrastructure/
│   ├── driven-adapters/
│   └── entry-points/
└── deployment/
```

### Capas principales

**Domain Model**

Contiene las entidades, objetos de valor, reglas de negocio y contratos (gateways) del dominio.

**Use Cases**

Contiene la lógica de aplicación y orquesta las operaciones del dominio.

**Driven Adapters**

Implementa integraciones externas, como persistencia en DynamoDB y servicios de notificación.

**Entry Points**

Expone las funcionalidades mediante una API REST utilizando Spring WebFlux.

## Modelo de datos

La Parte 1 utiliza un modelo de persistencia **NoSQL basado en Amazon DynamoDB**.

El diseño definitivo de tablas, claves y patrones de acceso será documentado a medida que avance la implementación.

## Reglas principales de negocio

El cliente inicia con un saldo de:

```text
COP $500.000
```

Cada fondo define un monto mínimo de vinculación.

Al realizar una suscripción:

1. Se valida que el fondo exista.
2. Se valida que el cliente tenga saldo suficiente.
3. Se descuenta el monto mínimo del fondo.
4. Se registra la suscripción.
5. Se genera una transacción con identificador único.
6. Se envía una notificación según la preferencia del cliente.

Cuando el saldo es insuficiente, la API debe responder:

```text
No tiene saldo disponible para vincularse al fondo <Nombre del fondo>
```

Al cancelar una suscripción, el monto vinculado se retorna al saldo disponible del cliente.

## Fondos disponibles

| ID | Fondo | Monto mínimo | Categoría |
|---|---|---:|---|
| 1 | FPV_YAM_PACTUAL_RECAUDADORA | COP $75.000 | FPV |
| 2 | FPV_YAM_PACTUAL_ECOPETROL | COP $125.000 | FPV |
| 3 | DEUDAPRIVADA | COP $50.000 | FIC |
| 4 | FDO-ACCIONES | COP $250.000 | FIC |
| 5 | FPV_YAM_PACTUAL_DINAMICA | COP $100.000 | FPV |

## API

Los endpoints serán documentados conforme avance la implementación.

```text
POST   /api/clients/{clientId}/subscriptions
DELETE /api/clients/{clientId}/subscriptions/{fundId}
GET    /api/clients/{clientId}/transactions
```

## Ejecución local

### Requisitos

- Java 21
- Gradle / Gradle Wrapper
- Docker
- Docker Compose

### Compilar

```bash
./gradlew clean build
```

### Ejecutar pruebas

```bash
./gradlew test
```

### Ejecutar aplicación

```bash
./gradlew bootRun
```

## Pruebas

La solución contempla:

- Pruebas unitarias.
- Pruebas de reglas de dominio.
- Pruebas de casos de uso.
- Pruebas de integración para los adapters cuando aplique.
- Reporte de cobertura mediante JaCoCo.

## Despliegue

La infraestructura será definida como código utilizando **AWS CloudFormation**.

La solución contempla servicios AWS como:

- DynamoDB
- SNS / SES
- ECS/Fargate
- IAM

Las instrucciones de despliegue se agregarán una vez esté definida la infraestructura.

## Parte 2 — SQL

La prueba también incluye un ejercicio independiente de SQL.

La solución y explicación de la consulta estarán disponibles en:

```text
docs/sql-query.sql
```

## Estado del proyecto

> En desarrollo.

Este README será actualizado progresivamente conforme se implementen las funcionalidades, decisiones arquitectónicas, modelo DynamoDB, pruebas y estrategia de despliegue.