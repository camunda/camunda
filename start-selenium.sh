#!/bin/sh

cd ./client
yarn
./node_modules/.bin/selenium-standalone install
./node_modules/.bin/selenium-standalone start
