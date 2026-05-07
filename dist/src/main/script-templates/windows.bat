@echo off
setlocal

set "APP_NAME=@applicationName@"
set "MAIN_CLASS=@mainClass@"
for %%i in ("%~dp0..") do set "APP_HOME=%%~fi"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA=java"
)

set "DEFAULT_JVM_OPTS=@defaultJvmOpts@"
set "CLASSPATH=%APP_HOME%\config;%APP_HOME%\lib\*;%APP_HOME%\driver-lib\*"

cd /d "%APP_HOME%"

"%JAVA%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% -Dapp.home="%APP_HOME%" -cp "%CLASSPATH%" %MAIN_CLASS% %*
