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
- OpenAPI / Swagger UI
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
│   │   └── dynamo-db/
│   ├── entry-points/
│   │   └── reactive-web/
│   └── helpers/
│       └── metrics/
└── deployment/
```

### Capas principales

**Domain Model**

Contiene las entidades, objetos de valor, reglas de negocio y contratos (gateways) del dominio.

**Use Cases**

Contiene la lógica de aplicación y orquesta las operaciones del dominio.

**Driven Adapters**

Implementa integraciones externas. Actualmente existe un driven adapter DynamoDB basado en AWS SDK for Java v2 y Enhanced Async Client.

**Entry Points**

Expone las funcionalidades mediante una API REST reactiva utilizando Spring WebFlux. Actualmente incluye un endpoint de salud y configuración OpenAPI/Swagger.

## Modelo de datos

La Parte 1 utiliza un modelo de persistencia **NoSQL basado en Amazon DynamoDB**.

El dominio no contiene anotaciones ni dependencias de AWS SDK o Spring Data. Los modelos de infraestructura DynamoDB viven en `infrastructure/driven-adapters/dynamo-db`.

### Tablas DynamoDB

| Tabla | Partition key | Sort key | Uso |
|---|---|---|---|
| `yam-funds-clients` | `id` | - | Clientes y saldo disponible |
| `yam-funds-funds` | `id` | - | Fondos disponibles |
| `yam-funds-subscriptions` | `clientId` | `fundId` | Suscripciones activas por cliente |
| `yam-funds-transactions` | `clientId` | `id` | Historial de transacciones por cliente |

Los nombres de tablas son configurables por variables de entorno.

### Concurrencia

El adapter DynamoDB protege las actualizaciones de saldo con `UpdateItem` condicional:

- descuenta saldo solo si el cliente existe y `balance >= amount`;
- incrementa `version` en cada actualización de saldo;
- evita el patrón inseguro `read -> mutate -> save` para descuento de saldo.

La operación transaccional completa `saldo + subscription + transaction` se evaluará en la HU del caso de uso de suscripción.

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

Endpoint implementado:

```text
GET /api/health
```

Endpoints funcionales pendientes:

```text
POST   /api/clients/{clientId}/subscriptions
DELETE /api/clients/{clientId}/subscriptions/{fundId}
GET    /api/clients/{clientId}/transactions
```

### OpenAPI / Swagger

```text
GET /v3/api-docs
GET /v3/swagger-ui.html
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

### DynamoDB Local

Levantar DynamoDB Local:

```bash
docker compose up -d dynamodb-local
```

La aplicación usa por defecto:

```text
AWS_REGION=us-east-1
AWS_DYNAMODB_ENDPOINT=http://localhost:8000
DYNAMODB_SEED_ENABLED=true
DYNAMODB_CLIENTS_TABLE=yam-funds-clients
DYNAMODB_FUNDS_TABLE=yam-funds-funds
DYNAMODB_SUBSCRIPTIONS_TABLE=yam-funds-subscriptions
DYNAMODB_TRANSACTIONS_TABLE=yam-funds-transactions
```

En AWS no se debe definir `AWS_DYNAMODB_ENDPOINT`, para que el SDK use DynamoDB administrado. El seed debe deshabilitarse en ambientes cloud:

```text
DYNAMODB_SEED_ENABLED=false
```

### Seed local

Cuando `DYNAMODB_SEED_ENABLED=true`, la aplicación crea las tablas si no existen e inserta datos iniciales solo si no existen:

- 5 fondos de la prueba.
- 1 cliente inicial con saldo `COP 500.000`.

El seed es idempotente y no sobrescribe registros existentes.

## Scaffold Bancolombia

Comandos oficiales usados:

```bash
./gradlew ca --name=YamFunds --type=reactive --coverage=jacoco
./gradlew gm --name=Client
./gradlew gm --name=Fund
./gradlew gm --name=Transaction
./gradlew gm --name=Subscription
./gradlew gda --type=dynamodb
./gradlew gep --type=webflux --swagger=true
```

Validaciones:

```bash
./gradlew vs
./gradlew build
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

Implementado hasta ahora:

- Scaffold Clean Architecture Bancolombia.
- Modelos y gateways de dominio.
- Driven adapter DynamoDB.
- DynamoDB Local con Docker Compose.
- Seed local de fondos y cliente inicial.
- Entry point WebFlux con `GET /api/health`.
- OpenAPI/Swagger configurado.

Pendiente:

- Casos de uso de suscripción, cancelación e historial.
- Entry points funcionales de negocio.
- Adapter de notificaciones SNS/SES.
- Manejador global de excepciones.
- Infraestructura CloudFormation.
- Solución SQL de Parte 2.
