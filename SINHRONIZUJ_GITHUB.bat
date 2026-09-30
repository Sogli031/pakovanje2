@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

set "MODE=%~1"
if /I "%MODE%"=="--dry-run" set "NO_PAUSE=1"

cd /d "%~dp0"
for %%P in ("%CD%") do set "PROJECT_NAME=%%~nxP"
set "EXPECTED_ORIGIN=github.com/Sogli/%PROJECT_NAME%"

echo.
echo === %PROJECT_NAME% GitHub sinhronizacija ===
echo Folder: %CD%
echo.

where git >nul 2>nul
if errorlevel 1 (
    echo Greška: Git nije pronađen.
    echo Instaliraj Git for Windows ili proveri da li radi iz Command Prompt-a.
    call :finish 1
    exit /b 1
)

git rev-parse --is-inside-work-tree >nul 2>nul
if errorlevel 1 (
    echo Greška: ovaj folder nije Git repo.
    call :finish 1
    exit /b 1
)

for /f "delims=" %%U in ('git remote get-url origin 2^>nul') do set "ORIGIN=%%U"
if not defined ORIGIN (
    echo Greška: nije podešen GitHub origin remote.
    call :finish 1
    exit /b 1
)

echo %ORIGIN% | findstr /I /C:"%EXPECTED_ORIGIN%" >nul
if errorlevel 1 (
    echo Greška: origin ne pokazuje na očekivani repo.
    echo Očekivani repo: %EXPECTED_ORIGIN%
    echo Trenutni origin: %ORIGIN%
    call :finish 1
    exit /b 1
)

for /f "delims=" %%B in ('git branch --show-current 2^>nul') do set "BRANCH=%%B"
if not defined BRANCH (
    echo Greška: Git nije na običnoj grani, pa ne znam gde da pošaljem izmene.
    call :finish 1
    exit /b 1
)

echo Repo: %ORIGIN%
echo Grana: %BRANCH%
echo.

if /I "%MODE%"=="--dry-run" (
    echo Dry-run: samo proveravam stanje, bez commit/push.
    git status --short --branch
    call :finish 0
    exit /b 0
)

git update-index -q --refresh
if errorlevel 1 (
    echo Greška: Git ne može da osveži stanje fajlova.
    call :finish 1
    exit /b 1
)

echo Dodajem sve lokalne izmene koje nisu u .gitignore...
git add -A
if errorlevel 1 (
    echo Greška: nije uspelo dodavanje izmena.
    call :finish 1
    exit /b 1
)

set "HAS_CHANGES="
git diff --cached --quiet
if errorlevel 1 set "HAS_CHANGES=1"

if defined HAS_CHANGES (
    for /f "delims=" %%T in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Date -Format 'yyyy-MM-dd HH:mm:ss'"') do set "STAMP=%%T"
    echo Pravim lokalni commit...
    git commit -m "Automatska sinhronizacija !STAMP!"
    if errorlevel 1 (
        echo Greška: commit nije uspeo.
        call :finish 1
        exit /b 1
    )
) else (
    echo Nema lokalnih izmena za commit.
)

echo.
echo Proveravam stanje GitHub grane...
git fetch origin
if errorlevel 1 (
    echo Greška: nije uspelo preuzimanje sa GitHub-a.
    call :finish 1
    exit /b 1
)

set "REMOTE_BRANCH_EXISTS="
git ls-remote --exit-code --heads origin "%BRANCH%" >nul 2>nul
set "LS_REMOTE_RESULT=!ERRORLEVEL!"
if "!LS_REMOTE_RESULT!"=="0" set "REMOTE_BRANCH_EXISTS=1"
if not "!LS_REMOTE_RESULT!"=="0" if not "!LS_REMOTE_RESULT!"=="2" (
    echo Greška: ne mogu da proverim da li GitHub grana "%BRANCH%" postoji.
    call :finish 1
    exit /b 1
)

if defined REMOTE_BRANCH_EXISTS (
    echo GitHub grana "%BRANCH%" postoji; lokalna verzija ima prednost.
) else (
    echo GitHub grana "%BRANCH%" još ne postoji; pravim je prvim push-om.
)

echo.
echo Šaljem lokalnu verziju na GitHub...
git push --force-with-lease -u origin "%BRANCH%"
if errorlevel 1 (
    echo Greška: push na GitHub nije uspeo.
    call :finish 1
    exit /b 1
)

echo.
echo Gotovo. Lokalna verzija je poslata na GitHub.
call :finish 0
exit /b 0

:finish
set "RESULT=%~1"
echo.
if "%RESULT%"=="0" (
    echo Uspešno završeno.
) else (
    echo Završeno sa greškom. Ne pokreći dodatne Git komande dok se ne proveri stanje.
)
echo.
if not defined NO_PAUSE pause
exit /b %RESULT%