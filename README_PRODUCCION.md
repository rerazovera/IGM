# IGM — Publicación APK / AAB en Linux

Este ZIP es el **proyecto fuente** de IGM, conservando sus plugins SFTP y AdMob. No incluye APK/AAB compilados ni claves privadas. Se eliminan `node_modules`, `platforms`, `plugins` y archivos generados; se recrean al compilar.

## 1. Requisitos

Node.js y npm compatibles con Cordova/cordova-android 15.1; JDK y Android SDK compatibles con esa versión; `cordova` global instalado, Android SDK Platform/Build Tools, `zipalign`, `apksigner`, `keytool`, `jarsigner` y Python 3. Ajusta `JAVA_HOME`, `ANDROID_HOME` y el `PATH` para acceder a SDK `platform-tools` y `build-tools`.

```bash
npm install -g cordova
cordova --version
java -version
```

## 2. Clave de publicación

**El ZIP de origen contenía `igm-release.keystore`. Por seguridad se excluyó de este ZIP.** Si la aplicación ya está firmada y distribuida, utiliza **esa misma clave** para mantener la continuidad de las actualizaciones. Extráela del ZIP original y guárdala fuera del proyecto con permisos restrictivos, sin subirla a repositorios. Si la app es nueva, genera una clave ejecutando:

```bash
bash scripts/crear-clave.sh
```

Guarda un respaldo seguro del archivo y de las contraseñas.

## 3. Configuración

```bash
cp produccion.env.ejemplo produccion.env
nano produccion.env
```

Configura `IGM_APP_ID` (ID definitivo del paquete), `IGM_VERSION`, `IGM_VERSION_CODE` (incrementar en cada publicación), `ADMOB_APP_ID` y `ADMOB_BANNER_ID` **propios y reales**, la ruta absoluta de `IGM_KEYSTORE` y `IGM_KEY_ALIAS`. No se puede publicar con IDs de muestra. **Si ya publicaste la aplicación, conserva el mismo ID de paquete y clave de firma.**

No se han sustituido automáticamente los IDs de prueba por valores desconocidos. El script de preparación los configura antes de instalar la plataforma, para que el plugin nativo reciba el ID correcto. El archivo `produccion.env` está ignorado por Git.

## 4. Compilar y firmar APK + AAB

```bash
chmod +x scripts/*.sh
./scripts/compilar-produccion.sh
```

El comando configura AdMob, instala dependencias sin ejecutar prematuramente `cordova prepare`, recrea y registra Android 15.1.0, prepara plugins y genera APK y AAB `release`, firma ambos con tu clave y verifica sus firmas. Requiere un entorno Linux con Android SDK disponible y conexión para descargar dependencias si faltan.

Resultados:
- `dist/IGM-Produccion.apk` — instalación directa.
- `dist/IGM-Produccion.aab` — publicación en Google Play.

Si se solicita la contraseña durante la firma, introdúcela en el terminal; no la guardes en `produccion.env` ni en comandos de consola.

## 5. Revisión antes de subir a Play Store

- Confirma permisos, funcionamiento SFTP en segundo plano, carga de banners y compatibilidad con Android real.
- Revisa requisitos vigentes de target SDK / Google Play, ficha de seguridad de datos, política de privacidad y consentimiento publicitario (UMP/CMP cuando aplique). **Esta plantilla no implementa un flujo nuevo de consentimiento de AdMob**.
- En Google Play, usa AAB y configura Play App Signing si corresponde.
- Este ZIP **no contiene binarios compilados**: no se han podido probar builds Android ni publicación desde este entorno.
- El plugin AdMob existente se conserva sin cambiar su lógica de UI. Se recomienda revisar consentimiento, políticas y evitar clics propios antes de producción.

## Solución al error «No platforms added to this project»

En la versión anterior, `npm install` ejecutaba el ciclo de vida `prepare` de npm
(`cordova prepare`) antes de instalar la plataforma Android. Este ZIP **elimina**
ese script automático y hace `cordova platform add android@15.1.0 --save`
antes de `cordova prepare android`.

**Si actualizas una carpeta anterior:** extrae este ZIP en una carpeta nueva,
no conserves el antiguo `package.json` ni los directorios `node_modules`,
`plugins` o `platforms`. Conserva tu `produccion.env` y la clave privada en
una ruta externa. Luego ejecuta `./scripts/compilar-produccion.sh`.

No ejecutes `npm run prepare` antes de instalar la plataforma. Si solo deseas
preparar un proyecto que ya tiene Android instalado, usa
`npm run prepare:android`.

Para comprobar la plataforma instalada: `cordova platform ls`.
