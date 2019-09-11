# --- !Ups

drop index no_similar_report;

CREATE OR REPLACE FUNCTION my_date_trunc(text, timestamp with time zone)
    RETURNS timestamp with time zone
    AS $$ select date_trunc($1, $2) $$
    LANGUAGE sql
    IMMUTABLE;

ALTER TABLE signalement ALTER date_creation TYPE timestamptz USING date_creation AT TIME ZONE 'UTC';

create unique index no_similar_report on signalement (email, nom, prenom, details, my_date_trunc('day'::text, date_creation), adresse_etablissement);

# --- !Downs

drop index no_similar_report;

ALTER TABLE signalement ALTER date_creation TYPE timestamp;

create unique index no_similar_report on signalement (email, nom, prenom, details, date_trunc('day'::text, date_creation), adresse_etablissement);
