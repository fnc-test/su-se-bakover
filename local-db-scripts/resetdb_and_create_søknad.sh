#!/bin/sh
BASEDIR=$(dirname "$0")
. "${BASEDIR}/functions.sh"
"${BASEDIR}/resetdb.sh"
while ! "$(dockerCompose)" ps | grep su-se-bakover | grep healthy
do
  echo "Waiting for postgres to start. Sleeping one second ..."
  sleep 1
done
./gradlew nySøknad
