-- !Ups

ALTER TABLE websites ADD CONSTRAINT COMPANY_HOST UNIQUE (company_id, host);
ALTER TABLE reports ADD COLUMN COMPANY_COUNTRY VARCHAR;

-- !Downs

ALTER TABLE reports DROP COLUMN COMPANY_COUNTRY;
ALTER TABLE websites DROP CONSTRAINT COMPANY_HOST;
