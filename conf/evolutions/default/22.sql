-- !Ups

ALTER TABLE PIECE_JOINTE RENAME TO REPORT_FILES;
ALTER TABLE REPORT_FILES RENAME COLUMN SIGNALEMENT_ID TO REPORT_ID;
ALTER TABLE REPORT_FILES RENAME COLUMN DATE_CREATION TO CREATION_DATE;
ALTER TABLE REPORT_FILES RENAME COLUMN NOM TO FILENAME;

ALTER TABLE REPORT_FILES ADD COLUMN ORIGIN VARCHAR NOT NULL DEFAULT 'consumer';
ALTER TABLE REPORT_FILES ALTER COLUMN ORIGIN DROP DEFAULT;

UPDATE EVENTS SET DETAILS = DETAILS::jsonb || '{"fileIds":[]}'::jsonb
WHERE ACTION = 'Réponse du professionnel au signalement'
AND DETAILS::jsonb -> 'fileIds' is null;

-- !Downs

ALTER TABLE REPORT_FILES DROP COLUMN ORIGIN;

ALTER TABLE REPORT_FILES RENAME COLUMN REPORT_ID TO SIGNALEMENT_ID;
ALTER TABLE REPORT_FILES RENAME COLUMN CREATION_DATE TO DATE_CREATION;
ALTER TABLE REPORT_FILES RENAME COLUMN FILENAME TO NOM;
ALTER TABLE REPORT_FILES RENAME TO PIECE_JOINTE;

