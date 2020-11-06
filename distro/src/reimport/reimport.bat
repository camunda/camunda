@echo off

:: ####################################################################################################################
:: #                                                                                                                  #
:: #                                        Optimize Reimport Script                                                  #
:: #                                                                                                                  #
:: #   Purges imported engine data from the Optimize database in order to perform a reimport of engine data           #
:: #   without losing your Optimize data (e.g. report definitions). See:                                              #
:: #   https://docs.camunda.org/optimize/latest/technical-guide/update/#force-reimport-of-engine-data-in-optimize     #
:: #                                                                                                                  #
:: ####################################################################################################################

set BASEDIR=%~dp0
cd "%BASEDIR%"

:: now set the path to java
IF DEFINED JAVA_HOME (
  set JAVA="%JAVA_HOME%\bin\java.exe"
) ELSE (
  set JAVA=java
)

:: check if there are custom JVM options set.
IF NOT DEFINED OPTIMIZE_JAVA_OPTS (
  set OPTIMIZE_JAVA_OPTS=-Xms128m -Xmx512m -XX:MetaspaceSize=64m -XX:MaxMetaspaceSize=256m
)

:: Set up classpath
set OPTIMIZE_CLASSPATH="%BASEDIR%\..\config;%BASEDIR%\*;%BASEDIR%\..\lib\*;%BASEDIR%\..\optimize-backend-${project.version}.jar"

echo.
echo Starting Camunda Optimize Reimport ${project.version}...
echo.

:: start optimize
set OPTIMIZE_CMD=%JAVA% %OPTIMIZE_JAVA_OPTS% -cp %OPTIMIZE_CLASSPATH% -Dfile.encoding=UTF-8 org.camunda.optimize.reimport.preparation.ReimportPreparation
call %OPTIMIZE_CMD%