# --- !Ups

CREATE TABLE USERS
(
    ID UUID PRIMARY KEY,
    EMAIL VARCHAR NOT NULL,
    PASSWORD VARCHAR NOT NULL,
    FIRSTNAME VARCHAR NOT NULL,
    LASTNAME VARCHAR NOT NULL,
    ROLE VARCHAR NOT NULL
);

INSERT INTO USERS VALUES ('e6de6b48-1c53-4d3e-a7ff-dd9b643073cf', 'jerome.rivals@gmail.com', '$2a$10$z5OdWpY05I9tvCJt0EplWOqLWLi6TFCyzZA4DoVlvCWbjmgCsQzwC', 'Jérôme', 'RIVALS', 'Admin')

# --- !Downs

DROP TABLE USERS;