# CloudFormation YAM Funds Backend

Este directorio contiene el template de infraestructura AWS-native para el backend.

## Template

```text
deployment/cloudformation/yam-funds-backend.yaml
```

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
    CorsAllowedOrigins=https://your-frontend-domain.example
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
    DynamoDBBillingMode=PROVISIONED \
    DynamoDBReadCapacityUnits=1 \
    DynamoDBWriteCapacityUnits=1
```

Este template ejecuta el servicio en subnets publicas con `AssignPublicIp: ENABLED`. Para un despliegue productivo mas robusto, conviene usar subnets privadas detras de un Application Load Balancer y restringir el ingreso al security group del ALB.
