#!/bin/bash
# Install local jar libs into maven repository

GUROBI_JAR=$1
PROLOGBEAN_JAR=$2
METAMAP_API_JAR=$3

mvn install:install-file -Dfile="$GUROBI_JAR" -DgroupId=gurobi \
     -DartifactId=gurobi -Dversion=6.0.5 -Dpackaging=jar

mvn install:install-file -Dfile="$PROLOGBEAN_JAR" -DgroupId=se.sics \
     -DartifactId=prologbeans -Dversion=4.2.1 -Dpackaging=jar

mvn install:install-file -Dfile="$METAMAP_API_JAR" -DgroupId=gov.nih.nlm.nls \
     -DartifactId=metamap-api -Dversion=2.0 -Dpackaging=jar
