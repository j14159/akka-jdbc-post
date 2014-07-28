#!/bin/bash

# This was heavily based on https://github.com/jackdb/pg-app-dev-vm/blob/master/Vagrant-setup/bootstrap.sh
# Use/reuse as you see fit at your own risk as this is intended solely for integration tests and basic demo.

DB_USER=akkajdbc
DB_PASS=akkajdbc

PG_VERSION=9.3
PG_REPO_APT_SOURCE=/etc/apt/sources.list.d/pgdg.list

echo "deb http://apt.postgresql.org/pub/repos/apt/ precise-pgdg main" > "$PG_REPO_APT_SOURCE"
wget --quiet -O - http://apt.postgresql.org/pub/repos/apt/ACCC4CF8.asc | apt-key add -

apt-get update

apt-get -y install "postgresql-$PG_VERSION" "postgresql-contrib-$PG_VERSION"

PG_CONF="/etc/postgresql/$PG_VERSION/main/postgresql.conf"
PG_HBA="/etc/postgresql/$PG_VERSION/main/pg_hba.conf"
PG_DIR="/var/lib/postgresql/$PG_VERSION/main"

# Edit postgresql.conf to change listen address to '*':
sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" "$PG_CONF"

# Append to pg_hba.conf to add password auth:
echo "host    all             all             all                     md5" >> "$PG_HBA"

service postgresql restart

sudo -u postgres createdb akkajdbc
sudo -u postgres psql -f /vagrant/schema.sql akkajdbc

