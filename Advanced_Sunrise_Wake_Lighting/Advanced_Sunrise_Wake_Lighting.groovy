/**
 * Advanced Sunrise Wake Lighting
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Sunrise Wake Lighting",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Gradual sunrise simulation with manual override protection, audio fade, snooze tracking, and routine hand-offs.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room</th><th style='padding: 8px;'>Good Night</th><th style='padding: 8px;'>Work/School</th><th style='padding: 8px;'>Wake Status</th></tr>"
            
            def configuredRooms = 0
            
            [1, 2, 3].each { rNum ->
                def lights = settings["r${rNum}_lights"]
                if (lights) {
                    configuredRooms++
                    
                    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
                    
                    // Good Night Switch Status
                    def gnSwitch = settings["r${rNum}_gnSwitch"]
                    def switchState = gnSwitch ? gnSwitch.currentValue("switch")?.toUpperCase() : "N/A"
                    def switchColor = (switchState == "ON") ? "green" : (switchState == "OFF" ? "red" : "gray")
                    
                    // Work/School Day Switch Status
                    def activeDaySwitch = settings["r${rNum}_activeDaySwitch"]
                    def activeDayState = activeDaySwitch ? activeDaySwitch.currentValue("switch")?.toUpperCase() : "N/A"
                    def activeDayColor = (activeDayState == "ON") ? "green" : (activeDayState == "OFF" ? "red" : "gray")
                    
                    def fadeStatus = "WAITING"
                    if (state["r${rNum}_isSnoozing"]) {
                        fadeStatus = "<span style='color: #9c27b0; font-weight: bold;'>SNOOZING</span>"
                    } else if (state["r${rNum}_isFading"]) {
                        def curPct = state["r${rNum}_currentPct"] ?: 0
                        def curTemp = state["r${rNum}_currentTemp"] ?: 2500
                        fadeStatus = "<span style='color: orange; font-weight: bold;'>FADING (${curPct}% at ${curTemp}K)</span>"
                    }
                    
                    def timeTxt = settings["r${rNum}_startTime"] ? new Date(timeToday(settings["r${rNum}_startTime"]).time).format("h:mm a", location.timeZone) : "--:--"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'>"
                    statusText += "<td style='padding: 8px;'><b>${rName}</b><br><span style='font-size: 11px; color: #666;'>Starts at ${timeTxt}</span></td>"
                    statusText += "<td style='padding: 8px; color: ${switchColor}; font-weight: bold;'>${switchState}</td>"
                    statusText += "<td style='padding: 8px; color: ${activeDayColor}; font-weight: bold;'>${activeDayState}</td>"
                    statusText += "<td style='padding: 8px;'>${fadeStatus}</td>"
                    statusText += "</tr>"
                }
            }
            
            statusText += "</table>"
            
            if (configuredRooms > 0) {
                paragraph statusText
            } else {
                paragraph "<i>Configure at least one room below to see live system status.</i>"
            }
        }
        
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as events occur.</i>"
            }
        }
        
        [1, 2, 3].each { rNum ->
            def currentRoomName = settings["r${rNum}_name"] ?: "Room ${rNum}"
            
            section("▶ ${currentRoomName} Configuration", hideable: true, hidden: true) {
                input "r${rNum}_name", "text", title: "Custom Room Name", required: false, defaultValue: "Room ${rNum}", submitOnChange: true
                input "r${rNum}_lights", "capability.colorTemperature", title: "Wake Lights (Must support Color Temp)", multiple: true, required: false
                
                if (settings["r${rNum}_lights"]) {
                    paragraph "<b>Timing & Limits</b>"
                    input "r${rNum}_days", "enum", title: "Days to Run", options: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"], multiple: true, required: true
                    input "r${rNum}_startTime", "time", title: "Simulation Start Time", required: true
                    input "r${rNum}_duration", "number", title: "Total Fade Duration (Minutes)", defaultValue: 30, required: true
                    
                    input "r${rNum}_maxLevel", "number", title: "Max Brightness Ceiling (%)", defaultValue: 100, range: "1..100", required: true
                    
                    input "r${rNum}_useVarTemp", "bool", title: "Sync Color Temp to Hub Variable?", defaultValue: false, submitOnChange: true
                    if (settings["r${rNum}_useVarTemp"]) {
                        input "r${rNum}_varTempName", "text", title: "Hub Variable Name (e.g., currentSunTemp)", required: true
                    } else {
                        input "r${rNum}_maxTemp", "number", title: "Max Color Temp Ceiling (K)", defaultValue: 6500, range: "2500..9000", required: true
                    }
                    
                    paragraph "<b>Gradual Audio Wake (Optional)</b>"
                    input "r${rNum}_speaker", "capability.musicPlayer", title: "Wake Up Speaker(s)", multiple: true, required: false, description: "Slowly fades up volume alongside the lights."
                    input "r${rNum}_maxVol", "number", title: "Max Volume Ceiling (%)", defaultValue: 40, range: "1..100", required: false
                    
                    paragraph "<b>Snooze Button (Optional)</b>"
                    input "r${rNum}_snoozeBtn", "capability.pushableButton", title: "Physical Snooze Button", required: false, description: "Button push instantly drops lights/audio to 1% and pauses the fade."
                    input "r${rNum}_snoozeMins", "number", title: "Snooze Duration (Minutes)", defaultValue: 9, required: false
                    
                    paragraph "<b>Controls & Handoffs</b>"
                    input "r${rNum}_gnSwitch", "capability.switch", title: "Virtual 'Good Night' Switch", required: true, description: "Must be ON for simulation to run. Turning OFF halts the fade."
                    input "r${rNum}_sunriseStateSwitch", "capability.switch", title: "Sunrise Active State Switch (To pause Good Night enforcement)", required: false
                    input "r${rNum}_activeDaySwitch", "capability.switch", title: "Work/School Day Switch (Optional)", required: false, description: "If selected, this switch MUST be ON for the sunrise to run."
                    input "r${rNum}_modes", "mode", title: "Active Modes (Optional)", multiple: true, required: false
                    
                    input "r${rNum}_notifier", "capability.notification", title: "Wake Up Notification Device(s)", multiple: true, required: false
                    input "r${rNum}_wakeMsg", "text", title: "Wake Up Message", defaultValue: "Time to wake up!", required: false
                    
                    input "r${rNum}_endSwitch", "capability.switch", title: "Morning Routine Handoff (Optional)", required: false, description: "Switch to turn on when fade completes."
                }
            }
        }
        
        section("System Maintenance") {
            input "btnForceReset", "button", title: "Reset Logs & State Variables"
        }
    }
}

def installed() {
    log.info "Advanced Sunrise Wake Lighting Installed."
    initialize()
}

def updated() {
    log.info "Advanced Sunrise Wake Lighting Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.historyLog = state.historyLog ?: []
    
    [1, 2, 3].each { rNum ->
        state["r${rNum}_isFading"] = false
        state["r${rNum}_isSnoozing"] = false
        state["r${rNum}_justResumed"] = false
        state["r${rNum}_currentPct"] = 0
        state["r${rNum}_currentTemp"] = 0
        
        if (settings["r${rNum}_lights"] && settings["r${rNum}_startTime"]) {
            schedule(settings["r${rNum}_startTime"], "room${rNum}StartHandler")
            
            if (settings["r${rNum}_gnSwitch"]) {
                subscribe(settings["r${rNum}_gnSwitch"], "switch.off", "room${rNum}SwitchOffHandler")
            }
            
            if (settings["r${rNum}_snoozeBtn"]) {
                subscribe(settings["r${rNum}_snoozeBtn"], "pushed", "room${rNum}SnoozeHandler")
            }
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnForceReset") {
        state.historyLog = []
        [1, 2, 3].each { rNum ->
            state["r${rNum}_isFading"] = false
            state["r${rNum}_isSnoozing"] = false
            state["r${rNum}_currentPct"] = 0
            state["r${rNum}_currentTemp"] = 0
        }
        addToHistory("SYSTEM: State variables and logs manually reset by user.")
    }
}

// ==========================================
// ROOM HANDLERS
// ==========================================
def room1StartHandler() { startFadeProcess(1) }
def room1FadeLoop() { fadeLoopProcess(1) }
def room1SwitchOffHandler(evt) { handleSwitchOff(1) }
def room1SnoozeHandler(evt) { handleSnooze(1) }
def room1ResumeFade() { resumeFade(1) }

def room2StartHandler() { startFadeProcess(2) }
def room2FadeLoop() { fadeLoopProcess(2) }
def room2SwitchOffHandler(evt) { handleSwitchOff(2) }
def room2SnoozeHandler(evt) { handleSnooze(2) }
def room2ResumeFade() { resumeFade(2) }

def room3StartHandler() { startFadeProcess(3) }
def room3FadeLoop() { fadeLoopProcess(3) }
def room3SwitchOffHandler(evt) { handleSwitchOff(3) }
def room3SnoozeHandler(evt) { handleSnooze(3) }
def room3ResumeFade() { resumeFade(3) }

// ==========================================
// CORE LOGIC FUNCTIONS
// ==========================================

def startFadeProcess(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    def gnSwitch = settings["r${rNum}_gnSwitch"]
    def activeDaySwitch = settings["r${rNum}_activeDaySwitch"]
    def modes = settings["r${rNum}_modes"]
    def days = settings["r${rNum}_days"]
    def tz = location.timeZone ?: TimeZone.getDefault()
    def currentDay = new Date().format("EEEE", tz)
    
    // 1. Check Days of the Week
    if (days && !days.contains(currentDay)) {
        log.info "${rName} Sunrise: Skipped. Today (${currentDay}) is not an active day."
        return
    }

    // 2. Check if Good Night Switch is ON
    if (gnSwitch && gnSwitch.currentValue("switch") != "on") {
        log.info "${rName} Sunrise: Skipped. Good Night switch is OFF."
        return
    }
    
    // 3. Check Work/School Day Switch
    if (activeDaySwitch && activeDaySwitch.currentValue("switch") != "on") {
        log.info "${rName} Sunrise: Skipped. Work/School Day switch is OFF."
        addToHistory("SKIPPED: ${rName} sunrise aborted. Work/School Day switch is OFF.")
        return
    }
    
    // 4. Check Active Modes
    if (modes && !modes.contains(location.mode)) {
        log.info "${rName} Sunrise: Skipped. Hub is not in an active mode."
        return
    }
    
    addToHistory("SUNRISE: ${rName} simulation started.")
    
    def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
    if (stateSwitch) stateSwitch.on()
    
    state["r${rNum}_isFading"] = true
    state["r${rNum}_isSnoozing"] = false
    state["r${rNum}_startMs"] = new Date().time
    state["r${rNum}_currentPct"] = 1
    state["r${rNum}_currentTemp"] = 2500
    
    def lights = settings["r${rNum}_lights"]
    def speakers = settings["r${rNum}_speaker"]
    
    lights.each { light ->
        light.on()
        light.setLevel(1)
        if (light.hasCommand("setColorTemperature")) {
            light.setColorTemperature(2500)
        }
    }
    
    if (speakers) {
        speakers.each { speaker ->
            speaker.setVolume(0)
            speaker.play()
        }
    }
    
    runIn(60, "room${rNum}FadeLoop")
}

def fadeLoopProcess(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    if (!state["r${rNum}_isFading"] || state["r${rNum}_isSnoozing"]) return
    
    def lights = settings["r${rNum}_lights"]
    def speakers = settings["r${rNum}_speaker"]
    def expectedLevel = state["r${rNum}_currentPct"] ?: 1
    
    if (state["r${rNum}_justResumed"]) {
        state["r${rNum}_justResumed"] = false
    } else {
        // FIX: Added a grace period. Only check for manual override if we are past the 2% mark.
        // This gives slow-reporting devices (like Hue bridges) a few minutes to sync status.
        if (expectedLevel > 2) {
            def actualLevel = lights[0].currentValue("level")?.toInteger() ?: expectedLevel
            // Increased tolerance from 10 to 15 to account for minor rounding differences
            if (actualLevel > expectedLevel + 15 || actualLevel < expectedLevel - 15) {
                addToHistory("MANUAL OVERRIDE: ${rName} lights were manually adjusted (Expected: ${expectedLevel}%, Hub saw: ${actualLevel}%). Aborting fade.")
                state["r${rNum}_isFading"] = false
                def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
                if (stateSwitch) stateSwitch.off()
                return
            }
        }
    }
    
    def durationMins = settings["r${rNum}_duration"] ?: 30
    def startMs = state["r${rNum}_startMs"] ?: new Date().time
    def maxLevel = settings["r${rNum}_maxLevel"] ?: 100
    
    def elapsedMs = new Date().time - startMs
    def elapsedMins = elapsedMs / 60000
    
    def progress = elapsedMins / durationMins
    if (progress >= 1.0) progress = 1.0
    
    def newLevel = (1 + (progress * (maxLevel - 1))).toInteger()
    state["r${rNum}_currentPct"] = newLevel
    
    def newTemp = 2500
    if (settings["r${rNum}_useVarTemp"] && settings["r${rNum}_varTempName"]) {
        def hubVar = getGlobalVar(settings["r${rNum}_varTempName"])
        newTemp = hubVar ? hubVar.value.toInteger() : 2500
    } else {
        def maxTemp = settings["r${rNum}_maxTemp"] ?: 6500
        newTemp = (2500 + (progress * (maxTemp - 2500))).toInteger()
    }
    state["r${rNum}_currentTemp"] = newTemp
    
    lights.each { light ->
        light.setLevel(newLevel)
        if (light.hasCommand("setColorTemperature")) {
            light.setColorTemperature(newTemp)
        }
    }
    
    if (speakers) {
        def maxVol = settings["r${rNum}_maxVol"] ?: 40
        def newVol = (progress * maxVol).toInteger()
        if (newVol < 1 && progress > 0) newVol = 1
        speakers.each { it.setVolume(newVol) }
    }
    
    if (progress < 1.0) {
        runIn(60, "room${rNum}FadeLoop")
    } else {
        state["r${rNum}_isFading"] = false
        addToHistory("SUNRISE: ${rName} complete. Reached ${newLevel}% at ${newTemp}K.")
        
        def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
        if (stateSwitch) stateSwitch.off()
        
        def notifier = settings["r${rNum}_notifier"]
        def wakeMsg = settings["r${rNum}_wakeMsg"] ?: "Time to wake up!"
        if (notifier) {
            addToHistory("NOTIFICATION: Sending wake alert for ${rName}.")
            notifier*.deviceNotification(wakeMsg)
        }
        
        def handoffSwitch = settings["r${rNum}_endSwitch"]
        if (handoffSwitch) {
            addToHistory("ROUTINE HANDOFF: Executing ${rName} end switch.")
            handoffSwitch.on()
        }
    }
}

def handleSnooze(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    if (!state["r${rNum}_isFading"]) return
    if (state["r${rNum}_isSnoozing"]) return
    
    state["r${rNum}_isSnoozing"] = true
    def snoozeMins = settings["r${rNum}_snoozeMins"] ?: 9
    
    unschedule("room${rNum}FadeLoop")
    
    def lights = settings["r${rNum}_lights"]
    lights?.each { it.setLevel(1) }
    
    def speakers = settings["r${rNum}_speaker"]
    speakers?.each { it.setVolume(0) }
    
    addToHistory("SNOOZE: ${rName} snoozed for ${snoozeMins} minutes.")
    
    runIn(snoozeMins * 60, "room${rNum}ResumeFade")
}

def resumeFade(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    state["r${rNum}_isSnoozing"] = false
    state["r${rNum}_justResumed"] = true
    
    def snoozeMins = settings["r${rNum}_snoozeMins"] ?: 9
    state["r${rNum}_startMs"] = state["r${rNum}_startMs"] + (snoozeMins * 60000)
    
    addToHistory("RESUME: ${rName} snooze ended. Resuming fade.")
    fadeLoopProcess(rNum)
}

def handleSwitchOff(rNum) {
    def rName = settings["r${rNum}_name"] ?: "Room ${rNum}"
    
    addToHistory("GOOD NIGHT OFF: ${rName} switch turned off. Halting logic.")
    
    def stateSwitch = settings["r${rNum}_sunriseStateSwitch"]
    if (stateSwitch) stateSwitch.off()
    
    state["r${rNum}_isFading"] = false
    state["r${rNum}_isSnoozing"] = false
    state["r${rNum}_currentPct"] = 0
    state["r${rNum}_currentTemp"] = 0
    
    unschedule("room${rNum}FadeLoop")
    unschedule("room${rNum}ResumeFade")
    
    def lights = settings["r${rNum}_lights"]
    if (lights) lights.each { it.off() }
    
    def speakers = settings["r${rNum}_speaker"]
    if (speakers) speakers.each { it.pause() }
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def tz = location.timeZone ?: TimeZone.getDefault()
    def timestamp = new Date().format("MM/dd HH:mm:ss", tz)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) {
        state.historyLog = state.historyLog.take(20)
    }
    log.info "HISTORY: [${timestamp}] ${msg}"
}
