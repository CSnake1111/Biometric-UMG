# Solución al problema de OpenCV

## El error más común
```
❌ OpenCV no disponible: no opencv_java4100 in java.library.path
```

## Solución paso a paso

### 1. Descargar OpenCV
Ve a https://opencv.org/releases/ y descarga **OpenCV 4.x** para Windows.

### 2. Copiar el archivo DLL
Dentro del ZIP/installer de OpenCV busca el archivo:
```
opencv\build\java\x64\opencv_java4xx.dll
```
(donde xx es la versión, ej: `opencv_java4100.dll`)

**Cópialo a la carpeta `lib\` de tu proyecto.**

### 3. Copiar el Cascade XML
Busca en OpenCV:
```
opencv\build\etc\haarcascades\haarcascade_frontalface_default.xml
```
**Cópialo a la carpeta `data\` de tu proyecto.**

### 4. Ejecutar con el .bat
Siempre usa `ejecutar.bat` para correr el programa.  
El `.bat` ya incluye `-Djava.library.path=lib` que es lo que le dice a Java dónde buscar la DLL.

### 5. En NetBeans/IntelliJ (para desarrollo)
Agrega a las VM Options de tu Run Configuration:
```
-Djava.library.path=lib
```

---

## Si el reconocimiento sigue fallando

El sistema está diseñado para funcionar **sin OpenCV** (modo degradado):
- El registro de personas funciona normalmente
- El login por credenciales funciona
- Los reportes y el árbol funcionan
- Solo se deshabilita el reconocimiento facial automático

La aplicación detecta si OpenCV está disponible y muestra un mensaje claro en lugar de crashear.
