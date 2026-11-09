#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KEYSTORE="${IGM_KEYSTORE:-$HOME/.config/igm/igm-release.keystore}"
ALIAS="${IGM_KEY_ALIAS:-igm}"
if [[ -e "$KEYSTORE" ]]; then echo "La clave ya existe: $KEYSTORE; no se sobrescribe"; exit 1; fi
mkdir -p "$(dirname "$KEYSTORE")"
chmod 700 "$(dirname "$KEYSTORE")"
keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000
chmod 600 "$KEYSTORE"
echo "Guarda una copia de seguridad de $KEYSTORE y sus contraseñas fuera del proyecto."
