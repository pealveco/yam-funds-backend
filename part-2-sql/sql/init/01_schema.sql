CREATE DATABASE IF NOT EXISTS YAM;

USE YAM;

DROP TABLE IF EXISTS visitan;
DROP TABLE IF EXISTS disponibilidad;
DROP TABLE IF EXISTS inscripcion;
DROP TABLE IF EXISTS producto;
DROP TABLE IF EXISTS sucursal;
DROP TABLE IF EXISTS cliente;

CREATE TABLE cliente (
    id INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    ciudad VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE sucursal (
    id INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    ciudad VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE producto (
    id INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    tipoProducto VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE inscripcion (
    idProducto INT NOT NULL,
    idCliente INT NOT NULL,
    PRIMARY KEY (idProducto, idCliente),
    CONSTRAINT fk_inscripcion_producto
        FOREIGN KEY (idProducto) REFERENCES producto (id),
    CONSTRAINT fk_inscripcion_cliente
        FOREIGN KEY (idCliente) REFERENCES cliente (id)
);

CREATE TABLE disponibilidad (
    idSucursal INT NOT NULL,
    idProducto INT NOT NULL,
    PRIMARY KEY (idSucursal, idProducto),
    CONSTRAINT fk_disponibilidad_sucursal
        FOREIGN KEY (idSucursal) REFERENCES sucursal (id),
    CONSTRAINT fk_disponibilidad_producto
        FOREIGN KEY (idProducto) REFERENCES producto (id)
);

CREATE TABLE visitan (
    idSucursal INT NOT NULL,
    idCliente INT NOT NULL,
    fechaVisita DATE NOT NULL,
    PRIMARY KEY (idSucursal, idCliente),
    CONSTRAINT fk_visitan_sucursal
        FOREIGN KEY (idSucursal) REFERENCES sucursal (id),
    CONSTRAINT fk_visitan_cliente
        FOREIGN KEY (idCliente) REFERENCES cliente (id)
);
