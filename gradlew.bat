@echo off
setlocal
set "GRADLE_VERSION=8.9"
set "GRADLE_USER_HOME=%~dp0.gradle-user-home"
set "GRADLE_HOME=%GRADLE_USER_HOME%\bootstrap\gradle-%GRADLE_VERSION%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  set "GRADLE_ZIP=%TEMP%\gradle-%GRADLE_VERSION%-bin.zip"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force -Path '%GRADLE_USER_HOME%\bootstrap' | Out-Null; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%TEMP%\gradle-%GRADLE_VERSION%-bin.zip'; Expand-Archive -LiteralPath '%TEMP%\gradle-%GRADLE_VERSION%-bin.zip' -DestinationPath '%GRADLE_USER_HOME%\bootstrap' -Force"
  if errorlevel 1 exit /b 1
)
set "GRADLE_USER_HOME=%~dp0.gradle-user-home"
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
