@echo off
setlocal
set "ROOT=%~dp0"
set "ANDROID_DIR=%ROOT%android"

if not defined JAVA_HOME (
  if exist "C:\Program Files\Java\jdk-21.0.11" set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
)
if not defined ANDROID_HOME (
  if exist "C:\Android\sdk" set "ANDROID_HOME=C:\Android\sdk"
)

if not defined JAVA_HOME (
  echo [ERROR] JAVA_HOME is not set. Install JDK 17 or 21 and set JAVA_HOME.
  exit /b 1
)
if not defined ANDROID_HOME (
  echo [ERROR] ANDROID_HOME is not set. Install Android SDK platform 35 first.
  exit /b 1
)

if not exist "%ANDROID_DIR%\local.properties" (
  echo sdk.dir=%ANDROID_HOME:\=\\%> "%ANDROID_DIR%\local.properties"
)

cd /d "%ANDROID_DIR%"

echo ============================================
echo  Building CHZUyyd Android (debug, arm64+x86_64)
echo ============================================
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 goto fail

echo ============================================
echo  Building CHZUyyd Android (release, arm64)
echo ============================================
call gradlew.bat assembleRelease -PchzuAbis=arm64-v8a --no-daemon
if errorlevel 1 goto fail

if not exist "%ROOT%dist" mkdir "%ROOT%dist"
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "%ROOT%dist\CHZUyyd-v1.0.0-debug.apk" >nul
copy /Y "app\build\outputs\apk\release\app-release.apk" "%ROOT%dist\CHZUyyd-v1.0.0-arm64.apk" >nul

echo.
echo Build OK:
echo   %ROOT%dist\CHZUyyd-v1.0.0-arm64.apk
echo   %ROOT%dist\CHZUyyd-v1.0.0-debug.apk
exit /b 0

:fail
echo.
echo [ERROR] Build failed. See the Gradle output above.
exit /b 1
