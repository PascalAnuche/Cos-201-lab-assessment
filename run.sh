#!/bin/sh
# Student Course Management System - compile and start (macOS / Linux)
# Needs a Java Development Kit (JDK), version 11 or newer.
cd "$(dirname "$0")" || exit 1
mkdir -p out
echo "Compiling..."
javac -encoding UTF-8 -d out src/*.java && java -cp out Main "$@"
