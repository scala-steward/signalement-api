# --- !Ups

ALTER TABLE SIGNALEMENT ADD COLUMN code_postal VARCHAR;

# --- !Downs

ALTER TABLE SIGNALEMENT DROP code_postal;