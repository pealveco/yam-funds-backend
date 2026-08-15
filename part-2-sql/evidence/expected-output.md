# Evidencia esperada

Comando:

```bash
docker compose -f docker-compose.part2.yml exec -T part2-mysql \
  mysql -uyam_part2 -pyam_part2_pass YAM < part-2-sql/sql/queries/03_solution.sql
```

Salida esperada:

```text
nombre	apellidos
Ana	Gomez
Laura	Torres
Maria	Lopez
```

La validacion `resultado_menos_esperado` y `esperado_menos_resultado` no debe retornar filas. La segunda consulta de validacion lista el producto inscrito que cumple la condicion por cliente.

Salida de validacion esperada:

```text
nombre	apellidos	producto_que_cumple
Ana	Gomez	CDT Express
Ana	Gomez	Fondo Conservador
Laura	Torres	Fondo Internacional
Maria	Lopez	Cuenta Premium
```
