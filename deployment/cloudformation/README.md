# CloudFormation YAM Funds Backend

Este directorio contiene el template de infraestructura AWS-native para el backend.

## Template

```text
deployment/cloudformation/yam-funds-backend.yaml
```

Para CI/CD con GitHub Actions tambien existe un template de soporte:

```text
deployment/cloudformation/github-actions-deployer-role.yaml
```

Ese template crea el IAM Role que GitHub Actions asume mediante OIDC, sin guardar access keys largas en GitHub.

## Recursos Incluidos

- Tablas DynamoDB:
  - clients
  - funds
  - subscriptions
  - transactions
- Modo de facturacion DynamoDB configurable:
  - default `PAY_PER_REQUEST` para cumplir el requisito de la prueba tecnica.
  - opcional `PROVISIONED` para mayor control de costos.
- ECS Fargate: cluster, task definition y service.
- Repositorio ECR opcional.
- IAM execution role y task role.
- Security group para el puerto `8080`.
- CloudWatch log group.
- Outputs utiles del stack.

## Nota Sobre Notificaciones

El task role incluye permisos reservados para futuros adapters de notificaciones:

- `sns:Publish`
- `ses:SendEmail`
- `ses:SendRawEmail`

La implementacion actual de la aplicacion sigue usando el fallback log-based de notificaciones. Antes de cerrar la entrega real de notificaciones AWS, se debe reemplazar o extender el adapter `notifications` para usar SNS/SES.

## Ejemplo De Despliegue

Antes de desplegar el stack, se debe construir y publicar la imagen de la aplicacion. Luego se pasa la URI resultante como parametro `ContainerImage`.

```bash
aws cloudformation deploy \
  --stack-name yam-funds-backend-prod \
  --template-file deployment/cloudformation/yam-funds-backend.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    ProjectName=yam-funds \
    EnvironmentName=prod \
    ContainerImage=<account-id>.dkr.ecr.<region>.amazonaws.com/yam-funds-backend:latest \
    VpcId=<vpc-id> \
    PublicSubnetIds=<subnet-id-1>,<subnet-id-2> \
    CorsAllowedOrigins=https://your-frontend-domain.example \
    CreateEcrRepository=false
```

## Control De Costos DynamoDB

El template usa `PAY_PER_REQUEST` por defecto porque la prueba tecnica lo solicita y porque mantiene el despliegue simple.

Para tener un techo de costo mas estricto, se puede desplegar con capacidad provisionada:

```bash
aws cloudformation deploy \
  --stack-name yam-funds-backend-prod \
  --template-file deployment/cloudformation/yam-funds-backend.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    ProjectName=yam-funds \
    EnvironmentName=prod \
    ContainerImage=<account-id>.dkr.ecr.<region>.amazonaws.com/yam-funds-backend:latest \
    VpcId=<vpc-id> \
    PublicSubnetIds=<subnet-id-1>,<subnet-id-2> \
    CorsAllowedOrigins=https://your-frontend-domain.example \
    CreateEcrRepository=false \
    DynamoDBBillingMode=PROVISIONED \
    DynamoDBReadCapacityUnits=1 \
    DynamoDBWriteCapacityUnits=1
```

Este template ejecuta el servicio en subnets publicas con `AssignPublicIp: ENABLED`. Para un despliegue productivo mas robusto, conviene usar subnets privadas detras de un Application Load Balancer y restringir el ingreso al security group del ALB.

## CI/CD Con GitHub Actions

El workflow vive en:

```text
.github/workflows/deploy.yml
```

Flujo:

```text
push o PR hacia develop/main
  -> CI: ./gradlew test

push a main, PR hacia main desde el mismo repositorio, o workflow manual
  -> CD: docker build
  -> CD: docker push a ECR
  -> CD: aws cloudformation deploy
```

### 1. Crear El Role OIDC Una Sola Vez

```bash
aws cloudformation deploy \
  --stack-name yam-funds-github-actions-deployer \
  --template-file deployment/cloudformation/github-actions-deployer-role.yaml \
  --region us-east-1 \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    GitHubRepository=pealveco/yam-funds-backend \
    GitHubDeployBranch=main \
    ProjectName=yam-funds \
    EnvironmentName=prod \
    EcrRepositoryName=yam-funds-backend
```

Si el proveedor OIDC de GitHub ya existe en la cuenta AWS, usar:

```bash
CreateGitHubOidcProvider=false
```

### 2. Obtener El ARN Del Role

```bash
aws cloudformation describe-stacks \
  --stack-name yam-funds-github-actions-deployer \
  --region us-east-1 \
  --query "Stacks[0].Outputs[?OutputKey=='GitHubActionsDeployRoleArn'].OutputValue | [0]" \
  --output text
```

### 3. Configurar Variables En GitHub

En el repositorio GitHub, crear o usar el environment `prod`:

```text
Settings -> Environments -> prod -> Environment variables
```

Crear:

```text
AWS_ROLE_TO_ASSUME=<role-arn-del-paso-anterior>
AWS_REGION=us-east-1
ECR_REPOSITORY=yam-funds-backend
PROJECT_NAME=yam-funds
ENVIRONMENT_NAME=prod
STACK_NAME=yam-funds-backend-prod
VPC_ID=<vpc-id>
PUBLIC_SUBNET_IDS=<subnet-id-1>,<subnet-id-2>
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example
DYNAMODB_BILLING_MODE=PROVISIONED
DYNAMODB_READ_CAPACITY_UNITS=1
DYNAMODB_WRITE_CAPACITY_UNITS=1
```

No se requieren `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` cuando se usa OIDC.
