USE YAM;

WITH resultado AS (
    SELECT DISTINCT
        c.id,
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
),
esperado AS (
    SELECT 1 AS id, 'Ana' AS nombre, 'Gomez' AS apellidos
    UNION ALL SELECT 3, 'Maria', 'Lopez'
    UNION ALL SELECT 5, 'Laura', 'Torres'
)
SELECT 'resultado_menos_esperado' AS validacion, r.*
FROM resultado r
LEFT JOIN esperado e ON e.id = r.id
WHERE e.id IS NULL
UNION ALL
SELECT 'esperado_menos_resultado' AS validacion, e.*
FROM esperado e
LEFT JOIN resultado r ON r.id = e.id
WHERE r.id IS NULL;

SELECT
    c.nombre,
    c.apellidos,
    p.nombre AS producto_que_cumple
FROM cliente c
JOIN inscripcion i ON i.idCliente = c.id
JOIN producto p ON p.id = i.idProducto
WHERE EXISTS (
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
ORDER BY c.nombre, c.apellidos, p.nombre;
