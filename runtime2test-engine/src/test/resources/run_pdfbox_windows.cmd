@echo off
setlocal

where java >nul 2>nul
if errorlevel 1 (
  echo Error: java not found in PATH. Please install JDK 17+ or set PATH correctly.
  exit /b 1
)

set "JAR_PATH="
for /f "delims=" %%I in ('dir /b /o-d "app\target\pdfbox-app-*.jar" 2^>nul') do (
  if not defined JAR_PATH set "JAR_PATH=app\target\%%I"
)
if not defined JAR_PATH (
  echo Error: no jar found at app\target\pdfbox-app-*.jar
  echo Run this script from your pdfbox project root ^(e.g. C:\Software Engineering\pdfbox^).
  exit /b 1
)

set "INPUT_PDF=CodeMonkey.pdf"
if not exist "%INPUT_PDF%" (
  set "INPUT_PDF="
  for %%F in (*.pdf) do (
    if /i not "%%~nxF"=="Merged.pdf" (
      if not defined INPUT_PDF set "INPUT_PDF=%%~nxF"
    )
  )
)

if not defined INPUT_PDF (
  echo Error: no input PDF found in current directory: %CD%
  echo Place a PDF in this directory, or rename your file to CodeMonkey.pdf.
  exit /b 1
)

del /q CodeMonkey-*.pdf 2>nul
del /q CodeMonkey*.jpg 2>nul
del /q Merged.pdf 2>nul

echo Running with: %JAR_PATH%
echo Input PDF: %INPUT_PDF%

if "%~1"=="" (
  java -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -jar "%JAR_PATH%" render --input="%INPUT_PDF%"
) else (
  java -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints %1 -jar "%JAR_PATH%" render --input="%INPUT_PDF%"
)
if errorlevel 1 exit /b 1

echo Done
