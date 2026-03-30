/**
 * Advanced Severe Weather Detector
 */

definition(
    name: "Advanced Severe Weather Detector",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "BMS-Grade multi-dimensional predictive hazard detection engine featuring DEFCON sub-minute watchdog polling, MSLP calibration, glitch filtering, and granular hardware routing.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "configPage")
    page(name: "dnaConfigPage")
}

def mainPage() {
    return dynamicPage(name: "mainPage", title: "<b>Advanced Severe Weather Detector</b>", install: true, uninstall: true) {
     
        section("<b>Live Threat Assessment Matrix</b>") {
            if (app.id) {
                input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            }
            
            def isWatchdog = state.watchdogActive ?: false
            def wdHtml = isWatchdog ? "<span style='color:#d9534f; font-weight:bold;'>[🚨 DEFCON WATCHDOG ACTIVE: Sub-Minute Polling Engaged]</span>" : "<span style='color:#5cb85c;'>[Standard Polling Routine]</span>"
            
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Runs predictive thermodynamic models for six severe weather profiles. Visualizes Threat Intensity, Probability, Confidence, and the raw algorithmic math driving the automated alarms. ${wdHtml}</div>"
            
            if (sensorTemp && sensorHum && sensorPress) {
                def matrix = state.threatMatrix ?: [:]
                def hazards = [
                    [id: "Tornado", icon: "🌪️"],
                    [id: "Thunderstorm", icon: "⛈️"],
                    [id: "Flood", icon: "🌊"],
                    [id: "Freeze", icon: "❄️"],
                    [id: "SevereHeat", icon: "🔥"],
                    [id: "Tropical", icon: "🌀"]
                ]
                
                hazards.each { hazMap ->
                    def haz = hazMap.id
                    def icon = hazMap.icon
                    def data = matrix[haz] ?: [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Gathering sensor data...", mathEx: "Awaiting math cycle..."]
                    
                    def stateColor = data.state == "ALARM" ? "#d9534f" : (data.state == "WARNING" ? "#f0ad4e" : "#5cb85c")
                    def headerBg = data.state == "ALARM" ? "#fdf0f0" : (data.state == "WARNING" ? "#fcf6ec" : "#f4f8f4")
                    
                    def tColor = data.threat > 75 ? "#d9534f" : (data.threat > 40 ? "#f0ad4e" : "#5bc0de")
                    def pColor = data.prob > 75 ? "#d9534f" : (data.prob > 40 ? "#f0ad4e" : "#5bc0de")
                    def cColor = data.conf > 75 ? "#5cb85c" : (data.conf > 40 ? "#f0ad4e" : "#d9534f")

                    def sectionTitle = "${icon} ${haz == 'SevereHeat' ? 'Severe Heat' : haz} DNA &nbsp;|&nbsp; Threat: ${data.threat}% &nbsp;|&nbsp; Conf: ${data.conf}% &nbsp;|&nbsp; State: ${data.state}"
                    def isHidden = (data.state == "Clear")

                    section("<b>${sectionTitle}</b>", hideable: true, hidden: isHidden) {
                        def dashboardHtml = """
                        <div style="border: 1px solid #e0e0e0; margin-bottom: 5px; border-radius: 8px; background: #ffffff; overflow: hidden; font-family: 'Segoe UI', Tahoma, sans-serif; box-shadow: 0 2px 5px rgba(0,0,0,0.05);">
                            <div style="display: flex; flex-wrap: wrap; padding: 15px; gap: 15px;">
                                <div style="flex: 1; min-width: 200px;">
                                    <div style="display: inline-block; padding: 3px 10px; background: ${headerBg}; color: ${stateColor}; border: 1px solid ${stateColor}; font-weight: bold; border-radius: 12px; font-size: 11px; margin-bottom: 12px; letter-spacing: 0.5px; text-transform: uppercase;">
                                        STATUS: ${data.state}
                                    </div>
                                    <div style="margin-bottom: 10px;">
                                        <div style="font-size: 12px; display: flex; justify-content: space-between; margin-bottom: 4px; color: #444;"><b>Threat Intensity</b><b>${data.threat}%</b></div>
                                        <div style="width: 100%; height: 8px; background: #eaecf0; border-radius: 4px;"><div style="width: ${data.threat}%; height: 100%; background: ${tColor}; border-radius: 4px; transition: width 0.5s ease;"></div></div>
                                    </div>
                                    <div style="margin-bottom: 10px;">
                                        <div style="font-size: 12px; display: flex; justify-content: space-between; margin-bottom: 4px; color: #444;"><b>Probability Score</b><b>${data.prob}%</b></div>
                                        <div style="width: 100%; height: 8px; background: #eaecf0; border-radius: 4px;"><div style="width: ${data.prob}%; height: 100%; background: ${pColor}; border-radius: 4px; transition: width 0.5s ease;"></div></div>
                                    </div>
                                    <div>
                                        <div style="font-size: 12px; display: flex; justify-content: space-between; margin-bottom: 4px; color: #444;"><b>System Confidence</b><b>${data.conf}%</b></div>
                                        <div style="width: 100%; height: 8px; background: #eaecf0; border-radius: 4px;"><div style="width: ${data.conf}%; height: 100%; background: ${cColor}; border-radius: 4px; transition: width 0.5s ease;"></div></div>
                                    </div>
                                </div>
                                <div style="flex: 1.2; min-width: 200px; font-size: 13px; color: #444; background: #f8f9fa; border-radius: 6px; padding: 12px; border-left: 4px solid ${stateColor}; display: flex; flex-direction: column; justify-content: center;">
                                    <b style="color:#222; margin-bottom: 6px; font-size: 14px;">Diagnostic Report:</b>
                                    <span style="line-height: 1.4;">${data.howWhy}</span>
                                </div>
                                <div style="flex: 1.2; min-width: 200px; font-size: 11px; color: #333; background: #eef2f5; border-radius: 6px; padding: 12px; border-left: 4px solid #8e9eab; display: flex; flex-direction: column; justify-content: center; font-family: 'Courier New', Courier, monospace;">
                                    <b style="color:#222; margin-bottom: 6px; font-size: 12px; font-family: 'Segoe UI', sans-serif;">Algorithmic Engine:</b>
                                    <span style="line-height: 1.5;">${data.mathEx}</span>
                                </div>
                            </div>
                        </div>
                        """
                        paragraph dashboardHtml
                    }
                }
                
                section("<b>Core Physics Data Stream</b>", hideable: true, hidden: true) {
                    def pTrend = state.pressureTrendStr ?: "Stable"
                    def tTrend = state.tempTrendStr ?: "Stable"
                    def wTrend = state.windTrendStr ?: "Stable"
                    def sTrend = state.spreadTrendStr ?: "Stable"
                    
                    def rawP = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], 0.0)
                    def p = rawP + (settings.pressOffset ?: 0.0)
                    
                    def windDir = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], "N/A")
                    def strikes = state.lightningHistory?.size() ?: 0
                    
                    def physicsDisplay = "<div style='padding: 12px; background: #f0f2f5; border-radius: 6px; font-size: 13px; border: 1px solid #dcdfe3; color: #555; line-height: 1.6;'>"
                    physicsDisplay += "<b>Calibrated MSLP Baro:</b> ${String.format('%.2f', p)} inHg (${pTrend})<br>"
                    physicsDisplay += "<b>Temp Velocity:</b> ${tTrend} &nbsp;|&nbsp; <b>Squeeze Vel:</b> ${sTrend}<br>"
                    physicsDisplay += "<b>Wind:</b> ${wTrend} @ ${windDir}° &nbsp;|&nbsp; <b>Lightning (30m):</b> ${strikes} strikes</div>"
                    paragraph physicsDisplay
                }
                
            } else {
                paragraph "<i>Primary sensors missing. Click Configuration below to assign devices.</i>"
            }
        }

        section("<b>System Configuration</b>") {
            href(name: "configPageLink", page: "configPage", title: "▶ Base Hardware & BMS Engine Tuning", description: "Set up environmental sensors, calibration offsets, DEFCON Watchdog polling, and hardware outputs.")
            href(name: "dnaConfigPageLink", page: "dnaConfigPage", title: "▶ Threat DNA Granular Mapping", description: "Configure Probability thresholds, specific Mode restrictions, and custom Audio/Siren routing per threat.")
        }
        
        section("<b>Child Device Integration</b>") {
            paragraph "<i>Create a single virtual device that exposes the entire Threat Matrix to your dashboards.</i>"
            if (app.id) {
                input "createDeviceBtn", "button", title: "➕ Create Severe Weather Information Device"
            } else {
                paragraph "<b>⚠ Please click 'Done' to fully install the app before creating the child device.</b>"
            }
        }
        
        if (app.id) {
            section("<b>Global Actions & Debugging</b>", hideable: true, hidden: true) {
                input "forceEvalBtn", "button", title: "⚙️ Force Matrix Evaluation"
                input "clearStateBtn", "button", title: "⚠ Reset Internal Matrix & Hardware"
                input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
                input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false
            }
        }

        section() {
            paragraph "<hr><div style='font-size:11px; color:#777; text-align:center; padding: 10px; background: #f9f9f9; border-radius: 6px; border: 1px solid #eee;'><b>DISCLAIMER:</b> This application is a localized predictive thermodynamic engine designed for home automation. It <b>DOES NOT</b> replace official NOAA broadcasts, National Weather Service alerts, or Emergency Alert System (EAS) activations. It should never be relied upon as a primary life-saving tool or safety device. The author and distributor assume no liability for missed meteorological events, false alarms, property damage, or injury.</div>"
        }
    }
}

def configPage() {
    return dynamicPage(name: "configPage", title: "<b>Base Hardware & BMS Engine Tuning</b>", install: false, uninstall: false) {
        
        section("<b>Primary Thermodynamic Sensors (Required)</b>") {
            input "sensorTemp", "capability.sensor", title: "Outdoor Temperature Sensor", required: true
            input "sensorHum", "capability.sensor", title: "Outdoor Humidity Sensor", required: true
            input "sensorPress", "capability.sensor", title: "Barometric Pressure Sensor", required: true
        }

        section("<b>Kinetic & Precipitation Sensors (Required for Full Matrix)</b>") {
            input "sensorWind", "capability.sensor", title: "Wind Speed / Gust Sensor", required: false
            input "sensorWindDir", "capability.sensor", title: "Wind Direction Sensor", required: false
            input "sensorLightning", "capability.sensor", title: "Lightning Detector", required: false
            input "sensorRain", "capability.sensor", title: "Rain Rate Sensor (in/hr or mm/hr)", required: false
            input "sensorRainDaily", "capability.sensor", title: "Daily Rain Accumulation Sensor", required: false
        }
        
        section("<b>BMS Engine Integrity & Calibration</b>") {
            input "pressOffset", "decimal", title: "Barometric MSLP Offset (inHg)", defaultValue: 0.0, description: "Crucial for Tropical DNA accuracy. If your station pressure reads 29.00 but your local weather station reports 29.90 (due to elevation), enter 0.90 here."
            input "enableHardwareFilter", "bool", title: "Enable Hardware Anomaly Rejection", defaultValue: true, description: "Filters out impossible data spikes (e.g. 10 degree jump in 1 minute) caused by sensor glitches or dead batteries."
            input "enableThermalSmoothing", "bool", title: "Thermal Smoothing (Sun-Spike Protection)", defaultValue: true
            input "staleDataTimeout", "number", title: "Stale Data Timeout (Minutes)", defaultValue: 30
        }
        
        section("<b>DEFCON Watchdog Polling</b>") {
            paragraph "<i>When a threat probability begins climbing, the engine can override standard polling schedules to acquire data rapidly.</i>"
            input "enablePolling", "bool", title: "Enable Standard Active Device Polling", defaultValue: true, submitOnChange: true
            if (enablePolling) {
                input "pollInterval", "number", title: "Standard Polling Interval (Minutes)", required: true, defaultValue: 5
            }
            input "enableDefcon", "bool", title: "Enable DEFCON Watchdog Overdrive", defaultValue: true, submitOnChange: true
            if (enableDefcon) {
                input "defconThresh", "number", title: "DEFCON Activation Threshold (Probability %)", required: true, defaultValue: 25, description: "Watchdog engages when any DNA hits this probability."
                input "defconSecs", "number", title: "DEFCON Sub-Minute Polling Rate (Seconds)", required: true, defaultValue: 30, range: "15..59"
            }
        }
        
        section("<b>Output Hardware Master Devices</b>") {
            paragraph "<i>Select the physical devices you wish to use. You will assign exactly HOW and WHEN these are used inside the DNA Mapping page.</i>"
            input "audioDevices", "capability.speechSynthesis", title: "TTS Audio Devices (e.g., Sonos/Echo)", multiple: true, required: false
            input "soundDevices", "capability.audioNotification", title: "Sound File/MP3 Players", multiple: true, required: false
            input "sirenDevices", "capability.alarm", title: "Sirens & Strobes (e.g., Zooz Siren)", multiple: true, required: false
            input "notifyDevices", "capability.notification", title: "Push Notification Devices", multiple: true, required: false
            input "audioVolume", "number", title: "Master Announcement Volume Level (%)", defaultValue: 65, range: "1..100"
        }
    }
}

def dnaConfigPage() {
    return dynamicPage(name: "dnaConfigPage", title: "<b>Threat DNA Granular Mapping</b>", install: false, uninstall: false) {
        
        section() {
            paragraph "<i>Fine-tune predictive thresholds, map physical automation switches, restrict alerts by Hub Mode, and route warnings exactly where you want them.</i>"
        }
        
        def dnas = [
            [id: "Tornado", icon: "🌪️", wTTS: "Warning. Elevated probability of tornado conditions.", aTTS: "Critical Alert. Tornado conditions detected locally. Take immediate action."],
            [id: "Thunderstorm", icon: "⛈️", wTTS: "Warning. Elevated probability of severe thunderstorms.", aTTS: "Critical Alert. Severe thunderstorm conditions actively detected."],
            [id: "Flood", icon: "🌊", wTTS: "Warning. Elevated probability of flash flood conditions.", aTTS: "Critical Alert. Flash flood conditions detected locally."],
            [id: "Freeze", icon: "❄️", wTTS: "Warning. Rapid trajectory towards hard freeze detected.", aTTS: "Alert. Hard freeze actively occurring."],
            [id: "SevereHeat", icon: "🔥", wTTS: "Warning. High heat index detected. Caution advised.", aTTS: "Critical Alert. Severe heat index conditions detected locally."],
            [id: "Tropical", icon: "🌀", wTTS: "Warning. Tropical cyclone signatures detected.", aTTS: "Critical Alert. Deep tropical cyclone conditions actively impacting location."]
        ]
        
        dnas.each { dna ->
            def id = dna.id
            def lName = id.toLowerCase()
            section("<b>${dna.icon} ${id == 'SevereHeat' ? 'Severe Heat' : id} DNA Configuration</b>", hideable: true, hidden: true) {
                input "enable${id}", "bool", title: "Enable ${id} Engine", defaultValue: true, submitOnChange: true
                if (settings["enable${id}"] != false) {
                    input "${lName}WarnThresh", "number", title: "WARNING Probability Threshold (%)", defaultValue: 50, required: true
                    input "${lName}AlarmThresh", "number", title: "ALARM Probability Threshold (%)", defaultValue: 80, required: true
                    input "switch${id}Warn", "capability.switch", title: "Map WARNING Virtual Switch", required: false
                    input "switch${id}Alarm", "capability.switch", title: "Map ALARM Virtual Switch", required: false
                    
                    input "${lName}Modes", "mode", title: "<b>Restrict Alerts to these Modes</b> (Leave blank for all)", multiple: true, required: false
                    
                    paragraph "<b>▶ Warning Output Routing</b>"
                    input "${lName}WarnNotify", "bool", title: "Send Push Notification", defaultValue: true
                    input "${lName}WarnTTS", "bool", title: "Broadcast TTS", defaultValue: true, submitOnChange: true
                    if (settings["${lName}WarnTTS"]) input "tts${id}Warn", "text", title: "TTS String", required: false, defaultValue: dna.wTTS
                    input "${lName}WarnSiren", "bool", title: "Trigger Siren Device", defaultValue: false
                    input "${lName}WarnSound", "bool", title: "Play Sound File", defaultValue: false, submitOnChange: true
                    if (settings["${lName}WarnSound"]) input "url${id}Warn", "text", title: "Sound File URL", required: true

                    paragraph "<b>▶ Alarm Output Routing</b>"
                    input "${lName}AlarmNotify", "bool", title: "Send Push Notification", defaultValue: true
                    input "${lName}AlarmTTS", "bool", title: "Broadcast TTS", defaultValue: true, submitOnChange: true
                    if (settings["${lName}AlarmTTS"]) input "tts${id}Alarm", "text", title: "TTS String", required: false, defaultValue: dna.aTTS
                    input "${lName}AlarmSiren", "bool", title: "Trigger Siren Device", defaultValue: (id == "Tornado" || id == "Thunderstorm")
                    input "${lName}AlarmSound", "bool", title: "Play Sound File", defaultValue: false, submitOnChange: true
                    if (settings["${lName}AlarmSound"]) input "url${id}Alarm", "text", title: "Sound File URL", required: true
                }
            }
        }
        
        section("<b>System-Wide Debounce</b>") {
            input "debounceMins", "number", title: "Safety Hardware Hold Time (Minutes)", required: true, defaultValue: 15, description: "Prevents switches and alarms from flipping rapidly. Keeps hardware locked in a safe state until the threat has fully passed."
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    state.threatMatrix = [
        "Tornado": [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0],
        "Thunderstorm": [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0],
        "Flood": [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0],
        "Freeze": [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0],
        "SevereHeat": [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0],
        "Tropical": [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0]
    ]
    
    state.watchdogActive = false
    if (!state.lastHeartbeat) state.lastHeartbeat = now()
    if (!state.smoothedTemp) state.smoothedTemp = null
    
    if (!state.pressureHistory) state.pressureHistory = []
    if (!state.tempHistory) state.tempHistory = []
    if (!state.spreadHistory) state.spreadHistory = []
    if (!state.windHistory) state.windHistory = []
    if (!state.windDirHistory) state.windDirHistory = []
    if (!state.lightningHistory) state.lightningHistory = []
   
    if (!state.currentDayRain) state.currentDayRain = 0.0
    if (!state.currentDateStr) state.currentDateStr = new Date().format("yyyy-MM-dd", location.timeZone)
    
    subscribeMulti(sensorTemp, ["temperature", "tempf"], "tempHandler")
    subscribeMulti(sensorHum, ["humidity"], "stdHandler")
    subscribeMulti(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], "pressureHandler")
    subscribeMulti(sensorWind, ["windSpeed", "windspeedmph", "wind"], "windHandler")
    subscribeMulti(sensorWindDir, ["windDirection", "winddir", "windDir"], "windDirHandler")
    subscribeMulti(sensorLightning, ["lightningDistance", "distance"], "lightningHandler")
    subscribeMulti(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], "stdHandler")
    subscribeMulti(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], "stdHandler")
    
    unschedule()
    if (enablePolling && pollInterval) {
        def safeInterval = Math.max(1, Math.min(59, pollInterval.toInteger()))
        schedule("0 */${safeInterval} * ? * *", "standardPoll")
    }
    
    runEvery1Minute("evaluateMatrix") 
    
    logAction("BMS Advanced Severe Weather Matrix Initialized.")
    evaluateMatrix()
}

def subscribeMulti(device, attrs, handler) {
    if (!device) return
    attrs.each { attr -> subscribe(device, attr, handler) }
}

def getFloat(device, attrs, fallbackStr = null) {
    if (!device) return fallbackStr
    for (attr in attrs) {
        def val = device.currentValue(attr)
        if (val != null) {
            try { return val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch (e) {}
        }
    }
    return fallbackStr
}

def standardPoll() {
    if (!state.watchdogActive) pollSensors()
}

def watchdogPoll() {
    if (state.watchdogActive) {
        pollSensors()
        def secs = settings.defconSecs ?: 30
        runIn(secs, "watchdogPoll")
    }
}

def pollSensors() {
    [sensorTemp, sensorHum, sensorPress, sensorRain, sensorWind, sensorWindDir, sensorLightning].each { dev ->
        if (dev && dev.hasCommand("refresh")) { try { dev.refresh() } catch (e) {} }
    }
}

// Strict void declaration to prevent HTTP response bugs in UI
void appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") return
    if (btn == "createDeviceBtn") { createChildDevice(); return }
    if (btn == "forceEvalBtn") { logAction("MANUAL OVERRIDE: Forcing matrix evaluation."); evaluateMatrix() }
    if (btn == "clearStateBtn") {
        logAction("EMERGENCY RESET: Purging matrix, history, and hardware outputs.")
        state.pressureHistory = []
        state.tempHistory = []
        state.spreadHistory = []
        state.windHistory = []
        state.windDirHistory = []
        state.lightningHistory = []
        state.currentDayRain = 0.0
        state.smoothedTemp = null
        state.watchdogActive = false
        
        initialize()
        stopAllSirens()
        
        def hazards = ["Tornado", "Thunderstorm", "Flood", "Freeze", "SevereHeat", "Tropical"]
        hazards.each { haz -> 
            safeOff(settings["switch${haz}Warn"])
            safeOff(settings["switch${haz}Alarm"])
        }
        evaluateMatrix()
    }
}

def createChildDevice() {
    def deviceId = "SevWeather-${app.id}"
    if (!getChildDevice(deviceId)) {
        try {
            addChildDevice("ShaneAllen", "Advanced Severe Weather Device", deviceId, null, [name: "Advanced Severe Weather Device", label: "Advanced Severe Weather Information Device", isComponent: false])
            logAction("Child device successfully created.")
        } catch (e) { log.error "Failed to create child device. ${e}" }
    }
}

// === HISTORY & HEARTBEAT ===
def markActive() { state.lastHeartbeat = now() }

def updateHistory(historyName, val, maxAgeMs) {
    markActive()
    if (val == null) return
    def cleanVal
    try { cleanVal = val.toString().replaceAll("[^\\d.-]", "").toFloat() } catch(e) { return }
    
    // ANOMALY FILTERING
    if (settings.enableHardwareFilter != false) {
        def hist = state."${historyName}" ?: []
        if (hist.size() > 0) {
            def lastVal = hist.last().value
            def timeDiffSecs = (now() - hist.last().time) / 1000.0
            def delta = Math.abs(cleanVal - lastVal)
            
            // Hard physics limits: Reject impossibly fast leaps over short durations
            if (timeDiffSecs < 120) {
                if (historyName == "tempHistory" && delta > 8.0) return // Temp cannot jump 8F in < 2 mins
                if (historyName == "pressureHistory" && delta > 0.3) return // Pressure cannot jump 0.3 inHg in < 2 mins
            }
        }
    }
    
    def hist = state."${historyName}" ?: []
    hist.add([time: now(), value: cleanVal])
    def cutoff = now() - maxAgeMs
    hist = hist.findAll { it.time >= cutoff }
    if (hist.size() > 60) hist = hist.drop(hist.size() - 60)
    state."${historyName}" = hist
}

def stdHandler(evt) { markActive(); runIn(1, "evaluateMatrix") }
def tempHandler(evt) { updateHistory("tempHistory", evt.value, 3600000); runIn(1, "evaluateMatrix") }
def pressureHandler(evt) { 
    def raw = getFloat(evt, ["value"], 0.0)
    def cal = raw + (settings.pressOffset ?: 0.0)
    updateHistory("pressureHistory", cal, 10800000)
    runIn(1, "evaluateMatrix") 
} 
def windHandler(evt) { updateHistory("windHistory", evt.value, 3600000); runIn(1, "evaluateMatrix") }
def windDirHandler(evt) { updateHistory("windDirHistory", evt.value, 3600000); runIn(1, "evaluateMatrix") }
def lightningHandler(evt) { updateHistory("lightningHistory", evt.value, 1800000); runIn(1, "evaluateMatrix") }

// === THERMODYNAMIC MATH ===
def getTrendData(hist, minTimeHr) {
    if (!hist || hist.size() < 2) return [rate: 0.0, diff: 0.0, str: "Gathering Data"]
    def oldest = hist.first()
    def newest = hist.last()
    def diff = newest.value - oldest.value
    def timeSpanHr = (newest.time - oldest.time) / 3600000.0
    if (timeSpanHr < minTimeHr) return [rate: 0.0, diff: diff, str: "Stable"]
    def ratePerHour = diff / timeSpanHr
    return [rate: ratePerHour, diff: diff, str: "${diff > 0 ? '+' : ''}${String.format('%.2f', ratePerHour)}/hr"]
}

def calculateDewPoint(tF, rh) {
    def tC = (tF - 32.0) * (5.0 / 9.0)
    def gamma = Math.log(rh / 100.0) + ((17.62 * tC) / (243.12 + tC))
    def dpC = (243.12 * gamma) / (17.62 - gamma)
    return (dpC * (9.0 / 5.0)) + 32.0
}

def getAngularDiff(angle1, angle2) {
    def diff = Math.abs(angle1 - angle2) % 360
    return diff > 180 ? 360 - diff : diff
}

def calculateHeatIndex(tF, rh) {
    if (tF < 80.0) return tF
    def hi = -42.379 + (2.04901523 * tF) + (10.14333127 * rh) - (0.22475541 * tF * rh) - (0.00683783 * tF * tF) - (0.05481717 * rh * rh) + (0.00122874 * tF * tF * rh) + (0.00085282 * tF * rh * rh) - (0.00000199 * tF * tF * rh * rh)
    if (rh < 13.0 && tF >= 80.0 && tF <= 112.0) {
        hi -= ((13.0 - rh) / 4.0) * Math.sqrt((17.0 - Math.abs(tF - 95.0)) / 17.0)
    } else if (rh > 85.0 && tF >= 80.0 && tF <= 87.0) {
        hi += ((rh - 85.0) / 10.0) * ((87.0 - tF) / 5.0)
    }
    return hi
}

// === THE MATRIX EVALUATOR ===
def evaluateMatrix() {
    def todayStr = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.currentDateStr || state.currentDateStr != todayStr) {
        state.currentDateStr = todayStr
        state.currentDayRain = 0.0
    }
    
    def currentDaily = getFloat(sensorRainDaily, ["rainDaily", "dailyrainin", "water", "dailyWater"], 0.0)
    if (currentDaily > (state.currentDayRain ?: 0.0)) state.currentDayRain = currentDaily

    if (!sensorTemp || !sensorHum || !sensorPress) return

    def staleMins = settings.staleDataTimeout ?: 30
    def isStale = (settings.enableStaleCheck != false) && ((now() - (state.lastHeartbeat ?: now())) > (staleMins * 60000))
    state.isStale = isStale
    
    // Fetch Data
    def t = getFloat(sensorTemp, ["temperature", "tempf"], 0.0)
    def h = getFloat(sensorHum, ["humidity"], 0.0)
    def rawP = getFloat(sensorPress, ["pressure", "Baromrelin", "baromrelin", "Baromabsin", "baromabsin", "barometricPressure"], 0.0)
    def p = rawP + (settings.pressOffset ?: 0.0) // Apply MSLP Calibration
    
    def r = getFloat(sensorRain, ["rainRate", "hourlyrainin", "precipRate", "hourlyRain"], 0.0)
    def windVal = getFloat(sensorWind, ["windSpeed", "windspeedmph", "wind"], 0.0)
    def windDirVal = getFloat(sensorWindDir, ["windDirection", "winddir", "windDir"], 0.0)
    def lightDist = getFloat(sensorLightning, ["lightningDistance", "distance"], 999.0)
    def strikeCount = state.lightningHistory?.size() ?: 0

    if (settings.enableThermalSmoothing != false) {
        def lastT = state.smoothedTemp != null ? state.smoothedTemp : t
        def delta = Math.abs(t - lastT)
        if (delta > 3.0 && state.tempHistory?.size() > 0) t = lastT + ((t - lastT) * 0.3)
        state.smoothedTemp = t
    }
    
    def dp = calculateDewPoint(t, h)
    def dpSpread = t - dp
    if (dpSpread < 0) dpSpread = 0.0
    updateHistory("spreadHistory", dpSpread, 3600000)

    def pTrendData = getTrendData(state.pressureHistory, 0.25) // 15 min for severe predictive
    def pTrend3Hr = getTrendData(state.pressureHistory, 2.5)  // Long trend for tropical
    def tTrendData = getTrendData(state.tempHistory, 0.25)
    def sTrendData = getTrendData(state.spreadHistory, 0.25)
    
    state.pressureTrendStr = pTrendData.str
    state.tempTrendStr = tTrendData.str
    state.spreadTrendStr = sTrendData.str
    state.windTrendStr = sensorWind ? getTrendData(state.windHistory, 0.25).str : "N/A"

    def shiftMagnitude = 0.0
    if (sensorWindDir && state.windDirHistory && state.windDirHistory.size() > 5) {
        def oldestDir = state.windDirHistory.first().value
        shiftMagnitude = getAngularDiff(oldestDir, windDirVal)
    }

    def debounceMs = (settings.debounceMins ?: 15) * 60000
    def highestProbThisCycle = 0.0

    // --------------------------------------------------------------------------------
    // 1. TORNADO / EXTREME SHEAR DNA
    // --------------------------------------------------------------------------------
    if (settings.enableTornado != false && !isStale) {
        def tThreat = 0.0
        def tMath = ""
        
        if (sensorWind) {
            tThreat = (windVal / 45.0) * 100.0
            if (tThreat > 100.0) tThreat = 100.0
            tMath += "Thrt: (W[${String.format('%.1f', windVal)}] / 45) * 100 = ${String.format('%.1f', tThreat)}%<br>"
        } else {
            tMath += "Thrt: 0% [No Wind Sensor]<br>"
        }
        
        def tProb = 0.0
        def tConf = 50
        def tWhy = "Atmospheric conditions are currently stable."
        tMath += "Prob Base: 0.0%<br>"
        
        if (pTrendData.rate < 0) {
            def pScore = (Math.abs(pTrendData.rate) / 0.10) * 40.0
            def pCap = pScore > 40.0 ? 40.0 : pScore
            tProb += pCap
            tMath += "&nbsp;↳ Baro Drop [${String.format('%.2f', pTrendData.rate)}]: +${String.format('%.1f', pCap)}%<br>"
        }
        
        if (pTrendData.rate <= -0.06 && shiftMagnitude >= 40.0) {
            tProb += 40.0
            tMath += "&nbsp;↳ PREDICTIVE: Severe Shear/Drop Synergy: +40.0%<br>"
        }
        
        if (sensorWind) {
            tConf += 25
            def wScore = (windVal / 45.0) * 40.0
            def wCap = wScore > 50.0 ? 50.0 : wScore
            tProb += wCap
            tMath += "&nbsp;↳ Kinetic [${String.format('%.1f', windVal)}]: +${String.format('%.1f', wCap)}%<br>"
        }
        if (sensorWindDir) {
            tConf += 25
            def sScore = (shiftMagnitude / 60.0) * 20.0
            def sCap = sScore > 30.0 ? 30.0 : sScore
            tProb += sCap
            tMath += "&nbsp;↳ Shear [${String.format('%.0f', shiftMagnitude)}°]: +${String.format('%.1f', sCap)}%<br>"
        }
        
        tProb = Math.round(tProb)
        if (tProb > 100) tProb = 100
        tThreat = Math.round(tThreat)
        if (tThreat > 100) tThreat = 100
        
        tMath += "<b>Final Prob: ${tProb}%</b>"
        
        if (tProb > highestProbThisCycle) highestProbThisCycle = tProb
        
        if (tProb > 20) {
            tWhy = "Probability is ${tProb}% driven by pressure dropping at ${String.format('%.2f', pTrendData.rate)} inHg/hr. "
            if (pTrendData.rate <= -0.06 && shiftMagnitude >= 40.0) tWhy += "<b>PREDICTIVE METRICS ENGAGED:</b> Dangerous synergy of rapidly falling pressure and shifting winds detected before kinetic impact. "
            if (windVal > 15) tWhy += "Threat is ${tThreat}% due to current kinetic winds of ${String.format('%.1f', windVal)} mph. "
            if (shiftMagnitude > 30) tWhy += "Wind shear is contributing via a ${String.format('%.0f', shiftMagnitude)}° shift."
        }
        
        processHazardState("Tornado", tThreat, tProb, tConf, tWhy, tMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 2. THUNDERSTORM DNA
    // --------------------------------------------------------------------------------
    if (settings.enableThunderstorm != false && !isStale) {
        def tsThreat = 0.0
        def tsMath = ""
        
        if (sensorWind) {
            tsThreat = (windVal / 30.0) * 100.0
            if (tsThreat > 100.0) tsThreat = 100.0
            tsMath += "Thrt(Wind): (W[${String.format('%.1f', windVal)}] / 30) * 100 = ${String.format('%.1f', tsThreat)}%<br>"
        }
        if (strikeCount > 5) {
            def lThreat = (strikeCount / 15.0) * 100.0
            if (lThreat > 100.0) lThreat = 100.0
            if (lThreat > tsThreat) tsThreat = lThreat
            tsMath += "Thrt(Lght): (Strk[${strikeCount}] / 15) * 100 = ${String.format('%.1f', lThreat)}%<br>"
        }
        if (!sensorWind && strikeCount <= 5) tsMath += "Thrt: 0.0%<br>"
        
        def tsProb = 0.0
        def tsConf = 40
        def tsWhy = "No localized convective or electrical activity detected."
        tsMath += "Prob Base: 0.0%<br>"
        
        if (t > 75.0 && dp > 65.0 && sTrendData.rate < -1.5) {
            tsProb += 40.0
            tsMath += "&nbsp;↳ PREDICTIVE CAPE Proxy: High Heat/Moisture + Atmospheric Squeeze: +40.0%<br>"
        }
        
        if (sensorLightning) {
            tsConf += 30
            def lScore = (strikeCount / 15.0) * 50.0
            if (lightDist <= 10.0) { lScore *= 1.2; tsMath += "&nbsp;↳ Proximity Multiplier: x1.2<br>" }
            def lCap = lScore > 60.0 ? 60.0 : lScore
            tsProb += lCap
            tsMath += "&nbsp;↳ Strikes [${strikeCount}]: +${String.format('%.1f', lCap)}%<br>"
        }
        if (sTrendData.rate < -2.0 || tTrendData.rate < -3.0) {
            tsProb += 25
            tsMath += "&nbsp;↳ Squeeze/Temp Drop: +25.0%<br>"
        }
        if (windVal > 20.0 && pTrendData.rate > 0.02) {
            tsProb += 25
            tsMath += "&nbsp;↳ Gust Front detected: +25.0%<br>"
        }
        if (sensorWind) tsConf += 30
        
        tsProb = Math.round(tsProb)
        if (tsProb > 100) tsProb = 100
        tsThreat = Math.round(tsThreat)
        if (tsThreat > 100) tsThreat = 100
        
        tsMath += "<b>Final Prob: ${tsProb}%</b>"
        
        if (tsProb > highestProbThisCycle) highestProbThisCycle = tsProb
        
        if (tsProb > 20) {
            tsWhy = "Probability is ${tsProb}%. "
            if (t > 75.0 && dp > 65.0 && sTrendData.rate < -1.5) tsWhy += "<b>PREDICTIVE METRICS ENGAGED:</b> High Heat/Moisture (CAPE Proxy) combined with a rapid atmospheric squeeze indicates brewing convection. "
            if (strikeCount > 0) tsWhy += "Driven by ${strikeCount} lightning strikes (closest: ${lightDist} mi). "
            if (sTrendData.rate < -1.5 && !(t > 75.0 && dp > 65.0)) tsWhy += "Atmospheric squeeze velocity is rapidly converging (${String.format('%.2f', sTrendData.rate)}°/hr) indicating rain shafts. "
            if (pTrendData.rate > 0.01 && windVal > 10) tsWhy += "A pressure bump and gust front was detected. "
        }
        
        processHazardState("Thunderstorm", tsThreat, tsProb, tsConf, tsWhy, tsMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 3. FLOOD DNA
    // --------------------------------------------------------------------------------
    if (settings.enableFlood != false && !isStale) {
        def fMath = ""
        def fThreat = (state.currentDayRain / 3.0) * 100.0
        if (fThreat > 100.0) fThreat = 100.0
        fMath += "Thrt: (Acc[${String.format('%.2f', state.currentDayRain)}] / 3.0) * 100 = ${String.format('%.1f', fThreat)}%<br>"
        
        def fProb = 0.0
        def fConf = 20
        def fWhy = "Ground is absorbing moisture effectively with no extreme rain rates."
        fMath += "Prob Base: 0.0%<br>"
        
        if (sensorRain) fConf += 40
        if (sensorRainDaily) fConf += 40
        
        if (state.currentDayRain >= 1.5 && pTrendData.rate <= -0.03) {
            fProb += 30.0
            fMath += "&nbsp;↳ PREDICTIVE: Saturated Ground + Low Pressure Front: +30.0%<br>"
        }
        
        def rScore = (r / 2.0) * 60.0
        def rCap = rScore > 70.0 ? 70.0 : rScore
        fProb += rCap
        fMath += "&nbsp;↳ Rate [${String.format('%.2f', r)} in/hr]: +${String.format('%.1f', rCap)}%<br>"
        
        def aScore = (state.currentDayRain / 3.0) * 40.0
        def aCap = aScore > 60.0 ? 60.0 : aScore
        fProb += aCap
        fMath += "&nbsp;↳ Accumulation: +${String.format('%.1f', aCap)}%<br>"
        
        fProb = Math.round(fProb)
        if (fProb > 100) fProb = 100
        fThreat = Math.round(fThreat)
        
        fMath += "<b>Final Prob: ${fProb}%</b>"
        
        if (fProb > highestProbThisCycle) highestProbThisCycle = fProb
        
        if (fProb > 0) {
            fWhy = "Threat intensity is ${fThreat}% based on ${String.format('%.2f', state.currentDayRain)} inches of total daily accumulation. "
            if (state.currentDayRain >= 1.5 && pTrendData.rate <= -0.03) fWhy += "<b>PREDICTIVE METRICS ENGAGED:</b> Ground is already heavily saturated and an incoming low-pressure front is detected. "
            if (r > 0) fWhy += "Probability of flash flooding is ${fProb}% driven by a real-time rain rate of ${String.format('%.2f', r)} in/hr. "
        }
        
        processHazardState("Flood", fThreat, fProb, fConf, fWhy, fMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 4. FREEZE DNA
    // --------------------------------------------------------------------------------
    if (settings.enableFreeze != false && !isStale) {
        def frMath = ""
        def frThreat = 0.0
        if (t <= 32.0) {
            frThreat = 100.0
            frMath += "Thrt: Temp[${t}] <= 32 = 100.0%<br>"
        } else {
            frThreat = 100.0 - ((t - 32.0) * 5.0)
            if (frThreat < 0.0) frThreat = 0.0
            frMath += "Thrt: 100 - ((T[${t}] - 32) * 5) = ${String.format('%.1f', frThreat)}%<br>"
        }
        
        def frProb = 0.0
        def frConf = 70
        def frWhy = "Temperatures safely above freezing."
        frMath += "Prob Base: 0.0%<br>"
        
        if (sensorHum) frConf += 30
        
        if (t <= 32.0) {
            frProb = 100
            frMath += "&nbsp;↳ Hard Freeze Active: 100%<br>"
        } else if (t <= 40.0 && tTrendData.rate < 0) {
            def dropRate = Math.abs(tTrendData.rate > 0.1 ? tTrendData.rate : 0.1)
            def hoursToFreeze = (t - 32.0) / dropRate
            frMath += "&nbsp;↳ PREDICTIVE: Est Time to 32°: ${String.format('%.1f', hoursToFreeze)} hrs<br>"
            if (hoursToFreeze <= 2.0) { frProb = 90; frMath += "&nbsp;&nbsp;&nbsp;↳ < 2hrs: +90.0%<br>" }
            else if (hoursToFreeze <= 4.0) { frProb = 65; frMath += "&nbsp;&nbsp;&nbsp;↳ < 4hrs: +65.0%<br>" }
            else { frProb = 30; frMath += "&nbsp;&nbsp;&nbsp;↳ > 4hrs: +30.0%<br>" }
        } else {
             frMath += "&nbsp;↳ Trajectory Safe.<br>"
        }
        
        frProb = Math.round(frProb)
        if (frProb > 100) frProb = 100
        frThreat = Math.round(frThreat)
        if (frThreat > 100) frThreat = 100
        
        frMath += "<b>Final Prob: ${frProb}%</b>"
        
        if (frProb > highestProbThisCycle) highestProbThisCycle = frProb
        
        if (frProb > 0) {
            frWhy = "Current temperature is ${String.format('%.1f', t)}°. Threat intensity is ${frThreat}%. "
            if (frProb == 100) {
                frWhy += (dpSpread <= 3.0) ? "Hard Freeze with Hoar Frost actively occurring. " : "Dry Hard Freeze actively occurring. "
            } else {
                frWhy += "Probability is ${frProb}% based on a trajectory of dropping ${String.format('%.2f', Math.abs(tTrendData.rate))}°/hr towards the freezing point. "
            }
        }
        
        processHazardState("Freeze", frThreat, frProb, frConf, frWhy, frMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 5. SEVERE HEAT DNA
    // --------------------------------------------------------------------------------
    if (settings.enableSevereHeat != false && !isStale) {
        def shMath = ""
        def hi = calculateHeatIndex(t, h)
        shMath += "NWS Heat Index: ${String.format('%.1f', hi)}°F<br>"
        
        def shThreat = 0.0
        if (hi >= 90.0) {
            shThreat = ((hi - 90.0) / 35.0) * 100.0 // Scales so HI 125 = 100% Threat
        }
        if (shThreat > 100.0) shThreat = 100.0
        shMath += "Thrt: ((HI - 90)/35)*100 = ${String.format('%.1f', shThreat)}%<br>"
        
        def shProb = 0.0
        def shConf = 60
        if (sensorHum) shConf += 40
        def shWhy = "Heat levels are currently safe."
        shMath += "Prob Base: 0.0%<br>"
        
        if (hi >= 90.0) {
            shProb = shThreat 
            shMath += "&nbsp;↳ Base Level: +${String.format('%.1f', shProb)}%<br>"
            if (tTrendData.rate > 0) {
                def bump = (tTrendData.rate / 2.0) * 20.0
                shProb += bump
                shMath += "&nbsp;↳ PREDICTIVE: Heating Bump [${String.format('%.1f', tTrendData.rate)}°/hr]: +${String.format('%.1f', bump)}%<br>"
            }
        } else if (t >= 80.0 && tTrendData.rate > 1.0) {
             shProb = ((t - 80.0)/10.0) * 20.0
             shMath += "&nbsp;↳ PREDICTIVE: Trajectory to 90°: +${String.format('%.1f', shProb)}%<br>"
        }
        
        shProb = Math.round(shProb)
        if (shProb > 100) shProb = 100
        shThreat = Math.round(shThreat)
        
        shMath += "<b>Final Prob: ${shProb}%</b>"
        
        if (shProb > highestProbThisCycle) highestProbThisCycle = shProb
        
        if (shProb > 0) {
            shWhy = "Current NWS Heat Index is ${String.format('%.1f', hi)}°F. "
            if (hi >= 103.0) shWhy += "<b>DANGER:</b> High risk of heat exhaustion or heat stroke for individuals or pets outside. "
            else if (hi >= 90.0) shWhy += "CAUTION: Prolonged exposure and physical activity may lead to heat exhaustion. "
        }
        
        processHazardState("SevereHeat", shThreat, shProb, shConf, shWhy, shMath, debounceMs)
    }

    // --------------------------------------------------------------------------------
    // 6. TROPICAL DNA
    // --------------------------------------------------------------------------------
    if (settings.enableTropical != false && !isStale) {
        def trMath = ""
        def trThreat = ((29.90 - p) / 0.50) * 100.0
        if (trThreat > 100.0) trThreat = 100.0
        if (trThreat < 0.0) trThreat = 0.0
        trMath += "Thrt: ((29.90 - P[${String.format('%.2f', p)}])/0.5) * 100 = ${String.format('%.1f', trThreat)}%<br>"
        
        def trProb = 0.0
        def trConf = 50
        def trWhy = "No sustained tropical barometric signatures detected locally."
        trMath += "Prob Base: 0.0%<br>"
        
        if (sensorPress) trConf += 25
        if (sensorWind) trConf += 25
        
        if (p < 29.80) {
            def pScore = ((29.80 - p) / 0.30) * 60.0
            def pCap = pScore > 70.0 ? 70.0 : pScore
            trProb += pCap
            trMath += "&nbsp;↳ Depth Score (MSLP Calibrated): +${String.format('%.1f', pCap)}%<br>"
        }
        
        if (pTrend3Hr.rate <= -0.05) {
            trProb += 15
            trMath += "&nbsp;↳ PREDICTIVE: Sustained 3hr Drop [${String.format('%.2f', pTrend3Hr.rate)}]: +15.0%<br>"
            if (dp >= 70.0) {
                trProb += 15
                trMath += "&nbsp;&nbsp;&nbsp;↳ PREDICTIVE: Deep Moisture Loading Synergy: +15.0%<br>"
            }
        }
        
        if (windVal > 30.0) {
            trProb += 30
            trMath += "&nbsp;↳ Sustained Wind [>30mph]: +30.0%<br>"
        }
        
        trProb = Math.round(trProb)
        if (trProb > 100) trProb = 100
        trThreat = Math.round(trThreat)
        if (trThreat > 100) trThreat = 100
        
        trMath += "<b>Final Prob: ${trProb}%</b>"
        
        if (trProb > highestProbThisCycle) highestProbThisCycle = trProb
        
        if (trProb > 20) {
            trWhy = "Threat intensity is ${trThreat}% due to a current barometric depth of ${String.format('%.2f', p)} inHg. "
            if (pTrend3Hr.rate <= -0.05 && dp >= 70.0) trWhy += "<b>PREDICTIVE METRICS ENGAGED:</b> A long-duration barometric vacuum is combining with deep atmospheric moisture loading. "
            trWhy += "Probability is ${trProb}% factoring in a 3-hour pressure trajectory of ${String.format('%.2f', pTrend3Hr.rate)} inHg/hr and sustained winds of ${String.format('%.1f', windVal)} mph. "
        }
        
        processHazardState("Tropical", trThreat, trProb, trConf, trWhy, trMath, debounceMs)
    }

    // Handle Stale Data Fallback
    if (isStale) {
        def hazards = ["Tornado", "Thunderstorm", "Flood", "Freeze", "SevereHeat", "Tropical"]
        hazards.each { haz -> processHazardState(haz, 0, 0, 0, "⚠ Sensors Offline. Stale data prevented safe calculation.", "Offline. Connect sensors.", debounceMs) }
        highestProbThisCycle = 0.0 // Don't trigger watchdog on offline sensors
    }

    // DEFCON WATCHDOG LOGIC
    if (settings.enableDefcon != false) {
        def threshold = settings.defconThresh ?: 25
        if (highestProbThisCycle >= threshold && !state.watchdogActive) {
            state.watchdogActive = true
            logAction("🚨 DEFCON WATCHDOG ENGAGED: Threat probability crossed ${threshold}%. Commencing sub-minute polling override.")
            watchdogPoll() // Kick off recursive fast polling
        } else if (highestProbThisCycle < threshold && state.watchdogActive) {
            state.watchdogActive = false
            logAction("✅ DEFCON WATCHDOG DISENGAGED: All threat probabilities below ${threshold}%. Returning to standard polling schedule.")
        }
    } else {
        state.watchdogActive = false
    }

    updateChildDevice()
}

// === HAZARD STATE ROUTING ===
def processHazardState(haz, threat, prob, conf, why, mathEx, debounceMs) {
    def matrix = state.threatMatrix ?: [:]
    def data = matrix[haz] ?: [threat: 0, prob: 0, conf: 0, state: "Clear", howWhy: "Initializing...", mathEx: "Awaiting calculation cycle...", lastTrig: 0]
    
    def pfx = haz.toLowerCase()
    def warnThresh = settings["${pfx}WarnThresh"] ?: 50
    def alarmThresh = settings["${pfx}AlarmThresh"] ?: 80
    
    def targetState = "Clear"
    if (prob >= alarmThresh) targetState = "ALARM"
    else if (prob >= warnThresh) targetState = "WARNING"
    
    def currentState = data.state
    def timeSinceChange = now() - data.lastTrig
    def allowTransition = false
    
    if (currentState == "Clear" && targetState != "Clear") allowTransition = true
    else if (currentState == "WARNING" && targetState == "ALARM") allowTransition = true
    else if (currentState == targetState) allowTransition = true 
    else if (timeSinceChange >= debounceMs) allowTransition = true
    else why += " <br><span style='color:#f0ad4e;'><b>[Safety Hardware Hold Active for ${Math.ceil((debounceMs - timeSinceChange)/60000).toInteger()} more mins]</b></span>"
    
    if (allowTransition && currentState != targetState) {
        logAction("${haz} DNA shifted from ${currentState} to ${targetState}. (Prob: ${prob}%)")
        data.lastTrig = now()
        
        def allowedModes = settings["${pfx}Modes"]
        def modeOk = (!allowedModes || allowedModes.contains(location.mode))
        
        def warnSw = settings["switch${haz}Warn"]
        def alarmSw = settings["switch${haz}Alarm"]
        
        if (targetState == "ALARM") {
            safeOn(alarmSw)
            safeOff(warnSw)
            
            if (modeOk) {
                if (settings["${pfx}AlarmNotify"]) sendNotification("🚨 CRITICAL: ${haz} ALARM Conditions Detected!")
                if (settings["${pfx}AlarmTTS"]) playAudio(settings["tts${haz}Alarm"])
                if (settings["${pfx}AlarmSiren"]) triggerSiren()
                if (settings["${pfx}AlarmSound"]) playSoundFile(settings["url${haz}Alarm"])
            } else {
                logAction("Alarm audio/push suppressed due to Mode Restriction.")
            }
            
        } else if (targetState == "WARNING") {
            safeOff(alarmSw)
            safeOn(warnSw)
            
            if (modeOk) {
                if (settings["${pfx}WarnNotify"]) sendNotification("⚠ WARNING: Elevated ${haz} Conditions.")
                if (settings["${pfx}WarnTTS"]) playAudio(settings["tts${haz}Warn"])
                if (settings["${pfx}WarnSiren"]) triggerSiren()
                if (settings["${pfx}WarnSound"]) playSoundFile(settings["url${haz}Warn"])
            } else {
                logAction("Warning audio/push suppressed due to Mode Restriction.")
            }
            
        } else {
            safeOff(alarmSw)
            safeOff(warnSw)
            if (currentState != "Clear") {
                stopAllSirens()
                if (modeOk && settings["${pfx}WarnNotify"]) sendNotification("✅ The localized ${haz} threat has cleared.")
            }
        }
    }
    
    data.threat = threat
    data.prob = prob
    data.conf = conf
    data.state = allowTransition ? targetState : currentState
    data.howWhy = why
    data.mathEx = mathEx
    
    matrix[haz] = data
    state.threatMatrix = matrix
}

// === HARDWARE ROUTING ===
def safeOn(dev) {
    if (dev && dev.currentValue("switch") != "on") {
        try { dev.on() } catch (e) { log.error "Failed ON: ${e}" }
    }
}

def safeOff(dev) {
    if (dev && dev.currentValue("switch") != "off") {
        try { dev.off() } catch (e) { log.error "Failed OFF: ${e}" }
    }
}

def sendNotification(msg) {
    if (notifyDevices) {
        notifyDevices.each { it.deviceNotification(msg) }
        logAction("Push Sent: ${msg}")
    }
}

def playAudio(msg) {
    if (!msg || !audioDevices) return
    def vol = audioVolume ?: 65
    try {
        audioDevices.each { speaker ->
            if (speaker.hasCommand("setVolume")) speaker.setVolume(vol)
            if (speaker.hasCommand("speak")) speaker.speak(msg)
            else if (speaker.hasCommand("playText")) speaker.playText(msg)
        }
        logAction("TTS Broadcasted: ${msg}")
    } catch (e) { log.error "TTS routing failed: ${e}" }
}

def playSoundFile(url) {
    if (!url || !soundDevices) return
    def vol = audioVolume ?: 65
    try {
        soundDevices.each { player ->
            if (player.hasCommand("setVolume")) player.setVolume(vol)
            if (player.hasCommand("playTrack")) player.playTrack(url)
        }
        logAction("Sound File Played: ${url}")
    } catch (e) { log.error "Sound file routing failed: ${e}" }
}

def triggerSiren() {
    if (sirenDevices) {
        try {
            sirenDevices.each { siren -> 
                if (siren.hasCommand("siren")) siren.siren()
                else if (siren.hasCommand("on")) siren.on() 
            }
            logAction("SIREN TRIGGERED")
        } catch (e) { log.error "Siren trigger failed: ${e}" }
    }
}

def stopAllSirens() {
    if (sirenDevices) {
        try {
            sirenDevices.each { siren ->
                if (siren.hasCommand("off")) siren.off()
            }
            logAction("All Sirens Silenced")
        } catch (e) { log.error "Siren shutoff failed: ${e}" }
    }
}

// === CHILD DEVICE SYNCHRONIZATION ===
def updateChildDevice() {
    def child = getChildDevice("SevWeather-${app.id}")
    if (child) {
        def matrix = state.threatMatrix
        def hazards = ["Tornado", "Thunderstorm", "Flood", "Freeze", "SevereHeat", "Tropical"]
        
        def highestState = "Clear"
        hazards.each { haz ->
            if (!matrix[haz]) return
            def s = matrix[haz].state
            if (s == "ALARM") highestState = "ALARM"
            else if (s == "WARNING" && highestState == "Clear") highestState = "WARNING"
            
            child.sendEvent(name: "${haz.toLowerCase()}Threat", value: matrix[haz].threat, unit: "%")
            child.sendEvent(name: "${haz.toLowerCase()}Prob", value: matrix[haz].prob, unit: "%")
            child.sendEvent(name: "${haz.toLowerCase()}Conf", value: matrix[haz].conf, unit: "%")
            child.sendEvent(name: "${haz.toLowerCase()}State", value: matrix[haz].state)
        }
        
        child.sendEvent(name: "globalThreatState", value: highestState)
        child.sendEvent(name: "pressureTrend", value: state.pressureTrendStr ?: "Stable")
        child.sendEvent(name: "tempTrend", value: state.tempTrendStr ?: "Stable")
        child.sendEvent(name: "windTrend", value: state.windTrendStr ?: "Stable")
        child.sendEvent(name: "currentDayRain", value: state.currentDayRain ?: 0.0)
    }
}

// === LOGGING ===
def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
