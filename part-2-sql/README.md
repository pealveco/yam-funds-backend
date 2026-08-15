# Parte 2 - SQL

Esta carpeta contiene la segunda parte de la prueba tecnica. Es independiente de la API de fondos y se ejecuta con un contenedor MySQL separado mediante `docker-compose.part2.yml`.

## Enunciado

Base de datos: `YAM`

Tablas disponibles:

```text
Cliente(id, nombre, apellidos, ciudad)
Sucursal(id, nombre, ciudad)
Producto(id, nombre, tipoProducto)
Inscripcion(idProducto, idCliente)
Disponibilidad(idSucursal, idProducto)
Visitan(idSucursal, idCliente, fechaVisita)
```

Consulta solicitada:

```text
Obtener los nombres de los clientes que tienen inscrito algun producto disponible solo en las sucursales que visitan.
```

## Archivos

```text
part-2-sql/
├── README.md
├── evidence/
│   └── expected-output.md
└── sql/
    ├── init/
    │   ├── 01_schema.sql
    │   └── 02_seed.sql
    └── queries/
        ├── 03_solution.sql
        └── 04_validation.sql
```

## Ejecutar con Docker Compose

Levantar MySQL para la parte 2:

```bash
docker compose -f docker-compose.part2.yml up -d
```

Ejecutar la consulta solucion:

```bash
docker compose -f docker-compose.part2.yml exec -T part2-mysql \
  mysql -uyam_part2 -pyam_part2_pass YAM < part-2-sql/sql/queries/03_solution.sql
```

Ejecutar la validacion:

```bash
docker compose -f docker-compose.part2.yml exec -T part2-mysql \
  mysql -uyam_part2 -pyam_part2_pass YAM < part-2-sql/sql/queries/04_validation.sql
```

Detener la base de datos:

```bash
docker compose -f docker-compose.part2.yml down
```

Recrear desde cero, incluyendo datos:

```bash
docker compose -f docker-compose.part2.yml down -v
docker compose -f docker-compose.part2.yml up -d
```

## Interpretacion de la consulta

Un cliente cumple cuando existe al menos un producto inscrito por ese cliente y todas las sucursales donde ese producto esta disponible pertenecen al conjunto de sucursales que el cliente visita.

La consulta evita el caso vacio exigiendo que el producto tenga al menos una fila en `disponibilidad`.

## Consulta solucion

```sql
SELECT DISTINCT
    c.nombre,
    c.apellidos
FROM cliente c
WHERE EXISTS (
    SELECT 1
    FROM inscripcion i
    WHERE i.idCliente = c.id
      AND EXISTS (
          SELECT 1
          FROM disponibilidad d
          WHERE d.idProducto = i.idProducto
      )
      AND NOT EXISTS (
          SELECT 1
          FROM disponibilidad d
          WHERE d.idProducto = i.idProducto
            AND NOT EXISTS (
                SELECT 1
                FROM visitan v
                WHERE v.idCliente = c.id
                  AND v.idSucursal = d.idSucursal
            )
      )
)
ORDER BY c.nombre, c.apellidos;
```
