@echo off
cd /d "%~dp0"
set "AFTERGLOW_PYTHON=%LOCALAPPDATA%\Programs\Python\Python311\python.exe"
if not exist "%AFTERGLOW_PYTHON%" set "AFTERGLOW_PYTHON=python"
"%AFTERGLOW_PYTHON%" "%~dp0controller.py" --start
if errorlevel 1 (
  echo.
  echo The controller request failed. Read the message above.
  pause
  exit /b 1
)
exit /b 0
