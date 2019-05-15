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

ALTER TABLE PIECE_JOINTE ADD CONSTRAINT fk_report_files FOREIGN KEY (signalement_id) REFERENCES signalement(id);

CREATE TABLE EVENTS
(
    ID UUID PRIMARY KEY,
    REPORT_ID UUID NOT NULL,
    USER_ID UUID NOT NULL,
    CREATION_DATE TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    EVENT_TYPE VARCHAR NOT NULL,
    ACTION VARCHAR NOT NULL,
    RESULT_ACTION VARCHAR,
    DETAIL VARCHAR
);

ALTER TABLE EVENTS ADD CONSTRAINT FK_EVENTS_REPORT FOREIGN KEY (REPORT_ID) REFERENCES SIGNALEMENT(ID);
ALTER TABLE EVENTS ADD CONSTRAINT FK_EVENTS_USERS FOREIGN KEY (USER_ID) REFERENCES USERS(ID);

ALTER TABLE SIGNALEMENT ADD COLUMN STATUS_PRO VARCHAR;
ALTER TABLE SIGNALEMENT ADD COLUMN STATUS_CONSO VARCHAR;


# --- !Downs

ALTER TABLE EVENTS DROP CONSTRAINT FK_EVENTS_REPORT;
ALTER TABLE EVENTS DROP CONSTRAINT FK_EVENTS_USERS;

DROP TABLE USERS;
DROP TABLE EVENTS;

ALTER TABLE PIECE_JOINTE DROP CONSTRAINT fk_report_files;

ALTER TABLE SIGNALEMENT DROP STATUS_PRO;
ALTER TABLE SIGNALEMENT DROP STATUS_CONSO;

