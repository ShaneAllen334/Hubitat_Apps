/**
 * Advanced Trash Day Reminder
 *
 * Author: ShaneAllen
 *
 * Version 1.5
 */
definition(
    name: "Advanced Trash Day Reminder",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced trash day scheduling with predictive AI, omni-axis spatial tracking, Utility Financial Auditing, and Bi-Weekly Recycling logic.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<div style='color:#1a1a1a; font-size:22px; font-weight:700; border-bottom:2px solid #3498db; padding-bottom:8px; margin-bottom:12px;'>📊 Advanced Trash Operations Center</div>", install: true, uninstall: true) {
        
        section() {
            paragraph "<div style='font-size:12px; color:#7f8c8d; font-weight:bold; margin-top:-10px;'>App Version: 1.5.0</div>"
        }

        section("<div style='background-color:#2c3e50; color:white; padding:6px 12px; border-radius:4px; font-weight:bold; font-size:14px; margin-bottom:10px;'>🖥️ REAL-TIME SYSTEM MONITOR</div>") {
            checkSensorHealth()
            def dashHTML = getDashHTML()
            paragraph dashHTML
        }
        
        section("<div style='background-color:#7f8c8d; color:white; padding:4px 10px; border-radius:4px; font-weight:bold; font-size:13px;'>⚙️ SYSTEM CONTROL PANEL</div>") {
            // Row 1: System Actions (12 columns total: 4 + 4 + 4)
            input "btnRefreshData", "button", title: "🔄 Refresh Dashboard", width: 4
            input "btnCalibrate", "button", title: "📐 Calibrate Baseline", width: 4
            input "btnCreateChild", "button", title: "🖥️ Re-link Tile", width: 4
            
            // Row 2: State Overrides (12 columns total: 3 + 3 + 3 + 3)
            input "btnSetHouse", "button", title: "🏠 Set: House", width: 3
            input "btnSetCurbFull", "button", title: "📦 Set: Curb (Full)", width: 3
            input "btnSetCurbEmptied", "button", title: "✅ Set: Curb (Empty)", width: 3
            input "btnSetMissed", "button", title: "⚠️ Set: Missed", width: 3

            // Row 3: Resets & Modifiers (12 columns total: 4 + 4 + 4)
            input "btnResetAI", "button", title: "🧠 Reset AI Memory", width: 4
            input "btnResetFinance", "button", title: "💳 Reset Ledger", width: 4
            input "btnHoliday", "button", title: state.holidayShift ? "Cancel Holiday Shift" : "Force Holiday Shift", width: 4
            
            // Row 4: Maintenance (12 columns)
            input "btnWashBin", "button", title: "🧽 Clear Hygiene Status (Log Wash)", width: 12
        }

        // --- PREFERENCES DROP MENUS (Native Hubitat Hideable Sections) ---
        
        section("<span style='font-weight:bold;'>🔊 Voice Butler Telemetry Broadcast Integration</span>", hideable: true, hidden: true) {
            paragraph "<i>Broadcast status telemetry streams and operational threshold disruptions directly to your Voice Butler ecosystem.</i>"
            input "sendToButler", "bool", title: "Enable Voice Butler Streams?", defaultValue: true, submitOnChange: true
        }

        section("<span style='font-weight:bold;'>💳 Utility Financial Auditing Ledger</span>", hideable: true, hidden: true) {
            paragraph "<i>Apportion quarterly processing invoices to calculate spatial degradation refunds when service providers fail pickup deadlines.</i>"
            input "quarterlyCost", "decimal", title: "Quarterly Waste Disposal Cost (\$)", description: "e.g., 90.00", required: false, submitOnChange: true
        }

        section("<span style='font-weight:bold;'>🧠 Predictive AI Scheduling & Recycling Profiles</span>", hideable: true, hidden: true) {
            input "usePredictiveTiming", "bool", title: "Enable AI Telemetry Collection (Predictive Timing)", defaultValue: true, submitOnChange: true
            input "enableRecycling", "bool", title: "Enable Bi-Weekly Alternating Recycling Matrix", defaultValue: false, submitOnChange: true
            if (enableRecycling) {
                input "recycleWeek", "enum", title: "Target Recycling Matrix Routine Day:", options: ["Even Weeks", "Odd Weeks"], required: true
                input "ttsRecycleText", "text", title: "Recycling Multi-Broadcast Announcement String", defaultValue: "Reminder, it is time to take the trash and the recycling out to the road."
            }
        }

        section("<span style='font-weight:bold;'>🚚 Automated Physical Tracking Acceleration Profiles</span>", hideable: true, hidden: true) {
            input "binMultiSensor", "capability.threeAxis", title: "Samsung Multipurpose Sensor Hardware Target", required: true, submitOnChange: true
            input "drivewaySurface", "enum", title: "Driveway Surface Friction/Jolt Profile", options: ["Smooth Pavement", "Mixed / Patchy", "Fine Gravel", "Chunky / Rough Gravel", "Custom (Manual Overrides)"], defaultValue: "Smooth Pavement", required: true, submitOnChange: true
            
            if (drivewaySurface == "Custom (Manual Overrides)") {
                input "transitMinTime", "number", title: "Minimum Transit Duration Bounds (Seconds)", defaultValue: 10, required: true
                input "transitTiltThreshold", "number", title: "Transit Tilt Vector Threshold", defaultValue: 150, required: true
                input "lidOpenThreshold", "number", title: "Lid Inversion Vector Threshold", defaultValue: 600, required: true
            }
            input "estimatedMaxOpens", "number", title: "Calculated Envelope Mass Volumetric Limits (Bags to Full)", defaultValue: 8, required: true
        }

        section("<span style='font-weight:bold;'>⛈️ Extreme Weather Overrides & Structural Failsafes</span>", hideable: true, hidden: true) {
            paragraph "<i>Virtual input sensors block data fluctuations during highly kinetic weather incidents.</i>"
            input "swTornado", "capability.switch", title: "Tornado Incident Emergency Switch", required: false
            input "swThunderstorm", "capability.switch", title: "Severe Thunderstorm Alarm Switch", required: false
            input "swRain", "capability.switch", title: "Active Precipitation Sensor Switch", required: false
            input "swSprinkle", "capability.switch", title: "Light Precipitation Sensor Switch", required: false
            
            input "enableFallenBin", "bool", title: "Enable Structural Dislocation (Fallen Bin) Notifications", defaultValue: true, submitOnChange: true
            if (enableFallenBin) {
                input "rainSensor", "capability.waterSensor", title: "Hardware Precipitation Subsystem Input", required: false
                input "weatherStation", "capability.sensor", title: "Meteorological Terminal Input (Wind)", required: false
                input "windThreshold", "number", title: "Velocity Cutoff Threshold (MPH)", defaultValue: 15, required: false
            }
        }

        section("<span style='font-weight:bold;'>🏡 Municipal & HOA Compliance Automation</span>", hideable: true, hidden: true) {
            input "enableHoaNag", "bool", title: "Enable HOA Return Post-Collection Mandate Nag", defaultValue: false, submitOnChange: true
            if (enableHoaNag) {
                input "hoaNagTime", "number", title: "Post-Disposal Buffer Delay (Hours before Nag):", defaultValue: 4, required: true
                input "ttsHoaText", "text", title: "HOA Post-Disposal Multi-Broadcast Compliance String", defaultValue: "The garbage truck has collected the trash. Please return the bin to the house."
            }
        }

        section("<span style='font-weight:bold;'>📅 Automated Municipal Holiday Shifting Parameters</span>", hideable: true, hidden: true) {
            input "autoHoliday", "bool", title: "Enable Automatic Calendar Interruption Routing", defaultValue: true, submitOnChange: true
            if (autoHoliday) {
                input "selectedHolidays", "enum", title: "Recognized Interruption Holidays", options: ["New Year's Day", "Memorial Day", "Independence Day", "Labor Day", "Thanksgiving", "Christmas"], multiple: true, required: true, defaultValue: ["New Year's Day", "Memorial Day", "Independence Day", "Labor Day", "Thanksgiving", "Christmas"]
            }
        }

        section("<span style='font-weight:bold;'>⏰ Core Schedule Mapping & Warning Offsets</span>", hideable: true, hidden: true) {
            input "trashDays", "enum", title: "Municipal Disposal Sequence Target Day(s)", options: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"], multiple: true, required: true, submitOnChange: true
            input "pickupTime", "time", title: "Baseline Routine Static Target Time", required: true, submitOnChange: true
            input "reminderOffset", "decimal", title: "Notification Sequence Offset Target (Hours Early)", required: true, defaultValue: 12.0, submitOnChange: true
        }

        section("<span style='font-weight:bold;'>🔔 Audio Broadcasters & Operational Restrictions</span>", hideable: true, hidden: true) {
            input "trashSwitch", "capability.switch", title: "Task Monitor Virtual Switch Entity", required: false
            input "enableNag", "bool", title: "Loop Alert Broadcast Every Hour Until Virtual Switch Reset?", defaultValue: false
            
            paragraph "<b>Operational Restriction Constraints</b>"
            input "pushModes", "mode", title: "Allowed Modes: Push Text Stream", multiple: true, required: false
            input "audioModes", "mode", title: "Allowed Modes: Voice TTS/Chime Engine", multiple: true, required: false
            input "nagModes", "mode", title: "Allowed Modes: Hourly Nags Sequence", multiple: true, required: false
            input "trackingModes", "mode", title: "Allowed Modes: Kinetic Tracking Processing", multiple: true, required: false
            
            paragraph "<b>Hardware Audio Interfaces</b>"
            input "notifyDevice", "capability.notification", title: "Push System Target Devices", required: false, multiple: true
            input "ttsSpeakers", "capability.speechSynthesis", title: "Smart Audio Speakers (Fallback Audio Output)", multiple: true, required: false
            
            input "ttsReminderText", "text", title: "Standard Dispatch Text Template", defaultValue: "Reminder, it is time to take the trash out to the road."
            input "ttsEmptiedText", "text", title: "Disposal Cleared Event Text Template", defaultValue: "The garbage truck has emptied your bin."
            input "ttsReturnedText", "text", title: "Return Event Home Text Template", defaultValue: "The trash bin has been returned to the house."
            
            paragraph "<b>Zooz Multi-Chime Direct Controls</b>"
            input "zoozChimes", "capability.chime", title: "Zooz Physical Chime Audio Arrays", multiple: true, required: false, submitOnChange: true
            if (zoozChimes) {
                input "zoozSoundReminder", "number", title: "Chime Target Index: Standard Dispatch Reminder", required: false
                input "zoozSoundEmptied", "number", title: "Chime Target Index: Disposal Cleared Event", required: false
                input "zoozSoundReturned", "number", title: "Chime Target Index: Return Event Home", required: false
            }
        }

        section("<span style='font-weight:bold;'>📝 Operational Logging & Execution History Stack</span>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Application Diagnostic Log Streams", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<div style='font-size:12px; font-family:monospace; background-color:#f8f9fa; padding:8px; border:1px solid #ddd; max-height:200px; overflow-y:auto;'>${historyStr}</div>"
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
    if (!state.binStatus) state.binStatus = "house"
    if (state.holidayShift == null) state.holidayShift = false
    if (state.autoHolidayTriggered == null) state.autoHolidayTriggered = false
    if (!state.lidOpens) state.lidOpens = 0
    if (!state.lastWashed) state.lastWashed = now()
    if (!state.maxTempSinceWash) state.maxTempSinceWash = 70
    if (state.isSensorDead == null) state.isSensorDead = false
    if (!state.lifetimeMissedHours) state.lifetimeMissedHours = 0.0
    if (state.queuedReminderTime == null) state.queuedReminderTime = null
    
    if (!state.historyPutOut) state.historyPutOut = []
    if (!state.historyEmptied) state.historyEmptied = []
    if (!state.historyReturned) state.historyReturned = []
    if (!state.historyFullness) state.historyFullness = []
    
    subscribe(location, "mode", modeChangeHandler)
    if (trashSwitch) subscribe(trashSwitch, "switch.off", ackHandler)
    
    if (binMultiSensor) {
        subscribe(binMultiSensor, "threeAxis", axisSpatialHandler)
        subscribe(binMultiSensor, "acceleration.active", binMoveActiveHandler)
        subscribe(binMultiSensor, "acceleration.inactive", binMoveInactiveHandler)
        subscribe(binMultiSensor, "temperature", tempHandler)
    }
    
    schedule("0 5 0 * * ?", updateSchedule) 
    runEvery1Hour("nagCheck")
    updateSchedule()
    updateHygiene()
    checkSensorHealth()
    pushChildUpdate()
    logAction("App Initialized.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefreshData") {
        checkSensorHealth()
        logAction("MANUAL: Dashboard data refreshed.")
    } else if (btn == "btnSetHouse") {
        state.binStatus = "house"
        state.lidOpens = 0
        unschedule("hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'House'.")
    } else if (btn == "btnSetCurbFull") {
        state.binStatus = "curb_full"
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        unschedule("hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'At Curb (Full)'.")
    } else if (btn == "btnSetCurbEmptied") {
        state.binStatus = "curb_emptied"
        state.lidOpens = 0
        if (enableHoaNag && hoaNagTime) runIn((hoaNagTime as Integer) * 3600, "hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'At Curb (Emptied)'.")
    } else if (btn == "btnSetMissed") {
        state.binStatus = "curb_missed"
        state.missedTime = now()
        unschedule("hoaNagHandler")
        logAction("MANUAL OVERRIDE: Bin forced to 'Missed Pickup'.")
    } else if (btn == "btnResetFinance") {
        state.lifetimeMissedHours = 0.0
        logAction("FINANCIAL AUDIT: Ledger reset to \$0.00.")
    } else if (btn == "btnResetAI") {
        state.historyEmptied = []
        state.predictiveActive = false
        updateSchedule()
        logAction("SYSTEM OVERRIDE: Predictive AI History Cleared. Resetting to baseline schedule.")
    } else if (btn == "btnHoliday") {
        state.holidayShift = !state.holidayShift
        logAction("MANUAL: Holiday Shift " + (state.holidayShift ? "Activated" : "Deactivated"))
        updateSchedule()
    } else if (btn == "btnWashBin") {
        state.lastWashed = now()
        def tempVal = binMultiSensor?.currentValue("temperature")
        state.maxTempSinceWash = tempVal != null ? tempVal : 70
        updateHygiene()
        logAction("MAINTENANCE: Bin hygiene manually reset to 'Clean'.")
    } else if (btn == "btnCalibrate") {
        calibrateSpatialBaseline()
    } else if (btn == "btnCreateChild") {
        def childDev = getChildDevice("trash_dash_${app.id}")
        if (!childDev) {
            try {
                childDev = addChildDevice("hubitat", "Virtual Device", "trash_dash_${app.id}", null, [name: "Trash Dashboard", label: "Trash Dashboard Tile"])
                logAction("SYSTEM: Virtual Dashboard Device Created successfully.")
            } catch (e) { log.error "Failed to create child device. ${e}" }
        } else {
            logAction("SYSTEM: Virtual Dashboard Device already exists.")
        }
    }
    pushChildUpdate()
}

// ------------------------------------------------------------------------------
// BUTLER TELEMETRY & NOTIFICATION ENGINE
// ------------------------------------------------------------------------------

def sendAlert(msg, forceButlerStash = false) {
    if (notifyDevice && isPushAllowed()) {
        notifyDevice*.deviceNotification(msg)
    }
    if (settings.sendToButler) {
        def stashPayload = forceButlerStash ? msg.toLowerCase() : ""
        sendLocationEvent(name: "voiceButlerMsg", value: "trash", descriptionText: msg, data: stashPayload, isStateChange: true)
    } else {
        if (ttsSpeakers) playTTS(ttsSpeakers, msg)
    }
}

def syncButlerDashboard() {
    if (settings.sendToButler) {
        def payload = [
            status: getHumanReadableStatus(),
            fill: getFillPct(),
            hygiene: state.hygieneStatus ?: "Clean ✨",
            nextPickup: state.nextPickupStr ?: "Calculating..."
        ]
        sendLocationEvent(name: "voiceButlerTrashSync", value: groovy.json.JsonOutput.toJson(payload), isStateChange: true)
    }
}

// ------------------------------------------------------------------------------
// HIGH-END HIGH-CONTRAST GRID DASHBOARD GENERATOR
// ------------------------------------------------------------------------------

def pushChildUpdate() {
    def childDev = getChildDevice("trash_dash_${app.id}")
    if (childDev) {
        def html = getDashHTML()
        childDev.sendEvent(name: "htmlTile", value: html, descriptionText: "Updated Dashboard HTML")
    }
    syncButlerDashboard()
}

String getDashHTML() {
    def nPickup = state.nextPickupStr ?: "Recalculating Operational Target..."
    def isRecycleWeek = checkRecyclingWeek()
    def recycleStr = isRecycleWeek ? "<span style='color:#3498db; font-weight:bold;'>Active Vector (Trash + Recycling)</span>" : "Standard Sequence (Trash Only)"
    def schedMode = state.predictiveActive ? "<span style='background:#9b59b6; color:white; padding:2px 6px; border-radius:3px; font-size:11px; font-weight:bold;'>PREDICTIVE AI</span>" : "<span style='background:#95a5a6; color:white; padding:2px 6px; border-radius:3px; font-size:11px; font-weight:bold;'>STATIC SCHED</span>"
    
    def headerStyle = "background: linear-gradient(135deg, #2c3e50, #34495e); color: white;"
    def humanStatus = "System Operational Initialization"
    
    if (state.isNagging || (trashSwitch && trashSwitch.currentValue("switch") == "on")) {
        headerStyle = "background: linear-gradient(135deg, #c0392b, #e74c3c); color: white; animation: pulse 2s infinite;"
        humanStatus = "PENDING DISPATCH: Deploy container to curb side zone immediately."
    } else if (state.binStatus == "curb_full") {
        headerStyle = "background: linear-gradient(135deg, #d35400, #f39c12); color: white;"
        humanStatus = "CONTAINER AT CURB: Awaiting processing fleet intercept."
    } else if (state.binStatus == "curb_emptied") {
        headerStyle = "background: linear-gradient(135deg, #27ae60, #2ecc71); color: white;"
        humanStatus = "DISPOSAL VERIFIED: Container structural matrix cleared. Return to base."
    } else if (state.binStatus == "curb_missed") {
        headerStyle = "background: linear-gradient(135deg, #7f8c8d, #34495e); color: white;"
        humanStatus = "SLA DISRUPTION REPORTED: Fleet missed scheduled intercept window."
    } else if (state.binStatus == "house") {
        headerStyle = "background: linear-gradient(135deg, #2c3e50, #2980b9); color: white;"
        humanStatus = "CONTAINER SECURED AT STATION: Holding resting baseline posture."
    }

    def battLvl = binMultiSensor ? binMultiSensor.currentValue("battery") : null
    def battDisplay = battLvl != null ? (battLvl <= 15 ? "<span style='color:#e74c3c; font-weight:bold;'>${battLvl}% (LOW)</span>" : "${battLvl}%") : "Offline"
    def sensorHealthStr = state.isSensorDead ? "<span style='color:#e74c3c; font-weight:bold;'>HARDWARE DISCONNECTED</span>" : "<span style='color:#2ecc71; font-weight:bold;'>ONLINE & TELEMETRIC</span>"
    
    int fillPct = getFillPct()
    def fillBarColor = "#2ecc71"
    if (fillPct > 85) fillBarColor = "#e74c3c"
    else if (fillPct > 50) fillBarColor = "#f1c40f"
    
    def ecoScore = getEcoScoreBadge()
    def hygStatus = state.hygieneStatus ?: "Clean ✨"
    def refundStr = calculateRefundOwed()

    return """
    <style>
        .op-container { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #f4f6f7; padding: 12px; border-radius: 8px; color: #2c3e50; }
        .op-header { padding: 14px; border-radius: 6px; font-weight: bold; font-size: 15px; text-shadow: 0 1px 2px rgba(0,0,0,0.2); box-shadow: 0 2px 4px rgba(0,0,0,0.05); margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; }
        .op-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
        .op-card { background: white; padding: 12px; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border-top: 3px solid #34495e; }
        .op-card-title { font-size: 11px; text-transform: uppercase; color: #7f8c8d; font-weight: 700; margin-bottom: 6px; letter-spacing: 0.5px; }
        .op-card-val { font-size: 15px; font-weight: 600; color: #2c3e50; }
        .op-bar-bg { background: #e2e8f0; border-radius: 4px; height: 8px; margin-top: 6px; overflow: hidden; }
        .op-bar-fill { height: 100%; border-radius: 4px; transition: width 0.5s ease; }
        @keyframes pulse { 0% { opacity: 1; } 50% { opacity: 0.85; } 100% { opacity: 1; } }
    </style>
    <div class="op-container">
        <div class="op-header" style="${headerStyle}">
            <span>${humanStatus}</span>
            <span>${schedMode}</span>
        </div>
        <div class="op-grid">
            <div class="op-card" style="border-top-color: #3498db;">
                <div class="op-card-title">INTERCEPT TARGET TIMELINE</div>
                <div class="op-card-val" style="font-size: 14px; color:#2980b9;">${nPickup}</div>
                <div style="font-size:12px; margin-top:4px; color:#555;">${recycleStr}</div>
            </div>
            <div class="op-card" style="border-top-color: #2ecc71;">
                <div class="op-card-title">VOLUME & ENVIRONMENTAL AUDIT</div>
                <div class="op-card-val">Mass Metrics: ${fillPct}%</div>
                <div class="op-bar-bg"><div class="op-bar-fill" style="width: ${fillPct}%; background: ${fillBarColor};"></div></div>
                <div style="font-size:11px; margin-top:5px; color:#7f8c8d;">Envelope Stack: ${ecoScore}</div>
            </div>
            <div class="op-card" style="border-top-color: #e67e22;">
                <div class="op-card-title">CONTAINER HYGIENE STATE</div>
                <div class="op-card-val">${hygStatus}</div>
                <div style="font-size:11px; margin-top:4px; color:#7f8c8d;">Substrate Biological Threat Metric</div>
            </div>
            <div class="op-card" style="border-top-color: #e74c3c;">
                <div class="op-card-title">FINANCIAL PERFORMANCE LEDGER</div>
                <div class="op-card-val" style="color:#c0392b; font-size:18px;">\$${refundStr}</div>
                <div style="font-size:11px; color:#7f8c8d;">Prorated SLA Violation Recovery</div>
            </div>
            <div class="op-card" style="border-top-color: #95a5a6; grid-column: span repeat(auto-fit, minmax(220px, 1fr));">
                <div class="op-card-title">HARDWARE SUBSYSTEM TELEMETRY</div>
                <div class="op-card-val" style="font-size:13px;">${sensorHealthStr}</div>
                <div style="font-size:12px; margin-top:3px; color:#555;">Power Vector Core: <b>${battDisplay}</b></div>
            </div>
        </div>
    </div>
    """
}

// ------------------------------------------------------------------------------
// FINANCIAL MATH & RECYCLING
// ------------------------------------------------------------------------------

boolean checkRecyclingWeek() {
    if (!settings.enableRecycling || !settings.recycleWeek) return false
    def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
    int weekNum = cal.get(Calendar.WEEK_OF_YEAR)
    boolean isEven = (weekNum % 2 == 0)
    if (settings.recycleWeek == "Even Weeks" && isEven) return true
    if (settings.recycleWeek == "Odd Weeks" && !isEven) return true
    return false
}

String calculateRefundOwed() {
    if (!settings.quarterlyCost || !state.lifetimeMissedHours) return "0.00"
    def qCost = settings.quarterlyCost.toDouble()
    def dailyRate = qCost / 91.25
    def hourlyRate = dailyRate / 24.0
    def refund = (state.lifetimeMissedHours.toDouble() * hourlyRate)
    return String.format("%.2f", refund)
}

def hoaNagHandler() {
    if (state.binStatus == "curb_emptied") {
        logAction("HOA COMPLIANCE: Sending return nag.")
        def msg = settings.ttsHoaText ?: "The garbage truck has collected the trash. Please return the bin to the house."
        sendAlert(msg, false)
        if (zoozChimes && zoozSoundReturned != null) playZoozChime(zoozSoundReturned)
    }
}

// ------------------------------------------------------------------------------
// MODE OPERATIONS FILTERING
// ------------------------------------------------------------------------------

boolean isPushAllowed() { return !settings.pushModes || (settings.pushModes as List).contains(location.mode) }
boolean isAudioAllowed() { return !settings.audioModes || (settings.audioModes as List).contains(location.mode) }
boolean isNagAllowed() { return !settings.nagModes || (settings.nagModes as List).contains(location.mode) }
boolean isTrackingAllowed() { return !settings.trackingModes || (settings.trackingModes as List).contains(location.mode) }

int getFillPct() {
    int maxOpens = settings.estimatedMaxOpens ?: 8
    int currentOpens = state.lidOpens ?: 0
    return Math.min(Math.round((currentOpens / maxOpens) * 100), 100)
}

def checkSensorHealth() {
    boolean isDead = false
    if (binMultiSensor) {
        def lastEvt = binMultiSensor.currentState("temperature")?.date
        if (lastEvt) {
            long hrsSince = (now() - lastEvt.time) / 3600000
            if (hrsSince > 48) isDead = true
        } else { isDead = true }
    }
    state.isSensorDead = isDead
}

def tempHandler(evt) {
    state.isSensorDead = false 
    def t = evt.numericValue
    if (t != null && t > (state.maxTempSinceWash ?: 0)) state.maxTempSinceWash = t
}

def updateHygiene() {
    long daysSince = state.lastWashed ? ((now() - state.lastWashed) / 86400000) as Integer : 0
    int maxT = state.maxTempSinceWash ?: 70
    String status = "Clean ✨"
    
    if (daysSince > 30 || (maxT > 90 && daysSince > 14)) status = "Bio-Hazard ☣️"
    else if (daysSince > 21 || (maxT > 85 && daysSince > 10)) status = "Gross 🤢"
    else if (daysSince > 14 || (maxT > 80 && daysSince > 7)) status = "Needs Washing 🧽"
    
    if (status != state.hygieneStatus && (status == "Bio-Hazard ☣️" || status == "Gross 🤢")) {
        sendAlert("Maintenance alert. Your exterior waste bin has reached a hygiene level of ${status.replaceAll(/[^a-zA-Z -]/, '')}. Please sanitize it soon.", true)
    }
    state.hygieneStatus = status
    pushChildUpdate()
}

def recordTelemetryTime(String stateKey) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    cal.setTime(new Date())
    int minutes = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
    def list = state[stateKey] ?: []
    list.add(0, minutes)
    if (list.size() > 10) list = list[0..9]
    state[stateKey] = list
}

String getEcoScoreBadge() {
    def hist = state.historyFullness ?: []
    if (hist.size() == 0) return "<span style='color:grey;'>Awaiting Processing Matrix Cycles</span>"
    int avgFill = Math.round(hist.sum() / hist.size()) as Integer
    if (avgFill <= 25) return "Rating A+ [Eco-Warrior]"
    if (avgFill <= 50) return "Rating A [Optimal]"
    if (avgFill <= 75) return "Rating B [Nominal]"
    if (avgFill <= 99) return "Rating C [Elevated]"
    return "Rating F [Saturation]"
}

// ------------------------------------------------------------------------------
// OPERATIONAL BALANCING & CRITICAL PHYSICS OVERRIDES
// ------------------------------------------------------------------------------

boolean isWithinAllowedTransitWindow() {
    if (state.binStatus == "curb_missed") return true
    if (!state.nextPickupMs) return true 
    
    def tz = location.timeZone ?: TimeZone.getDefault()
    def nowCal = Calendar.getInstance(tz)
    nowCal.setTime(new Date())
    
    def pickupCal = Calendar.getInstance(tz)
    pickupCal.setTime(new Date(state.nextPickupMs as Long))
    
    nowCal.set(Calendar.HOUR_OF_DAY, 0); nowCal.set(Calendar.MINUTE, 0); nowCal.set(Calendar.SECOND, 0); nowCal.set(Calendar.MILLISECOND, 0)
    pickupCal.set(Calendar.HOUR_OF_DAY, 0); pickupCal.set(Calendar.MINUTE, 0); pickupCal.set(Calendar.SECOND, 0); pickupCal.set(Calendar.MILLISECOND, 0)
    
    long diffDays = (nowCal.getTimeInMillis() - pickupCal.getTimeInMillis()) / 86400000
    return (diffDays >= -1 && diffDays <= 1)
}

def calibrateSpatialBaseline() {
    if (!binMultiSensor) return
    def xyz = binMultiSensor.currentValue("threeAxis")
    if (xyz) {
        def axes = ["x": xyz.x, "y": xyz.y, "z": xyz.z]
        def dominantAxis = axes.max { Math.abs(it.value as Integer) }.key
        state.activeAxis = dominantAxis
        state.baselineValue = axes[dominantAxis]
        state.baselineX = xyz.x as Integer
        state.baselineY = xyz.y as Integer
        state.baselineZ = xyz.z as Integer
        state.isSensorDead = false 
        logAction("SYSTEM: 3D Calibration complete. Locked to global [${dominantAxis.toUpperCase()}] axis for Truck Dumps.")
        pushChildUpdate()
    }
}

def getSurfaceProfile() {
    def profile = [transitTime: 10, transitTilt: 150, lidTilt: 600]
    if (settings.drivewaySurface == "Smooth Pavement") {
        profile = [transitTime: 8, transitTilt: 120, lidTilt: 600]
    } else if (settings.drivewaySurface == "Mixed / Patchy") {
        profile = [transitTime: 12, transitTilt: 150, lidTilt: 600]
    } else if (settings.drivewaySurface == "Fine Gravel") {
        profile = [transitTime: 15, transitTilt: 180, lidTilt: 600]
    } else if (settings.drivewaySurface == "Chunky / Rough Gravel") {
        profile = [transitTime: 20, transitTilt: 220, lidTilt: 600]
    } else if (settings.drivewaySurface == "Custom (Manual Overrides)") {
        profile.transitTime = settings.transitMinTime ?: 10
        profile.transitTilt = settings.transitTiltThreshold ?: 150
        profile.lidTilt = settings.lidOpenThreshold ?: 600
    }
    return profile
}

def binMoveActiveHandler(evt) {
    state.isSensorDead = false
    state.motionStartTime = now()
    state.maxRelativeDevDuringMotion = 0
    state.wasFlippedDuringMotion = false
    state.hasTriggeredMechanicalDump = false
    
    if (state.lastKnownXYZ) {
        state.restX = state.lastKnownXYZ.x
        state.restY = state.lastKnownXYZ.y
        state.restZ = state.lastKnownXYZ.z
    }
}

def axisSpatialHandler(evt) {
    def xyz = binMultiSensor.currentValue("threeAxis")
    if (!xyz) return
    
    if (!state.motionStartTime) {
        state.lastKnownXYZ = [x: xyz.x as Integer, y: xyz.y as Integer, z: xyz.z as Integer]
        return
    }
    
    int curX = xyz.x as Integer
    int curY = xyz.y as Integer
    int curZ = xyz.z as Integer
    
    int rX = state.restX != null ? state.restX : (state.baselineX ?: 0)
    int rY = state.restY != null ? state.restY : (state.baselineY ?: 0)
    int rZ = state.restZ != null ? state.restZ : (state.baselineZ ?: 0)
    
    int devX = Math.abs(curX - rX)
    int devY = Math.abs(curY - rY)
    int devZ = Math.abs(curZ - rZ)
    int maxRelative = Math.max(devX, Math.max(devY, devZ))
    
    if (maxRelative > (state.maxRelativeDevDuringMotion ?: 0)) {
        state.maxRelativeDevDuringMotion = maxRelative
    }

    if ((state.binStatus == "curb_full" || state.binStatus == "curb_missed") && maxRelative >= 1800) {
        if (!state.isCurrentlyDumped && !state.hasTriggeredMechanicalDump) {
            state.hasTriggeredMechanicalDump = true
            state.isCurrentlyDumped = true
            processTruckDump()
        }
    }

    boolean isFlipped = false
    if (state.activeAxis && state.baselineValue != null) {
        int currentDom = xyz[state.activeAxis] as Integer
        int baselineDom = state.baselineValue as Integer
        isFlipped = (baselineDom > 0 && currentDom < -500) || (baselineDom < 0 && currentDom > 500)
        if (isFlipped) state.wasFlippedDuringMotion = true
    }

    if (isFlipped && (state.binStatus == "curb_full" || state.binStatus == "curb_missed")) {
        if (!state.isCurrentlyDumped && !state.hasTriggeredMechanicalDump) {
            state.isCurrentlyDumped = true
            processTruckDump()
        }
    } else if (!isFlipped && !state.hasTriggeredMechanicalDump) {
        state.isCurrentlyDumped = false
    }
}

def binMoveInactiveHandler(evt) {
    if (settings.enableFallenBin) runIn(300, "fallenBinCheck", [overwrite: true])
    if (!state.motionStartTime) return
    
    boolean severeSwitch = (swTornado?.currentValue("switch") == "on" || swThunderstorm?.currentValue("switch") == "on")
    if (severeSwitch) {
        logAction("WEATHER OVERRIDE: Suppressing physical tracking event.")
        state.motionStartTime = null
        return
    }
    
    long durationSec = (now() - state.motionStartTime) / 1000
    def profile = getSurfaceProfile()
    int maxTilt = state.maxRelativeDevDuringMotion ?: 0
    
    if (state.hasTriggeredMechanicalDump) {
        logAction("Motion Event Ended: Dump completed early via mechanical identification threshold.")
        state.motionStartTime = null
        state.hasTriggeredMechanicalDump = false
        return
    }
    
    boolean isCurrentlyFlipped = false
    boolean startedFlipped = false
    def xyz = binMultiSensor.currentValue("threeAxis")
    
    if (state.activeAxis && state.baselineValue != null) {
        int baselineDom = state.baselineValue as Integer
        if (xyz) {
            int currentDom = xyz[state.activeAxis] as Integer
            isCurrentlyFlipped = (baselineDom > 0 && currentDom < -500) || (baselineDom < 0 && currentDom > 500)
        }
        if (state.lastKnownXYZ) {
            int startDom = state.lastKnownXYZ[state.activeAxis] as Integer
            startedFlipped = (baselineDom > 0 && startDom < -500) || (baselineDom < 0 && startDom > 500)
        }
    }
    
    boolean lidWasClosedDuringMotion = (startedFlipped && !isCurrentlyFlipped)
    state.motionStartTime = null 
    
    logAction("Motion Event Ended: Duration: ${durationSec}s | Max Relative Tilt: ${maxTilt} | Flipped: ${isCurrentlyFlipped}")
    
    if (isCurrentlyFlipped) {
        if (maxTilt >= profile.lidTilt && isTrackingAllowed()) {
            state.lidOpens = (state.lidOpens ?: 0) + 1
            logAction("Action Processed: Trash Toss (Lid Flipped Open). Capacity: (${state.lidOpens}).")
            if (getFillPct() >= 100) sendAlert("Warning, the exterior trash bin is now at maximum capacity.", true)
            pushChildUpdate()
        }
        return
    }
    
    boolean isValidTransit = false
    if (durationSec >= profile.transitTime && maxTilt >= profile.transitTilt) {
        if (maxTilt < profile.lidTilt) { isValidTransit = true } 
        else if (!state.wasFlippedDuringMotion) { isValidTransit = true } 
        else if (lidWasClosedDuringMotion) { isValidTransit = true } 
        else if (state.binStatus == "curb_emptied" && durationSec >= 20) { isValidTransit = true }
    }

    if (isValidTransit) {
        if (isTrackingAllowed()) {
            logAction("Action Processed: Sustained Transit Matrix Tracked.")
            processValidTransit()
        }
        return
    }

    if (maxTilt >= profile.lidTilt || (durationSec <= 4 && maxTilt >= 450)) {
        if (isTrackingAllowed()) {
            state.lidOpens = (state.lidOpens ?: 0) + 1
            logAction("Action Processed: Trash Toss (Velocity Snap). Capacity: (${state.lidOpens}).")
            if (getFillPct() >= 100) sendAlert("Warning, the exterior trash bin is now at maximum capacity.", true)
            pushChildUpdate()
        }
        return
    }
    logAction("Action Processed: Ignored bump/wind oscillation.")
}

def fallenBinCheck() {
    def xyz = binMultiSensor.currentValue("threeAxis")
    if (!xyz || !state.baselineValue || !state.activeAxis) return
    int curDom = xyz[state.activeAxis] as Integer
    
    boolean severeSwitch = (swTornado?.currentValue("switch") == "on" || swThunderstorm?.currentValue("switch") == "on")
    boolean wetSwitch = (swRain?.currentValue("switch") == "on" || swSprinkle?.currentValue("switch") == "on")
    boolean isRaining = wetSwitch || (rainSensor && rainSensor.currentValue("water") == "wet")
    def wSpeed = weatherStation ? weatherStation.currentValue("windSpeed") : 0
    boolean isWindy = wSpeed && wSpeed.toBigDecimal() >= (settings.windThreshold ?: 15)
    
    if (severeSwitch) return

    if (Math.abs(curDom) < 400 && !state.isCurrentlyDumped) {
        if (state.binStatus == "curb_emptied") return
        if (isRaining || isWindy) {
            sendAlert("SEVERE WEATHER ALERT: Your outdoor trash bin was knocked over by the weather.", false)
        } else {
            sendAlert("BIN ALERT: Your outdoor trash bin is sideways or open. The weather is calm, so check for animals or mishaps.", false)
        }
    }
}

def processTruckDump() {
    if (!isWithinAllowedTransitWindow()) return
    logAction("AUTOMATION: Kinetic Mechanical Displacement Inversion Confirmed. Matrix Cleared.")
    
    if (state.binStatus == "curb_missed" && state.missedTime) {
        long hrsLate = (now() - state.missedTime) / 3600000
        state.lastMissedDuration = hrsLate
        state.lifetimeMissedHours = (state.lifetimeMissedHours ?: 0.0) + hrsLate
        sendAlert("FINANCIAL TRACKER: Your missed trash was finally collected ${hrsLate} hours late. Ledger updated.", true)
        state.missedTime = null
    }
    
    def histFull = state.historyFullness ?: []
    histFull.add(0, getFillPct())
    if (histFull.size() > 10) histFull = histFull[0..9]
    state.historyFullness = histFull
    
    recordTelemetryTime("historyEmptied")
    state.binStatus = "curb_emptied"
    state.lastDumpTime = now() 
    
    if (enableHoaNag && hoaNagTime) runIn((hoaNagTime as Integer) * 3600, "hoaNagHandler")
    updateSchedule()
    
    def msg = ttsEmptiedText ?: "The garbage truck has emptied your bin."
    sendAlert(msg, false)
    if (zoozChimes && zoozSoundEmptied != null) playZoozChime(zoozSoundEmptied)
    pushChildUpdate()
}

def processValidTransit() {
    if (state.binStatus == "house") {
        if (!isWithinAllowedTransitWindow()) return
        logAction("AUTOMATION: Container moved to curb side perimeter.")
        recordTelemetryTime("historyPutOut")
        
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        state.queuedReminderTime = null
        unschedule("playQueuedReminder")
        
        state.binStatus = "curb_full" 
        pushChildUpdate()
    }
    else if (state.binStatus == "curb_emptied" || state.binStatus == "curb_missed") {
        if (state.binStatus == "curb_emptied" && state.lastDumpTime) {
            long msSinceDump = now() - state.lastDumpTime
            if (msSinceDump < 120000) return
        }
        
        logAction("AUTOMATION: Container safely docked at structural anchor station.")
        recordTelemetryTime("historyReturned")
        state.binStatus = "house" 
        state.lidOpens = 0 
        unschedule("hoaNagHandler")
        
        def msg = ttsReturnedText ?: "The trash bin has been returned to the house."
        sendAlert(msg, false)
        if (zoozChimes && zoozSoundReturned != null) playZoozChime(zoozSoundReturned)
        pushChildUpdate()
    }
}

def ackHandler(evt) {
    if (state.isNagging) {
        state.isNagging = false
        state.binStatus = "curb_full"
        state.queuedReminderTime = null
        unschedule("playQueuedReminder")
        logAction("System Acknowledged: Switch turned OFF.")
        pushChildUpdate()
    }
}

String getHumanReadableStatus() {
    if (trashSwitch && trashSwitch.currentValue("switch") == "on" || state.isNagging) return "Pending"
    if (state.binStatus == "curb_full") return "At Curb"
    if (state.binStatus == "curb_emptied") return "Emptied"
    if (state.binStatus == "curb_missed") return "Missed"
    return "Secured Base"
}

// ------------------------------------------------------------------------------
// PREDICTIVE SCHEDULING ENGINE
// ------------------------------------------------------------------------------

boolean isHolidayShiftRequired(Calendar targetPickup) {
    if (!settings.autoHoliday || !settings.selectedHolidays) return false
    int year = targetPickup.get(Calendar.YEAR)
    def tz = location.timeZone ?: TimeZone.getDefault()
    def validHolidays = settings.selectedHolidays as List
    def holidayDates = []
    
    if (validHolidays.contains("New Year's Day")) {
        def ny = Calendar.getInstance(tz); ny.set(year, Calendar.JANUARY, 1, 0, 0, 0); ny.set(Calendar.MILLISECOND, 0); holidayDates << ny
    }
    if (validHolidays.contains("Memorial Day")) {
        def mem = Calendar.getInstance(tz); mem.set(year, Calendar.MAY, 31, 0, 0, 0); mem.set(Calendar.MILLISECOND, 0)
        while(mem.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { mem.add(Calendar.DAY_OF_MONTH, -1) }; holidayDates << mem
    }
    if (validHolidays.contains("Independence Day")) {
        def ind = Calendar.getInstance(tz); ind.set(year, Calendar.JULY, 4, 0, 0, 0); ind.set(Calendar.MILLISECOND, 0); holidayDates << ind
    }
    if (validHolidays.contains("Labor Day")) {
        def lab = Calendar.getInstance(tz); lab.set(year, Calendar.SEPTEMBER, 1, 0, 0, 0); lab.set(Calendar.MILLISECOND, 0)
        while(lab.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { lab.add(Calendar.DAY_OF_MONTH, 1) }; holidayDates << lab
    }
    if (validHolidays.contains("Thanksgiving")) {
        def tg = Calendar.getInstance(tz); tg.set(year, Calendar.NOVEMBER, 1, 0, 0, 0); tg.set(Calendar.MILLISECOND, 0)
        int thursdays = 0
        while(thursdays < 4) {
            if (tg.get(Calendar.DAY_OF_WEEK) == Calendar.THURSDAY) thursdays++
            if (thursdays < 4) tg.add(Calendar.DAY_OF_MONTH, 1)
        }; holidayDates << tg
    }
    if (validHolidays.contains("Christmas")) {
        def xmas = Calendar.getInstance(tz); xmas.set(year, Calendar.DECEMBER, 25, 0, 0, 0); xmas.set(Calendar.MILLISECOND, 0); holidayDates << xmas
    }
    
    def weekStart = targetPickup.clone()
    while(weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { weekStart.add(Calendar.DAY_OF_MONTH, -1) }
    weekStart.set(Calendar.HOUR_OF_DAY, 0); weekStart.set(Calendar.MINUTE, 0); weekStart.set(Calendar.SECOND, 0)
    
    def targetEnd = targetPickup.clone()
    targetEnd.set(Calendar.HOUR_OF_DAY, 23); targetEnd.set(Calendar.MINUTE, 59)
    
    for (cal in holidayDates) {
        if (cal.getTimeInMillis() >= weekStart.getTimeInMillis() && cal.getTimeInMillis() <= targetEnd.getTimeInMillis()) return true
    }
    return false
}

def updateSchedule() {
    checkSensorHealth()
    if (!trashDays || !pickupTime || reminderOffset == null) return
    
    try {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def now = new Date()
        def cal = Calendar.getInstance(tz)
        cal.setTime(now)
        int currentDayNum = cal.get(Calendar.DAY_OF_WEEK) 
        
        def parsedTime = timeToday(pickupTime, tz)
        def pCal = Calendar.getInstance(tz)
        pCal.setTime(parsedTime)
        int pHour = pCal.get(Calendar.HOUR_OF_DAY)
        int pMin = pCal.get(Calendar.MINUTE)
        
        if (settings.usePredictiveTiming && state.historyEmptied && state.historyEmptied.size() >= 2 && !state.isSensorDead) {
            def avgMins = Math.round(state.historyEmptied.sum() / state.historyEmptied.size()) as Integer
            pHour = avgMins.intdiv(60)
            pMin = avgMins % 60
            state.predictiveActive = true
        } else { state.predictiveActive = false }
        
        def dayMap = ["Sunday":1, "Monday":2, "Tuesday":3, "Wednesday":4, "Thursday":5, "Friday":6, "Saturday":7]
        long nextPickupMs = Long.MAX_VALUE
        boolean shiftApplied = false
        
        trashDays.each { dayName ->
            int targetDay = dayMap[dayName]
            int daysToAdd = targetDay - currentDayNum
            if (daysToAdd < 0) daysToAdd += 7
            
            def testCal = Calendar.getInstance(tz)
            testCal.setTime(now)
            testCal.add(Calendar.DAY_OF_YEAR, daysToAdd)
            testCal.set(Calendar.HOUR_OF_DAY, pHour); testCal.set(Calendar.MINUTE, pMin); testCal.set(Calendar.SECOND, 0)
            
            if (daysToAdd == 0 && testCal.getTimeInMillis() <= now.time) testCal.add(Calendar.DAY_OF_YEAR, 7)
            
            if (settings.autoHoliday && isHolidayShiftRequired(testCal)) {
                testCal.add(Calendar.DAY_OF_YEAR, 1)
                shiftApplied = true
            } else if (state.holidayShift) { testCal.add(Calendar.DAY_OF_YEAR, 1) }
            if (testCal.getTimeInMillis() < nextPickupMs) nextPickupMs = testCal.getTimeInMillis()
        }
        
        state.autoHolidayTriggered = shiftApplied
        def pickupDate = new Date(nextPickupMs)
        def offsetMs = (reminderOffset.toDouble() * 3600000).toLong()
        def reminderDate = new Date(nextPickupMs - offsetMs)
        
        state.nextPickupStr = pickupDate.format("EEEE, MMM d 'at' h:mm a", tz)
        state.nextReminderStr = reminderDate.format("EEEE, MMM d 'at' h:mm a", tz)
        state.nextPickupMs = nextPickupMs
        
        unschedule("triggerReminder")
        unschedule("autoResetHandler")
        
        if (reminderDate.time > now.time) runOnce(reminderDate, "triggerReminder", [overwrite: true])
        runOnce(new Date(nextPickupMs + 7200000), "autoResetHandler", [overwrite: true])
        pushChildUpdate()
    } catch (e) { log.error "Schedule Calculation Error: ${e}" }
}

def triggerReminder() {
    checkSensorHealth()
    if (state.binStatus == "curb_full" && !state.isSensorDead) {
        if (trashSwitch && trashSwitch.currentValue("switch") != "off") trashSwitch.off()
        state.isNagging = false
        pushChildUpdate()
        return
    }

    if (trashSwitch) trashSwitch.on()
    state.isNagging = true
    if (state.binStatus != "curb_missed") state.binStatus = "house" 
    
    String finalMsg = ttsReminderText ?: "Reminder, it is time to take the trash out to the road."
    if (checkRecyclingWeek()) finalMsg = settings.ttsRecycleText ?: "Reminder, it is time to take the trash and recycling out."
    
    sendAlert(finalMsg, false)
    if (zoozChimes && zoozSoundReminder != null) playZoozChime(zoozSoundReminder)
    pushChildUpdate()
}

def nagCheck() {
    checkSensorHealth()
    if (binMultiSensor && binMultiSensor.currentValue("battery") != null) {
        int batt = binMultiSensor.currentValue("battery") as Integer
        if (batt <= 15) {
            def tz = location.timeZone ?: TimeZone.getDefault()
            if (state.lastBatteryWarningDate != new Date().format("yyyy-MM-dd", tz)) {
                sendAlert("WARNING: Your outdoor trash bin sensor battery is low (${batt}%).", true)
                state.lastBatteryWarningDate = new Date().format("yyyy-MM-dd", tz)
            }
        }
    }

    updateHygiene()
    if (!enableNag || !state.isNagging) return
    if (trashSwitch && trashSwitch.currentValue("switch") == "off") { state.isNagging = false; return }
    if (!isNagAllowed()) return
    
    String finalMsg = ttsReminderText ?: "Don't forget the trash!"
    if (checkRecyclingWeek()) finalMsg = settings.ttsRecycleText ?: "Don't forget the trash and recycling!"
    
    sendAlert("NAG: " + finalMsg, false)
    if (zoozChimes && zoozSoundReminder != null) playZoozChime(zoozSoundReminder)
    pushChildUpdate()
}

def autoResetHandler() {
    if (trashSwitch) trashSwitch.off()
    state.isNagging = false
    
    if (state.binStatus == "curb_full" && !state.isSensorDead) {
        state.binStatus = "curb_missed"
        state.missedTime = now()
        sendAlert("ALERT: The garbage truck missed your scheduled pickup!", true)
    } else {
        state.binStatus = "house" 
        state.lidOpens = 0
    }
    if (state.holidayShift) state.holidayShift = false
    updateSchedule()
}

// ------------------------------------------------------------------------------
// INTERIOR MOTION AUDIO ANNOUNCEMENT HANDLERS
// ------------------------------------------------------------------------------

def room1MotionHandler(evt) { state.lastMotionRoom1 = now() }
def room2MotionHandler(evt) { state.lastMotionRoom2 = now() }
def room3MotionHandler(evt) { state.lastMotionRoom3 = now() }
def room4MotionHandler(evt) { state.lastMotionRoom4 = now() }
def room5MotionHandler(evt) { state.lastMotionRoom5 = now() }

def modeChangeHandler(evt) {
    if (isAudioAllowed() && state.isNagging) {
        state.queuedReminderTime = now() + 1800000 
        runIn(1800, "playQueuedReminder", [overwrite: true])
        pushChildUpdate()
    } else if (!isAudioAllowed()) {
        unschedule("playQueuedReminder")
        state.queuedReminderTime = null
        pushChildUpdate()
    }
}

def playQueuedReminder() {
    state.queuedReminderTime = null 
    if (state.isNagging && state.binStatus != "curb_full" && state.binStatus != "curb_missed") {
        String finalMsg = ttsReminderText ?: "Reminder, it is time to take the trash out to the road."
        if (checkRecyclingWeek()) finalMsg = settings.ttsRecycleText ?: "Reminder, it is time to take the trash and recycling out."
        sendAlert(finalMsg, false)
        if (zoozChimes && zoozSoundReminder != null) playZoozChime(zoozSoundReminder)
    }
    pushChildUpdate()
}

def isSpeakerMotionActive(speaker) {
    boolean isMapped = false; boolean hasMotion = false
    for (int i = 1; i <= 7; i++) {
        def mappedSpeaker = settings["room${i}Speaker"]
        if (mappedSpeaker) {
            def mappedList = mappedSpeaker instanceof List ? mappedSpeaker : [mappedSpeaker]
            if (mappedList.any { it.id == speaker.id }) {
                isMapped = true
                if (settings.alwaysOnRoom && settings.alwaysOnRoom.toString() == i.toString()) hasMotion = true
                if (!hasMotion) {
                    def motion = settings["room${i}Motion"]
                    if (!motion) { hasMotion = true } 
                    else {
                        def mList = motion instanceof List ? motion : [motion]
                        if (mList.any { it?.currentValue("motion") == "active" }) {
                            state."lastMotionRoom${i}" = now(); hasMotion = true
                        } else {
                            def lastTime = state."lastMotionRoom${i}"
                            if (lastTime && (now() - lastTime) <= ((settings.audioMotionTimeout ?: 5) * 60 * 1000)) hasMotion = true
                        }
                    }
                }
            }
        }
    }
    return !isMapped || hasMotion
}

def playTTS(speakers, msg, forcePlay = false) {
    if (!speakers || !msg || (!forcePlay && !isAudioAllowed())) return
    def msgList = msg.split(",").collect{ it.trim() }
    def selectedMsg = msgList[new Random().nextInt(msgList.size())]
    def devList = speakers instanceof List ? speakers : [speakers]
    devList.each { dev ->
        if (forcePlay || isSpeakerMotionActive(dev)) {
            try { dev.speak(selectedMsg) } catch (e) { log.error "Failed to play TTS: ${e}" }
        }
    }
}

def playZoozChime(soundNum, forcePlay = false) {
    if (!settings.zoozChimes || soundNum == null || (!forcePlay && !isAudioAllowed())) return
    def trackNum = soundNum.toString().isNumber() ? soundNum.toString().toInteger() : null
    int playCount = 0
    settings.zoozChimes.each { chime ->
        if (forcePlay || isSpeakerMotionActive(chime)) {
            if (playCount > 0) pauseExecution(1000)
            try {
                if (chime.hasCommand("playSound") && trackNum != null) chime.playSound(trackNum)
                else if (chime.hasCommand("playTrack")) chime.playTrack(soundNum.toString())
                else if (chime.hasCommand("chime") && trackNum != null) chime.chime(trackNum)
                playCount++
            } catch (e) { log.error "${chime.displayName} chime command exception: ${e}" }
        }
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
