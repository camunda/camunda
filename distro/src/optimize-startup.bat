@echo off

:: ###############################################################################
:: #                                                                             #
:: #                     Optimize Production Startup Script                      #
:: #                                                                             #
:: ###############################################################################
::
:: Note: This script is supposed to be used in production
:: to start-up Optimize. For further information please consult
:: the documentation: https://docs.camunda.org/optimize/latest/technical-guide/installation/
::
:: Optionally, you can overwrite the default JVM options by setting the `OPTIMIZE_JAVA_OPTS`
:: variable.

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
  set OPTIMIZE_JAVA_OPTS=-Xms1024m -Xmx1024m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m
)

:: Set up the optimize classpaths, i.e. add the environment folder, all jars in the
:: plugin directory and the optimize jar
set OPTIMIZE_CLASSPATH="%BASEDIR%environment;%BASEDIR%plugin\*;%BASEDIR%optimize-backend-${project.version}.jar"

echo.
echo Starting Camunda Optimize ${project.version}
echo.

:: start optimize
set OPTIMIZE_CMD=%JAVA% %OPTIMIZE_JAVA_OPTS% -cp %OPTIMIZE_CLASSPATH% -Dfile.encoding=UTF-8 org.camunda.optimize.Main
call %OPTIMIZE_CMD%