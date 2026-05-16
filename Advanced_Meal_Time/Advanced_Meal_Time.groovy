/*
 * Advanced Meal Time
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Meal Time",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Automates meal time by monitoring seat sensors. Features 3D Spatial Vectoring, Kick Accumulators, Chef's Chair Interlock, Family Lifestyle Metrics, and Smart Room 'Call to Meal' Announcements.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "speakerMappingPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Meal Time</b>", install: true, uninstall: true) {
        
        section("<b>Live System Dashboard</b>") {
            def sysStatus = state.mealTimeActive ? "<b style='color:#27ae60;'>ACTIVE (${state.activeMealType?.capitalize()} in Progress)</b>" : "<b style='color:#555;'>IDLE (Waiting for occupants)</b>"
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${sysStatus}</div>"

            def reqChairs = settings.minChairs ?: 2
            def reqVibes = settings.minTotalVibes ?: 5
            def occCount = state.occupiedCount ?: 0
            def totalVibes = state.totalVibes ?: 0
            
            def chairStr = state.mealTimeActive ? "<b style='color:green;'>${occCount} Occupied</b>" : "${occCount} Occupied (Requires ${reqChairs})"
            def vibeStr = state.mealTimeActive ? "<b style='color:green;'>${totalVibes} Human Movements</b>" : "${totalVibes} Human Movements (Requires ${reqVibes})"
            
            def modeStatus = isModeAllowed() ? "<b style='color:green;'>Allowed</b>" : "<b style='color:red;'>Restricted</b>"
            def bfastBypassStr = isBreakfastBypassed() ? "<b style='color:#e67e22;'>Bypassed (Work/School Day)</b>" : "<span style='color:green;'>Standard Operation</span>"
            
            def currentWin = getCurrentTimeWindow()
            def timeStatus = currentWin != "none" ? "<b style='color:green;'>${currentWin.capitalize()} Window Active</b>" : "<b style='color:#e67e22;'>Outside Meal Windows</b>"
            
            def guestActive = (guestSwitch && guestSwitch.currentValue("switch") == "on")
            def timeoutMins = guestActive ? (settings.guestTimeout ?: 45) : (settings.inactiveTimeout ?: 5)
            def guestStr = guestActive ? "<b style='color:#8e44ad;'>ACTIVE (${timeoutMins} min timeout)</b>" : "Inactive (${timeoutMins} min timeout)"
            
            def chefStatusStr = "<span style='color:#555;'>Disabled (Anyone can trigger)</span>"
            if (settings.chefChairSelect && settings.chefChairSelect != "None") {
                if (state.chefIsSeated) chefStatusStr = "<b style='color:green;'>Seated (${settings.chefChairSelect})</b>"
                else chefStatusStr = "<b style='color:#e67e22;'>Waiting for Chef (${settings.chefChairSelect})</b>"
            }
            
            // Dynamic Countdown Calculation
            def countdownStr = "Cleared"
            if (state.shutoffPending && state.expectedEndTime) {
                long remainingMs = state.expectedEndTime - now()
                if (remainingMs > 0) {
                    long remSecs = remainingMs / 1000
                    long m = remSecs / 60
                    long s = remSecs % 60
                    String sStr = s < 10 ? "0${s}" : "${s}"
                    countdownStr = "<b style='color:#c0392b;'>Ending in ${m}:${sStr}</b>"
                } else {
                    countdownStr = "<b style='color:#c0392b;'>Ending Now...</b>"
                }
            }

            def liveStats = state.dashboardSeatStats ?: "No recent data"
            def calibStr = state.chairBaselines ? "<b style='color:green;'>3D Spatial Vectors Locked</b>" : "<span style='color:#555;'>No 3D Data / Standard Sensors</span>"

            input "btnRefreshData", "button", title: "🔄 Refresh Dashboard Data"

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
                .dash-sub { background-color: #e9ecef; font-weight: bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Metric</th><th colspan="3">Current Value</th></tr></thead>
                <tbody>
                    <tr><td colspan="4" class="dash-sub">Trigger Conditions & Overrides</td></tr>
                    <tr><td class="dash-hl">Operating Mode</td><td colspan="3" class="dash-val">${modeStatus}</td></tr>
                    <tr><td class="dash-hl">Breakfast Bypass Status</td><td colspan="3" class="dash-val">${bfastBypassStr}</td></tr>
                    <tr><td class="dash-hl">Current Meal Window</td><td colspan="3" class="dash-val">${timeStatus}</td></tr>
                    <tr><td class="dash-hl">Guest Mode Override</td><td colspan="3" class="dash-val">${guestStr}</td></tr>
                    <tr><td class="dash-hl">Chef's Chair Interlock</td><td colspan="3" class="dash-val">${chefStatusStr}</td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Family Lifestyle Metrics (Resets Sundays)</td></tr>
                    <tr><td class="dash-hl">Meals Recorded This Week</td><td colspan="3" class="dash-val" style="font-size:16px;"><b>${state.weeklyMealCount ?: 0}</b></td></tr>
                    <tr><td class="dash-hl">Average Meal Duration</td><td colspan="3" class="dash-val" style="font-size:16px; color:#2980b9;"><b>${getAverageMealDurationStr()}</b></td></tr>
                    
                    <tr><td colspan="4" class="dash-sub">Physical Tracking (Rolling Window)</td></tr>
                    <tr><td class="dash-hl">Current Seat Status</td><td colspan="3" class="dash-val">${chairStr}</td></tr>
                    <tr><td class="dash-hl">Accumulated Energy</td><td colspan="3" class="dash-val">${vibeStr}</td></tr>
                    <tr><td class="dash-hl">Live Sensor Data</td><td colspan="3" class="dash-val" style="font-family:monospace; font-size:12px;">${liveStats}</td></tr>
                    <tr><td class="dash-hl">Hardware State</td><td colspan="3" class="dash-val">${calibStr}</td></tr>
                    <tr><td class="dash-hl">Auto-End Countdown</td><td colspan="3" class="dash-val">${countdownStr}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
            
            paragraph "<br>"
            input "btnCalibrateChairs", "button", title: "🪑 Calibrate Empty Chairs (For Samsung 3D Sensors)"
            input "btnResetStats", "button", title: "📊 Reset Weekly Meal Stats"
            input "btnForceBreakfast", "button", title: "▶️ Force Start Breakfast"
            input "btnForceDinner", "button", title: "▶️ Force Start Dinner"
            input "btnForceEnd", "button", title: "⏹️ Force End Meal Time"
        }

        section("<b>System Event History</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
        }

        section("<b>Seat Monitoring Hardware</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Select up to 4 sensors mounted to your dining chairs. The app will automatically route them to the 3D physics engine if they support it.</div>"
            input "chair1", "capability.accelerationSensor", title: "Chair Sensor 1", required: false
            input "chair2", "capability.accelerationSensor", title: "Chair Sensor 2", required: false
            input "chair3", "capability.accelerationSensor", title: "Chair Sensor 3", required: false
            input "chair4", "capability.accelerationSensor", title: "Chair Sensor 4", required: false
        }
        
        section("<b>The 'Chef's Chair' Interlock</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Homework Filter:</b> Prevent the meal from starting until a specific chair (e.g., Mom or Dad) is occupied. Kids can sit at the table all afternoon without triggering the house.</div>"
            input "chefChairSelect", "enum", title: "Designate the Chef's Chair", options: ["None", "Chair 1", "Chair 2", "Chair 3", "Chair 4"], defaultValue: "None", required: true, submitOnChange: true
        }
        
        section("<b>Anti-False Alarm Filters (Physics & Accumulation)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Samsung 3D Mode (The 'Roomba' Filter):</b> If using Samsung Multi-Sensors, the app checks the 3D spatial shift. Micro-vibrations are ignored.</div>"
            input "spatialThreshold", "number", title: "Samsung 3D Tilt Threshold", description: "Minimum spatial shift required to count as human weight (Default: 30).", defaultValue: 30, required: true
            
            paragraph "<div style='font-size:13px; color:#555;'><b>Accumulator (The 'Kick' Filter):</b> Prevents accidental bumps from triggering a meal by counting multiple valid movements over a rolling window.</div>"
            input "vibeWindow", "number", title: "Tracking Window (Minutes)", defaultValue: 5, required: true
            input "minChairs", "number", title: "Minimum Occupied Chairs to Trigger", defaultValue: 2, required: true
            input "minVibesPerChair", "number", title: "Min Events per Chair", description: "How many times must a chair register weight/movement to be 'Occupied'? (Default: 2)", defaultValue: 2, required: true
            input "minTotalVibes", "number", title: "Min Total Events Combined", description: "Total valid events required across all occupied chairs. (Default: 5)", defaultValue: 5, required: true
        }

        section("<b>Timeout & Guest Override</b>", hideable: true, hidden: true) {
            input "inactiveTimeout", "number", title: "Standard Empty Table Timeout (Minutes)", description: "How long must ALL chairs remain still before Meal Time ends? (Default: 5)", defaultValue: 5, required: true
            
            paragraph "<div style='font-size:13px; color:#555;'><b>Guest Mode Filter:</b> Extends the timeout so lights don't shut off on guests getting dessert.</div>"
            input "guestSwitch", "capability.switch", title: "Guest Mode Virtual Switch", required: false
            input "guestTimeout", "number", title: "Guest Mode Empty Table Timeout (Minutes)", description: "Extended timeout duration (Default: 45)", defaultValue: 45, required: true
        }
        
        section("<b>Global Mode Restrictions</b>", hideable: true, hidden: true) {
            input "allowedModes", "mode", title: "Allowed Modes for Automation (Leave blank for all)", multiple: true, required: false
        }
        
        section("<b>Day Restrictions (Breakfast Bypass)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>If either of these switches are ON, Breakfast automation will be bypassed. Dinner will function normally.</div>"
            input "workDaySwitch", "capability.switch", title: "Virtual Work Day Switch", required: false
            input "schoolDaySwitch", "capability.switch", title: "Virtual School Day Switch", required: false
        }
        
        section("<b>📺 TV Conflict Override (Optional)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>If you have another app that turns lights back ON when the TV is turned OFF, link the TV switch here. This app will turn the TV OFF immediately, wait for the other app to fire, and then execute its own Light OFF commands.</div>"
            input "tvSwitch", "capability.switch", title: "TV Switch", required: false
            input "tvLightDelay", "number", title: "Delay before turning lights OFF (Seconds)", description: "Applies only if the TV was ON. (Default: 5)", defaultValue: 5, required: false
        }

        section("<b>🔔 'Call to Meal' Announcement Engine</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Map a button controller to manually broadcast meal time announcements to active rooms. <br><b>Push = Breakfast | Double Tap = Lunch | Hold = Dinner</b></div>"
            input "mealButton", "capability.pushableButton", title: "Call to Meal Button Controller", required: false
            input "mealButtonNum", "number", title: "Button Number on Controller", defaultValue: 1, required: true
            
            input "announceType", "enum", title: "Announcement Type", options: ["TTS (Text to Speech)", "Sound File / Chime"], defaultValue: "TTS (Text to Speech)", submitOnChange: true
            
            if (announceType == "TTS (Text to Speech)") {
                input "bfastPayload", "text", title: "Breakfast TTS String", defaultValue: "Breakfast is ready."
                input "lunchPayload", "text", title: "Lunch TTS String", defaultValue: "Lunch is ready."
                input "dinnerPayload", "text", title: "Dinner TTS String", defaultValue: "Dinner is ready. Please come to the table."
            } else {
                input "bfastPayload", "text", title: "Breakfast Track/Chime #", description: "e.g., 1"
                input "lunchPayload", "text", title: "Lunch Track/Chime #", description: "e.g., 2"
                input "dinnerPayload", "text", title: "Dinner Track/Chime #", description: "e.g., 3"
            }
            
            paragraph "<b>Output Hardware</b>"
            input "audioDevices", "capability.speechSynthesis", title: "TTS Audio Devices (e.g., Sonos/Echo)", multiple: true, required: false
            input "soundDevices", "capability.audioNotification", title: "Sound File/MP3/Zooz Players", multiple: true, required: false
            input "audioVolume", "number", title: "Master Announcement Volume Level (%)", defaultValue: 65, range: "1..100"
            
            if (audioDevices || soundDevices) {
                href(name: "speakerMappingPageLink", page: "speakerMappingPage", title: "▶ Smart Room Presence Audio Routing", description: "Map your speakers to motion sensors to avoid broadcasting to empty rooms.")
            }
        }

        section("<b>🎵 Meal Time Ambiance</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Automatically start background music or trigger an ambiance switch after a delay during meal time. It will be paused/turned off when the meal ends.</div>"
            input "ambianceDelay", "number", title: "Delay before starting ambiance (Minutes)", defaultValue: 5, required: false
            
            input "ambianceSpeaker", "capability.musicPlayer", title: "Ambiance Speaker", required: false, submitOnChange: true
            if (ambianceSpeaker) {
                input "ambianceAudioMode", "enum", title: "↳ Audio Source", options: ["Track URI", "Favorite Virtual Switch"], required: true, defaultValue: "Track URI", submitOnChange: true
                
                if (ambianceAudioMode == "Favorite Virtual Switch") {
                    input "ambianceFavSwitch", "capability.switch", title: "↳ Favorite Virtual Switch", required: false
                } else {
                    input "ambianceTrack", "text", title: "↳ Music Track URI/File to play (Optional)", required: false
                }
            }
            
            input "ambianceVolume", "number", title: "Target Ambiance Volume (%)", range: "1..100", required: false
            input "ambianceFadeEnable", "bool", title: "Fade-in Volume (Starts at 5% and gently ramps up)", defaultValue: false, required: false
            input "ambianceSwitch", "capability.switch", title: "Ambiance Switch (Turns ON)", required: false
        }

        section("<b>🍳 BREAKFAST Configuration</b>", hideable: true, hidden: true) {
            input "bfastStartTime", "time", title: "Breakfast Start Time", required: false
            input "bfastEndTime", "time", title: "Breakfast End Time", required: false
            
            paragraph "<b>Breakfast Actions</b>"
            input "bfastMealSwitch", "capability.switch", title: "Virtual Breakfast Switch (Turns ON)", required: false
            input "bfastDndSwitch", "capability.switch", title: "Do Not Disturb Switch (Turns ON)", required: false
            input "bfastOnLights", "capability.switch", title: "Lights to Turn ON", multiple: true, required: false
            input "bfastOffLights", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false
            input "bfastLockDoors", "capability.lock", title: "Doors to Lock", multiple: true, required: false
            input "bfastPauseSpeakers", "capability.musicPlayer", title: "Speakers/Media to Pause", multiple: true, required: false
        }
        
        section("<b>🍽️ DINNER Configuration</b>", hideable: true, hidden: true) {
            input "dinnerStartTime", "time", title: "Dinner Start Time", required: false
            input "dinnerEndTime", "time", title: "Dinner End Time", required: false
            
            paragraph "<b>Dinner Actions</b>"
            input "dinnerMealSwitch", "capability.switch", title: "Virtual Dinner Switch (Turns ON)", required: false
            input "dinnerDndSwitch", "capability.switch", title: "Do Not Disturb Switch (Turns ON)", required: false
            input "dinnerOnLights", "capability.switch", title: "Lights to Turn ON", multiple: true, required: false
            input "dinnerOffLights", "capability.switch", title: "Lights to Turn OFF", multiple: true, required: false
            input "dinnerLockDoors", "capability.lock", title: "Doors to Lock", multiple: true, required: false
            input "dinnerPauseSpeakers", "capability.musicPlayer", title: "Speakers/Media to Pause", multiple: true, required: false
        }
    }
}

def speakerMappingPage() {
    return dynamicPage(name: "speakerMappingPage", title: "<b>Smart Room Presence Audio Routing</b>", install: false, uninstall: false) {
        section() { paragraph "<i>Assign one or multiple motion sensors to your speakers.</i>" }
        def allSpeakers = []
        if (settings.audioDevices) allSpeakers += settings.audioDevices
        if (settings.soundDevices) allSpeakers += settings.soundDevices
        allSpeakers = allSpeakers.unique { it.id }
        
        if (allSpeakers) {
            allSpeakers.each { speaker ->
                section("<b>Routing for: ${speaker.displayName}</b>", hideable: true, hidden: true) {
                    input "isAlwaysOn_${speaker.id}", "bool", title: "🎙️ Make this a Master Always-Announce Speaker", defaultValue: false, submitOnChange: true
                    if (!settings["isAlwaysOn_${speaker.id}"]) {
                        input "motionMap_${speaker.id}", "capability.motionSensor", title: "🚶 Required Motion Sensors (Active within 5 mins)", required: false, multiple: true
                        input "nightSwitch_${speaker.id}", "capability.switch", title: "🌙 Room 'Good Night' Override Switch", required: false
                    }
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (state.mealTimeActive == null) state.mealTimeActive = false
    if (state.shutoffPending == null) state.shutoffPending = false
    if (state.activeMealType == null) state.activeMealType = "none"
    if (state.chefIsSeated == null) state.chefIsSeated = false
    if (!state.seatHistory) state.seatHistory = [:]
    if (!state.chairBaselines) state.chairBaselines = [:]
    
    // Lifestyle Metrics Initialization
    if (state.weeklyMealCount == null) state.weeklyMealCount = 0
    if (state.weeklyMealDurationMs == null) state.weeklyMealDurationMs = 0
    
    // Schedule Auto-Reset for Sunday at Midnight
    schedule("0 0 0 ? * SUN", resetWeeklyStats)
    
    // Auto-Detect and Subscribe based on capabilities
    def chairs = [chair1, chair2, chair3, chair4]
    chairs.each { c ->
        if (c) {
            subscribe(c, "acceleration", combinedAccelerationHandler)
            
            if (c.hasAttribute("threeAxis")) {
                subscribe(c, "threeAxis", chairAxisHandler)
                logAction("HARDWARE: ${c.displayName} running in Hybrid Mode (3D Spatial + Standard Vibration).")
            } else {
                logAction("HARDWARE: ${c.displayName} running in Standard Vibration Mode.")
            }
        }
    }
    
    // Subscribe to Call-to-Meal Button
    if (settings.mealButton) {
        subscribe(settings.mealButton, "pushed", "mealButtonHandler")
        subscribe(settings.mealButton, "doubleTapped", "mealButtonHandler")
        subscribe(settings.mealButton, "held", "mealButtonHandler")
    }
    
    // Subscribe to Room Motion Sensors for Audio Routing
    def motionSensorsToSub = []
    settings.each { key, val ->
        if (key.startsWith("motionMap_") && val) {
            if (val instanceof List) motionSensorsToSub.addAll(val)
            else motionSensorsToSub << val
        }
    }
    if (motionSensorsToSub) {
        motionSensorsToSub = motionSensorsToSub.unique { it.id }
        subscribe(motionSensorsToSub, "motion.active", "motionActiveHandler")
    }
    
    evaluateSeats() 
    logAction("App Initialized.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefreshData") {
        evaluateSeats()
        logAction("MANUAL: Dashboard data refreshed.")
    } else if (btn == "btnCalibrateChairs") {
        calibrateChairs()
    } else if (btn == "btnResetStats") {
        resetWeeklyStats()
    } else if (btn == "btnForceBreakfast") {
        logAction("MANUAL OVERRIDE: Breakfast Forced ON.")
        startMealTime("breakfast")
    } else if (btn == "btnForceDinner") {
        logAction("MANUAL OVERRIDE: Dinner Forced ON.")
        startMealTime("dinner")
    } else if (btn == "btnForceEnd") {
        logAction("MANUAL OVERRIDE: Meal Time Forced OFF.")
        endMealTime()
    }
}

// ------------------------------------------------------------------------------
// AUDIO ROUTING & CALL TO MEAL LOGIC
// ------------------------------------------------------------------------------

def mealButtonHandler(evt) {
    def targetBtn = settings.mealButtonNum ? settings.mealButtonNum.toString() : "1"
    if (evt.value == targetBtn) {
        if (evt.name == "pushed") {
            logAction("Call to Meal Button: Pushed (Breakfast)")
            executeAnnouncement(settings.bfastPayload)
        } else if (evt.name == "doubleTapped") {
            logAction("Call to Meal Button: Double Tapped (Lunch)")
            executeAnnouncement(settings.lunchPayload)
        } else if (evt.name == "held") {
            logAction("Call to Meal Button: Held (Dinner)")
            executeAnnouncement(settings.dinnerPayload)
        }
    }
}

def executeAnnouncement(payload) {
    if (!payload) return
    if (settings.announceType == "TTS (Text to Speech)") {
        playAudio(payload)
    } else {
        playSoundFile(payload)
    }
}

def motionActiveHandler(evt) { 
    state["motionLastActive_${evt.deviceId}"] = now() 
}

def shouldSpeakerAnnounce(device) {
    if (settings["isAlwaysOn_${device.id}"]) return true
    def overrideSw = settings["nightSwitch_${device.id}"]
    if (overrideSw && overrideSw.currentValue("switch") == "on") return true
    
    def mappedSensors = settings["motionMap_${device.id}"]
    if (!mappedSensors) return true 
    
    def sensorList = mappedSensors instanceof List ? mappedSensors : [mappedSensors]
    def isActive = false
    for (sensor in sensorList) {
        if (sensor.currentValue("motion") == "active") {
            isActive = true
            break
        }
        def lastActive = state["motionLastActive_${sensor.id}"] ?: 0
        if (now() - lastActive <= 300000) { // 5 minutes
            isActive = true
            break
        }
    }
    return isActive
}

def playAudio(msg, force = false) {
    if (!msg || !settings.audioDevices) return
    def vol = settings.audioVolume ?: 65
    try {
        settings.audioDevices.each { speaker ->
            if (force || shouldSpeakerAnnounce(speaker)) {
                if (speaker.hasCommand("setVolume")) speaker.setVolume(vol)
                if (speaker.hasCommand("speak")) speaker.speak(msg)
                else if (speaker.hasCommand("playText")) speaker.playText(msg)
            } else {
                logAction("TTS suppressed on ${speaker.displayName} - Room Empty.")
            }
        }
        logAction("TTS Announcement Dispatched.")
    } catch (e) { log.error "TTS routing failed: ${e}" }
}

def playSoundFile(url, force = false) {
    if (!url || !settings.soundDevices) return
    def vol = settings.audioVolume ?: 65
    try {
        settings.soundDevices.each { player ->
            if (force || shouldSpeakerAnnounce(player)) {
                if (player.hasCommand("setVolume")) player.setVolume(vol)
                if (url.trim().isInteger() && player.hasCommand("playSound")) {
                    player.playSound(url.trim().toInteger())
                } else if (player.hasCommand("playTrack")) {
                    player.playTrack(url.trim())
                } else if (player.hasCommand("chime")) {
                    player.chime(url.trim().toInteger())
                }
            } else {
                logAction("Sound file suppressed on ${player.displayName} - Room Empty.")
            }
        }
        logAction("Audio Track Announcement Dispatched.")
    } catch (e) { log.error "Audio routing failed: ${e}" }
}

// ------------------------------------------------------------------------------
// HARDWARE CALIBRATION
// ------------------------------------------------------------------------------

def calibrateChairs() {
    def baselines = [:]
    def chairs = [chair1, chair2, chair3, chair4]
    chairs.each { c ->
        if (c && c.hasAttribute("threeAxis")) {
            def xyz = c.currentValue("threeAxis")
            if (xyz) {
                baselines[c.id] = [x: xyz.x as Integer, y: xyz.y as Integer, z: xyz.z as Integer]
                logAction("Calibrated 3D baseline for ${c.displayName}: ${xyz}")
            }
        }
    }
    state.chairBaselines = baselines
    logAction("SYSTEM: All 3D spatial baselines locked in.")
}

// ------------------------------------------------------------------------------
// LIFESTYLE METRICS HELPERS
// ------------------------------------------------------------------------------

def resetWeeklyStats() {
    state.weeklyMealCount = 0
    state.weeklyMealDurationMs = 0
    logAction("SYSTEM: Weekly Family Lifestyle metrics have been reset.")
}

String getAverageMealDurationStr() {
    if (!state.weeklyMealCount || state.weeklyMealCount == 0) return "N/A"
    long avgMs = (state.weeklyMealDurationMs ?: 0) / state.weeklyMealCount
    long totalMins = avgMs / 60000
    long hrs = totalMins / 60
    long mins = totalMins % 60
    if (hrs > 0) return "${hrs}h ${mins}m"
    return "${mins}m"
}

// ------------------------------------------------------------------------------
// TIME & MODE HELPERS
// ------------------------------------------------------------------------------

boolean isModeAllowed() {
    if (!settings.allowedModes) return true
    return (settings.allowedModes as List).contains(location.mode)
}

boolean isTimeInWindow(startTimeStr, endTimeStr) {
    if (!startTimeStr || !endTimeStr) return false
    
    def t0 = timeToday(startTimeStr, location.timeZone)
    def t1 = timeToday(endTimeStr, location.timeZone)
    def now = new Date()
    
    if (t1.time < t0.time) { 
        return (now.time >= t0.time || now.time <= t1.time)
    } else {
        return (now.time >= t0.time && now.time <= t1.time)
    }
}

String getCurrentTimeWindow() {
    if (isTimeInWindow(settings.bfastStartTime, settings.bfastEndTime)) return "breakfast"
    if (isTimeInWindow(settings.dinnerStartTime, settings.dinnerEndTime)) return "dinner"
    return "none"
}

boolean isBreakfastBypassed() {
    if (settings.workDaySwitch && settings.workDaySwitch.currentValue("switch") == "on") return true
    if (settings.schoolDaySwitch && settings.schoolDaySwitch.currentValue("switch") == "on") return true
    return false
}

int getTimeoutSeconds() {
    if (settings.guestSwitch && settings.guestSwitch.currentValue("switch") == "on") {
        return (settings.guestTimeout ?: 45) * 60
    }
    return (settings.inactiveTimeout ?: 5) * 60
}

// ------------------------------------------------------------------------------
// SENSOR PHYSICS & TRACKING
// ------------------------------------------------------------------------------

def combinedAccelerationHandler(evt) {
    if (evt.value == "active") {
        recordValidEvent(evt.device.id)
    } else if (evt.value == "inactive") {
        evaluateSeats() 
    }
}

def chairAxisHandler(evt) {
    def c = evt.device
    def xyz = c.currentValue("threeAxis")
    if (!xyz) return

    def baselines = state.chairBaselines ?: [:]
    def base = baselines[c.id]
    if (!base) return 

    int curX = xyz.x as Integer
    int curY = xyz.y as Integer
    int curZ = xyz.z as Integer

    int devX = Math.abs(curX - base.x)
    int devY = Math.abs(curY - base.y)
    int devZ = Math.abs(curZ - base.z)
    int maxDev = Math.max(devX, Math.max(devY, devZ))

    int thresh = settings.spatialThreshold ?: 30

    if (maxDev >= thresh) {
        recordValidEvent(c.id)
    }
}

def recordValidEvent(devId) {
    def history = state.seatHistory ?: [:]
    def devEvents = history[devId] ?: []
    
    long nowMs = now()
    
    if (devEvents.size() > 0 && (nowMs - devEvents.last()) < 2000) return
    
    devEvents << nowMs
    
    if (devEvents.size() > 50) devEvents = devEvents.drop(devEvents.size() - 50)
    
    history[devId] = devEvents
    state.seatHistory = history
    
    evaluateSeats()
}

// ------------------------------------------------------------------------------
// MEAL LOGIC EVALUATION
// ------------------------------------------------------------------------------

def evaluateSeats() {
    def history = state.seatHistory ?: [:]
    long cutoff = now() - ((settings.vibeWindow ?: 5) * 60 * 1000)
    
    int occupiedChairsCount = 0
    int totalValidVibes = 0
    int liveActiveCount = 0
    
    boolean chefSeated = false
    def targetedChefChair = null
    
    if (settings.chefChairSelect == "Chair 1") targetedChefChair = chair1
    else if (settings.chefChairSelect == "Chair 2") targetedChefChair = chair2
    else if (settings.chefChairSelect == "Chair 3") targetedChefChair = chair3
    else if (settings.chefChairSelect == "Chair 4") targetedChefChair = chair4
    
    if (!targetedChefChair || settings.chefChairSelect == "None") {
        chefSeated = true 
    }
    
    def chairList = [chair1, chair2, chair3, chair4]
    def debugStrings = []
    
    chairList.each { chair ->
        if (chair) {
            if (chair.currentValue("acceleration") == "active") {
                liveActiveCount++
            }
            
            def devId = chair.id
            def events = history[devId] ?: []
            events = events.findAll { it >= cutoff } 
            history[devId] = events 
            
            int vibeCount = events.size()
            int reqPerChair = settings.minVibesPerChair ?: 2
            boolean thisChairOccupied = false
            
            if (vibeCount >= reqPerChair) {
                occupiedChairsCount++
                totalValidVibes += vibeCount
                thisChairOccupied = true
            }
            
            if (targetedChefChair && chair.id == targetedChefChair.id && thisChairOccupied) {
                chefSeated = true
            }
            
            debugStrings << "${chair.displayName}: ${vibeCount}"
        }
    }
    
    state.seatHistory = history 
    state.dashboardSeatStats = debugStrings.join(" | ")
    state.occupiedCount = occupiedChairsCount
    state.totalVibes = totalValidVibes
    state.chefIsSeated = chefSeated
    
    // 1. CANCEL SHUTOFF IF LIVE MOVEMENT
    if (liveActiveCount > 0 && state.shutoffPending) {
        logAction("Table activity detected. Canceling auto-end timer.")
        unschedule("endMealTime")
        state.shutoffPending = false
        state.expectedEndTime = null
    }
    
    // 2. TRIGGER NEW MEAL LOGIC
    if (!state.mealTimeActive) {
        int reqChairs = settings.minChairs ?: 2
        int reqTotal = settings.minTotalVibes ?: 5
        
        if (occupiedChairsCount >= reqChairs && totalValidVibes >= reqTotal) {
            if (!chefSeated) {
                logAction("Seats occupied, but waiting for Chef's Chair to trigger meal.")
            } else {
                String win = getCurrentTimeWindow()
                
                if (win == "breakfast" && isBreakfastBypassed()) {
                    logAction("Seats occupied, but Breakfast is bypassed due to Work/School day switch.")
                } else if (win != "none" && isModeAllowed()) {
                    logAction("TRIGGER: ${occupiedChairsCount} chairs occupied, Chef is seated. Initiating ${win.capitalize()}.")
                    startMealTime(win)
                } else {
                    if (!isModeAllowed()) logAction("Seats occupied, but ignored due to Mode restrictions.")
                    else if (win == "none") logAction("Seats occupied, but currently outside of Breakfast/Dinner windows.")
                }
            }
        }
    } 
    // 3. END MEAL LOGIC
    else if (state.mealTimeActive && liveActiveCount == 0 && !state.shutoffPending) {
        int delaySeconds = getTimeoutSeconds()
        int delayMinutes = delaySeconds / 60
        String guestTag = (settings.guestSwitch?.currentValue("switch") == "on") ? " (Guest Mode Active)" : ""
        
        logAction("All chairs are currently still. Scheduling ${state.activeMealType?.capitalize()} end in ${delayMinutes} minutes${guestTag}.")
        state.shutoffPending = true
        state.expectedEndTime = now() + (delaySeconds * 1000)
        runIn(delaySeconds, "endMealTime", [overwrite: true])
    }
}

def startMealTime(String mealType) {
    state.mealTimeActive = true
    state.activeMealType = mealType
    state.shutoffPending = false
    state.expectedEndTime = null
    state.mealStartTime = now() 
    
    logAction("AUTOMATION: Executing ${mealType.capitalize()} ON routines.")
    
    // Fire the Ambiance Delay Logic
    int delayMins = settings.ambianceDelay != null ? settings.ambianceDelay : 5
    if (settings.ambianceSpeaker || settings.ambianceSwitch) {
        if (delayMins > 0) {
            logAction("Scheduling Meal Time Ambiance to start in ${delayMins} minutes.")
            runIn(delayMins * 60, "startAmbiance")
        } else {
            startAmbiance()
        }
    }
    
    boolean delayOffLights = false
    if (settings.tvSwitch) {
        if (settings.tvSwitch.currentValue("switch") == "on") {
            logAction("TV is ON. Turning it off and delaying light shutoff by ${settings.tvLightDelay ?: 5} seconds.")
            delayOffLights = true
        }
        settings.tvSwitch.off() 
    }
    
    if (mealType == "breakfast") {
        if (bfastMealSwitch) bfastMealSwitch.on()
        if (bfastDndSwitch) bfastDndSwitch.on()
        if (bfastOnLights) bfastOnLights.on()
        if (bfastLockDoors) bfastLockDoors.lock()
        pauseAudio(bfastPauseSpeakers)
        
        if (delayOffLights && bfastOffLights) {
            runIn(settings.tvLightDelay ?: 5, "turnOffBfastLights")
        } else if (bfastOffLights) {
            bfastOffLights.off()
        }
    } else if (mealType == "dinner") {
        if (dinnerMealSwitch) dinnerMealSwitch.on()
        if (dinnerDndSwitch) dinnerDndSwitch.on()
        if (dinnerOnLights) dinnerOnLights.on()
        if (dinnerLockDoors) dinnerLockDoors.lock()
        pauseAudio(dinnerPauseSpeakers)
        
        if (delayOffLights && dinnerOffLights) {
            runIn(settings.tvLightDelay ?: 5, "turnOffDinnerLights")
        } else if (dinnerOffLights) {
            dinnerOffLights.off()
        }
    }
}

// ------------------------------------------------------------------------------
// AMBIANCE EXECUTION
// ------------------------------------------------------------------------------

def startAmbiance() {
    logAction("AUTOMATION: Starting Meal Time Ambiance.")
    if (ambianceSwitch) {
        ambianceSwitch.on()
    }
    
    if (ambianceSpeaker) {
        try {
            int targetVol = settings.ambianceVolume ? settings.ambianceVolume as int : 50
            def audioMode = settings.ambianceAudioMode ?: "Track URI"
            
            if (settings.ambianceFadeEnable) {
                int startVol = 5
                ambianceSpeaker.setVolume(startVol)
                
                if (audioMode == "Favorite Virtual Switch" && settings.ambianceFavSwitch) {
                    settings.ambianceFavSwitch.on()
                } else if (settings.ambianceTrack) {
                    ambianceSpeaker.playTrack(settings.ambianceTrack)
                } else {
                    ambianceSpeaker.play()
                }
                
                int stepAmt = Math.max(1, (targetVol - startVol) / 5 as int)
                logAction("Initiating volume fade-in from ${startVol}% to ${targetVol}%.")
                runIn(3, "rampAmbianceVolume", [data: [currentVol: startVol, targetVol: targetVol, step: stepAmt]])
            } else {
                if (settings.ambianceVolume) ambianceSpeaker.setVolume(targetVol)
                
                if (audioMode == "Favorite Virtual Switch" && settings.ambianceFavSwitch) {
                    settings.ambianceFavSwitch.on()
                } else if (settings.ambianceTrack) {
                    ambianceSpeaker.playTrack(settings.ambianceTrack)
                } else {
                    ambianceSpeaker.play()
                }
            }
        } catch (e) { log.error "Failed to play ambiance on ${ambianceSpeaker.displayName}: ${e}" }
    }
}

def rampAmbianceVolume(Map data) {
    if (!state.mealTimeActive) return // Abort ramp if the meal was suddenly ended
    
    int currentVol = data.currentVol
    int targetVol = data.targetVol
    int step = data.step
    
    currentVol += step
    
    if (currentVol >= targetVol) {
        ambianceSpeaker.setVolume(targetVol)
        logAction("Ambiance volume reached target (${targetVol}%).")
    } else {
        ambianceSpeaker.setVolume(currentVol)
        runIn(3, "rampAmbianceVolume", [data: [currentVol: currentVol, targetVol: targetVol, step: step]])
    }
}

def turnOffBfastLights() {
    logAction("AUTOMATION: TV delay finished. Turning off Breakfast lights.")
    if (bfastOffLights) bfastOffLights.off()
}

def turnOffDinnerLights() {
    logAction("AUTOMATION: TV delay finished. Turning off Dinner lights.")
    if (dinnerOffLights) dinnerOffLights.off()
}

def pauseAudio(speakers) {
    if (!speakers) return
    speakers.each { speaker ->
        try {
            if (speaker.hasCommand("pause")) speaker.pause()
        } catch (e) { log.error "Failed to pause speaker ${speaker.displayName}: ${e}" }
    }
}

def endMealTime() {
    String mealType = state.activeMealType ?: "none"
    state.mealTimeActive = false
    state.activeMealType = "none"
    state.shutoffPending = false
    state.expectedEndTime = null
    
    unschedule("turnOffBfastLights")
    unschedule("turnOffDinnerLights")
    unschedule("startAmbiance")
    
    // Pause Ambiance if still actively playing
    if (ambianceSpeaker) {
        try {
            def status = ambianceSpeaker.currentValue("status")
            if (status == "playing") {
                logAction("AUTOMATION: Ambiance still playing. Issuing Pause command.")
                ambianceSpeaker.pause()
            }
        } catch (e) { log.error "Failed to pause ambiance on ${ambianceSpeaker.displayName}: ${e}" }
        
        if (settings.ambianceAudioMode == "Favorite Virtual Switch" && settings.ambianceFavSwitch) {
            try { settings.ambianceFavSwitch.off() } catch(e){}
        }
    }
    
    if (ambianceSwitch) ambianceSwitch.off()
    
    if (state.mealStartTime) {
        long durationMs = now() - state.mealStartTime
        state.weeklyMealDurationMs = (state.weeklyMealDurationMs ?: 0) + durationMs
        state.weeklyMealCount = (state.weeklyMealCount ?: 0) + 1
        state.mealStartTime = null
        logAction("Meal Metrics Logged: Duration was ${Math.round(durationMs / 60000)} minutes.")
    }
    
    if (mealType != "none") {
        logAction("AUTOMATION: ${mealType.capitalize()} has concluded. Executing OFF routines.")
    } else {
        logAction("AUTOMATION: Meal Time manually forced off.")
    }
    
    if (mealType == "breakfast") {
        if (bfastMealSwitch) bfastMealSwitch.off()
        if (bfastDndSwitch) bfastDndSwitch.off()
    } else if (mealType == "dinner") {
        if (dinnerMealSwitch) dinnerMealSwitch.off()
        if (dinnerDndSwitch) dinnerDndSwitch.off()
    } else {
        if (bfastMealSwitch) bfastMealSwitch.off()
        if (bfastDndSwitch) bfastDndSwitch.off()
        if (dinnerMealSwitch) dinnerMealSwitch.off()
        if (dinnerDndSwitch) dinnerDndSwitch.off()
    }
    
    state.seatHistory = [:]
    evaluateSeats()
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
