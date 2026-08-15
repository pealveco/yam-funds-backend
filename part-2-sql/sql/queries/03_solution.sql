USE YAM;

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
