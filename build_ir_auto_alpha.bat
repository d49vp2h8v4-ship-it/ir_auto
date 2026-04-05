@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ALPHA_VER=0.8_a"
set "OUT_NAME=ir_auto_alpha_0.8_a.jar"
set "ROOT=%~dp0"

echo [ir_auto] Building obfuscated mod jar...
echo [ir_auto] Alpha version: %ALPHA_VER%
echo [ir_auto] Output: %OUT_NAME%

pushd "%ROOT%" >nul
echo [ir_auto] Running Gradle (reobfJar)...
cmd /c ""%ROOT%gradlew.bat" clean reobfJar --no-daemon"
if errorlevel 1 goto :fail_popd

set "SRC_JAR="
for /f "delims=" %%F in ('dir /b /a:-d /o:-d "%ROOT%build\libs\*.jar" ^| findstr /i /v "sources dev"') do (
  set "SRC_JAR=%ROOT%build\libs\%%F"
  goto :found_jar
)
:found_jar
if "%SRC_JAR%"=="" (
  echo [ir_auto] ERROR: No built jar found in "%ROOT%build\libs".
  goto :fail_popd
)

echo [ir_auto] Using jar: %SRC_JAR%
copy /y "%SRC_JAR%" "%ROOT%%OUT_NAME%" >nul
if errorlevel 1 goto :fail_popd

if not exist "%ROOT%%OUT_NAME%" goto :fail_popd

echo [ir_auto] OK: %OUT_NAME%
popd >nul
exit /b 0

:fail
echo [ir_auto] FAILED
exit /b 1

:fail_popd
echo [ir_auto] FAILED
popd >nul
exit /b 1
