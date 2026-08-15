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
- AWS SNS / SES (pendiente de integración real)
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
│   │   ├── dynamo-db/
│   │   └── notifications/
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

Implementa integraciones externas. Actualmente existen un driven adapter DynamoDB basado en AWS SDK for Java v2 y Enhanced Async Client, y un driven adapter genérico de notificaciones con fallback log-based.

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
| `yam-funds-transactions` | `clientId` | `sortKey` (`timestamp#transactionId`) | Historial de transacciones por cliente |

Los nombres de tablas son configurables por variables de entorno.

### Concurrencia

El adapter DynamoDB protege las actualizaciones de saldo con escritura condicional:

- descuenta saldo solo si el cliente existe y `balance >= amount`;
- incrementa `version` en cada actualización de saldo;
- evita el patrón inseguro `read -> mutate -> save` para descuento de saldo.

Los casos de uso de suscripción y cancelación ejecutan sus cambios relacionados mediante `TransactWriteItems`, de forma que las operaciones sobre saldo, suscripción y transacción se confirmen o fallen como una sola operación.

El historial de transacciones se consulta con `Query` por `clientId` y sort key `timestamp#transactionId` en orden descendente. El timestamp se almacena en formato numérico fijo para conservar el orden cronológico lexicográfico de DynamoDB. Este access pattern evita `Scan` para obtener las transacciones de un cliente.

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
You do not have any available balance to link to the fund <Fund name>
```

Al cancelar una suscripción, el monto vinculado se retorna al saldo disponible del cliente.

### Notificaciones

`NotificationRepository` permanece como puerto del dominio. La implementación actual usa un driven adapter genérico `notifications` generado con el scaffold de Bancolombia y registra la notificación por logs según la preferencia del cliente:

- `EMAIL`: fallback log-based para correo.
- `SMS`: fallback log-based para mensaje de texto.

El fallo de notificación no revierte la operación financiera. La suscripción se persiste primero y la notificación se ejecuta como best-effort.

La integración real con AWS queda preparada como extensión de infraestructura:

- `EMAIL` mediante Amazon SES.
- `SMS` mediante Amazon SNS.

## Fondos disponibles

| ID | Fondo | Monto mínimo | Categoría |
|---|---|---:|---|
| 1 | FPV_YAM_PACTUAL_RECAUDADORA | COP $75.000 | FPV |
| 2 | FPV_YAM_PACTUAL_ECOPETROL | COP $125.000 | FPV |
| 3 | DEUDAPRIVADA | COP $50.000 | FIC |
| 4 | FDO-ACCIONES | COP $250.000 | FIC |
| 5 | FPV_YAM_PACTUAL_DINAMICA | COP $100.000 | FPV |

## API

Base URL local:

```text
http://localhost:8080
```

Base URL desplegada en AWS App Runner:

```text
https://wmdyjiavxt.us-east-1.awsapprunner.com
```

Endpoints implementados:

```text
GET /api/health
POST /api/clients/{clientId}/subscriptions
DELETE /api/clients/{clientId}/subscriptions/{fundId}
GET /api/clients/{clientId}/transactions
```

### Health

Request:

```bash
curl -i "$BASE_URL/api/health"
```

Response `200 OK`:

```json
{
  "status": "UP"
}
```

### Crear suscripcion

Request:

```bash
curl -i -X POST "$BASE_URL/api/clients/client-001/subscriptions" \
  -H "Content-Type: application/json" \
  -d '{"fundId":"1"}'
```

Body:

```json
{
  "fundId": "1"
}
```

Response `201 Created`:

Header:

```text
Location: /api/clients/client-001/subscriptions/1
```

Body:

```json
{
  "clientId": "client-001",
  "fundId": "1",
  "fundName": "FPV_YAM_PACTUAL_RECAUDADORA",
  "amount": 75000,
  "subscribedAt": "2026-08-11T00:00:00Z"
}
```

Errores posibles:

`400 Bad Request`, saldo insuficiente:

```json
{
  "error": "You do not have any available balance to link to the fund FPV_YAM_PACTUAL_RECAUDADORA",
  "status": 400,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

`404 Not Found`, fondo no existe:

```json
{
  "error": "Fund not found: not-found",
  "status": 404,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

`404 Not Found`, cliente no existe:

```json
{
  "error": "Client not found: client-999",
  "status": 404,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

`409 Conflict`, suscripcion duplicada:

```json
{
  "error": "Client client-001 is already subscribed to fund 1",
  "status": 409,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

`409 Conflict`, conflicto de concurrencia:

```json
{
  "error": "Concurrent subscription conflict for client client-001 and fund 1",
  "status": 409,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

### Cancelar suscripcion

Request:

```bash
curl -i -X DELETE "$BASE_URL/api/clients/client-001/subscriptions/1"
```

Response `204 No Content`:

```text
Sin body.
```

Errores posibles:

`404 Not Found`, cliente, fondo o suscripcion no existe:

```json
{
  "error": "Subscription not found for client client-001 and fund 1",
  "status": 404,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

`409 Conflict`, conflicto de concurrencia:

```json
{
  "error": "Concurrent subscription conflict for client client-001 and fund 1",
  "status": 409,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

### Consultar historial de transacciones

Request:

```bash
curl -i "$BASE_URL/api/clients/client-001/transactions"
```

Response `200 OK` con transacciones:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000001",
    "clientId": "client-001",
    "fundId": "1",
    "fundName": "FPV_YAM_PACTUAL_RECAUDADORA",
    "type": "SUBSCRIPTION",
    "amount": 75000,
    "timestamp": "2026-08-11T00:00:00Z"
  }
]
```

Response `200 OK` sin transacciones:

```json
[]
```

Error posible:

`404 Not Found`, cliente no existe:

```json
{
  "error": "Client not found: client-999",
  "status": 404,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

### Errores

La API responde errores con un formato consistente:

```json
{
  "error": "message",
  "status": 409,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

Mapeo HTTP:

| Excepción | HTTP |
|---|---:|
| `InsufficientBalanceException` | 400 |
| `FundNotFoundException` | 404 |
| `ClientNotFoundException` | 404 |
| `SubscriptionNotFoundException` | 404 |
| `DuplicateSubscriptionException` | 409 |
| `SubscriptionConcurrencyException` | 409 |
| `Exception` | 500 |

Response `500 Internal Server Error`:

```json
{
  "error": "Unexpected error",
  "status": 500,
  "timestamp": "2026-08-11T00:00:00Z"
}
```

Los errores `500` no exponen detalles tecnicos, stack traces, excepciones internas ni paths de infraestructura en la respuesta.

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

### Docker Compose local

Levantar la aplicación con DynamoDB Local:

```bash
docker compose up --build
```

El `docker-compose.yml` está pensado para desarrollo local. Por defecto activa `SPRING_PROFILES_ACTIVE=local`, apunta la aplicación a `http://dynamodb-local:8000` y habilita el seed de datos demo.

También se puede cargar la configuración de Compose desde `.env.compose`:

```bash
docker compose --env-file .env.compose up --build
```

`.env.local` y `.env.compose` no se versionan. El archivo versionado es `.env.example`, que sirve como plantilla.

Si se ejecuta la app desde la máquina host con `./gradlew bootRun` y solo DynamoDB corre en Docker, `AWS_DYNAMODB_ENDPOINT` debe ser `http://localhost:8000`. Si la app corre dentro de Docker Compose, debe ser `http://dynamodb-local:8000`.

### DynamoDB Local

Levantar DynamoDB Local:

```bash
docker compose up -d dynamodb-local
```

Verificar el estado del contenedor:

```bash
docker compose ps
```

Ver logs de DynamoDB Local:

```bash
docker compose logs -f dynamodb-local
```

Detener DynamoDB Local:

```bash
docker compose down
```

Recrear DynamoDB Local desde cero:

```bash
docker compose down
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

En AWS no se debe definir `AWS_DYNAMODB_ENDPOINT`, para que el SDK use DynamoDB administrado. Para esta prueba tecnica se mantiene el seed activo en `prod`, porque deja datos base disponibles para validar la API:

```text
DYNAMODB_SEED_ENABLED=true
```

### Seed local

Cuando `DYNAMODB_SEED_ENABLED=true`, la aplicación crea las tablas si no existen e inserta datos iniciales solo si no existen:

- 5 fondos de la prueba.
- 1 cliente inicial con saldo `COP 500.000`.

El seed es idempotente y no sobrescribe registros existentes.

Si DynamoDB Local ya estaba levantado con una versión anterior del esquema de `yam-funds-transactions`, se debe recrear la base local porque DynamoDB no permite cambiar la key schema de una tabla existente:

```bash
docker compose down
docker compose up -d dynamodb-local
```

Luego se reinicia la aplicación para que el seed cree nuevamente las tablas.

### Perfiles de Spring

La configuración común vive en `application.yaml`. La configuración de ambiente vive en `application-local.yaml` y `application-prod.yaml`, por lo que la aplicación debe ejecutarse con un perfil explícito.

Perfil local:

```text
SPRING_PROFILES_ACTIVE=local
AWS_DYNAMODB_ENDPOINT=http://localhost:8000
DYNAMODB_SEED_ENABLED=true
```

Perfil AWS/prod:

```text
SPRING_PROFILES_ACTIVE=prod
AWS_REGION=us-east-1
DYNAMODB_SEED_ENABLED=true
DYNAMODB_CLIENTS_TABLE=yam-funds-clients
DYNAMODB_FUNDS_TABLE=yam-funds-funds
DYNAMODB_SUBSCRIPTIONS_TABLE=yam-funds-subscriptions
DYNAMODB_TRANSACTIONS_TABLE=yam-funds-transactions
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example
```

En AWS no se debe definir `AWS_DYNAMODB_ENDPOINT`; así el AWS SDK usa DynamoDB administrado según la región configurada. `.env.prod` puede usarse como archivo local no versionado para preparar/importar variables en el servicio de despliegue, pero no debe commitearse.

La implementación actual de notificaciones sigue siendo fallback log-based; la integración real con SNS/SES queda como trabajo de infraestructura posterior.

## Scaffold Bancolombia

Comandos oficiales usados:

```bash
./gradlew ca --name=YamFunds --type=reactive --coverage=jacoco
./gradlew gm --name=Client
./gradlew gm --name=Fund
./gradlew gm --name=Transaction
./gradlew gm --name=Subscription
./gradlew gda --type=dynamodb
./gradlew gda --type=generic --name=notifications
./gradlew gep --type=webflux --swagger=true
./gradlew guc --name=SubscribeToFund
./gradlew guc --name=CancelSubscription
./gradlew guc --name=GetTransactionHistory
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

Template principal:

```text
deployment/cloudformation/yam-funds-backend.yaml
```

Recursos incluidos:

- 4 tablas DynamoDB con billing mode configurable; `PAY_PER_REQUEST` queda como default para cumplir la PT y `PROVISIONED` queda disponible para control de costo.
- AWS App Runner para la aplicación, usando imagen privada de ECR y URL estable administrada por AWS.
- ECR opcional para el flujo completo de imagen.
- IAM runtime role con permisos mínimos para DynamoDB.
- Permisos IAM preparados para `sns:Publish`, `ses:SendEmail` y `ses:SendRawEmail`.
- Outputs de tablas, App Runner, IAM y ECR.
- Workflow GitHub Actions para CI/CD con OIDC, build/push a ECR y deploy CloudFormation.

Nota: los permisos SNS/SES quedan preparados para el adapter real de notificaciones. La implementación actual sigue usando fallback log-based; antes de cerrar integración real de notificaciones se debe reemplazar o extender el adapter `notifications`.

Guía detallada:

```text
deployment/cloudformation/README.md
```

### Despliegue Manual Con AWS CLI

Validar identidad AWS:

```bash
aws sts get-caller-identity
```

Crear/verificar repositorio ECR, construir imagen y publicarla:

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR_REPOSITORY=yam-funds-backend
export IMAGE_TAG=latest
export IMAGE_URI=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG

aws ecr describe-repositories --repository-names $ECR_REPOSITORY --region $AWS_REGION \
  || aws ecr create-repository --repository-name $ECR_REPOSITORY --region $AWS_REGION

aws ecr get-login-password --region $AWS_REGION \
  | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

docker build -f deployment/Dockerfile -t yam-funds-backend:$IMAGE_TAG .
docker tag yam-funds-backend:$IMAGE_TAG $IMAGE_URI
docker push $IMAGE_URI
```

Desplegar CloudFormation:

```bash
aws cloudformation deploy \
  --stack-name yam-funds-backend-prod \
  --template-file deployment/cloudformation/yam-funds-backend.yaml \
  --region $AWS_REGION \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    ProjectName=yam-funds \
    EnvironmentName=prod \
    ContainerImage=$IMAGE_URI \
    CorsAllowedOrigins=http://localhost:4200 \
    CreateEcrRepository=false \
    AppRunnerCpu="0.5 vCPU" \
    AppRunnerMemory="1 GB" \
    DynamoDBBillingMode=PROVISIONED \
    DynamoDBReadCapacityUnits=1 \
    DynamoDBWriteCapacityUnits=1 \
    DynamoDBSeedEnabled=true
```

Obtener la URL estable:

```bash
aws cloudformation describe-stacks \
  --stack-name yam-funds-backend-prod \
  --region $AWS_REGION \
  --query "Stacks[0].Outputs[?OutputKey=='ApplicationBaseUrl'].OutputValue | [0]" \
  --output text
```

### CI/CD Con GitHub Actions

El pipeline vive en:

```text
.github/workflows/deploy.yml
```

Reglas:

- CI corre en `push` y `pull_request` hacia `develop` y `main`.
- CD corre en `push` a `main` despues del merge y en ejecución manual.
- GitHub Actions se autentica en AWS mediante OIDC, sin `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY`.
- Las variables de despliegue se configuran en el environment `prod` de GitHub.

El role OIDC se crea con:

```text
deployment/cloudformation/github-actions-deployer-role.yaml
```

## Parte 2 — SQL

La prueba también incluye un ejercicio independiente de SQL.

La solución queda aislada de la aplicación principal en:

```text
part-2-sql/
```

Incluye un Docker Compose separado (`docker-compose.part2.yml`), esquema, seed, consulta solucion, validacion y evidencia esperada para ejecutar la base relacional `YAM` sin afectar DynamoDB ni el backend de la Parte 1.

## Estado del proyecto

> En desarrollo.

Implementado hasta ahora:

- Scaffold Clean Architecture Bancolombia.
- Modelos y gateways de dominio.
- Driven adapter DynamoDB.
- Driven adapter genérico `notifications`.
- DynamoDB Local con Docker Compose.
- Seed local de fondos y cliente inicial.
- Entry point WebFlux con `GET /api/health`.
- OpenAPI/Swagger configurado.
- Caso de uso `SubscribeToFund`.
- Caso de uso `CancelSubscription`.
- Caso de uso `GetTransactionHistory`.
- Endpoint `POST /api/clients/{clientId}/subscriptions`.
- Endpoint `DELETE /api/clients/{clientId}/subscriptions/{fundId}`.
- Endpoint `GET /api/clients/{clientId}/transactions`.
- Escritura transaccional DynamoDB para descontar saldo, crear suscripción y registrar transacción.
- Escritura transaccional DynamoDB para restaurar saldo, eliminar suscripción y registrar transacción.
- Consulta optimizada de historial con `Query` por `clientId` y sort key descendente, sin `Scan`.
- Notificaciones best-effort con fallback log-based para `EMAIL` y `SMS`.
- Manejador global de excepciones con formato estándar `error/status/timestamp`.

Pendiente:

- Integración real de notificaciones con AWS SNS/SES durante las tareas de despliegue en AWS.
- Prueba de concurrencia end-to-end contra DynamoDB Local para requests simultáneos de suscripción.
- Solución SQL de Parte 2.

Mejoras profesionales identificadas:

- Agregar paginación al historial de transacciones usando `LastEvaluatedKey` / `ExclusiveStartKey` de DynamoDB y retornar metadata de paginación en el response.
