# Publicidad Google AdMob — IGM (Cordova Android)

Se agregó un **banner AdMob nativo**, fijo en la parte inferior y separado del contenido para no cubrir los controles SFTP. La transferencia en segundo plano funciona sin anuncios.

## Estado actual: ANUNCIOS DE PRUEBA

La integración usa IDs de prueba oficiales de Google, por lo que no genera ingresos. No publiques la aplicación con estos IDs.

- App ID de prueba: `ca-app-pub-3940256099942544~3347511713`
- Banner ID de prueba: `ca-app-pub-3940256099942544/6300978111`

## Activar publicidad real

1. Registra la aplicación Android en https://admob.google.com/ y crea una unidad **Banner**.
2. Reemplaza el **App ID** de prueba por tu App ID real en **ambos**: `config.xml` (variable `ADMOB_APP_ID`) y `package.json` (`cordova.plugins.cordova-plugin-igm-admob.ADMOB_APP_ID`).
3. Reemplaza el **Banner Ad Unit ID** por tu ID real en `www/js/admob-config.js`.
4. Para desactivar anuncios, cambia `bannerEnabled` a `false` (se dejará el espacio inferior reservado; si quieres quitarlo, elimina la regla `.app{padding-bottom...}` de `www/css/style.css`).
5. En producción, configura consentimiento de usuarios cuando corresponda (Google UMP), mensajes de privacidad en AdMob, publicación de política de privacidad y los requisitos de Google Play. **Esta versión no implementa un flujo UMP de consentimiento**; agrégalo antes de servir anuncios reales en territorios donde sea exigible.

## Compilar

```sh
npm install
npx cordova prepare android
npx cordova build android
```

Se necesita Android SDK/Java/Gradle configurados y acceso a los repositorios Google Maven para descargar `com.google.android.gms:play-services-ads:24.5.0`. La versión del SDK debe ser compatible con `cordova-android` instalado.

**Importante**: `app-debug.apk` del ZIP original se excluyó de la entrega, pues es una compilación antigua sin publicidad. El ZIP contiene el código actualizado; compilar el APK de nuevo.

## Archivos modificados/agregados

- `local-plugins/cordova-plugin-igm-admob/` — plugin Android nativo que solicita y presenta el banner.
- `www/js/admob-config.js` — interruptor y unidad de banner.
- `www/js/app.js` — inicia AdMob al recibir `deviceready`.
- `www/index.html` — incluye la configuración de anuncios.
- `www/css/style.css` — espacio seguro para el banner.
- `config.xml`, `package.json`, `build-extras.gradle` — registro del plugin/SDK.

