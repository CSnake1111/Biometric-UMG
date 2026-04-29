@echo off
setlocal EnableDelayedExpansion
title BiometricUMG 2.0 — Instalador y Lanzador
color 0B
cls

echo.
echo  =====================================================================
echo   BiometricUMG 2.0 — Sistema Biometrico UMG
echo   Universidad Mariano Galvez - Sede La Florida
echo   [Instalador Automatico de Dependencias]
echo  =====================================================================
echo.

cd /d "%~dp0"

REM ═══════════════════════════════════════════════════════
REM  1. VERIFICAR JAVA
REM ═══════════════════════════════════════════════════════
echo  [1/5] Verificando Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo.
    echo  [ERROR] Java no esta instalado o no esta en el PATH.
    echo          Instala Java 17 o superior desde:
    echo          https://adoptium.net
    echo.
    pause
    exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%v
)
echo  [OK] Java detectado: %JAVA_VER%

REM ═══════════════════════════════════════════════════════
REM  2. VERIFICAR / INSTALAR MAVEN
REM ═══════════════════════════════════════════════════════
echo.
echo  [2/5] Verificando Maven...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo  [INFO] Maven no encontrado. Descargando Maven 3.9.6...
    echo.

    REM Verificar si existe PowerShell
    powershell -Command "exit" >nul 2>&1
    if errorlevel 1 (
        echo  [ERROR] PowerShell no disponible. Instala Maven manualmente:
        echo          https://maven.apache.org/download.cgi
        echo          Luego agrega Maven al PATH y vuelve a ejecutar.
        pause
        exit /b 1
    )

    REM Descargar Maven con PowerShell
    set MVN_URL=https://downloads.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip
    set MVN_ZIP=%~dp0maven-3.9.6.zip
    set MVN_DIR=%~dp0maven

    echo  Descargando Maven, espera un momento...
    powershell -Command "Invoke-WebRequest -Uri '%MVN_URL%' -OutFile '%MVN_ZIP%' -UseBasicParsing" 2>nul
    if errorlevel 1 (
        echo  [ERROR] No se pudo descargar Maven. Verifica tu conexion a internet.
        pause
        exit /b 1
    )

    echo  Extrayendo Maven...
    powershell -Command "Expand-Archive -Path '%MVN_ZIP%' -DestinationPath '%MVN_DIR%' -Force" 2>nul
    del /f /q "%MVN_ZIP%" >nul 2>&1

    REM Agregar Maven al PATH para esta sesion
    set "PATH=%MVN_DIR%\apache-maven-3.9.6\bin;%PATH%"

    mvn -version >nul 2>&1
    if errorlevel 1 (
        echo  [ERROR] Maven descargado pero no funciona correctamente.
        pause
        exit /b 1
    )
    echo  [OK] Maven 3.9.6 instalado localmente en .\maven\
) else (
    for /f "tokens=3" %%v in ('mvn -version 2^>^&1 ^| findstr /i "Apache Maven"') do (
        set MVN_VER=%%v
    )
    echo  [OK] Maven detectado: %MVN_VER%
)

REM ═══════════════════════════════════════════════════════
REM  3. DESCARGAR DEPENDENCIAS Y COMPILAR
REM ═══════════════════════════════════════════════════════
echo.
echo  [3/5] Descargando dependencias y compilando...
echo         (Primera vez puede tardar varios minutos)
echo.

REM Verificar que pom.xml existe
if not exist "pom.xml" (
    echo  [ERROR] No se encontro pom.xml en esta carpeta.
    echo          Ejecuta este .bat desde la carpeta raiz del proyecto.
    pause
    exit /b 1
)

REM Compilar con Maven (descarga todo automaticamente)
mvn clean package -DskipTests -q
if errorlevel 1 (
    echo.
    echo  [ERROR] La compilacion fallo. Revisa los mensajes de arriba.
    echo.
    echo  Posibles causas:
    echo    - Sin conexion a internet
    echo    - Version de Java incompatible (requiere Java 17+)
    echo    - Repositorio de Maven bloqueado por firewall
    echo.
    pause
    exit /b 1
)
echo  [OK] Compilacion exitosa.

REM ═══════════════════════════════════════════════════════
REM  4. PREPARAR CARPETAS Y JAVAFX
REM ═══════════════════════════════════════════════════════
echo.
echo  [4/5] Preparando entorno...

REM Crear carpetas necesarias
if not exist "data\fotos"   mkdir "data\fotos"
if not exist "data\rostros" mkdir "data\rostros"
if not exist "data\pdf"     mkdir "data\pdf"
if not exist "lib\javafx"   mkdir "lib\javafx"

REM Copiar JARs de JavaFX desde el repositorio local de Maven al lib\javafx
set M2_HOME=%USERPROFILE%\.m2\repository\org\openjfx
set JFX_VER=21.0.2
set JFX_PLAT=win

REM Detectar arquitectura
set JFX_ARCH=
if "%PROCESSOR_ARCHITECTURE%"=="AMD64" set JFX_ARCH=-win
if "%PROCESSOR_ARCHITECTURE%"=="x86"   set JFX_ARCH=-win
if "%PROCESSOR_ARCHITECTURE%"=="ARM64" set JFX_ARCH=-win

echo  Copiando modulos JavaFX al directorio lib\javafx...
for %%M in (javafx-controls javafx-fxml javafx-base javafx-graphics javafx-swing javafx-media) do (
    if exist "%M2_HOME%\%%M\%JFX_VER%\%%M-%JFX_VER%%JFX_ARCH%.jar" (
        copy /y "%M2_HOME%\%%M\%JFX_VER%\%%M-%JFX_VER%%JFX_ARCH%.jar" "lib\javafx\" >nul
    )
)

REM Contar JARs copiados
set JFX_COUNT=0
for %%f in (lib\javafx\*.jar) do set /a JFX_COUNT+=1

if %JFX_COUNT% EQU 0 (
    echo  [AVISO] No se encontraron JARs de JavaFX en el repositorio local.
    echo          Si la app no inicia, descarga JavaFX SDK 21 manualmente:
    echo          https://gluonhq.com/products/javafx/
    echo          Y descomprime en la carpeta lib\javafx\
) else (
    echo  [OK] JavaFX listo ^(%JFX_COUNT% modulos^).
)

REM Detectar JAR principal
set JAR_FILE=
for %%f in (target\BiometricUMG*.jar) do (
    echo %%f | findstr /i "original" >nul
    if errorlevel 1 set JAR_FILE=%%f
)

REM Fallback: buscar en raiz
if "%JAR_FILE%"=="" (
    if exist "BiometricUMG.jar" set JAR_FILE=BiometricUMG.jar
)

if "%JAR_FILE%"=="" (
    echo  [ERROR] No se encontro el JAR compilado en target\
    pause
    exit /b 1
)
echo  [OK] JAR listo: %JAR_FILE%

REM ═══════════════════════════════════════════════════════
REM  5. EJECUTAR APLICACION
REM ═══════════════════════════════════════════════════════
echo.
echo  [5/5] Iniciando BiometricUMG...
echo.
echo  ─────────────────────────────────────────────────────
echo   NOTA sobre OpenCV / Reconocimiento Facial:
echo    Si el reconocimiento facial no funciona:
echo    1. Descarga OpenCV 4.x: https://opencv.org/releases/
echo    2. Copia opencv_java4xx.dll a la carpeta lib\
echo    3. Copia haarcascade_frontalface_default.xml a data\
echo  ─────────────────────────────────────────────────────
echo.

REM Construir modulepath con los JARs disponibles
set MOD_PATH=lib\javafx

REM Ejecutar — intentar con modulos si existen JARs, si no ejecutar simple
if %JFX_COUNT% GTR 0 (
    java ^
      -Djava.library.path=lib ^
      --module-path "%MOD_PATH%" ^
      --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.media ^
      -jar "%JAR_FILE%"
) else (
    java ^
      -Djava.library.path=lib ^
      -jar "%JAR_FILE%"
)

if errorlevel 1 (
    echo.
    echo  [ERROR] La aplicacion termino con errores.
    echo.
    echo  Soluciones comunes:
    echo    - Java 17+ requerido: https://adoptium.net
    echo    - JavaFX no encontrado: descarga el SDK de https://gluonhq.com/products/javafx/
    echo      y descomprime en lib\javafx\
    echo    - SQL Server no alcanzable: verifica la cadena de conexion en config
    echo.
    pause
)

endlocal
