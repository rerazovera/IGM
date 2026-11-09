#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f ./produccion.env ]] && { set -a; source ./produccion.env; set +a; }
for cmd in node npm cordova java keytool jarsigner zipalign apksigner python3; do
 command -v "$cmd" >/dev/null || { echo "Falta $cmd. Revisa ANDROID_HOME, build-tools y PATH." >&2; exit 1; }
done
: "${IGM_KEYSTORE:?Indica ruta absoluta a tu keystore en produccion.env}"
: "${IGM_KEY_ALIAS:?Indica alias del certificado en produccion.env}"
[[ -f "$IGM_KEYSTORE" ]] || { echo "No existe IGM_KEYSTORE=$IGM_KEYSTORE" >&2; exit 1; }
python3 scripts/configurar-produccion.py
# Importante: npm install ejecutaba automáticamente `npm run prepare` y fallaba
# porque Android todavía no estaba instalado. El proyecto ya no define ese
# script de ciclo de vida; la preparación se realiza explícitamente DESPUÉS.
# Usar `cordova platform rm` elimina también Android del package.json: al
# regenerarlo, cordova platform add --save vuelve a registrar la plataforma.
if [[ -d platforms/android ]]; then
  echo "Eliminando plataforma Android anterior..."
  cordova platform rm android
fi
# Eliminar restos de plugins instalados para obtener una instalación limpia.
if [[ -d plugins ]]; then rm -rf plugins; fi

echo "Instalando dependencias npm (sin `cordova prepare` automático)..."
npm install --no-audit --no-fund

echo "Agregando plataforma Android antes de preparar el proyecto..."
cordova platform add android@15.1.0 --save
[[ -d platforms/android ]] || { echo "ERROR: no se instaló platforms/android" >&2; exit 1; }

echo "Preparando plugins y recursos Android..."
cordova prepare android
cordova platform ls
mkdir -p dist
cordova build android --release -- --packageType=apk
APK="platforms/android/app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$APK" ]] || { echo "No se encontró el APK sin firmar: $APK" >&2; exit 1; }
zipalign -f -p 4 "$APK" dist/IGM-aligned.apk
# Pide contraseña de manera interactiva cuando no está en el entorno.
apksigner sign --ks "$IGM_KEYSTORE" --ks-key-alias "$IGM_KEY_ALIAS" --out dist/IGM-Produccion.apk dist/IGM-aligned.apk
apksigner verify --verbose dist/IGM-Produccion.apk
rm -f dist/IGM-aligned.apk
cordova build android --release -- --packageType=bundle
AAB="platforms/android/app/build/outputs/bundle/release/app-release.aab"
[[ -f "$AAB" ]] || { echo "No se encontró el AAB: $AAB" >&2; exit 1; }
cp "$AAB" dist/IGM-Produccion.aab
jarsigner -keystore "$IGM_KEYSTORE" -signedjar dist/IGM-Produccion-firmado.aab dist/IGM-Produccion.aab "$IGM_KEY_ALIAS"
mv -f dist/IGM-Produccion-firmado.aab dist/IGM-Produccion.aab
jarsigner -verify -verbose -certs dist/IGM-Produccion.aab >/dev/null
echo "OK: dist/IGM-Produccion.apk y dist/IGM-Produccion.aab"
