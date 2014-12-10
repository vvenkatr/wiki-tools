#!/bin/bash

export MAVEN_OPTS="-Xmx4G"
args=(${@// /\\ })
mvn exec:java -Dexec.mainClass='de.mpii.wiki.WikiToolExecutor' -Dexec.args="${args[*]}"