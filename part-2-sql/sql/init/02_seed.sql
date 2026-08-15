USE YAM;

INSERT INTO cliente (id, nombre, apellidos, ciudad) VALUES
    (1, 'Ana', 'Gomez', 'Bogota'),
    (2, 'Carlos', 'Ruiz', 'Cali'),
    (3, 'Maria', 'Lopez', 'Medellin'),
    (4, 'Diego', 'Perez', 'Bogota'),
    (5, 'Laura', 'Torres', 'Cali'),
    (6, 'Sofia', 'Ramos', 'Bogota');

INSERT INTO sucursal (id, nombre, ciudad) VALUES
    (10, 'Sucursal Norte', 'Bogota'),
    (20, 'Sucursal Centro', 'Bogota'),
    (30, 'Sucursal Sur', 'Cali'),
    (40, 'Sucursal Poblado', 'Medellin');

INSERT INTO producto (id, nombre, tipoProducto) VALUES
    (100, 'Fondo Conservador', 'Fondo'),
    (200, 'Fondo Acciones', 'Fondo'),
    (300, 'Cuenta Premium', 'Cuenta'),
    (400, 'CDT Express', 'CDT'),
    (500, 'Fondo Internacional', 'Fondo');

INSERT INTO disponibilidad (idSucursal, idProducto) VALUES
    (10, 100),
    (20, 100),
    (20, 200),
    (30, 200),
    (40, 300),
    (10, 400),
    (30, 500),
    (40, 500);

INSERT INTO inscripcion (idProducto, idCliente) VALUES
    (100, 1),
    (200, 1),
    (400, 1),
    (200, 2),
    (300, 3),
    (500, 3),
    (100, 4),
    (500, 5),
    (400, 6);

INSERT INTO visitan (idSucursal, idCliente, fechaVisita) VALUES
    (10, 1, '2026-08-01'),
    (20, 1, '2026-08-02'),
    (20, 2, '2026-08-03'),
    (40, 3, '2026-08-04'),
    (10, 4, '2026-08-05'),
    (30, 5, '2026-08-06'),
    (40, 5, '2026-08-07');
