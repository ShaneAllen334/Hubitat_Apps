/**
 * Advanced Mode Manager
 */
definition(
    name: "Advanced Mode Manager",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade Mode engine. Handles timed transitions AND instant state enforcement. Features modular UI for Mode-based Device Control and Mode-to-Mode Transitions.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Mode Manager</b>", install: true, uninstall: true) {
        
    
        section("<b>Live Mode Dashboard & History</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of your current Hubitat location mode and logs recent activity.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #8e44ad;'>" +
                      "<b>Engine Status:</b> ${statusExplanation}</div>"

            def currentMode = location.mode ?: "Unknown"
            def pendingActionStr = "<span style='color:gray;'>None (Stable)</span>"
            if (state.pendingTargetMode && state.pendingTargetTime) {
                def remainingMins = Math.max(0, Math.round((state.pendingTargetTime - now()) / 60000))
                pendingActionStr = "<span style='color:#e67e22;'><b>Shifting to '${state.pendingTargetMode}' in ${remainingMins} minutes</b></span>"
            } else if (state.autoAwayPending) {
                pendingActionStr = "<span style='color:#e74c3c;'><b>Auto Away Countdown Active</b></span>"
            }

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-val { text-align: left !important; padding-left: 15px !important; font-weight:bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th colspan="2">Real-Time Mode Metrics</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Current Hub Mode</td><td class="dash-val" style="color:#8e44ad; font-size: 16px;">${currentMode}</td></tr>
                    <tr><td class="dash-hl">Pending Automated Transition</td><td class="dash-val">${pendingActionStr}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
            
            input "sweepModeBtn", "button", title: "Sweep Mode (Force Immediate Enforcement)"
            if (state.pendingTargetMode || state.autoAwayPending) input "abortTransition", "button", title: "Abort Pending Transition"

            paragraph "<hr><b>Recent Action History</b>"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<div style='background-color:#f8f9fa; padding:10px; border:1px solid #ccc; font-size: 13px; font-family: monospace; max-height: 200px; overflow-y: auto;'>${historyStr}</div>"
            }
            input "clearHistory", "button", title: "Clear Action History"
        }

        // ==============================================================================
        // SECTION 1: DEVICE CONTROL PER MODE
        // ==============================================================================
        section("<b>Device Control per Mode</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Instant Enforcement:</b> Define what devices should turn on, turn off, or lock the exact moment a specific mode becomes active. <i>Click a rule below to expand it.</i></div>"
            
            input "enableColorRefresh", "bool", title: "<b>Enable 30-Minute Constant Color Refresh</b> (Re-applies colors/effects if blockers are off)", defaultValue: true, submitOnChange: true
        }
        
        for (int i = 1; i <= 8; i++) {
            def dynamicTitle = (settings["dcEnable${i}"] && settings["dcMode${i}"]) ? "<b>Device Control Rule ${i} (${settings["dcMode${i}"]})</b>" : "<b>Device Control Rule ${i}</b>"
            
            section(dynamicTitle, hideable: true, hidden: true) {
                input "dcEnable${i}", "bool", title: "<b>Enable Device Control ${i}</b>", defaultValue: false, submitOnChange: true
                if (settings["dcEnable${i}"]) {
                 
                    paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #2ecc71;'><b>State Sweep / Device Enforcement</b></div>"
                    input "dcMode${i}", "mode", title: "<b>[TRIGGER]</b> When mode becomes...", required: true, submitOnChange: true
                    input "dcSwitchesOff${i}", "capability.switch", title: "Turn OFF these switches", required: false, multiple: true
                    input "dcSwitchesOn${i}", "capability.switch", title: "Turn ON these switches", required: false, multiple: true
                    input "dcLocksLock${i}", "capability.lock", title: "Lock these doors", required: false, multiple: true
                    input "dcGarageClose${i}", "capability.garageDoorControl", title: "Close these garages", required: false, multiple: true
                    
                    // --- DELAYED ACTIONS ---
                    paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #34495e; margin-top:10px;'><b>Delayed Actions</b></div>"
                    input "dcDelayedSwitchesOn${i}", "capability.switch", title: "Turn ON these switches after a delay", required: false, multiple: true
                    input "dcDelayMins${i}", "number", title: "Delay time (minutes)", required: false, defaultValue: 5

                    // --- WEATHER FORECAST SWITCH ---
                    paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #1abc9c; margin-top:10px;'><b>Weather Integrations</b></div>"
                    input "dcWeatherSwitch${i}", "capability.switch", title: "Weather Forecast Switch (Turns ON, waits 5 minutes, then turns OFF)", required: false, multiple: true
                    input "dcWeatherDelay${i}", "number", title: "Wait this many minutes before turning Weather Switch ON", required: false, defaultValue: 0

                    // --- AUDIO NOTIFICATIONS ---
                    paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #f39c12; margin-top:10px;'><b>Audio Announcements</b></div>"
                    input "dcTtsSpeakers${i}", "capability.speechSynthesis", title: "Target Smart Speakers (Sonos, etc.)", required: false, multiple: true, submitOnChange: true
                    input "dcTtsMessage${i}", "text", title: "TTS Announcement Message", required: false, submitOnChange: true
                    input "dcTtsBlocker${i}", "capability.switch", title: "Block Announcements if this switch is ON (e.g., TV)", required: false, multiple: false
                    
                    if (settings["dcTtsSpeakers${i}"] && settings["dcTtsMessage${i}"]) {
                        input "testTts${i}", "button", title: "Test TTS Announcement (Bypasses Motion Check)"
                    }
                    
                    input "dcZoozChimes${i}", "capability.chime", title: "Target Zooz Chime Devices", required: false, multiple: true, submitOnChange: true
                    input "dcZoozSound${i}", "number", title: "Zooz Chime Sound File #", required: false, submitOnChange: true
                    
                    if (settings["dcZoozChimes${i}"] && settings["dcZoozSound${i}"] != null) {
                        input "testZooz${i}", "button", title: "Test Zooz Chime (Bypasses Motion Check)"
                    }

                    // --- INOVELLI LED CONTROL ---
                    paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #9b59b6; margin-top:10px;'><b>Inovelli LED Notifications</b></div>"
                    input "dcInovelli${i}", "capability.configuration", title: "Set Inovelli LED Notifications for these switches", required: false, multiple: true, submitOnChange: true
                    if (settings["dcInovelli${i}"]) {
                        input "dcInovelliTarget${i}", "enum", title: "Target LEDs", required: true, defaultValue: "All", options: [
                            "All":"All LEDs", "7":"LED 7 (Top)", "6":"LED 6", "5":"LED 5", "4":"LED 4 (Middle)", "3":"LED 3", "2":"LED 2", "1":"LED 1 (Bottom)"
                        ]
                        input "dcInovelliColor${i}", "enum", title: "LED Color", required: true, options: [
                            "0":"Red", "14":"Orange", "35":"Lemon", "64":"Lime", 
                            "85":"Green", "106":"Teal", "127":"Cyan", "149":"Aqua", 
                            "170":"Blue", "191":"Violet", "212":"Magenta", "234":"Pink", "255":"White"
                        ]
                        input "dcInovelliLevel${i}", "number", title: "LED Light % (0-100)", required: true, defaultValue: 100
                        input "dcInovelliEffect${i}", "enum", title: "Notification Effect", required: true, options: [
                            "0":"Off", "1":"Solid", "2":"Fast Blink", "3":"Slow Blink", "4":"Pulse", "5":"Chase", "6":"Falling", "7":"Rising", "8":"Blink"
                        ], defaultValue: "1"
                        
                        input "dcInovelliBlocker${i}", "capability.switch", title: "Block LED color changes if this switch is ON (e.g., Mail Arrived Virtual Switch)", required: false, multiple: false
                    }
                }
            }
        }

        // ==============================================================================
        // SECTION 2: MODE TO MODE TRANSITIONS
        // ==============================================================================
        section("<b>Mode to Mode Transitions</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Automated Timers:</b> Configure delayed transitions between modes. Example: When mode changes to 'Arrival', wait 15 minutes, then change to 'Home'. <i>Click a rule below to expand it.</i></div>"
        }
            
        for (int i = 1; i <= 8; i++) {
            def dynamicTitle = (settings["transEnable${i}"] && settings["transTriggerMode${i}"] && settings["transTargetMode${i}"]) ? "<b>Transition Rule ${i} (${settings["transTriggerMode${i}"]} ➔ ${settings["transTargetMode${i}"]})</b>" : "<b>Transition Rule ${i}</b>"
        
            section(dynamicTitle, hideable: true, hidden: true) {
                input "transEnable${i}", "bool", title: "<b>Enable Transition Rule ${i}</b>", defaultValue: false, submitOnChange: true
                if (settings["transEnable${i}"]) {
                    input "transTriggerMode${i}", "mode", title: "<b>[TRIGGER]</b> If mode becomes...", required: true, submitOnChange: true
                    input "transDelay${i}", "number", title: "<b>[TIMER]</b> Wait this many minutes", required: true, defaultValue: 15
                    input "transTargetMode${i}", "mode", title: "<b>[TRANSITION]</b> Then change mode to...", required: true, submitOnChange: true
                    
                    // Modular Toggle: Presence Tethering
                    input "transUseTether${i}", "bool", title: "Enable Presence Tethering?", defaultValue: false, submitOnChange: true
                    if (settings["transUseTether${i}"]) {
                        paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #3498db;'><b>Presence Tethering</b> (Timer Intercept)</div>"
                        input "transTetherPresence${i}", "capability.presenceSensor", title: "Tether to Presence Sensor", required: true
                        input "transTetherFallbackMode${i}", "mode", title: "If sensor departs, force mode to...", required: true
                    }

                    // Modular Toggle: Condition Gates
                    input "transUseGates${i}", "bool", title: "Enable Condition Gates?", defaultValue: false, submitOnChange: true
                    if (settings["transUseGates${i}"]) {
                        paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #e67e22;'><b>Condition Gates</b> (Pre-Transition Check)</div>"
                        input "transConditionMotion${i}", "capability.motionSensor", title: "Abort if Motion active", required: false, multiple: true
                        input "transConditionPower${i}", "capability.powerMeter", title: "Abort if Power > Threshold", required: false
                        if (settings["transConditionPower${i}"]) input "transPowerThreshold${i}", "decimal", title: "Watts", defaultValue: 15.0
                    }
                }
            }
        }

        // ==============================================================================
        // SECTION 3: GLOBAL AUDIO ROOM MAPPING
        // ==============================================================================
        section("<b>Global Audio Room Mapping</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>1-to-1 Motion Filtering:</b> Map your speakers to motion sensors here. When a Mode Rule attempts to play audio on a speaker, it will automatically intercept the command and check if that specific speaker's room has recent motion. (Speakers not mapped here will play unconditionally).</div>"
            
            input "audioMotionTimeout", "number", title: "Audio Motion Timeout (Minutes)", defaultValue: 5, description: "Time to wait after motion stops before muting announcements (prevents muting if someone is sitting still)."
            input "alwaysOnRoom", "enum", title: "Select ONE room to ALWAYS announce (Ignores motion)", options: ["1": "Room 1", "2": "Room 2", "3": "Room 3", "4": "Room 4", "5": "Room 5", "6": "Room 6", "7": "Room 7"], required: false
            
            input "room1Speaker", "capability.actuator", title: "Room 1 Speaker(s) (TTS/Zooz)", required: false, multiple: true
            input "room1Motion", "capability.motionSensor", title: "Room 1 Motion Sensor(s)", required: false, multiple: true
            
            input "room2Speaker", "capability.actuator", title: "Room 2 Speaker(s)", required: false, multiple: true
            input "room2Motion", "capability.motionSensor", title: "Room 2 Motion Sensor(s)", required: false, multiple: true
            
            input "room3Speaker", "capability.actuator", title: "Room 3 Speaker(s)", required: false, multiple: true
            input "room3Motion", "capability.motionSensor", title: "Room 3 Motion Sensor(s)", required: false, multiple: true
            
            input "room4Speaker", "capability.actuator", title: "Room 4 Speaker(s)", required: false, multiple: true
            input "room4Motion", "capability.motionSensor", title: "Room 4 Motion Sensor(s)", required: false, multiple: true
            
            input "room5Speaker", "capability.actuator", title: "Room 5 Speaker(s)", required: false, multiple: true
            input "room5Motion", "capability.motionSensor", title: "Room 5 Motion Sensor(s)", required: false, multiple: true
            
            input "room6Speaker", "capability.actuator", title: "Room 6 Speaker(s)", required: false, multiple: true
            input "room6Motion", "capability.motionSensor", title: "Room 6 Motion Sensor(s)", required: false, multiple: true
            
            input "room7Speaker", "capability.actuator", title: "Room 7 Speaker(s)", required: false, multiple: true
            input "room7Motion", "capability.motionSensor", title: "Room 7 Motion Sensor(s)", required: false, multiple: true
        }

        // ==============================================================================
        // SECTION 4: INOVELLI SCHEDULED DIMMING
        // ==============================================================================
        section("<b>Scheduled Inovelli LED Dimming</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Daily Timers:</b> Automatically adjust the default brightness of your Inovelli LED light bars at specific times (e.g., dim at night, brighten in morning).</div>"
            
            input "ledDimEnable", "bool", title: "<b>Enable Scheduled Dimming</b>", defaultValue: false, submitOnChange: true
            
            if (settings["ledDimEnable"]) {
                input "ledDimSwitches", "capability.configuration", title: "Target Inovelli Switches", required: true, multiple: true
                
                paragraph "<b>Timer 1 (e.g., Sunset/Dim)</b>"
                input "ledTime1", "time", title: "Time", required: true
                input "ledLevel1", "number", title: "LED Light % (0-100)", required: true, defaultValue: 50
                 
                paragraph "<b>Timer 2 (e.g., Sunrise/Bright)</b>"
                input "ledTime2", "time", title: "Time", required: true
                input "ledLevel2", "number", title: "LED Light % (0-100)", required: true, defaultValue: 100
            }
        }
        
        // ==============================================================================
        // SECTION 5: BUTTON CONTROLLER MODE MAPPING
        // ==============================================================================
        section("<b>Button Controller Mode Mapping</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Physical Override:</b> Change modes instantly using a physical button or remote. You can restrict these button commands to only work if the hub is currently in a specific mode. <i>Click a rule below to expand it.</i></div>"
        }
        
        for (int i = 1; i <= 8; i++) {
            def dynamicTitle = (settings["btnEnable${i}"] && settings["btnDevice${i}"]) ? "<b>Button Rule ${i} (${settings["btnDevice${i}"]} Btn ${settings["btnNum${i}"]})</b>" : "<b>Button Rule ${i}</b>"
            
            section(dynamicTitle, hideable: true, hidden: true) {
                input "btnEnable${i}", "bool", title: "<b>Enable Button Rule ${i}</b>", defaultValue: false, submitOnChange: true
                if (settings["btnEnable${i}"]) {
                    input "btnDevice${i}", "capability.pushableButton", title: "Button Device", required: true, submitOnChange: true
                    input "btnNum${i}", "number", title: "Button Number", required: true, defaultValue: 1
                    input "btnAction${i}", "enum", title: "Button Action", required: true, defaultValue: "pushed", options: [
                        "pushed": "Pushed", 
                        "held": "Held", 
                        "doubleTapped": "Double Tapped", 
                        "released": "Released"
                    ]
                    
                    paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #c0392b;'><b>Conditions & Transitions</b></div>"
                    input "btnCondMode${i}", "mode", title: "<b>[CONDITION]</b> ONLY execute if current mode is...", required: false, multiple: true, description: "Leave blank to allow any mode"
                    input "btnTargetMode${i}", "mode", title: "<b>[TRANSITION]</b> Then change mode to...", required: true
                }
            }
        }

        // ==============================================================================
        // SECTION 6: AUTO AWAY
        // ==============================================================================
        section("<b>Auto Away</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Smart Departure:</b> Automatically transition to an Away mode when everyone leaves, no motion is detected for a set time, and Guest Mode is disabled.</div>"
            
            input "autoAwayEnable", "bool", title: "<b>Enable Auto Away Logic</b>", defaultValue: false, submitOnChange: true
            
            if (settings["autoAwayEnable"]) {
                paragraph "<div style='background-color:#f4f6f9; padding:8px; border-left:3px solid #e74c3c;'><b>Away Conditions</b></div>"
                input "aaAllowedModes", "mode", title: "<b>[CONDITION]</b> Only execute if current mode is...", required: true, multiple: true
                input "aaTargetMode", "mode", title: "<b>[TRANSITION]</b> Change Mode To...", required: true
                
                input "aaPresenceSensors", "capability.presenceSensor", title: "Arrival Sensors (All must be departed)", required: true, multiple: true
                input "aaGuestSwitch", "capability.switch", title: "Guest Virtual Switch (Must be OFF)", required: false, multiple: false
                
                input "aaMotionSensors", "capability.motionSensor", title: "Motion Sensors to Monitor", required: true, multiple: true
                input "aaQuietTime", "number", title: "Quiet Time (Minutes of no motion before triggering)", required: true, defaultValue: 15
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    clearPendingTransition()
    state.autoAwayPending = false
    
    // Subscribe to Mode Changes
    subscribe(location, "mode", "modeChangeHandler")
    
    // Subscribe to Auto Away Sensors
    if (settings["autoAwayEnable"]) {
        subscribe(settings["aaPresenceSensors"], "presence", "autoAwayEvalHandler")
        subscribe(settings["aaMotionSensors"], "motion", "autoAwayEvalHandler")
        if (settings["aaGuestSwitch"]) {
            subscribe(settings["aaGuestSwitch"], "switch", "autoAwayEvalHandler")
        }
    }
    
    // Subscribe to specific room motion sensors for Audio Logic
    if (settings.room1Motion) subscribe(settings.room1Motion, "motion.active", "room1MotionHandler")
    if (settings.room2Motion) subscribe(settings.room2Motion, "motion.active", "room2MotionHandler")
    if (settings.room3Motion) subscribe(settings.room3Motion, "motion.active", "room3MotionHandler")
    if (settings.room4Motion) subscribe(settings.room4Motion, "motion.active", "room4MotionHandler")
    if (settings.room5Motion) subscribe(settings.room5Motion, "motion.active", "room5MotionHandler")
    if (settings.room6Motion) subscribe(settings.room6Motion, "motion.active", "room6MotionHandler")
    if (settings.room7Motion) subscribe(settings.room7Motion, "motion.active", "room7MotionHandler")
    
    // Schedule Inovelli LED Timers
    if (settings["ledDimEnable"] && settings["ledTime1"]) {
        schedule(settings["ledTime1"], "executeLedTimer1")
    }
    if (settings["ledDimEnable"] && settings["ledTime2"]) {
        schedule(settings["ledTime2"], "executeLedTimer2")
    }
    
    // Schedule 30-Minute Refresh for Inovelli Colors
    if (settings["enableColorRefresh"] != false) {
        runEvery30Minutes("refreshInovelliColor")
    } else {
        unschedule("refreshInovelliColor")
    }
   
    for (int i = 1; i <= 8; i++) {
        // Subscribe to Presence Tethers
        if (settings["transEnable${i}"] && settings["transUseTether${i}"] && settings["transTetherPresence${i}"]) {
            subscribe(settings["transTetherPresence${i}"], "presence", "presenceTetherHandler")
        }
        
        // Subscribe to LED Blocker Switches
        if (settings["dcEnable${i}"] && settings["dcInovelliBlocker${i}"]) {
            subscribe(settings["dcInovelliBlocker${i}"], "switch.off", "blockerSwitchHandler")
        }
        
        // Subscribe to Inovelli switches turning off
        if (settings["dcEnable${i}"] && settings["dcInovelli${i}"]) {
            subscribe(settings["dcInovelli${i}"], "switch.off", "inovelliSwitchOffHandler")
        }
        
        // Subscribe to Button Controllers
        if (settings["btnEnable${i}"] && settings["btnDevice${i}"] && settings["btnAction${i}"]) {
            subscribe(settings["btnDevice${i}"], settings["btnAction${i}"], "buttonHandler")
        }
    }
    
    logAction("Mode Manager Initialized.")
}

String getHumanReadableStatus() {
    if (state.autoAwayPending) return "Auto Away conditions met. Countdown to Away active."
    if (state.pendingTargetMode) return "Countdown Active. Monitoring tethers and waiting to transition."
    return "Idle. Waiting for a trigger mode to activate."
}

def appButtonHandler(btn) {
    if (btn == "abortTransition") { 
        if (state.autoAwayPending) {
            logAction("User aborted Auto Away transition.")
            unschedule("executeAutoAway")
            state.autoAwayPending = false
        }
        if (state.pendingTargetMode) {
            logAction("User aborted transition to '${state.pendingTargetMode}'.")
            clearPendingTransition() 
        }
    }
    else if (btn == "clearHistory") { 
        state.actionHistory = []
        logAction("History cleared.") 
    }
    else if (btn == "sweepModeBtn") { 
        logAction("Sweep Triggered. Enforcing rules for current mode...")
        executeSweep() 
    }
    else if (btn.startsWith("testTts")) {
        def idx = btn.replaceAll("\\D+", "").toInteger()
        def speakers = settings["dcTtsSpeakers${idx}"]
        def msg = settings["dcTtsMessage${idx}"]
        if (speakers && msg) {
            playTts(speakers, msg, true)
            logAction("Tested TTS for Rule ${idx}: ${msg} (Motion Check Bypassed)")
        }
    }
    else if (btn.startsWith("testZooz")) {
        def idx = btn.replaceAll("\\D+", "").toInteger()
        def chimes = settings["dcZoozChimes${idx}"]
        def sound = settings["dcZoozSound${idx}"]
        if (chimes && sound != null) {
            playZoozSound(chimes, sound, true)
            logAction("Tested Zooz Chime for Rule ${idx}: Sound ${sound} (Motion Check Bypassed)")
        }
    }
}

// ------------------------------------------------------------------------------
// HANDLERS
// ------------------------------------------------------------------------------

def buttonHandler(evt) {
    def devId = evt.device.id
    def action = evt.name
    def btnNum = evt.value.toString()
    def currentMode = location.mode

    for (int i = 1; i <= 8; i++) {
        if (settings["btnEnable${i}"] && settings["btnDevice${i}"]?.id == devId) {
            if (settings["btnAction${i}"] == action && settings["btnNum${i}"].toString() == btnNum) {
                
                def conditionModes = settings["btnCondMode${i}"]
                def targetMode = settings["btnTargetMode${i}"]
                
                if (!conditionModes || conditionModes.contains(currentMode)) {
                    logAction("🔘 Button Rule ${i} Match: ${evt.device.displayName} (Button ${btnNum} ${action}). Changing mode to '${targetMode}'.")
                    
                    if (state.pendingTargetMode != null) clearPendingTransition()
                    
                    location.setMode(targetMode)
                } else {
                    logAction("🔘 Button Rule ${i} Skipped: Current mode '${currentMode}' does not match required conditions.")
                }
            }
        }
    }
}

def presenceTetherHandler(evt) {
    if (state.pendingRuleNumber == null || evt.value != "not present") return
    def ruleIdx = state.pendingRuleNumber
    if (settings["transUseTether${ruleIdx}"] && settings["transTetherPresence${ruleIdx}"]?.id == evt.device.id) {
        def fallback = settings["transTetherFallbackMode${ruleIdx}"]
        logAction("🚨 Tether Broken: ${evt.device.displayName} left. Forcing mode to '${fallback}'.")
        clearPendingTransition()
        location.setMode(fallback)
    }
}

def blockerSwitchHandler(evt) {
    if (evt.value == "off") {
        logAction("LED Blocker Switch '${evt.device.displayName}' turned off. Re-evaluating LED colors for current mode...")
        def currentMode = location.mode
        
        for (int i = 1; i <= 8; i++) {
            if (settings["dcEnable${i}"] && settings["dcMode${i}"] == currentMode) {
                if (settings["dcInovelli${i}"] && settings["dcInovelliColor${i}"] != null) {
                    
                    def ruleBlocker = settings["dcInovelliBlocker${i}"]
                    if (!ruleBlocker || ruleBlocker.currentValue("switch") != "on") {
                        enforceInovelliLEDs(i)
                        
                        def colorMap = ["0":"Red", "14":"Orange", "35":"Lemon", "64":"Lime", "85":"Green", "106":"Teal", "127":"Cyan", "149":"Aqua", "170":"Blue", "191":"Violet", "212":"Magenta", "234":"Pink", "255":"White"]
                        def colorName = colorMap[settings["dcInovelliColor${i}"]] ?: settings["dcInovelliColor${i}"]
                        def levelStr = settings["dcInovelliLevel${i}"] ?: 100
                        def effectStr = settings["dcInovelliEffect${i}"] ?: "1"
                        def targetStr = settings["dcInovelliTarget${i}"] == "All" ? "All LEDs" : "LED ${settings["dcInovelliTarget${i}"]}"
                        logAction("Deferred LED Update -> [Inovelli: ${targetStr} ${colorName} at ${levelStr}% (Effect: ${effectStr})]")
                    }
                }
            }
        }
    }
}

def inovelliSwitchOffHandler(evt) {
    def currentMode = location.mode
    for (int i = 1; i <= 8; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == currentMode) {
            if (settings["dcInovelli${i}"]?.find { it.id == evt.device.id }) {
                def blocker = settings["dcInovelliBlocker${i}"]
                if (!blocker || blocker.currentValue("switch") != "on") {
                    logAction("Switch '${evt.device.displayName}' turned off. Re-applying Mode LED settings.")
                    enforceInovelliLEDs(i)
                }
            }
        }
    }
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    
    if (state.pendingTargetMode != null) {
        logAction("Intervention: Mode changed to '${newMode}'. Aborting timer for '${state.pendingTargetMode}'.")
        clearPendingTransition()
    }
    
    // Evaluate Auto Away on Mode Change (Ensures we check if entering an allowed mode)
    if (settings["autoAwayEnable"]) checkAutoAwayConditions()
    
    // 1. Process Instant Device Controls
    for (int i = 1; i <= 8; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == newMode) {
            logAction("Device Control ${i}: Instant Enforcement triggered for mode '${newMode}'.")
            enforceDevices(i)
        }
    }

    // 2. Process Transition Timers
    for (int i = 1; i <= 8; i++) {
        if (settings["transEnable${i}"] && settings["transTriggerMode${i}"] == newMode) {
            def delayMins = settings["transDelay${i}"] ?: 0
            
            if (delayMins > 0) {
                def target = settings["transTargetMode${i}"]
                state.pendingTriggerMode = newMode
                state.pendingTargetMode = target
                state.pendingTargetTime = now() + (delayMins * 60000)
                state.pendingRuleNumber = i
                logAction("Transition Rule ${i}: Delay active. Shifting '${newMode}' to '${target}' in ${delayMins} mins.")
                runIn((delayMins * 60).toInteger(), "executeTransition")
                break 
            }
        }
    }
}

// ------------------------------------------------------------------------------
// AUTO AWAY ENGINE
// ------------------------------------------------------------------------------

def autoAwayEvalHandler(evt) {
    checkAutoAwayConditions()
}

def checkAutoAwayConditions() {
    if (!settings["autoAwayEnable"]) return

    def currentMode = location.mode
    def allowed = settings["aaAllowedModes"]?.contains(currentMode)
    
    // Guest Switch OFF Check (if undefined, treat as OFF)
    def guestOff = !settings["aaGuestSwitch"] || settings["aaGuestSwitch"].currentValue("switch") == "off"
    
    // Presence check (Every sensor must NOT be present)
    def allGone = settings["aaPresenceSensors"]?.every { it.currentValue("presence") != "present" }
    
    // Motion check (Every sensor must NOT be active)
    def allQuiet = settings["aaMotionSensors"]?.every { it.currentValue("motion") != "active" }

    if (allowed && guestOff && allGone && allQuiet) {
        if (!state.autoAwayPending) {
            def delayMins = settings["aaQuietTime"] ?: 15
            logAction("🚗 Auto Away conditions met. Initiating ${delayMins}-minute countdown to '${settings.aaTargetMode}'.")
            state.autoAwayPending = true
            // Setting overwrite to true handles consecutive quiet moments correctly
            runIn(delayMins * 60, "executeAutoAway", [overwrite: true])
        }
    } else {
        if (state.autoAwayPending) {
            logAction("🛑 Auto Away Interrupted: Conditions no longer met. Countdown aborted.")
            unschedule("executeAutoAway")
            state.autoAwayPending = false
        }
    }
}

def executeAutoAway() {
    // Final verification to ensure conditions haven't changed in the exact millisecond of execution
    def allowed = settings["aaAllowedModes"]?.contains(location.mode)
    def guestOff = !settings["aaGuestSwitch"] || settings["aaGuestSwitch"].currentValue("switch") == "off"
    def allGone = settings["aaPresenceSensors"]?.every { it.currentValue("presence") != "present" }
    def allQuiet = settings["aaMotionSensors"]?.every { it.currentValue("motion") != "active" }

    if (allowed && guestOff && allGone && allQuiet) {
        def target = settings["aaTargetMode"]
        logAction("⏱️ Auto Away countdown complete! Transitioning mode to '${target}'.")
        state.autoAwayPending = false
        location.setMode(target)
    } else {
        logAction("⚠️ Auto Away countdown complete, but conditions changed at the last second. Aborting.")
        state.autoAwayPending = false
    }
}

// ------------------------------------------------------------------------------
// AUDIO & 1-TO-1 MOTION HELPER ENGINE
// ------------------------------------------------------------------------------

def room1MotionHandler(evt) { state.lastMotionRoom1 = now() }
def room2MotionHandler(evt) { state.lastMotionRoom2 = now() }
def room3MotionHandler(evt) { state.lastMotionRoom3 = now() }
def room4MotionHandler(evt) { state.lastMotionRoom4 = now() }
def room5MotionHandler(evt) { state.lastMotionRoom5 = now() }
def room6MotionHandler(evt) { state.lastMotionRoom6 = now() }
def room7MotionHandler(evt) { state.lastMotionRoom7 = now() }

def isSpeakerMotionActive(speaker) {
    boolean isMapped = false
    boolean hasMotion = false
    
    for (int i = 1; i <= 7; i++) {
        def mappedSpeaker = settings["room${i}Speaker"]
        if (mappedSpeaker) {
            def mappedList = mappedSpeaker instanceof List ? mappedSpeaker : [mappedSpeaker]
            if (mappedList.any { it.id == speaker.id }) {
                isMapped = true
                
                if (settings.alwaysOnRoom && settings.alwaysOnRoom.toString() == i.toString()) {
                    hasMotion = true
                }
                
                def motion = settings["room${i}Motion"]
                if (!motion) {
                    hasMotion = true 
                } else {
                    def mList = motion instanceof List ? motion : [motion]
                    if (mList.any { it.currentValue("motion") == "active" }) {
                        state."lastMotionRoom${i}" = now()
                        hasMotion = true
                    } else {
                        def lastTime = state."lastMotionRoom${i}"
                        if (lastTime) {
                            long timeoutMillis = (settings.audioMotionTimeout ?: 5) * 60 * 1000
                            if ((now() - lastTime) <= timeoutMillis) {
                                hasMotion = true
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (!isMapped) return true 
    return hasMotion
}

def playTts(speakers, msg, force = false) {
    if (!speakers || !msg) return
    def devList = speakers instanceof List ? speakers : [speakers]
    
    devList.each { dev ->
        if (force || isSpeakerMotionActive(dev)) {
            try {
                dev.speak(msg)
            } catch (e) {
                log.error "Failed to play TTS on ${dev.displayName}: ${e}"
            }
        } else {
            logAction("Skipping TTS on ${dev.displayName}: No recent motion.")
        }
    }
}

def playZoozSound(devices, sound, force = false) {
    if (!devices || sound == null) return
    def soundInt = sound.toString().isNumber() ? sound.toString().toInteger() : null
    
    def devList = devices instanceof List ? devices : [devices]
    
    devList.eachWithIndex { dev, index ->
        if (force || isSpeakerMotionActive(dev)) {
            if (index > 0) {
                pauseExecution(1000)
            }
            try {
                if (dev.hasCommand("playSound") && soundInt != null) {
                    dev.playSound(soundInt)
                } else if (dev.hasCommand("playTrack")) {
                    dev.playTrack(sound.toString())
                } else if (dev.hasCommand("chime") && soundInt != null) {
                    dev.chime(soundInt)
                } else {
                    log.warn "Advanced Mode Manager: Device ${dev.displayName} does not support standard sound commands."
                }
            } catch (e) {
                log.error "Failed to play Zooz audio on ${dev.displayName}: ${e}"
            }
        } else {
            logAction("Skipping Zooz Chime on ${dev.displayName}: No recent motion.")
        }
    }
}

// ------------------------------------------------------------------------------
// EXECUTION LOGIC
// ------------------------------------------------------------------------------

def executeSweep() {
    def currentMode = location.mode
    def matchFound = false
    
    for (int i = 1; i <= 8; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == currentMode) {
            logAction("Sweep Match (Device Control ${i}): Executing enforcement for '${currentMode}'.")
            enforceDevices(i)
            matchFound = true
        }
    }
    if (!matchFound) logAction("Sweep: No Device Control rules found for mode '${currentMode}'.")
}

def executeTransition() {
    def ruleIdx = state.pendingRuleNumber
    if (location.mode == state.pendingTriggerMode && ruleIdx != null) {
        
        if (settings["transUseGates${ruleIdx}"]) {
            if (settings["transConditionMotion${ruleIdx}"]?.any { it.currentValue("motion") == "active" }) {
                logAction("🛑 Aborted: Motion active.")
                clearPendingTransition()
                return
            }
            def pSens = settings["transConditionPower${ruleIdx}"]
            if (pSens && (pSens.currentValue("power") ?: 0) > (settings["transPowerThreshold${ruleIdx}"] ?: 15)) {
                logAction("🛑 Aborted: Power threshold exceeded.")
                clearPendingTransition()
                return
            }
        }
        
        def target = state.pendingTargetMode
        logAction("Transitioning '${location.mode}' to '${target}'.")
        clearPendingTransition()
        location.setMode(target) 
        
    } else {
        logAction("Failsafe: Mode mismatch. Aborting.")
        clearPendingTransition()
    }
}

def enforceDevices(ruleIdx) {
    def logMsg = "State Sweep -> "
    
    if (settings["dcSwitchesOff${ruleIdx}"]) { settings["dcSwitchesOff${ruleIdx}"].off(); logMsg += "[OFF] " }
    if (settings["dcSwitchesOn${ruleIdx}"]) { settings["dcSwitchesOn${ruleIdx}"].on(); logMsg += "[ON] " }
    if (settings["dcLocksLock${ruleIdx}"]) { settings["dcLocksLock${ruleIdx}"].lock(); logMsg += "[Locked] " }
    if (settings["dcGarageClose${ruleIdx}"]) { settings["dcGarageClose${ruleIdx}"].close(); logMsg += "[Closed] " }
    
    if (settings["dcDelayedSwitchesOn${ruleIdx}"] && settings["dcDelayMins${ruleIdx}"] != null) {
        def delaySecs = (settings["dcDelayMins${ruleIdx}"] * 60).toInteger()
        runIn(delaySecs, "turnOnDelayedSwitches", [data: [ruleIdx: ruleIdx], overwrite: false])
        logMsg += "[Delayed ON: ${settings["dcDelayMins${ruleIdx}"]}-min timer started] "
    }

    if (settings["dcWeatherSwitch${ruleIdx}"]) { 
        def weatherDelayMins = settings["dcWeatherDelay${ruleIdx}"] ?: 0
        if (weatherDelayMins > 0) {
            def delaySecs = (weatherDelayMins * 60).toInteger()
            runIn(delaySecs, "turnOnWeatherSwitch", [data: [ruleIdx: ruleIdx], overwrite: false])
            logMsg += "[Weather Forecast: Scheduled ON in ${weatherDelayMins} min] "
        } else {
            settings["dcWeatherSwitch${ruleIdx}"].on() 
            logMsg += "[Weather Forecast Switch ON] "
            runIn(300, "turnOffWeatherSwitch", [data: [ruleIdx: ruleIdx], overwrite: false])
        }
    }
    
    def ruleTtsSpeakers = settings["dcTtsSpeakers${ruleIdx}"]
    def ttsMsg = settings["dcTtsMessage${ruleIdx}"]
    def ttsBlocker = settings["dcTtsBlocker${ruleIdx}"]
    
    def ruleZoozChimes = settings["dcZoozChimes${ruleIdx}"]
    def chimeSound = settings["dcZoozSound${ruleIdx}"]
    
    if (ttsMsg && ruleTtsSpeakers) {
        if (ttsBlocker && ttsBlocker.currentValue("switch") == "on") {
            logMsg += "[TTS: Blocked by '${ttsBlocker.displayName}'] "
        } else {
            playTts(ruleTtsSpeakers, ttsMsg)
            logMsg += "[TTS: Processed '${ttsMsg}'] "
        }
    }
    
    if (chimeSound != null && ruleZoozChimes) {
        runInMillis(2000, "playDelayedZoozChimes", [data: [ruleIdx: ruleIdx]])
        logMsg += "[Zooz Chime: Scheduled File ${chimeSound} (Delayed 2s)] "
    }

    if (settings["dcInovelli${ruleIdx}"] && settings["dcInovelliColor${ruleIdx}"] != null) {
        def blocker = settings["dcInovelliBlocker${ruleIdx}"]
        
        if (blocker && blocker.currentValue("switch") == "on") {
            logMsg += "[Inovelli LEDs: Blocked by '${blocker.displayName}'] "
        } else {
            enforceInovelliLEDs(ruleIdx)
            
            def colorMap = ["0":"Red", "14":"Orange", "35":"Lemon", "64":"Lime", "85":"Green", "106":"Teal", "127":"Cyan", "149":"Aqua", "170":"Blue", "191":"Violet", "212":"Magenta", "234":"Pink", "255":"White"]
            def colorName = colorMap[settings["dcInovelliColor${ruleIdx}"]] ?: settings["dcInovelliColor${ruleIdx}"]
            def level = settings["dcInovelliLevel${ruleIdx}"] ?: 100
            def effect = settings["dcInovelliEffect${ruleIdx}"] ?: "1"
            def targetStr = settings["dcInovelliTarget${ruleIdx}"] == "All" ? "All LEDs" : "LED ${settings["dcInovelliTarget${ruleIdx}"]}"
            
            logMsg += "[Inovelli: ${targetStr} ${colorName} at ${level}% (Effect: ${effect})] "
        }
    }
    
    if (logMsg != "State Sweep -> ") logAction(logMsg)
}

def enforceInovelliLEDs(ruleIdx) {
    def rawColor = settings["dcInovelliColor${ruleIdx}"]
    def rawLevel = settings["dcInovelliLevel${ruleIdx}"]
    def rawEffect = settings["dcInovelliEffect${ruleIdx}"]
   
    def colorVal = rawColor != null ? rawColor.toInteger() : 170
    def levelVal = rawLevel != null ? rawLevel.toInteger() : 100
    def effectVal = rawEffect != null ? rawEffect.toInteger() : 1
    def targetVal = settings["dcInovelliTarget${ruleIdx}"] ?: "All"
    def durationVal = 255 
    
    settings["dcInovelli${ruleIdx}"].each { dev ->
        dev.setParameter(95, colorVal) 
        dev.setParameter(96, colorVal) 
    
        dev.setParameter(97, levelVal) 
        dev.setParameter(98, levelVal) 
        
        if (targetVal == "All") {
            if (dev.hasCommand("ledEffectAll")) {
                dev.ledEffectAll(effectVal, colorVal, levelVal, durationVal)
            }
        } else {
            if (dev.hasCommand("ledEffectOne")) {
                dev.ledEffectOne(targetVal, effectVal, colorVal, levelVal, durationVal)
            }
        }
    }
}

def refreshInovelliColor() {
    def currentMode = location.mode
    def refreshCount = 0
    for (int i = 1; i <= 8; i++) {
        if (settings["dcEnable${i}"] && settings["dcMode${i}"] == currentMode) {
            if (settings["dcInovelli${i}"] && settings["dcInovelliColor${i}"] != null) {
                
                def blocker = settings["dcInovelliBlocker${i}"]
                
                if (!blocker || blocker.currentValue("switch") != "on") {
                    enforceInovelliLEDs(i)
                    refreshCount++
                }
            }
        }
    }
    if (refreshCount > 0) {
        logAction("30-Min Refresh: Reissued LED settings for current mode '${currentMode}'.")
    }
}

// ------------------------------------------------------------------------------
// SCHEDULED LED TIMERS
// ------------------------------------------------------------------------------

def executeLedTimer1() {
    if (!settings["ledDimEnable"] || !settings["ledDimSwitches"]) return
    def level = settings["ledLevel1"] != null ? settings["ledLevel1"].toInteger() : 50
    logAction("Scheduled LED Timer 1 triggered. Setting Inovelli LEDs to ${level}%.")
    settings["ledDimSwitches"].each { dev ->
        dev.setParameter(97, level) 
        dev.setParameter(98, level) 
    }
}

def executeLedTimer2() {
    if (!settings["ledDimEnable"] || !settings["ledDimSwitches"]) return
    def level = settings["ledLevel2"] != null ? settings["ledLevel2"].toInteger() : 100
    logAction("Scheduled LED Timer 2 triggered. Setting Inovelli LEDs to ${level}%.")
    settings["ledDimSwitches"].each { dev ->
        dev.setParameter(97, level) 
        dev.setParameter(98, level) 
    }
}

// ------------------------------------------------------------------------------
// UTILITY FUNCTIONS
// ------------------------------------------------------------------------------

def turnOnDelayedSwitches(data) {
    def ruleIdx = data?.ruleIdx
    if (ruleIdx && settings["dcDelayedSwitchesOn${ruleIdx}"]) {
        settings["dcDelayedSwitchesOn${ruleIdx}"].on()
        logAction("Delayed Switches for Rule ${ruleIdx} turned ON after scheduled delay.")
    }
}

def turnOnWeatherSwitch(data) {
    def ruleIdx = data?.ruleIdx
    if (ruleIdx && settings["dcWeatherSwitch${ruleIdx}"]) {
        settings["dcWeatherSwitch${ruleIdx}"].on()
        logAction("Weather Forecast Switch for Rule ${ruleIdx} turned ON after delay. Scheduling 5-minute auto-off.")
        runIn(300, "turnOffWeatherSwitch", [data: [ruleIdx: ruleIdx], overwrite: false])
    }
}

def turnOffWeatherSwitch(data) {
    def ruleIdx = data?.ruleIdx
    if (ruleIdx && settings["dcWeatherSwitch${ruleIdx}"]) {
        settings["dcWeatherSwitch${ruleIdx}"].off()
        logAction("Weather Forecast Switch for Rule ${ruleIdx} turned OFF automatically after 5 minutes.")
    }
}

def playDelayedZoozChimes(data) {
    def ruleIdx = data?.ruleIdx
    if (ruleIdx && settings["dcZoozChimes${ruleIdx}"]) {
        def sound = settings["dcZoozSound${ruleIdx}"]
        playZoozSound(settings["dcZoozChimes${ruleIdx}"], sound)
    }
}

def clearPendingTransition() {
    unschedule("executeTransition")
    state.pendingTargetMode = null
    state.pendingTriggerMode = null
    state.pendingTargetTime = null
    state.pendingRuleNumber = null
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if (h.size() > 30) h = h[0..29]
    state.actionHistory = h
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
