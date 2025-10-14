@echo off
setlocal enabledelayedexpansion

REM Script de compilation Traccar - Backend + Frontend
REM Auteur: Percroy2
REM Description: Compile le serveur Java et l'interface web React

set "ERROR_COLOR=91"
set "SUCCESS_COLOR=92"
set "INFO_COLOR=96"
set "WARNING_COLOR=93"

set "BACKEND_ONLY=0"
set "FRONTEND_ONLY=0"
set "CLEAN=0"

REM Parse arguments
:parse_args
if "%1"=="" goto :main
if "%1"=="--backend-only" set "BACKEND_ONLY=1" & shift & goto :parse_args
if "%1"=="--frontend-only" set "FRONTEND_ONLY=1" & shift & goto :parse_args
if "%1"=="--clean" set "CLEAN=1" & shift & goto :parse_args
if "%1"=="--help" goto :show_help
echo Unknown argument: %1
goto :show_help

:show_help
echo === Script de compilation Traccar ===
echo.
echo Usage: build.bat [OPTIONS]
echo.
echo Options:
echo   --backend-only    Compile uniquement le backend Java
echo   --frontend-only   Compile uniquement le frontend React
echo   --clean           Nettoyage complet avant compilation
echo   --help            Affiche cette aide
echo.
echo Exemples:
echo   build.bat                     # Compile tout
echo   build.bat --backend-only      # Backend seulement
echo   build.bat --frontend-only     # Frontend seulement
echo   build.bat --clean             # Nettoyage + compilation complète
echo.
exit /b 0

:main
echo [%INFO_COLOR%m%] Démarrage de la compilation Traccar...
echo.

set "START_TIME=%TIME%"
set "BACKEND_SUCCESS=0"
set "FRONTEND_SUCCESS=0"

REM Compilation Backend
if "%FRONTEND_ONLY%"=="0" (
    call :compile_backend
    echo.
)

REM Compilation Frontend
if "%BACKEND_ONLY%"=="0" (
    call :compile_frontend
    echo.
)

REM Affichage des résultats
call :show_results
exit /b %EXIT_CODE%

:compile_backend
echo [%INFO_COLOR%m%] Compilation du backend Java...

REM Vérifier Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [%ERROR_COLOR%m%] Java n'est pas installé ou pas dans le PATH
    set "BACKEND_SUCCESS=1"
    goto :eof
)

REM Vérifier Gradle wrapper
if not exist "gradlew.bat" (
    echo [%ERROR_COLOR%m%] Gradle wrapper non trouvé
    set "BACKEND_SUCCESS=1"
    goto :eof
)

REM Nettoyage si demandé
if "%CLEAN%"=="1" (
    echo [%WARNING_COLOR%m%] Nettoyage du backend...
    call gradlew.bat clean
    if errorlevel 1 (
        echo [%WARNING_COLOR%m%] Nettoyage échoué, continuation...
    )
)

REM Compilation
echo [%INFO_COLOR%m%] Compilation en cours...
call gradlew.bat build -x test
if errorlevel 1 (
    echo [%ERROR_COLOR%m%] Erreur lors de la compilation du backend
    set "BACKEND_SUCCESS=1"
) else (
    echo [%SUCCESS_COLOR%m%] Backend compilé avec succès!
    set "BACKEND_SUCCESS=0"
)
goto :eof

:compile_frontend
echo [%INFO_COLOR%m%] Compilation du frontend React...

REM Vérifier Node.js
node --version >nul 2>&1
if errorlevel 1 (
    echo [%ERROR_COLOR%m%] Node.js n'est pas installé ou pas dans le PATH
    set "FRONTEND_SUCCESS=1"
    goto :eof
)

REM Vérifier npm
npm --version >nul 2>&1
if errorlevel 1 (
    echo [%ERROR_COLOR%m%] npm n'est pas installé
    set "FRONTEND_SUCCESS=1"
    goto :eof
)

REM Vérifier que traccar-web existe
if not exist "traccar-web" (
    echo [%ERROR_COLOR%m%] Dossier traccar-web non trouvé
    set "FRONTEND_SUCCESS=1"
    goto :eof
)

REM Aller dans le dossier frontend
pushd "traccar-web"

REM Nettoyage si demandé
if "%CLEAN%"=="1" (
    echo [%WARNING_COLOR%m%] Nettoyage du frontend...
    if exist "node_modules" rmdir /s /q "node_modules"
    if exist "dist" rmdir /s /q "dist"
)

REM Installation des dépendances
echo [%INFO_COLOR%m%] Installation des dépendances...
call npm install
if errorlevel 1 (
    echo [%ERROR_COLOR%m%] Erreur lors de l'installation des dépendances
    popd
    set "FRONTEND_SUCCESS=1"
    goto :eof
)

REM Compilation
echo [%INFO_COLOR%m%] Compilation en cours...
call npm run build
if errorlevel 1 (
    echo [%ERROR_COLOR%m%] Erreur lors de la compilation du frontend
    popd
    set "FRONTEND_SUCCESS=1"
) else (
    echo [%SUCCESS_COLOR%m%] Frontend compilé avec succès!
    set "FRONTEND_SUCCESS=0"
)

popd
goto :eof

:show_results
echo.
echo === RÉSULTATS ===

if "%BACKEND_ONLY%"=="1" (
    if "%BACKEND_SUCCESS%"=="0" (
        echo [%SUCCESS_COLOR%m%] Backend compilé avec succès!
        echo [%INFO_COLOR%m%] Fichier JAR: build/libs/tracker-server.jar
        set "EXIT_CODE=0"
    ) else (
        echo [%ERROR_COLOR%m%] Échec de la compilation du backend
        set "EXIT_CODE=1"
    )
) else if "%FRONTEND_ONLY%"=="1" (
    if "%FRONTEND_SUCCESS%"=="0" (
        echo [%SUCCESS_COLOR%m%] Frontend compilé avec succès!
        echo [%INFO_COLOR%m%] Dossier build: traccar-web/build
        set "EXIT_CODE=0"
    ) else (
        echo [%ERROR_COLOR%m%] Échec de la compilation du frontend
        set "EXIT_CODE=1"
    )
) else (
    if "%BACKEND_SUCCESS%"=="0" if "%FRONTEND_SUCCESS%"=="0" (
        echo [%SUCCESS_COLOR%m%] Compilation complète réussie!
        echo [%INFO_COLOR%m%] Backend: build/libs/tracker-server.jar
        echo [%INFO_COLOR%m%] Frontend: traccar-web/build
        echo.
        echo [%SUCCESS_COLOR%m%] Prêt pour le déploiement!
        set "EXIT_CODE=0"
    ) else (
        echo [%ERROR_COLOR%m%] Échec de la compilation
        if not "%BACKEND_SUCCESS%"=="0" echo    ❌ Backend échoué
        if not "%FRONTEND_SUCCESS%"=="0" echo    ❌ Frontend échoué
        set "EXIT_CODE=1"
    )
)

echo.
goto :eof
