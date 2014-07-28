-- clearly don't do this in a real project, this is just for convenience here:
CREATE USER akkajdbc WITH PASSWORD 'akkajdbc';

-- our basic aggregate root:
CREATE TABLE person (
  id         serial primary key,
  name       text,
  email      text unique
);

-- obviously minimal:
CREATE TABLE address (
  owner     integer references person (id),
  street     text,
  city       text
);

GRANT SELECT,INSERT,UPDATE,DELETE ON person TO akkajdbc;
GRANT SELECT,INSERT,UPDATE,DELETE ON address TO akkajdbc;
GRANT SELECT,USAGE ON person_id_seq TO akkajdbc;

