#!/usr/bin/env python3
"""Configura el identificador Android y los IDs reales de AdMob antes de publicar."""
import json,os,re,sys,pathlib,xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parent.parent
pkg=os.environ.get('IGM_APP_ID','').strip()
app=os.environ.get('ADMOB_APP_ID','').strip()
banner=os.environ.get('ADMOB_BANNER_ID','').strip()
if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*){1,}',pkg) or pkg.startswith('com.ejemplo.'):
 sys.exit('ERROR: configura IGM_APP_ID con un identificador definitivo (ej. cl.lnkn.igm).')
if not re.fullmatch(r'ca-app-pub-\d{16}~\d{10}',app) or app=='ca-app-pub-3940256099942544~3347511713':
 sys.exit('ERROR: ADMOB_APP_ID debe ser el ID de aplicación real, no de prueba.')
if not re.fullmatch(r'ca-app-pub-\d{16}/\d{10}',banner) or banner=='ca-app-pub-3940256099942544/6300978111':
 sys.exit('ERROR: ADMOB_BANNER_ID debe ser el ID de unidad real, no de prueba.')
# El ID anterior podría ser un ID ajeno al propietario; exigir cuenta explícita es intencional.
ns='http://www.w3.org/ns/widgets';ET.register_namespace('',ns);ET.register_namespace('cdv','http://cordova.apache.org/ns/1.0')
path=root/'config.xml';tree=ET.parse(path);widget=tree.getroot()
widget.set('id',pkg);widget.set('android-packageName',pkg)
version=os.environ.get('IGM_VERSION','1.1.0')
if not re.fullmatch(r'\d+\.\d+\.\d+',version):sys.exit('ERROR: IGM_VERSION debe ser X.Y.Z')
widget.set('version',version)
version_code=os.environ.get('IGM_VERSION_CODE','2')
if not version_code.isdigit() or int(version_code)<1:sys.exit('ERROR: IGM_VERSION_CODE debe ser entero positivo')
widget.set('{http://schemas.android.com/apk/res/android}versionCode',version_code)
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
for elem in widget.iter():
 if elem.tag.endswith('plugin') and elem.get('name')=='cordova-plugin-igm-admob':
  for v in elem:
   if v.get('name')=='ADMOB_APP_ID':v.set('value',app)
tree.write(path,encoding='utf-8',xml_declaration=True)
pkgfile=root/'package.json';data=json.loads(pkgfile.read_text())
data['version']=version
data['cordova']['plugins']['cordova-plugin-igm-admob']['ADMOB_APP_ID']=app
pkgfile.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
js=root/'www/js/admob-config.js'
js.write_text('/* IDs de Google AdMob configurados para producción. */\nwindow.IGM_ADMOB_CONFIG = Object.freeze({bannerEnabled: true, bannerAdUnitId: '+json.dumps(banner)+'});\n')
print('Configuración aplicada:',pkg,'versión',version,'código',version_code)
