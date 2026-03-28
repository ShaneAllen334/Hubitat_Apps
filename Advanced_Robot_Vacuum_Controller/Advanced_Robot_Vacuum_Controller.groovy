/**
 * Advanced Robot Vacuum Controller
 * Orchestration app for the Roborock Robot Vacuum driver with Live Dashboard, Master Override, and Dynamic 12-Room Setup.
 */

definition(
    name: "Advanced Robot Vacuum Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced orchestration, Dual-Vacuum support, ROI Analytics, Utilization Logic, and Adaptive Suction.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Vacuum Controller", install: true, uninstall: true) {
        
        section() {
            input "appEnableSwitch", "capability.switch", title: "<b>Master Application Kill Switch (Virtual Switch)</b>", required: false, multiple: false, description: "ON = App Runs normally. OFF = App goes completely dormant.", submitOnChange: true
            if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
                paragraph "<div style='color:red; font-weight:bold; border: 2px solid red; padding: 10px; background-color: #ffebee;'>APPLICATION PAUSED: All automated routines, schedules, and event handlers are currently suspended via the virtual kill switch.</div>"
            }
        }

        List<String> availableRooms = ["All"]
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) {
                availableRooms << settings["roomName_${i}"]
            }
        }

        if (vacuum1 || vacuum2) {
            section("<b>Live System Dashboard</b>", hideable: true, hidden: false) {
                paragraph buildDashboardHTML()
            }
            
            section("<b>Instant Command Center (Clean Now)</b>", hideable: true, hidden: false) {
                String cmdCenterHTML = """
                <table style='width:100%; border-collapse: collapse; font-family: sans-serif; font-size: 13px; border: 1px solid #ccc; background-color: #fcfcfc;'>
                    <tr style='background-color: #333; color: white; text-align: center;'>
                        <th style='padding: 8px;'>AD-HOC DISPATCH TERMINAL</th>
                    </tr>
                    <tr>
                        <td style='padding: 10px; text-align: center; color: #555;'><i>Select targets and parameters below to immediately override schedules and forcefully dispatch the fleet.</i></td>
                    </tr>
                </table>
                """
                paragraph cmdCenterHTML
                input "quickCleanRooms", "enum", title: "Target Rooms", options: availableRooms, multiple: true, submitOnChange: true
                input "quickCleanSuction", "enum", title: "Suction Level", options: ["Quiet", "Balanced", "Turbo", "Max"], defaultValue: "Balanced"
                input "quickCleanWater", "enum", title: "Mop Water Level", options: ["Off", "Low", "Medium", "High"], defaultValue: "Medium"
                if (quickCleanRooms) {
                    input name: "btnExecuteQuickClean", type: "button", title: "🚀 Execute Clean Now"
                }
            }

            section("<b>Event History (Last 25 Events)</b>", hideable: true, hidden: false) {
                paragraph buildHistoryHTML()
            }
        }
        
        section("<b>1. Device Selection & Profiles</b>", hideable: true, hidden: true) {
            input "vacuum1", "capability.actuator", title: "Select Vacuum 1 (Primary)", required: true, multiple: false, submitOnChange: true
            if (vacuum1) {
                input "vac1Brand", "enum", title: "↳ Vacuum 1 Brand Profile (Driver Abstraction)", options: ["Roborock (Community)", "iRobot Roomba (Native)", "Ecovacs/Deebot", "Dreame", "Generic/Switch"], defaultValue: "Roborock (Community)", submitOnChange: true
                input "vac1Model", "enum", title: "↳ Vacuum 1 Phantom Draw Model", options: ["Roborock S8 Series (3W Standby)", "Roborock QRevo Curve (4W Standby)", "Generic Roomba (7W Standby)", "Custom"], defaultValue: "Roborock QRevo Curve (4W Standby)", submitOnChange: true
                if (vac1Model == "Custom") input "vac1Watts", "decimal", title: "↳ Custom Standby Watts (V1)", defaultValue: 3.0
            }
            
            input "vacuum2", "capability.actuator", title: "Select Vacuum 2 (Secondary)", required: false, multiple: false, submitOnChange: true
            if (vacuum2) {
                input "vac2Brand", "enum", title: "↳ Vacuum 2 Brand Profile (Driver Abstraction)", options: ["Roborock (Community)", "iRobot Roomba (Native)", "Ecovacs/Deebot", "Dreame", "Generic/Switch"], defaultValue: "Roborock (Community)", submitOnChange: true
                input "vac2Model", "enum", title: "↳ Vacuum 2 Phantom Draw Model", options: ["Roborock S8 Series (3W Standby)", "Roborock QRevo Curve (4W Standby)", "Generic Roomba (7W Standby)", "Custom"], defaultValue: "Roborock QRevo Curve (4W Standby)", submitOnChange: true
                if (vac2Model == "Custom") input "vac2Watts", "decimal", title: "↳ Custom Standby Watts (V2)", defaultValue: 4.0
            }
            
            input "masterSwitch", "capability.switch", title: "Physical Master Suspend/Resume Switch", required: false, description: "If OFF, all automated routines are ignored and active cleans are paused."
            input "fullCleanSwitch", "capability.switch", title: "Physical Full House Clean Switch", required: false, description: "Trigger full house clean. Bypasses Time/Mode constraints."
        }

        section("<b>2. Smart Room Configuration</b>", hideable: true, hidden: true) {
            paragraph "<i>Configure up to 12 rooms. Define parameters, sequencing, and environmental awareness.</i>"
            
            for (int i = 1; i <= 12; i++) {
                def rName = settings["roomName_${i}"] ?: "Room ${i}"
                def isHidden = settings["enableRoom_${i}"] ? false : true
                
                input "enableRoom_${i}", "bool", title: "<b>Enable ${rName}</b>", defaultValue: false, submitOnChange: true
                if (settings["enableRoom_${i}"]) {
                    input "vacuumAssign_${i}", "enum", title: "  ↳ Assign to Vacuum", options: ["Vacuum 1", "Vacuum 2"], defaultValue: "Vacuum 1", required: true
                    input "roomName_${i}", "text", title: "  ↳ Room Name (e.g., Kitchen)", required: true 
                    input "roomId_${i}", "text", title: "  ↳ Room ID (from vacuum state)", required: false
                    input "roomZone_${i}", "text", title: "  ↳ Optional: Zone Coordinates (Overrides Room ID)", required: false
                    
                    input "roomWater_${i}", "enum", title: "  ↳ Mop Water Level", options: ["Off", "Low", "Medium", "High"], defaultValue: "Medium"
                    input "roomSuction_${i}", "enum", title: "  ↳ Base Suction Power", options: ["Quiet", "Balanced", "Turbo", "Max"], defaultValue: "Balanced"
                    input "roomSeq_${i}", "number", title: "  ↳ Cleaning Sequence Order (1 = First)", defaultValue: i
                    
                    input "roomOccupancyThreshold_${i}", "number", title: "  ↳ Min. Active Minutes to Require Cleaning", defaultValue: 15
                    input "roomHeavyTraffic_${i}", "number", title: "  ↳ Active Mins for Adaptive Turbo Suction", defaultValue: 120
                    input "roomFan_${i}", "capability.switch", title: "  ↳ Post-Clean Fan/Purifier", required: false
                    input "roomFanTimer_${i}", "number", title: "  ↳ Fan Run Time After Vacuum Leaves (Mins)", defaultValue: 15
                    
                    input "roomHumidity_${i}", "capability.relativeHumidityMeasurement", title: "  ↳ Micro-Climate Block (Humidity Sensor)", required: false
                    input "roomHumidityThreshold_${i}", "number", title: "    ↳ Block if Humidity > (%)", defaultValue: 75
                    input "roomMedia_${i}", "capability.musicPlayer", title: "  ↳ Acoustic Adjust (Media Player)", required: false
                    input "roomContact_${i}", "capability.contactSensor", title: "  ↳ Perimeter Halt (Skip if Door Open)", required: false
                    
                    input "roomMotion_${i}", "capability.motionSensor", title: "  ↳ Pre-Check Motion Sensor", required: false
                    input "roomLight_${i}", "capability.switch", title: "  ↳ Pre-Check Lighting (ON = Occupied)", required: false
                    input "roomSwitch_${i}", "capability.switch", title: "  ↳ Individual Room Trigger Switch", required: false
                    paragraph "<hr style='border: 1px solid #eee;'>"
                }
            }
        }

        section("<b>3. Automated Triggers & Modes</b>", hideable: true, hidden: true) {
            input "goodNightMode", "mode", title: "Trigger Sweep on 'Good Night' Mode", required: false, multiple: true
            input "goodNightRooms", "enum", title: "Rooms to clean during Good Night routine", options: availableRooms, required: false, multiple: true
            input "goodNightConflicts", "capability.switch", title: "Device Conflict Block", required: false, multiple: true, description: "Do not start Good Night sweep if these devices are actively ON."
            
            input "fullCleanMode", "mode", title: "Trigger Configured Full Clean on Mode", required: false, multiple: true
            input "fullCleanIgnoreSkip", "bool", title: "Ignore Occupancy Skip Logic (Force Clean All)", defaultValue: false
            input "fullCleanSuction", "enum", title: "Override Base Suction Power", options: ["Quiet", "Balanced", "Turbo", "Max"], required: false
            input "fullCleanWater", "enum", title: "Override Base Mop Water", options: ["Off", "Low", "Medium", "High"], required: false
            
            input "schoolRunSwitch", "capability.switch", title: "School Drop-Off/Pickup Switch", required: false
            input "schoolRunRooms", "enum", title: "Rooms to clean during School Run", options: availableRooms, required: false, multiple: true
        }

        section("<b>4. Global Constraints & Overdue Logic</b>", hideable: true, hidden: true) {
            input "allowedModes", "mode", title: "Allowed Operating Modes (Leave blank for any)", required: false, multiple: true
            input "allowedStartTime", "time", title: "Quiet Hours: Do NOT run BEFORE", required: false
            input "allowedEndTime", "time", title: "Quiet Hours: Do NOT run AFTER", required: false
            input "maxIdleDays", "number", title: "Max days without a clean before forcing a Deep Clean", defaultValue: 3, required: false
        }

        section("<b>5. Scheduled Mop-Only Routines</b>", hideable: true, hidden: true) {
            input "mopDays", "enum", title: "Days to Run Mop-Only", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], required: false, multiple: true
            input "mopTime", "time", title: "Time to Run Mop-Only", required: false
            input "mopRooms", "enum", title: "Rooms to Mop", options: availableRooms, required: false, multiple: true
        }

        section("<b>6. Hardware Protection, Efficiency & ROI</b>", hideable: true, hidden: true) {
            input "pauseGracePeriod", "number", title: "Occupancy Grace Period (Minutes)", defaultValue: 2, required: true
            input "kwRate", "decimal", title: "Electricity Rate (\$ per kWh)", defaultValue: 0.15, required: true
            input "dockPlug1", "capability.switch", title: "Vacuum 1 Dock Smart Plug", required: false
            input "dockPlug2", "capability.switch", title: "Vacuum 2 Dock Smart Plug", required: false
            input "wakeUpTime", "time", title: "Daily Time to Wake Docks (Prep for routines)", required: false
        }

        section("<b>7. Notifications & Maintenance Alerts</b>", hideable: true, hidden: true) {
            input "notifyDevice", "capability.notification", title: "Send Push Notifications To", required: false, multiple: true
            input "notifyTypes", "enum", title: "Select Alert Types to Receive", options: ["Errors", "Bin Full", "Water Low", "Filter Dirty", "Clean Sensors", "Replace Bag"], multiple: true, required: false, defaultValue: ["Errors", "Bin Full", "Water Low", "Filter Dirty", "Clean Sensors", "Replace Bag"]
            input "alertThreshold", "number", title: "Alert when consumables drop below (%)", defaultValue: 5, required: true
            input "autoPauseOnError", "bool", title: "Auto-Pause Vacuum on Error", defaultValue: true
        }
        
        section("<b>8. Logging & Maintenance</b>", hideable: true, hidden: true) {
            input "logEnable", "bool", title: "Enable Informational Logging", defaultValue: true
            input "clearHistory", "bool", title: "Clear Dashboard History", defaultValue: false
            input "clearOccupancy", "bool", title: "Reset All Room Occupancy Counters", defaultValue: false
            input "clearROI", "bool", title: "Reset Financial Savings Counter", defaultValue: false
        }
    }
}

def installed() {
    logInfo "Installed with settings: ${settings}"
    state.history = []
    state.lastCleanTime = now() 
    initialize()
}

def updated() {
    logInfo "Updated with settings: ${settings}"
    if (!state.history) state.history = []
    if (!state.lastCleanTime) state.lastCleanTime = now()
    
    if (settings.clearHistory) {
        state.history = []
        app.updateSetting("clearHistory", [value: "false", type: "bool"])
    }
    if (settings.clearOccupancy) {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"]) state["occMins_${settings["roomName_${i}"]}"] = 0
        }
        app.updateSetting("clearOccupancy", [value: "false", type: "bool"])
    }
    if (settings.clearROI) {
        state.dock1OfflineHours = 0.0
        state.dock2OfflineHours = 0.0
        app.updateSetting("clearROI", [value: "false", type: "bool"])
    }

    initialize()
}

boolean isAppPaused() {
    return (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off")
}

def initialize() {
    unsubscribe()
    unschedule()
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", appEnableHandler)

    if (isAppPaused()) {
        logInfo "App Initialized in PAUSED state via Master Virtual Switch."
        return
    }

    addToHistory("App Initialized and Subscriptions Updated.")
    
    state.v1_intentAction = "Idle"
    state.v1_intentRooms = "--"
    state.v1_maskedRooms = []
    
    state.v2_intentAction = "Idle"
    state.v2_intentRooms = "--"
    state.v2_maskedRooms = []
    
    if (wakeUpTime) schedule(wakeUpTime, "wakeDocks")
    if (mopTime) schedule(mopTime, "mopRoutineHandler")
    if (maxIdleDays) runEvery1Hour("overdueCheckHandler")
    
    if (masterSwitch) subscribe(masterSwitch, "switch", masterSwitchHandler)
    if (fullCleanSwitch) subscribe(fullCleanSwitch, "switch.on", fullCleanHandler)
    if (schoolRunSwitch) subscribe(schoolRunSwitch, "switch.on", schoolRunHandler)
    subscribe(location, "mode", modeHandler)
    
    if (dockPlug1) subscribe(dockPlug1, "switch", dockPlugHandler)
    if (dockPlug2) subscribe(dockPlug2, "switch", dockPlugHandler)
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"]) {
            if (settings["roomSwitch_${i}"]) subscribe(settings["roomSwitch_${i}"], "switch.on", roomSwitchHandler)
            if (settings["roomMotion_${i}"]) subscribe(settings["roomMotion_${i}"], "motion", occupancyHandler)
            if (settings["roomLight_${i}"]) subscribe(settings["roomLight_${i}"], "switch", occupancyHandler)
        }
    }
    
    // Subscribing to specific Roborock Telemetry
    if (vacuum1) {
        subscribe(vacuum1, "state", vacuumStateHandler)
        subscribe(vacuum1, "battery", batteryHandler)
        subscribe(vacuum1, "error", errorHandler)
        subscribe(vacuum1, "dockError", dockErrorHandler)
        subscribe(vacuum1, "remainingFilter", consumableHandler)
        subscribe(vacuum1, "remainingMainBrush", consumableHandler)
        subscribe(vacuum1, "remainingSideBrush", consumableHandler)
        subscribe(vacuum1, "remainingSensors", consumableHandler)
    }
    if (vacuum2) {
        subscribe(vacuum2, "state", vacuumStateHandler)
        subscribe(vacuum2, "battery", batteryHandler)
        subscribe(vacuum2, "error", errorHandler)
        subscribe(vacuum2, "dockError", dockErrorHandler)
        subscribe(vacuum2, "remainingFilter", consumableHandler)
        subscribe(vacuum2, "remainingMainBrush", consumableHandler)
        subscribe(vacuum2, "remainingSideBrush", consumableHandler)
        subscribe(vacuum2, "remainingSensors", consumableHandler)
    }
}

// ==========================================
// VIRTUAL KILL SWITCH HANDLER
// ==========================================

def appEnableHandler(evt) {
    if (evt.value == "off") {
        addToHistory("<span style='color:red;'><b>SYSTEM PAUSED via Master Virtual Switch</b></span>")
        initialize() 
    } else {
        addToHistory("<span style='color:green;'><b>SYSTEM RESUMED via Master Virtual Switch</b></span>")
        initialize() 
    }
}

// ==========================================
// HARDWARE ABSTRACTION LAYER (HAL)
// ==========================================

void commandVacuum(vacDevice, String brand, String cmd) {
    if (!vacDevice) return
    try {
        switch(brand) {
            case "Roborock (Community)":
                if (cmd == "pause") vacDevice.appPause()
                if (cmd == "resume") vacDevice.appRoomResume()
                break
            case "iRobot Roomba (Native)":
            case "Ecovacs/Deebot":
            case "Dreame":
                if (cmd == "pause") vacDevice.pause()
                if (cmd == "resume") vacDevice.resume()
                break
            default:
                if (cmd == "pause") vacDevice.off()
                if (cmd == "resume") vacDevice.on()
                break
        }
    } catch (e) { logInfo("HAL Error sending ${cmd} to ${brand}: ${e}") }
}

void dispatchVacuum(vacDevice, String brand, String type, String target, String water, String suction) {
    if (!vacDevice) return
    try {
        switch(brand) {
            case "Roborock (Community)":
                if (type == "room") vacDevice.appRoomClean(target, water, suction)
                else if (type == "zone") vacDevice.appZoneClean(target)
                break
            case "Dreame":
                if (type == "room") vacDevice.appRoomClean(target, water, suction)
                break
            case "iRobot Roomba (Native)":
            case "Ecovacs/Deebot":
                if (type == "room") vacDevice.cleanRoom(target)
                break
            default:
                vacDevice.on()
                break
        }
    } catch (e) { logInfo("HAL Error dispatching ${type} to ${brand}: ${e}") }
}

// ==========================================
// GATEKEEPER LOGIC (TIME & MODE)
// ==========================================

boolean canRunAutomated() {
    if (isAppPaused()) return false
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return false

    if (allowedModes && !allowedModes.contains(location.mode)) return false

    if (allowedStartTime && allowedEndTime) {
        Date start = timeToday(allowedStartTime, location.timeZone)
        Date end = timeToday(allowedEndTime, location.timeZone)
        Date now = new Date()
        
        if (start.before(end)) {
            if (!(now.after(start) && now.before(end))) return false
        } else {
            if (!(now.after(start) || now.before(end))) return false
        }
    }
    return true
}

// ==========================================
// NOTIFICATION ROUTER
// ==========================================

void sendAlert(String alertType, String msg) {
    if (settings.notifyTypes?.contains(alertType)) {
        if (notifyDevice) notifyDevice.deviceNotification(msg)
        addToHistory("<span style='color:blue;'><b>Push Sent (${alertType}):</b> ${msg}</span>")
    }
}

// ==========================================
// EVENT HANDLERS & SCHEDULED JOBS
// ==========================================

def appButtonHandler(btn) {
    if (isAppPaused()) {
        logInfo "Command Center ignored: Master Virtual Switch is OFF."
        return
    }
    if (btn == "btnExecuteQuickClean") {
        if (settings.quickCleanRooms) {
            wakeDocks()
            addToHistory("<span style='color:purple;'><b>Command Center: Executing Quick Clean</b></span>")
            executeRoomClean(settings.quickCleanRooms, [
                ignoreSkip: true, 
                suction: settings.quickCleanSuction, 
                water: settings.quickCleanWater
            ])
            app.updateSetting("quickCleanRooms", [type: "enum", value: []])
        }
    }
}

def mopRoutineHandler() {
    if (!canRunAutomated()) return
    
    if (mopDays) {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)
        String day = df.format(new Date())
        if (!mopDays.contains(day)) return
    }
    
    if (mopRooms) {
        wakeDocks()
        addToHistory("<span style='color:teal;'><b>Triggered: Scheduled Mop-Only Routine</b></span>")
        executeRoomClean(mopRooms, [ignoreSkip: false, suction: "Off", water: "High"])
    }
}

def overdueCheckHandler() {
    if (isAppPaused()) return
    if (!maxIdleDays || !state.lastCleanTime) return
    
    long daysIdle = (now() - state.lastCleanTime) / 86400000
    if (daysIdle >= maxIdleDays) {
        if (canRunAutomated()) {
            wakeDocks()
            addToHistory("<span style='color:purple;'><b>Overdue Catcher: ${daysIdle} days idle. Forcing Deep Clean!</b></span>")
            executeRoomClean(["All"], [ignoreSkip: true, suction: "Max", water: "High"])
        }
    }
}

def consumableHandler(evt) {
    if (isAppPaused()) return
    String part = evt.name?.toLowerCase()
    int value = (evt.value ?: "100") as Integer
    int threshold = settings.alertThreshold ?: 5
    
    if (value <= threshold) {
        String msg = "${evt.device.displayName} Maintenance: ${part.capitalize()} life is at ${value}%."
        if (part.contains("filter")) sendAlert("Filter Dirty", msg)
        else if (part.contains("sensor")) sendAlert("Clean Sensors", msg)
        else if (part.contains("bag") || part.contains("dust")) sendAlert("Replace Bag", msg)
    }
}

def dockErrorHandler(evt) {
    if (isAppPaused()) return
    String err = evt.value?.toString()?.toLowerCase()
    if (err == "no error" || err == "ok" || err == "0") return
    
    if (err.contains("water empty") || err.contains("water low")) {
        sendAlert("Water Low", "${evt.device.displayName} Dock: Water Empty. Refill required.")
    } else if (err.contains("dust") || err.contains("bag")) {
        sendAlert("Replace Bag", "${evt.device.displayName} Dock: ${evt.value.capitalize()}")
    } else {
        sendAlert("Errors", "${evt.device.displayName} Dock Alert: ${evt.value.capitalize()}")
    }
    addToHistory("<span style='color:red;'><b>Dock Error: ${evt.value}</b></span>")
}

def batteryHandler(evt) {
    if (isAppPaused()) return
    if (evt.value == "100") {
        if (evt.deviceId == vacuum1?.id && dockPlug1 && dockPlug1.currentValue("switch") != "off" && vacuum1.currentValue("state") in ["charging", "charged", "docked"]) {
            dockPlug1.off()
            addToHistory("V1 Dock Powered OFF (Phantom Draw Assassin)")
        }
        if (evt.deviceId == vacuum2?.id && dockPlug2 && dockPlug2.currentValue("switch") != "off" && vacuum2.currentValue("state") in ["charging", "charged", "docked"]) {
            dockPlug2.off()
            addToHistory("V2 Dock Powered OFF (Phantom Draw Assassin)")
        }
    }
}

def dockPlugHandler(evt) {
    if (isAppPaused()) return
    if (evt.value == "off") {
        if (evt.deviceId == dockPlug1?.id && !state.dock1OffTime) state.dock1OffTime = now()
        if (evt.deviceId == dockPlug2?.id && !state.dock2OffTime) state.dock2OffTime = now()
    } else if (evt.value == "on") {
        if (evt.deviceId == dockPlug1?.id && state.dock1OffTime) {
            state.dock1OfflineHours = (state.dock1OfflineHours ?: 0.0) + ((now() - state.dock1OffTime) / 3600000.0)
            state.dock1OffTime = null
        }
        if (evt.deviceId == dockPlug2?.id && state.dock2OffTime) {
            state.dock2OfflineHours = (state.dock2OfflineHours ?: 0.0) + ((now() - state.dock2OffTime) / 3600000.0)
            state.dock2OffTime = null
        }
    }
}

def wakeDocks() {
    if (isAppPaused()) return
    if (dockPlug1 && dockPlug1.currentValue("switch") != "on") dockPlug1.on()
    if (dockPlug2 && dockPlug2.currentValue("switch") != "on") dockPlug2.on()
    addToHistory("Docks Powered ON (Scheduled Wake-Up)")
}

def vacuumStateHandler(evt) {
    if (isAppPaused()) return
    String vState = evt.value?.toString()?.toLowerCase()
    
    if (vState in ["charging", "charged", "docked", "returning to dock", "idle"]) {
        if (evt.device.id == vacuum1?.id && state.v1_maskedRooms?.size() > 0) {
            state.v1_maskedRooms.each { rName -> triggerFanCountdown(rName) }
            state.v1_maskedRooms = []
            state.v1_intentAction = "Idle"
            state.v1_intentRooms = "--"
        }
        if (evt.device.id == vacuum2?.id && state.v2_maskedRooms?.size() > 0) {
            state.v2_maskedRooms.each { rName -> triggerFanCountdown(rName) }
            state.v2_maskedRooms = []
            state.v2_intentAction = "Idle"
            state.v2_intentRooms = "--"
        }
    }
}

def triggerFanCountdown(String roomName) {
    if (isAppPaused()) return
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"] && settings["roomName_${i}"] == roomName && settings["roomFan_${i}"]) {
            int delayMins = settings["roomFanTimer_${i}"] ?: 15
            runIn(delayMins * 60, "turnOffFan", [data: [roomId: i], overwrite: true])
            addToHistory("${roomName} Dust Settler: Fan off in ${delayMins}m.")
        }
    }
}

def turnOffFan(data) {
    if (isAppPaused()) return
    def fan = settings["roomFan_${data.roomId}"]
    if (fan) {
        fan.off()
        addToHistory("${settings["roomName_${data.roomId}"]} Dust Settler: Complete (Fan OFF)")
    }
}

def masterSwitchHandler(evt) {
    if (isAppPaused()) return
    if (evt.value == "off") {
        addToHistory("<span style='color:orange;'>Master Switch: OFF (Suspended)</span>")
        if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) commandVacuum(vacuum1, settings.vac1Brand, "pause")
        if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) commandVacuum(vacuum2, settings.vac2Brand, "pause")
        unschedule("resumeVacuum1")
        unschedule("resumeVacuum2")
    } else {
        addToHistory("<span style='color:green;'>Master Switch: ON (Resumed)</span>")
    }
}

def occupancyHandler(evt) {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    
    String triggeredRoom = ""
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"]) {
            if (settings["roomMotion_${i}"]?.id == evt.deviceId || settings["roomLight_${i}"]?.id == evt.deviceId) {
                triggeredRoom = settings["roomName_${i}"]
                break
            }
        }
    }
    if (!triggeredRoom) return

    if (evt.name == "motion") {
        if (evt.value == "active") {
            state["motionStart_${triggeredRoom}"] = now()
        } else if (evt.value == "inactive") {
            long start = state["motionStart_${triggeredRoom}"] ?: now()
            int mins = ((now() - start) / 60000) as Integer
            if (mins > 0) {
                state["occMins_${triggeredRoom}"] = (state["occMins_${triggeredRoom}"] ?: 0) + mins
            }
        }
    }

    if (state.v1_maskedRooms?.contains(triggeredRoom) || state.v2_maskedRooms?.contains(triggeredRoom)) return 

    if (evt.value == "active" || evt.value == "on") {
        if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("<span style='color:orange;'>V1 Paused: Intrusion in ${triggeredRoom}</span>")
            commandVacuum(vacuum1, settings.vac1Brand, "pause")
            unschedule("resumeVacuum1")
        }
        if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("<span style='color:orange;'>V2 Paused: Intrusion in ${triggeredRoom}</span>")
            commandVacuum(vacuum2, settings.vac2Brand, "pause")
            unschedule("resumeVacuum2")
        }
    } else if (evt.value == "inactive" || evt.value == "off") {
        int delaySeconds = (settings.pauseGracePeriod ?: 2) * 60
        if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() == "paused") {
            addToHistory("V1 Timer: ${triggeredRoom} clear. Resuming in ${settings.pauseGracePeriod}m.")
            runIn(delaySeconds, "resumeVacuum1", [overwrite: true])
        }
        if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() == "paused") {
            addToHistory("V2 Timer: ${triggeredRoom} clear. Resuming in ${settings.pauseGracePeriod}m.")
            runIn(delaySeconds, "resumeVacuum2", [overwrite: true])
        }
    }
}

def resumeVacuum1() {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() == "paused") {
        addToHistory("<span style='color:green;'>V1 Resumed: Grace Period Complete</span>")
        commandVacuum(vacuum1, settings.vac1Brand, "resume")
    }
}

def resumeVacuum2() {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() == "paused") {
        addToHistory("<span style='color:green;'>V2 Resumed: Grace Period Complete</span>")
        commandVacuum(vacuum2, settings.vac2Brand, "resume")
    }
}

def modeHandler(evt) {
    if (isAppPaused()) return
    if (goodNightMode && evt.value in goodNightMode) {
        if (!canRunAutomated()) {
            addToHistory("Good Night Blocked: Global Constraints Active")
            return
        }
        if (goodNightConflicts) {
            def activeConflicts = goodNightConflicts.findAll { it.currentValue("switch") == "on" }
            if (activeConflicts.size() > 0) {
                def conflictNames = activeConflicts.collect { it.displayName }.join(", ")
                addToHistory("<span style='color:orange;'>Good Night Blocked: Active Devices (${conflictNames})</span>")
                return
            }
        }
        wakeDocks() 
        addToHistory("Triggered: Good Night Sweep")
        executeRoomClean(goodNightRooms, [ignoreSkip: false])
    }
    
    if (fullCleanMode && evt.value in fullCleanMode) {
        if (!canRunAutomated()) return
        wakeDocks()
        addToHistory("Triggered: Configurable Full Clean Mode")
        executeRoomClean(["All"], [
            ignoreSkip: settings.fullCleanIgnoreSkip, 
            suction: settings.fullCleanSuction, 
            water: settings.fullCleanWater
        ])
    }
}

def fullCleanHandler(evt) {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    wakeDocks()
    addToHistory("Triggered: Full Clean Switch (Manual Override)")
    executeRoomClean(["All"], [ignoreSkip: true]) 
}

def roomSwitchHandler(evt) {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    wakeDocks()
    for (int i = 1; i <= 12; i++) {
        def sw = settings["roomSwitch_${i}"]
        if (settings["enableRoom_${i}"] && sw && sw.id == evt.deviceId) {
            executeRoomClean([settings["roomName_${i}"]], [ignoreSkip: true])
            break
        }
    }
}

def schoolRunHandler(evt) {
    if (isAppPaused()) return
    if (!canRunAutomated()) {
        addToHistory("School Run Blocked: Global Constraints Active")
        return
    }
    wakeDocks()
    addToHistory("Triggered: School Run Routine")
    executeRoomClean(schoolRunRooms, [ignoreSkip: false])
}

def errorHandler(evt) {
    if (isAppPaused()) return
    String errorMsg = evt.value?.toString()?.toLowerCase()
    if (errorMsg == "no error" || errorMsg == "0") return
    
    String msg = "${evt.device.displayName} Error: ${evt.value}"
    addToHistory("<span style='color:red;'><b>${msg}</b></span>")
    
    sendAlert("Errors", msg)
    
    if (autoPauseOnError) {
        def brand = (evt.device.id == vacuum1?.id) ? settings.vac1Brand : settings.vac2Brand
        commandVacuum(evt.device, brand, "pause")
    }
}

// ==========================================
// CORE LOGIC: PRE-EVALUATION & DISPATCH
// ==========================================

void executeRoomClean(List selectedRoomNames, Map options = [:]) {
    if (isAppPaused()) return
    boolean ignoreSkip = options.ignoreSkip ?: false
    String overrideSuction = options.suction
    String overrideWater = options.water

    if (!selectedRoomNames) return

    if (selectedRoomNames.contains("All")) {
        List<String> allActive = []
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) allActive << settings["roomName_${i}"]
        }
        selectedRoomNames = allActive
    }

    def v1Queue = []
    def v2Queue = []
    def skippedOccupied = []
    def skippedClean = []

    for (String targetName : selectedRoomNames) {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"] == targetName) {
                
                if (settings["roomContact_${i}"] && settings["roomContact_${i}"].currentValue("contact") == "open") {
                    skippedClean << "${targetName} (Perimeter Open)"
                    continue
                }

                if (settings["roomHumidity_${i}"]) {
                    def currentHum = settings["roomHumidity_${i}"].currentValue("humidity") ?: 0
                    def limitHum = settings["roomHumidityThreshold_${i}"] ?: 75
                    if (currentHum > limitHum) {
                        skippedClean << "${targetName} (High Humidity: ${currentHum}%)"
                        continue
                    }
                }
                
                int occMins = state["occMins_${targetName}"] ?: 0
                int minThreshold = settings["roomOccupancyThreshold_${i}"] ?: 15
                int heavyThreshold = settings["roomHeavyTraffic_${i}"] ?: 120
                
                if (!ignoreSkip && occMins < minThreshold) {
                    skippedClean << "${targetName} (${occMins}/${minThreshold}m)"
                    continue 
                }

                boolean isOccupied = false
                if (settings["roomMotion_${i}"] && settings["roomMotion_${i}"].currentValue("motion") == "active") isOccupied = true
                if (settings["roomLight_${i}"] && settings["roomLight_${i}"].currentValue("switch") == "on") isOccupied = true
                
                if (isOccupied) {
                    skippedOccupied << targetName
                    continue
                }

                String finalSuction = overrideSuction ?: settings["roomSuction_${i}"] ?: "Balanced"
                
                if (settings["roomMedia_${i}"]) {
                    def pState = settings["roomMedia_${i}"].currentValue("status") ?: settings["roomMedia_${i}"].currentValue("state")
                    if (pState?.toString()?.toLowerCase() == "playing") {
                        finalSuction = "Quiet"
                        addToHistory("Acoustic Override: ${targetName} set to Quiet (Media Playing)")
                    }
                } else if (!overrideSuction && occMins >= heavyThreshold) {
                    finalSuction = "Turbo"
                    addToHistory("Adaptive Suction: ${targetName} set to Turbo (${occMins}m traffic)")
                }

                def roomData = [
                    name: targetName,
                    id: settings["roomId_${i}"],
                    zone: settings["roomZone_${i}"],
                    water: overrideWater ?: settings["roomWater_${i}"] ?: "Medium",
                    suction: finalSuction,
                    seq: settings["roomSeq_${i}"] ?: 99,
                    index: i
                ]
                if (settings["vacuumAssign_${i}"] == "Vacuum 2") v2Queue << roomData
                else v1Queue << roomData
            }
        }
    }

    if (skippedOccupied.size() > 0) addToHistory("<span style='color:orange;'>Skipped (Occupied): ${skippedOccupied.join(', ')}</span>")
    if (skippedClean.size() > 0) addToHistory("<span style='color:gray;'>Skipped (Condition/Clean): ${skippedClean.join(', ')}</span>")

    if (v1Queue.size() > 0 || v2Queue.size() > 0) {
        state.lastCleanTime = now()
    }

    if (v1Queue.size() > 0 && vacuum1) {
        v1Queue.sort { it.seq }
        state.v1_maskedRooms = v1Queue.collect { it.name }
        state.v1_intentAction = "Dispatched (Sequence)"
        state.v1_intentRooms = state.v1_maskedRooms.join(", ")
        
        v1Queue.each { room ->
            if (settings["roomFan_${room.index}"]) {
                settings["roomFan_${room.index}"].on()
                addToHistory("${room.name} Dust Settler: Fan ON")
            }
            if (room.zone) dispatchVacuum(vacuum1, settings.vac1Brand, "zone", room.zone, "", "")
            else if (room.id) dispatchVacuum(vacuum1, settings.vac1Brand, "room", room.id, room.water, room.suction)
            
            state["occMins_${room.name}"] = 0 
            pauseExecution(2500)
        }
    }

    if (v2Queue.size() > 0 && vacuum2) {
        v2Queue.sort { it.seq }
        state.v2_maskedRooms = v2Queue.collect { it.name }
        state.v2_intentAction = "Dispatched (Sequence)"
        state.v2_intentRooms = state.v2_maskedRooms.join(", ")
        
        v2Queue.each { room ->
            if (settings["roomFan_${room.index}"]) {
                settings["roomFan_${room.index}"].on()
                addToHistory("${room.name} Dust Settler: Fan ON")
            }
            if (room.zone) dispatchVacuum(vacuum2, settings.vac2Brand, "zone", room.zone, "", "")
            else if (room.id) dispatchVacuum(vacuum2, settings.vac2Brand, "room", room.id, room.water, room.suction)
            
            state["occMins_${room.name}"] = 0 
            pauseExecution(2500)
        }
    }
}

void logInfo(String msg) {
    if (settings?.logEnable) log.info "${app.name}: ${msg}"
}

// ==========================================
// HTML DASHBOARD GENERATION
// ==========================================

void addToHistory(String event) {
    if (!state.history) state.history = []
    String timeStamp = new Date().format("MM/dd/yy hh:mm:ss a", location.timeZone)
    state.history.add(0, "[${timeStamp}] ${event}")
    if (state.history.size() > 25) state.history = state.history[0..24] 
}

String buildHistoryHTML() {
    if (!state.history || state.history.size() == 0) return "<div style='padding:10px; border: 1px solid #ccc; border-radius: 5px; background-color: #fcfcfc;'><i>No events logged yet.</i></div>"
    
    String html = "<div style='max-height: 250px; overflow-y: auto; padding: 10px; border: 1px solid #ccc; border-radius: 5px; background-color: #fcfcfc; font-size: 13px; font-family: monospace;'>"
    state.history.each { item -> html += "<div style='margin-bottom: 4px; border-bottom: 1px solid #eee; padding-bottom: 2px;'>${item}</div>" }
    html += "</div>"
    return html
}

double getVacWatts(String modelStr, Double customWatts) {
    if (modelStr?.contains("S8")) return 3.0
    if (modelStr?.contains("QRevo")) return 4.0
    if (modelStr?.contains("Roomba")) return 7.0
    return customWatts ?: 3.0
}

String getConsumableVal(vacDevice, String attr) {
    def val = vacDevice.currentValue(attr)
    return val != null ? "${val}%" : "--"
}

String generateVacuumTable(vacDevice, String vacTitle, String intentAction, String intentRooms) {
    if (!vacDevice) return ""
    String vState = vacDevice.currentValue("state") ?: "Unknown"
    String vBat = vacDevice.currentValue("battery") ?: "--"
    String vErr = vacDevice.currentValue("error") ?: "No error"
    String dockErr = vacDevice.currentValue("dockError") ?: "No error"
    
    String cleanArea = vacDevice.currentValue("cleanArea") ?: "--"
    String cleanTime = vacDevice.currentValue("cleanTime") ?: "--"

    String stateColor = "black"
    if (vState.toLowerCase() in ["cleaning", "room clean", "zone clean"]) stateColor = "green"
    if (vState.toLowerCase() in ["charging", "charged"]) stateColor = "blue"
    if (vState.toLowerCase() in ["paused", "returning to dock"]) stateColor = "orange"

    // Map to specific Qrevo Curv attributes
    String filter = getConsumableVal(vacDevice, "remainingFilter") ?: getConsumableVal(vacDevice, "filter")
    String mBrush = getConsumableVal(vacDevice, "remainingMainBrush") ?: getConsumableVal(vacDevice, "mainBrush")
    String sBrush = getConsumableVal(vacDevice, "remainingSideBrush") ?: getConsumableVal(vacDevice, "sideBrush")
    String sensor = getConsumableVal(vacDevice, "remainingSensors") ?: getConsumableVal(vacDevice, "sensor")
    
    String html = "<div style='margin-bottom: 15px;'><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
    html += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th colspan='4' style='padding: 8px;'>${vacTitle}</th></tr>"
    
    html += "<tr style='border-bottom: 1px solid #ddd;'>"
    html += "<td style='padding: 8px; width: 25%;'><b>State</b></td><td style='padding: 8px; width: 25%; color: ${stateColor}; font-weight: bold;'>${vState.capitalize()}</td>"
    html += "<td style='padding: 8px; width: 25%;'><b>Battery</b></td><td style='padding: 8px; width: 25%; font-weight: bold;'>${vBat}%</td>"
    html += "</tr>"
    
    html += "<tr style='border-bottom: 1px solid #ddd;'>"
    html += "<td style='padding: 8px;'><b>App Action</b></td><td style='padding: 8px; color: #0066cc;'><b>${intentAction}</b></td>"
    html += "<td style='padding: 8px;'><b>App Target</b></td><td style='padding: 8px;'>${intentRooms}</td>"
    html += "</tr>"

    html += "<tr style='border-bottom: 1px solid #ddd; background-color: #f9f9f9;'>"
    html += "<td style='padding: 8px;'><b>Last Clean Area</b></td><td style='padding: 8px;'>${cleanArea} m²</td>"
    html += "<td style='padding: 8px;'><b>Last Clean Time</b></td><td style='padding: 8px;'>${cleanTime} mins</td>"
    html += "</tr>"

    String errColor = (vErr.toLowerCase() in ['no error', 'ok', '0']) ? "green" : "red"
    String dockErrColor = (dockErr.toLowerCase() in ['no error', 'ok', '0']) ? "green" : "red"
    
    html += "<tr style='border-bottom: 2px solid #ddd;'>"
    html += "<td style='padding: 8px;'><b>Hardware Error</b></td><td style='padding: 8px; color: ${errColor}; font-weight:bold;'>${vErr.capitalize()}</td>"
    html += "<td style='padding: 8px;'><b>Dock Status</b></td><td style='padding: 8px; color: ${dockErrColor}; font-weight:bold;'>${dockErr.capitalize()}</td>"
    html += "</tr>"

    html += "<tr style='background-color: #f0f0f0; text-align: center; font-size: 11px;'><th style='padding: 4px;'>Filter</th><th style='padding: 4px;'>Main Brush</th><th style='padding: 4px;'>Side Brush</th><th style='padding: 4px;'>Sensors</th></tr>"
    html += "<tr style='text-align: center; font-size: 12px; border-bottom: 1px solid #ccc;'><td style='padding: 4px;'>${filter}</td><td style='padding: 4px;'>${mBrush}</td><td style='padding: 4px;'>${sBrush}</td><td style='padding: 4px;'>${sensor}</td></tr>"

    html += "</table></div>"
    return html
}

String buildDashboardHTML() {
    String html = ""
    if (isAppPaused()) {
        html += "<div style='margin-bottom: 15px; padding: 10px; background: #ffebee; border: 2px solid red; border-radius: 4px; text-align: center; font-weight: bold; color: red;'>SYSTEM PAUSED VIA MASTER VIRTUAL SWITCH</div>"
    }

    if (vacuum1) html += generateVacuumTable(vacuum1, "Vacuum 1 (Primary)", state.v1_intentAction ?: "Idle", state.v1_intentRooms ?: "--")
    if (vacuum2) html += generateVacuumTable(vacuum2, "Vacuum 2 (Secondary)", state.v2_intentAction ?: "Idle", state.v2_intentRooms ?: "--")
    
    List<String> utilList = []
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) {
            int mins = state["occMins_${settings["roomName_${i}"]}"] ?: 0
            utilList << "<b>${settings["roomName_${i}"]}:</b> ${mins}m"
        }
    }
    
    boolean isPaused = masterSwitch ? (masterSwitch.currentValue("switch") == "off") : false
    String globalStatus = isPaused ? "<span style='color: red; font-weight: bold;'>PAUSED (Physical Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
        
    html += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 13px;'><b>Hardware Interlock:</b> ${globalStatus}</div>"
    
    long daysIdle = state.lastCleanTime ? ((now() - state.lastCleanTime) / 86400000) : 0
    html += "<div style='margin-top: 10px; padding: 8px; background: #fff3e0; border: 1px solid #ffe0b2; border-radius: 4px; font-size: 13px; color: #e65100;'><b>Time Since Last Dispatch:</b> ${daysIdle} Days</div>"
    
    if (utilList.size() > 0) {
        html += "<div style='margin-top: 10px; padding: 8px; background: #f4f4f4; border: 1px solid #ddd; border-radius: 4px; font-size: 12px; color: #555;'><b>Current Room Utilization:</b> ${utilList.join(' | ')}</div>"
    }

    double v1Watts = getVacWatts(settings.vac1Model, settings.vac1Watts)
    double v2Watts = getVacWatts(settings.vac2Model, settings.vac2Watts)
    
    double currentV1Off = (state.dock1OffTime) ? ((now() - state.dock1OffTime) / 3600000.0) : 0.0
    double currentV2Off = (state.dock2OffTime) ? ((now() - state.dock2OffTime) / 3600000.0) : 0.0
    
    double totalV1Hours = (state.dock1OfflineHours ?: 0.0) + currentV1Off
    double totalV2Hours = (state.dock2OfflineHours ?: 0.0) + currentV2Off
    
    double kwhSaved = ((totalV1Hours * v1Watts) + (totalV2Hours * v2Watts)) / 1000.0
    double moneySaved = kwhSaved * (settings.kwRate ?: 0.15)
    
    if (kwhSaved > 0) {
        html += "<div style='margin-top: 10px; padding: 8px; background: #e8f5e9; border: 1px solid #c8e6c9; border-radius: 4px; font-size: 13px; color: #2e7d32;'>"
        html += "<b>Financial & Energy ROI:</b> System has prevented <b>${String.format("%.2f", kwhSaved)} kWh</b> of phantom draw, saving <b>\$${String.format("%.2f", moneySaved)}</b>.</div>"
    }

    return html
}
