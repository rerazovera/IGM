const $ = id => document.getElementById(id);
const log = msg => { const el = $("transfer-log"); if (el) { el.textContent = new Date().toLocaleTimeString() + " · " + msg + "\n" + el.textContent; } };
const mostrarEstado = (t, m, ok = true) => { $("status").classList.remove("hidden"); $("status-title").textContent = t; $("status-message").textContent = m; $("status-icon").textContent = ok ? "✓" : "!"; };
const ocultarEstado = () => $("status").classList.add("hidden");
function directoryPlugin() { if (window.DirectoryPicker && typeof window.DirectoryPicker.select === "function") return window.DirectoryPicker; if (window.cordova && cordova.plugins && cordova.plugins.directoryPicker) return cordova.plugins.directoryPicker; throw new Error("Plugin DirectoryPicker no instalado. Ejecuta una instalación limpia de Cordova."); }
function sftpPlugin() { if (window.cordova && cordova.plugins && cordova.plugins.sftpBackground) return cordova.plugins.sftpBackground; throw new Error("Plugin SFTP Background no instalado. Ejecuta una instalación limpia de Cordova."); }
document.addEventListener("deviceready", init);
function init() {
    $("btnOrigen").addEventListener("click", () => seleccionarDirectorio("origen"));
    $("btnDestino").addEventListener("click", () => seleccionarDirectorio("destino"));
    $("btnGrabar").addEventListener("click", grabarRutas);
    $("btnBackground").addEventListener("click", alternarBackground);
    if ($("btnLogs")) $("btnLogs").addEventListener("click", cargarLogs);
    cargarDirectorios(); cargarSftp(); actualizarConexion(); actualizarEstadoBackground(); cargarLogs();
    setInterval(actualizarConexion, 30000);
    setInterval(actualizarEstadoBackground, 5000);
    log("Aplicación inicializada.");
}
async function seleccionarDirectorio(tipo) {
    try {
        mostrarEstado("Abriendo explorador...", "Selecciona una carpeta", true);
        const r = await directoryPlugin().select();
        if (!r || r.cancelled) return ocultarEstado();
        if (!r.uri) throw new Error("Android no devolvió el URI de la carpeta.");
        localStorage.setItem(`directory_${tipo}`, r.uri); localStorage.setItem(`directory_${tipo}_name`, r.name || "Directorio seleccionado");
        $(`${tipo}-text`).textContent = r.name || "Directorio seleccionado"; mostrarEstado("Directorio seleccionado", r.uri, true); log(`${tipo}: ${r.name || r.uri}`);
    } catch (e) { mostrarEstado("No fue posible seleccionar", e.message || String(e), false); log("Error DirectoryPicker: " + (e.stack || e.message || e)); }
}
function cargarDirectorios() { ["origen", "destino"].forEach(t => { const n = localStorage.getItem(`directory_${t}_name`); if (n) $(`${t}-text`).textContent = n; }); }
function cargarSftp() { ["Host", "Port", "User", "Password"].forEach(k => { const v = localStorage.getItem("sftp_" + k.toLowerCase()); if (v) $("sftp" + k).value = v; }); }
function getConfig() { return { host: localStorage.getItem("sftp_host"), port: Number(localStorage.getItem("sftp_port") || 22), user: localStorage.getItem("sftp_user"), password: localStorage.getItem("sftp_password"), directory_origen: localStorage.getItem("directory_origen"), directory_destino: localStorage.getItem("directory_destino"), remoteImages: "/home/rerazo/IGM/imagenes", remoteFiles: "/home/rerazo/IGM/archivos" }; }
//function validarConfig() { const c = getConfig(); if (!c.directory_origen || !c.directory_destino || !c.host || !c.user || !c.password) throw new Error("Completa ambos directorios y la configuración SFTP."); return c; }
function grabarRutas() {
    try {
        //const c = validarConfig(); 
        localStorage.setItem("sftp_host", $("sftpHost").value);
        localStorage.setItem("sftp_port", $("sftpPort").value);
        localStorage.setItem("sftp_user", $("sftpUser").value);
        localStorage.setItem("sftp_password", $("sftpPassword").value);
        localStorage.setItem("directories_saved", "true");
        mostrarEstado("Configuración guardada", "Ahora puedes iniciar el proceso en background.", true);
        log("Configuración guardada.");
    } catch (e) {
        mostrarEstado("Faltan datos", e.message, false);
    }
}
function iniciarServicioSftp() {
    try {
        const c = validarConfig();
        if (!c) { log("Configuración inválida."); return; }
        const p = sftpPlugin();
        const config = {
            host: localStorage.getItem("sftp_host"),
            port: Number(localStorage.getItem("sftp_port") || 22),
            user: localStorage.getItem("sftp_user"),
            password: localStorage.getItem("sftp_password"),
            directory_origen: localStorage.getItem("directory_origen"),
            directory_destino: localStorage.getItem("directory_destino"),
            remoteImages: "/home/rerazo/IGM/imagenes",
            remoteFiles: "/home/rerazo/IGM/archivos"
        };
        log("Iniciando proceso background...");
        p.start(config, () => { log("Proceso background iniciado."); actualizarEstadoBackground(); }, e => { log("Error iniciando SFTP: " + (e && e.message ? e.message : e)); mostrarEstado("Error iniciando SFTP", String(e && e.message ? e.message : e), false); });
    }
    catch (e) { mostrarEstado("No se puede iniciar", e.message || String(e), false); log("Error: " + (e.stack || e.message || e)); }
}
function detenerServicioSftp() { try { sftpPlugin().stop(() => { log("Proceso background detenido."); actualizarEstadoBackground(); }, e => log("Error deteniendo SFTP: " + e)); } catch (e) { log("Error deteniendo SFTP: " + e.message); } }
function alternarBackground() { const b = $("btnBackground"); if (b.dataset.running === "true") detenerServicioSftp(); else iniciarServicioSftp(); }
function actualizarEstadoBackground() { try { sftpPlugin().status(r => { const running = !!r.running; const enabled = !!r.enabled; const b = $("btnBackground"); b.dataset.running = running ? "true" : "false"; b.textContent = running ? "⏹ Detener proceso background" : "▶ Iniciar proceso background"; b.classList.toggle("running", running); const st = $("background-text"); if (st) st.textContent = running ? "Proceso activo, incluso con pantalla apagada" : (enabled ? "Activado, iniciando..." : "Proceso detenido"); }, e => { }); } catch (e) { } }
function cargarLogs() { try { sftpPlugin().logs(rows => { const el = $("transfer-log"); if (!rows || !rows.length) { el.textContent = "Sin procesos registrados todavía."; return; } el.textContent = rows.map((x, i) => `${i + 1}. ${x.timestamp} · ${x.status}\n   ${x.detail || ""}${x.uploaded != null ? ` · enviados ${x.uploaded}, omitidos ${x.skipped || 0}` : ""}`).join("\n"); }, e => log("Error leyendo logs: " + e)); } catch (e) { } }
function actualizarConexion() { const online = navigator.onLine; $("connection-text").textContent = online ? "Internet disponible" : "Sin conexión a Internet"; $("connection-dot").classList.toggle("online", online); }
function validarConfig() {
    // 1. Mapeo de claves guardadas en localStorage
    // (Asegúrate de ajustar las claves entre comillas si usas otros nombres en tu app)

    const requeridos = {
        "Directorio Imagenes": localStorage.getItem("dir_imagenes") || localStorage.getItem("dirImagenes") || localStorage.getItem("imagenesPath") || localStorage.getItem("directory_origen"),
        "Directorio Archivos": localStorage.getItem("dir_archivos") || localStorage.getItem("dirArchivos") || localStorage.getItem("archivosPath") || localStorage.getItem("directory_destino"),
        "Host SFTP": localStorage.getItem("sftp_host") || localStorage.getItem("host"),
        "Puerto SFTP": localStorage.getItem("sftp_port") || localStorage.getItem("port"),
        "Usuario SFTP": localStorage.getItem("sftp_user") || localStorage.getItem("user"),
        "Contraseña SFTP": localStorage.getItem("sftp_pass") || localStorage.getItem("password") || localStorage.getItem("sftp_password")
    };

    //alert("Contenido actual de localStorage:\n\n" + JSON.stringify(requeridos, null, 2) + "\n\nRevise los valores antes de continuar." + JSON.stringify(localStorage, null, 2));
    //return false;

    const camposFaltantes = [];

    // 2. Comprobar uno a uno los campos
    for (const [nombreCampo, valor] of Object.entries(requeridos)) {
        if (!valor || valor.toString().trim() === "") {
            camposFaltantes.push(nombreCampo);
        }
    }

    // 3. Reportar el resultado de la validación
    if (camposFaltantes.length > 0) {
        const detalleError = `Campos pendientes: ${camposFaltantes.join(", ")}`;

        console.error("[DIAGNÓSTICO CONFIGURACIÓN]", detalleError);

        // Muestra una alerta emergente en la pantalla del dispositivo
        alert("Atención:\n" + detalleError);

        throw new Error(detalleError);
    }

    console.log(" Configuración validada correctamente.");
    return true;
}