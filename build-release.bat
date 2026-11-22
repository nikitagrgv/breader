@echo off
rem Builds the release APK: app\build\outputs\apk\release\app-release.apk
cd /d "%~dp0"
call "%~dp0gradlew.bat" assembleRelease
if errorlevel 1 (
    echo.
    echo Build failed.
) else (
    echo.
    echo APK: %~dp0app\build\outputs\apk\release\app-release.apk
)
rem Keeps the window open when started by a double click.
echo %cmdcmdline% | find /i "%~0" >nul && pause
