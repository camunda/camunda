@echo off

set BASEDIR=%~dp0
cd "%BASEDIR%"

call yarn
yarn build
