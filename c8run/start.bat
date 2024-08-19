
@echo on

:checkCamundaStartup
set RETRIES=24
set SLEEP_TIME=14
set URL=%~1
set NAME=%~2
set COMMAND="curl -XGET %URL%"

:loop
%COMMAND%
set commandErrorLevel=%ERROR_LEVEL%
timeout /NOBREAK %SLEEP_TIME%
set RETRIES=%RETRIES%-1

if %RETRIES% == 0 (
  echo "Error: %NAME% did not start!"
  EXIT /B 1
)
echo "Polling %NAME% ... %RETRIES% retries left"

if %commandErrorLevel% NEQ 0 (
  goto loop
)
echo "%NAME% has successfully been started."
start %URL%
EXIT /B 0

set URL="http://localhost:8080/operate/login"
start /B checkCamundaStartup "%URL%" "Camunda" 

@REM start /B internal\run.bat
call internal\run.bat %*
