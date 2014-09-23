#!/bin/bash

export MAVEN_OPTS="-Xmx4G"
args=(${@// /\\ })
mvn exec:java -Dexec.mainClass='de.mpii.wikitools.WikiRevTools' -Dexec.args="${args[*]}"