@echo off

:: ####################################################################################################################
:: #                                                                                                                  #
:: #                                        Optimize Upgrade Script                                                   #
:: #                                                                                                                  #
:: #   Performs incremental upgrade of elasticsearch indexes and data structures to current version from previous.    #
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
set OPTIMIZE_CLASSPATH="%BASEDIR%\..\config;%BASEDIR%\..\lib\*;%BASEDIR%\*;%BASEDIR%\..\optimize-backend-${project.version}.jar"

echo.
echo Starting Camunda Optimize Upgrade to ${project.version}...
echo.

:: start optimize
set OPTIMIZE_CMD=%JAVA% %OPTIMIZE_JAVA_OPTS% -cp %OPTIMIZE_CLASSPATH% -Dfile.encoding=UTF-8 org.camunda.optimize.upgrade.main.UpgradeMain %1
call %OPTIMIZE_CMD%