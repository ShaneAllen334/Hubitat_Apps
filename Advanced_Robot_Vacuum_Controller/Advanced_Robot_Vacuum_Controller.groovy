/**
 * Advanced Robot Vacuum Controller
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
                input name: "btnRefresh", type: "button", title: "🔄 Refresh Data & Re-Evaluate Power"
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
                input "quickCleanSuction", "enum", title: "Suction Level", options: ["Quiet", "Balanced", "Turbo", "Max", "Off"], defaultValue: "Balanced"
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
            
            if (vacuum1) {
                input name: "btnSyncRooms", type: "button", title: "🔄 Auto-Sync Rooms from Vacuum 1"
                paragraph "<i><span style='color: #666;'>Clicking Auto-Sync will pull the room map directly from Vacuum 1. It will automatically enable the slots, name the rooms, and insert the IDs below.</span></i>"
                paragraph "<hr style='border: 1px solid #ccc;'>"
            }
            
            if (vacuum2) {
                input name: "btnSyncRoomsV2", type: "button", title: "🔄 Auto-Sync Rooms from Vacuum 2"
                paragraph "<i><span style='color: #666;'>Clicking this pulls the room map from Vacuum 2 into any available empty slots below, automatically assigning them to Vacuum 2.</span></i>"
                paragraph "<hr style='border: 1px solid #ccc;'>"
            }
            
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
                    input "roomSuction_${i}", "enum", title: "  ↳ Base Suction Power", options: ["Quiet", "Balanced", "Turbo", "Max", "Off"], defaultValue: "Balanced"
                    input "roomSeq_${i}", "number", title: "  ↳ Cleaning Sequence Order (1 = First)", defaultValue: i
                    
                    input "roomOccupancyThreshold_${i}", "number", title: "  ↳ Min. Active Minutes to Require Cleaning", defaultValue: 15
                    input "roomHeavyTraffic_${i}", "number", title: "  ↳ Active Mins for Adaptive Turbo Suction", defaultValue: 120
                    input "disableML_${i}", "bool", title: "  ↳ Disable ML Tracking (Use Basic Sensors Only)", defaultValue: false, description: "Bypasses the accumulator algorithms. Ideal for Guest Rooms."

                    input "roomFan_${i}", "capability.switch", title: "  ↳ Post-Clean Fan/Purifier", required: false
                    input "roomFanTimer_${i}", "number", title: "  ↳ Fan Run Time After Vacuum Leaves (Mins)", defaultValue: 15
                    
                    input "roomHumidity_${i}", "capability.relativeHumidityMeasurement", title: "  ↳ Micro-Climate Block (Humidity Sensor)", required: false
                    input "roomHumidityThreshold_${i}", "number", title: "    ↳ Block if Humidity > (%)", defaultValue: 75
                    input "roomMedia_${i}", "capability.musicPlayer", title: "  ↳ Acoustic Adjust (Media Player)", required: false
                    input "roomContact_${i}", "capability.contactSensor", title: "  ↳ Perimeter Halt (Skip if Door Open)", required: false
                    
                    paragraph "<b>Duration-Based Virtual Trigger:</b>"
                    input "roomVirtualOcc_${i}", "capability.switch", title: "  ↳ Virtual Occupancy Switch", required: false
                    input "roomVirtualOccTime_${i}", "number", title: "    ↳ Min. Minutes ON to trigger clean when turned OFF", defaultValue: 30

                    paragraph "<b>Occupancy Arrays (Deduplicated Tracking):</b>"
                    input "roomBedPresence_${i}", "capability.presenceSensor", title: "  ↳ Bed Occupancy Pad (Settle Lockout Mode)", required: false, multiple: true
                    input "roomBedSwitch_${i}", "capability.switch", title: "  ↳ Bed Occupancy Switch (Settle Lockout Mode)", required: false, multiple: true
                    input "roomMotion_${i}", "capability.motionSensor", title: "  ↳ Motion Sensors (Accumulates Usage Time)", required: false, multiple: true
                    input "roomBypass_${i}", "capability.motionSensor", title: "  ↳ Real-Time Bypass Sensors (Skips Clean if Active)", required: false, multiple: true
                    input "roomLight_${i}", "capability.switch", title: "  ↳ Pre-Check Lighting (ON = Occupied)", required: false, multiple: true
                    input "roomTV_${i}", "capability.switch", title: "  ↳ Pre-Check TVs/Displays (ON = Occupied)", required: false, multiple: true
                    input "roomSwitch_${i}", "capability.switch", title: "  ↳ Individual Room Trigger Switch", required: false
                    paragraph "<hr style='border: 1px solid #eee;'>"
                }
            }
        }

        section("<b>3. Automated Triggers & Modes</b>", hideable: true, hidden: true) {
            paragraph "<b>Presence-Based Interruption & Resume:</b>"
            input "arrivalModes", "mode", title: "Arrival Modes (Pause & Return to Dock)", multiple: true, required: false, description: "Interrupts active vacuums to prevent disturbance when users arrive."
            input "awayModes", "mode", title: "Departure Modes (Resume Unfinished Cleans)", multiple: true, required: false, description: "Resumes vacuums previously interrupted by an arrival mode."

            paragraph "<b>Post-Sickness Deep Clean:</b>"
            input "sickSwitches", "capability.switch", title: "Sick Mode Virtual Switches", required: false, multiple: true, description: "Triggers a Max Full Clean when all switches are turned off (after at least one was on)."

            paragraph "<b>Guest Mode Departure Cleanup:</b>"
            input "guestModeSwitch", "capability.switch", title: "Guest Mode Virtual Switch", required: false, description: "Forces a Full House Max Clean when turned OFF."

            paragraph "<b>Appliance Telemetry (Kitchen Sweep):</b>"
            input "dishwasherPlug", "capability.powerMeter", title: "Dishwasher Smart Plug (Power Telemetry)", required: false
            input "dishwasherThreshold", "decimal", title: "Active Power Threshold (Watts)", defaultValue: 15.0
            input "dishwasherRooms", "enum", title: "Rooms to clean after cycle completes", options: availableRooms, multiple: true, required: false

            paragraph "<b>High-Traffic Exterior Transitions (Mudroom):</b>"
            input "exteriorDoor", "capability.contactSensor", title: "High-Traffic Exterior Door", required: false, multiple: true
            input "doorCountThreshold", "number", title: "Openings required to trigger sweep", defaultValue: 3
            input "doorTimeWindow", "number", title: "Time window for openings (Minutes)", defaultValue: 15
            input "mudroomRooms", "enum", title: "Rooms to clean after high traffic", options: availableRooms, multiple: true, required: false

            paragraph "<b>Good Night Logic & Quiet Sweep:</b>"
            input "goodNightSwitch", "capability.switch", title: "Good Night Toggle Switch (Suspends Motion Tracking)", required: false
            input "goodNightMode", "mode", title: "Trigger Sweep on 'Good Night' Mode", required: false, multiple: true
            input "goodNightRooms", "enum", title: "Rooms to clean during Good Night routine", options: availableRooms, required: false, multiple: true
            input "goodNightSuction", "enum", title: "Override Suction Power (Night Mode)", options: ["Quiet", "Balanced", "Turbo", "Max", "Off"], defaultValue: "Quiet", required: false
            input "goodNightWater", "enum", title: "Override Mop Water (Night Mode)", options: ["Off", "Low", "Medium", "High"], defaultValue: "Off", required: false
            input "goodNightConflicts", "capability.switch", title: "Device Conflict Block", required: false, multiple: true, description: "Do not start Good Night sweep if these devices are actively ON."
            
            paragraph "<b>Configurable Full House Clean Mode:</b>"
            input "fullCleanMode", "mode", title: "Trigger Configured Full Clean on Mode", required: false, multiple: true
            input "fullCleanDelay", "number", title: "Settle Delay (Minutes) before dispatching", defaultValue: 10, required: true, description: "Allows motion sensors to clear and doors to close before checking occupancy."
            input "fullCleanBypass", "bool", title: "Bypass Global Time/Mode Constraints for Mode Clean", defaultValue: true, description: "Forces clean to run regardless of your Allowed Modes or Quiet Hours."
            input "fullCleanNotifyFail", "bool", title: "Send Push Notification if Mode Clean Fails/Blocked", defaultValue: true
            input "fullCleanIgnoreSkip", "bool", title: "Ignore Occupancy Skip Logic (Force Clean All)", defaultValue: false
            input "fullCleanSuction", "enum", title: "Override Base Suction Power", options: ["Quiet", "Balanced", "Turbo", "Max"], required: false
            input "fullCleanWater", "enum", title: "Override Base Mop Water", options: ["Off", "Low", "Medium", "High"], required: false
            
            paragraph "<b>Post-Meal Cleanup (Ecosystem Link):</b>"
            input "mealTimeSwitch", "capability.switch", title: "Meal Time Virtual Switch", required: false
            input "mealTimeRooms", "enum", title: "Rooms to clean after Meal Time ends", options: availableRooms, required: false, multiple: true
            
            paragraph "<b>Other Triggers:</b>"
            input "schoolRunSwitch", "capability.switch", title: "School Drop-Off/Pickup Switch", required: false
            input "schoolRunRooms", "enum", title: "Rooms to clean during School Run", options: availableRooms, required: false, multiple: true
        }

        section("<b>4. Global Constraints & Overdue Logic</b>", hideable: true, hidden: true) {
            input "pauseOnIntrusion", "bool", title: "Pause Active Cleans on Intrusion (Real-Time Interruption)", defaultValue: false
            input "allowedModes", "mode", title: "Allowed Operating Modes (Leave blank for any)", required: false, multiple: true
            input "allowedStartTime", "time", title: "Quiet Hours: Do NOT run BEFORE", required: false
            input "allowedEndTime", "time", title: "Quiet Hours: Do NOT run AFTER", required: false
            input "maxIdleDays", "number", title: "Max days without a FULL clean before forcing a Deep Clean", defaultValue: 3, required: false
        }

        section("<b>5. Scheduled Specialized Routines (Mopping & Dry Dusting)</b>", hideable: true, hidden: true) {
            paragraph "<b>Wet Mop Only (No Suction):</b>"
            input "mopDays", "enum", title: "Days to Run Mop-Only", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], required: false, multiple: true
            input "mopTime", "time", title: "Time to Run Mop-Only", required: false
            input "mopRooms", "enum", title: "Rooms to Wet Mop", options: availableRooms, required: false, multiple: true

            paragraph "<b>Dry Dusting Only (No Suction, No Water):</b>"
            input "dryMopDays", "enum", title: "Days to Run Dry Dusting", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], required: false, multiple: true
            input "dryMopTime", "time", title: "Time to Run Dry Dusting", required: false
            input "dryMopRooms", "enum", title: "Rooms to Dry Dust", options: availableRooms, required: false, multiple: true
        }

        section("<b>6. Hardware Protection, Efficiency & ROI</b>", hideable: true, hidden: true) {
            input "pauseGracePeriod", "number", title: "Occupancy Grace Period (Minutes)", defaultValue: 2, required: true
            input "kwRate", "decimal", title: "Electricity Rate (\$ per kWh)", defaultValue: 0.15, required: true
            
            paragraph "<b>Smart ROI Savings (Phantom Power Control):</b>"
            input "smartROISavings", "bool", title: "<b>Enable Smart ROI Power Management</b>", defaultValue: true, submitOnChange: true
            
            input "dockPlug1", "capability.switch", title: "Vacuum 1 Dock Smart Plug", required: false
            input "dockPlug2", "capability.switch", title: "Vacuum 2 Dock Smart Plug", required: false
            input "wakeUpTime", "time", title: "Daily Time to Wake Docks (Prep for schedules)", required: false
        }

        section("<b>7. Notifications & Maintenance Alerts</b>", hideable: true, hidden: true) {
            input "enableSelfHealing", "bool", title: "<b>Enable Auto-Recovery (Self-Healing)</b>", defaultValue: true, description: "Automatically attempts to recover from minor navigation errors (stuck, trapped) up to 3 times before pausing."
            input "notifyDevice", "capability.notification", title: "Send Push Notifications To", required: false, multiple: true
            input "notifyTypes", "enum", title: "Select Alert Types to Receive", options: ["Errors", "Bin Full", "Water Low", "Filter Dirty", "Clean Sensors", "Replace Bag", "Swap Mop Pads", "Replace Brushes"], multiple: true, required: false, defaultValue: ["Errors", "Bin Full", "Water Low", "Filter Dirty", "Clean Sensors", "Replace Bag", "Swap Mop Pads", "Replace Brushes"]
            input "alertThreshold", "number", title: "Alert when consumables drop below (%)", defaultValue: 5, required: true
            input "autoPauseOnError", "bool", title: "Auto-Pause Vacuum on Error", defaultValue: true
            input "ignoredPauseErrors", "text", title: "Ignore these Error Codes (Comma separated, e.g. 38, 105)", required: false
        }
        
        section("<b>8. Logging & Maintenance</b>", hideable: true, hidden: true) {
            input "logEnable", "bool", title: "Enable Informational Logging", defaultValue: true
            input "clearHistory", "bool", title: "Clear Dashboard History", defaultValue: false
            input "clearOccupancy", "bool", title: "Reset All Room Occupancy Counters", defaultValue: false
            input "clearROI", "bool", title: "Reset Financial Savings Counter", defaultValue: false
        }
            
        section("<b>9. Voice Butler Integration</b>", hideable: true, hidden: true) {
            input "enableButlerLink", "bool", title: "Enable Voice Butler Integration?", defaultValue: false, description: "Sends silent background events to the Advanced Voice Butler app so it can dynamically announce or stash vacuum activities."
        }
    }
}

def installed() {
    logInfo "Installed with settings: ${settings}"
    state.history = []
    state.lastCleanTime = now() 
    state.lastFullCleanTime = now()
    state.sicknessActive = false
    initialize()
}

def updated() {
    logInfo "Updated with settings: ${settings}"
    if (!state.history) state.history = []
    if (!state.lastCleanTime) state.lastCleanTime = now()
    if (!state.lastFullCleanTime) state.lastFullCleanTime = now()
    if (!state.lastDispatchLog) state.lastDispatchLog = [:]
    if (state.sicknessActive == null) state.sicknessActive = false
    
    if (settings.clearHistory) {
        state.history = []
        app.updateSetting("clearHistory", [value: "false", type: "bool"])
    }
    if (settings.clearOccupancy) {
        flushOccupancyArrays()
        state.lastDispatchLog = [:]
        app.updateSetting("clearOccupancy", [value: "false", type: "bool"])
    }
    if (settings.clearROI) {
        state.dock1OfflineHours = 0.0
        state.dock2OfflineHours = 0.0
        app.updateSetting("clearROI", [value: "false", type: "bool"])
    }

    initialize()
    evaluateROIPowerState()
    
    // --- BUTLER INJECTION ---
    // Force a sync to the Butler's clipboard when settings are saved
    broadcastStaffSchedule()
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
    if (dryMopTime) schedule(dryMopTime, "dryMopRoutineHandler")
    if (maxIdleDays) runEvery1Hour("overdueCheckHandler")
    
    if (masterSwitch) subscribe(masterSwitch, "switch", masterSwitchHandler)
    if (fullCleanSwitch) subscribe(fullCleanSwitch, "switch.on", fullCleanHandler)
    if (schoolRunSwitch) subscribe(schoolRunSwitch, "switch.on", schoolRunHandler)
    if (mealTimeSwitch) subscribe(mealTimeSwitch, "switch.off", postMealHandler)
    if (guestModeSwitch) subscribe(guestModeSwitch, "switch.off", guestModeHandler)
    if (goodNightSwitch) subscribe(goodNightSwitch, "switch", goodNightSwitchHandler)
    if (sickSwitches) subscribe(sickSwitches, "switch", sickSwitchHandler)
    
    subscribe(location, "mode", modeHandler)
    
    if (dishwasherPlug) subscribe(dishwasherPlug, "power", dishwasherPowerHandler)
    if (exteriorDoor) subscribe(exteriorDoor, "contact", exteriorDoorHandler)
    
    if (dockPlug1) {
        subscribe(dockPlug1, "switch", dockPlugHandler)
        if (dockPlug1.currentValue("switch") == "off" && !state.dock1OffTime) state.dock1OffTime = now()
    }
    if (dockPlug2) {
        subscribe(dockPlug2, "switch", dockPlugHandler)
        if (dockPlug2.currentValue("switch") == "off" && !state.dock2OffTime) state.dock2OffTime = now()
    }
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"]) {
            String rName = settings["roomName_${i}"]
            if (state["occSecs_${rName}"] == null) state["occSecs_${rName}"] = 0
            if (state["motionEvents_${rName}"] == null) state["motionEvents_${rName}"] = 0
            if (state["isMotionActive_${rName}"] == null) state["isMotionActive_${rName}"] = false
            
            if (settings["roomSwitch_${i}"]) subscribe(settings["roomSwitch_${i}"], "switch.on", roomSwitchHandler)
            if (settings["roomVirtualOcc_${i}"]) subscribe(settings["roomVirtualOcc_${i}"], "switch", virtualOccHandler)
            if (settings["roomMotion_${i}"]) subscribe(settings["roomMotion_${i}"], "motion", occupancyHandler)
            if (settings["roomBypass_${i}"]) subscribe(settings["roomBypass_${i}"], "motion", occupancyHandler)
            if (settings["roomLight_${i}"]) subscribe(settings["roomLight_${i}"], "switch", occupancyHandler)
            if (settings["roomTV_${i}"]) subscribe(settings["roomTV_${i}"], "switch", occupancyHandler)
            if (settings["roomBedPresence_${i}"]) subscribe(settings["roomBedPresence_${i}"], "presence", bedSensorHandler)
            if (settings["roomBedSwitch_${i}"]) subscribe(settings["roomBedSwitch_${i}"], "switch", bedSensorHandler)
        }
    }
    
    if (vacuum1) {
        subscribe(vacuum1, "state", vacuumStateHandler)
        subscribe(vacuum1, "presence", healthHandler)
        subscribe(vacuum1, "battery", batteryHandler)
        subscribe(vacuum1, "error", errorHandler)
        subscribe(vacuum1, "dockError", dockErrorHandler)
        subscribe(vacuum1, "remainingFilter", consumableHandler)
        subscribe(vacuum1, "remainingMainBrush", consumableHandler)
        subscribe(vacuum1, "remainingSideBrush", consumableHandler)
        subscribe(vacuum1, "remainingSensors", consumableHandler)
        subscribe(vacuum1, "remainingMop", consumableHandler)
    }
    if (vacuum2) {
        subscribe(vacuum2, "state", vacuumStateHandler)
        subscribe(vacuum2, "presence", healthHandler)
        subscribe(vacuum2, "battery", batteryHandler)
        subscribe(vacuum2, "error", errorHandler)
        subscribe(vacuum2, "dockError", dockErrorHandler)
        subscribe(vacuum2, "remainingFilter", consumableHandler)
        subscribe(vacuum2, "remainingMainBrush", consumableHandler)
        subscribe(vacuum2, "remainingSideBrush", consumableHandler)
        subscribe(vacuum2, "remainingSensors", consumableHandler)
        subscribe(vacuum2, "remainingMop", consumableHandler)
    }
}

// ==========================================
// STATE FLUSHING
// ==========================================

void flushOccupancyArrays() {
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"]) {
            String rName = settings["roomName_${i}"]
            state["occSecs_${rName}"] = 0
            state["motionEvents_${rName}"] = 0
            state["isMotionActive_${rName}"] = false
            state["bedOccupied_${rName}"] = false
            state["virtOccStart_${rName}"] = null
        }
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
                if (cmd == "dock") vacDevice.appDock()
                break
            case "iRobot Roomba (Native)":
            case "Ecovacs/Deebot":
            case "Dreame":
                if (cmd == "pause") vacDevice.pause()
                if (cmd == "resume") vacDevice.resume()
                if (cmd == "dock") { try { vacDevice.charge() } catch(e) { try { vacDevice.home() } catch(ex){} } }
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
                if (suction) {
                    Map suctionMap = ["quiet": 101, "balanced": 102, "turbo": 103, "max": 104, "off": 105]
                    Integer sCode = suctionMap[suction.toLowerCase()]
                    if (sCode) {
                        try { vacDevice.execute("set_custom_mode", "[${sCode}]") } catch(e) { logInfo("Suction execute error: ${e}") }
                    }
                }
                
                if (type == "room") {
                    try {
                        if (water) {
                            vacDevice.appRoomClean(target, water.capitalize())
                        } else {
                            vacDevice.appRoomClean(target)
                        }
                    } catch(e) { logInfo("appRoomClean dispatch error: ${e}") }
                }
                else if (type == "zone") {
                    try { vacDevice.execute("app_zoned_clean", "[${target}]") } catch(e) { }
                }
                else if (type == "waterOff") {
                    try { vacDevice.execute("set_water_box_custom_mode", "[200]") } catch(e) { }
                }
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

boolean canRunAutomated(String context = "", boolean bypassConstraints = false) {
    if (isAppPaused()) return false
    
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") {
        if (context) addToHistory("<span style='color:orange;'>${context} Blocked: Physical Master Switch is OFF</span>")
        return false
    }

    if (!bypassConstraints && allowedModes && !allowedModes.contains(location.mode)) {
        if (context) addToHistory("<span style='color:orange;'>${context} Blocked: Current Mode ('${location.mode}') is not in Allowed Operating Modes</span>")
        return false
    }

    if (!bypassConstraints && allowedStartTime && allowedEndTime) {
        Date start = timeToday(allowedStartTime, location.timeZone)
        Date end = timeToday(allowedEndTime, location.timeZone)
        Date now = new Date()
        
        boolean outsideAllowed = false
        if (start.before(end)) {
            if (!(now.after(start) && now.before(end))) outsideAllowed = true
        } else {
            if (!(now.after(start) || now.before(end))) outsideAllowed = true
        }
        
        if (outsideAllowed) {
            if (context) addToHistory("<span style='color:orange;'>${context} Blocked: Active Quiet Hours Restrictions</span>")
            return false
        }
    }
    return true
}

boolean isRoomCurrentlyOccupied(int roomIndex) {
    String rName = settings["roomName_${roomIndex}"]
    
    if (state["bedOccupied_${rName}"]) {
        long lockTime = state["bedLockTime_${rName}"] ?: 0
        def bPresence = [settings["roomBedPresence_${roomIndex}"]].flatten().findAll{it}
        def bSwitch = [settings["roomBedSwitch_${roomIndex}"]].flatten().findAll{it}
        
        boolean physicallyActive = bPresence.any{it.currentValue("presence") == "present"} || bSwitch.any{it.currentValue("switch") == "on"}
        if (physicallyActive) return true
        
        if (now() - lockTime < 1800000) {
             return true 
        } else {
             state["bedOccupied_${rName}"] = false 
        }
    }
    
    def motions = [settings["roomMotion_${roomIndex}"]].flatten().findAll { it }
    def bypasses = [settings["roomBypass_${roomIndex}"]].flatten().findAll { it }
    def lights = [settings["roomLight_${roomIndex}"]].flatten().findAll { it }
    def tvs = [settings["roomTV_${roomIndex}"]].flatten().findAll { it }
    
    if (motions.any { it.currentValue("motion") == "active" }) return true
    if (bypasses.any { it.currentValue("motion") == "active" }) return true
    if (lights.any { it.currentValue("switch") == "on" }) return true
    if (tvs.any { it.currentValue("switch") == "on" }) return true
    
    return false
}

// ==========================================
// NOTIFICATION ROUTER
// ==========================================

void sendAlert(String alertType, String msg) {
    if (settings.notifyTypes?.contains(alertType) || alertType == "Errors") {
        if (notifyDevice) notifyDevice.deviceNotification(msg)
        addToHistory("<span style='color:blue;'><b>Push Sent:</b> ${msg}</span>")
    }
}

// ==========================================
// EVENT HANDLERS & SMART DISPATCH WRAPPER
// ==========================================

void requestDispatch(List selectedRoomNames, Map options = [:]) {
    if (isAppPaused()) return
    
    boolean needsWake = false
    
    if (settings.smartROISavings != false) {
        if (dockPlug1 && dockPlug1.currentValue("switch") == "off") needsWake = true
        if (dockPlug2 && dockPlug2.currentValue("switch") == "off") needsWake = true
    }

    if (needsWake) {
        wakeDocks()
        addToHistory("<span style='color:teal;'><b>Smart ROI Active: Waking docks. Dispatch delayed by 90 seconds.</b></span>")
        state.pendingDispatchRooms = selectedRoomNames
        state.pendingDispatchOptions = options
        runIn(90, "executePendingDispatch", [overwrite: true])
    } else {
        executeRoomClean(selectedRoomNames, options)
    }
}

def executePendingDispatch() {
    if (isAppPaused()) return
    List rooms = state.pendingDispatchRooms ?: []
    Map opts = state.pendingDispatchOptions ?: [:]
    if (rooms) {
        executeRoomClean(rooms, opts)
    }
 
    state.pendingDispatchRooms = []
    state.pendingDispatchOptions = [:]
}

void evaluateROIPowerState() {
    if (isAppPaused() || settings.smartROISavings == false) return
    if (state.pendingDispatchRooms?.size() > 0) return 
    
    checkVacuumHealth(vacuum1, dockPlug1, 1)
    checkVacuumHealth(vacuum2, dockPlug2, 2)
}

void checkVacuumHealth(vacDevice, dockPlug, int vacNum) {
    if (!vacDevice || !dockPlug) return
    
    String vState = vacDevice.currentValue("state")?.toString()?.toLowerCase() ?: ""
    String presence = vacDevice.currentValue("presence")?.toString()?.toLowerCase() ?: "present"
    String batStr = vacDevice.currentValue("battery")?.toString()?.replaceAll("[^0-9]", "")
    int bat = batStr?.isInteger() ? (batStr as Integer) : 0
    
    boolean isOffline = (vState == "offline" || presence == "not present" || presence == "offline")

    if (isOffline) {
        if (dockPlug.currentValue("switch") == "off") {
            dockPlug.on()
            addToHistory("V${vacNum} Dock Powered ON (Connection Lost: Vacuum Offline)")
            runIn(15, "remountVacuum${vacNum}")
        }
        return
    }

    if (bat == 100 && vState in ["charging", "charged", "docked", "idle", "full"]) {
        if (dockPlug.currentValue("switch") != "off") {
            dockPlug.off()
            addToHistory("V${vacNum} Dock Powered OFF (Smart ROI Assassin)")
        }
    } else if (bat <= 80) {
        if (dockPlug.currentValue("switch") == "off") {
            dockPlug.on()
            addToHistory("V${vacNum} Dock Powered ON (Smart ROI Top-Off: Battery at ${bat}%)")
            runIn(15, "remountVacuum${vacNum}")
        }
    }
}

def forceDockVacuum1() {
    if (isAppPaused()) return
    if (vacuum1) commandVacuum(vacuum1, settings.vac1Brand, "dock")
}

def forceDockVacuum2() {
    if (isAppPaused()) return
    if (vacuum2) commandVacuum(vacuum2, settings.vac2Brand, "dock")
}

def remountVacuum1() {
    if (vacuum1 && dockPlug1 && dockPlug1.currentValue("switch") == "on") {
        commandVacuum(vacuum1, settings.vac1Brand, "dock")
        addToHistory("<span style='color:teal;'><b>V1 Alignment: Securing charging pins.</b></span>")
    }
}

def remountVacuum2() {
    if (vacuum2 && dockPlug2 && dockPlug2.currentValue("switch") == "on") {
        commandVacuum(vacuum2, settings.vac2Brand, "dock")
        addToHistory("<span style='color:teal;'><b>V2 Alignment: Securing charging pins.</b></span>")
    }
}

def remountVacuums() {
    remountVacuum1()
    remountVacuum2()
}

def appButtonHandler(btn) {
    if (isAppPaused()) {
        logInfo "Command Center ignored: Master Virtual Switch is OFF."
        return
    }
    
    if (btn == "btnRefresh") {
        logInfo "Dashboard data manually refreshed."
        if (vacuum1 && vacuum1.hasCommand("refresh")) {
            try { vacuum1.refresh() } catch (e) { log.warn "Vacuum 1 refresh failed: ${e}" }
        }
        if (vacuum2 && vacuum2.hasCommand("refresh")) {
            try { vacuum2.refresh() } catch (e) { log.warn "Vacuum 2 refresh failed: ${e}" }
        }
        evaluateROIPowerState()
        addToHistory("<span style='color:purple;'><b>Data Refresh: Triggered successfully.</b></span>")
    }
    
    if (btn == "btnSyncRooms") {
        if (vacuum1) {
            String roomsAttr = vacuum1.currentValue("rooms")?.toString() ?: vacuum1.currentValue("Rooms")?.toString()
            if (roomsAttr) {
                try {
                    def roomMap = new groovy.json.JsonSlurper().parseText(roomsAttr)
                    int i = 1
                    roomMap.each { id, name ->
                        if (i <= 12) {
                            app.updateSetting("enableRoom_${i}", [type: "bool", value: true])
                            app.updateSetting("vacuumAssign_${i}", [type: "enum", value: "Vacuum 1"])
                            app.updateSetting("roomName_${i}", [type: "text", value: name])
                            app.updateSetting("roomId_${i}", [type: "text", value: id])
                            i++
                        }
                    }
                    addToHistory("<span style='color:purple;'><b>Room Sync: Successfully imported ${i-1} rooms from Vacuum 1.</b></span>")
                } catch (e) {
                    addToHistory("<span style='color:red;'><b>Room Sync Failed: Could not parse vacuum room data.</b></span>")
                    log.error "Room Sync Error: ${e}"
                }
            } else {
                addToHistory("<span style='color:red;'><b>Room Sync Failed: No room data found on Vacuum 1.</b></span>")
            }
        }
    }
    
    if (btn == "btnSyncRoomsV2") {
        if (vacuum2) {
            String roomsAttr = vacuum2.currentValue("rooms")?.toString() ?: vacuum2.currentValue("Rooms")?.toString()
            if (roomsAttr) {
                try {
                    def roomMap = new groovy.json.JsonSlurper().parseText(roomsAttr)
                    int added = 0
                    roomMap.each { id, name ->
                        int targetSlot = 0
                        for (int k = 1; k <= 12; k++) {
                            if (!settings["enableRoom_${k}"]) {
                                targetSlot = k
                                break
                            }
                        }
                        if (targetSlot > 0) {
                            app.updateSetting("enableRoom_${targetSlot}", [type: "bool", value: true])
                            app.updateSetting("vacuumAssign_${targetSlot}", [type: "enum", value: "Vacuum 2"])
                            app.updateSetting("roomName_${targetSlot}", [type: "text", value: name])
                            app.updateSetting("roomId_${targetSlot}", [type: "text", value: id])
                            added++
                        }
                    }
                    addToHistory("<span style='color:purple;'><b>Room Sync: Successfully imported ${added} rooms from Vacuum 2 into empty slots.</b></span>")
                } catch (e) {
                    addToHistory("<span style='color:red;'><b>Room Sync Failed: Could not parse vacuum room data for Vacuum 2.</b></span>")
                    log.error "Room Sync Error V2: ${e}"
                }
            } else {
                addToHistory("<span style='color:red;'><b>Room Sync Failed: No room data found on Vacuum 2.</b></span>")
            }
        }
    }
    
    if (btn == "btnExecuteQuickClean") {
        if (settings.quickCleanRooms) {
            List roomsToClean = [] + settings.quickCleanRooms
            requestDispatch(roomsToClean, [
                ignoreSkip: true, 
                suction: settings.quickCleanSuction, 
                water: settings.quickCleanWater
            ])
            app.updateSetting("quickCleanRooms", [type: "enum", value: []])
        }
    }
}

def mopRoutineHandler() {
    if (!canRunAutomated("Scheduled Mop Routine")) return
    if (mopDays) {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)
        String day = df.format(new Date())
        if (!mopDays.contains(day)) return
    }
    if (mopRooms) {
        addToHistory("<span style='color:teal;'><b>Triggered: Scheduled Mop-Only Routine</b></span>")
        requestDispatch(mopRooms, [ignoreSkip: false, suction: "Off", water: "High"])
    }
}

def dryMopRoutineHandler() {
    if (!canRunAutomated("Scheduled Dry Dusting")) return
    if (dryMopDays) {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)
        String day = df.format(new Date())
        if (!dryMopDays.contains(day)) return
    }
    if (dryMopRooms) {
        addToHistory("<span style='color:teal;'><b>Triggered: Scheduled Dry Dusting Routine</b></span>")
        requestDispatch(dryMopRooms, [ignoreSkip: false, suction: "Off", water: "Off"])
    }
}

def overdueCheckHandler() {
    if (isAppPaused()) return
    if (!maxIdleDays || !state.lastFullCleanTime) return
    long daysIdle = (now() - state.lastFullCleanTime) / 86400000
    if (daysIdle >= maxIdleDays) {
        if (canRunAutomated("Overdue Deep Clean")) {
            addToHistory("<span style='color:purple;'><b>Overdue Catcher: ${daysIdle} days since last FULL clean. Forcing Deep Clean!</b></span>")
            
            // --- BUTLER INJECTION ---
            sendToButler("As the residence has not received a deep clean in ${daysIdle} days, I have taken the liberty of deploying the cleaning staff for a full sweep.", "I deployed the automated vacuums to handle an overdue full house deep clean")
            
            requestDispatch(["All"], [ignoreSkip: true, suction: "Max", water: "High"])
        }
    }
}

def guestModeHandler(evt) {
    if (isAppPaused()) return
    if (!canRunAutomated("Guest Departure Cleanup")) return
    
    addToHistory("Triggered: Guest Departure Mode (Max Clean Sweep)")
    
    // --- BUTLER INJECTION ---
    sendToButler("Now that our guests have departed, I have ordered a deep clean of the main floors to restore the house.", "I deployed the vacuums to clean up after the guests departed")
    
    requestDispatch(["All"], [ignoreSkip: true, suction: "Max", water: "High"])
}

def goodNightSwitchHandler(evt) {
    if (isAppPaused()) return
    if (evt.value == "off") {
        addToHistory("Good Night Deactivated: Flushing all stale occupancy states to reset the house.")
        flushOccupancyArrays()
    }
}

def dishwasherPowerHandler(evt) {
    if (isAppPaused()) return
    if (!dishwasherPlug || !dishwasherRooms) return
    
    BigDecimal pwr = (evt.value as BigDecimal) ?: 0.0
    BigDecimal threshold = settings.dishwasherThreshold ?: 15.0
    boolean isRunning = state.dishwasherRunning ?: false
    
    if (pwr >= threshold && !isRunning) {
        state.dishwasherRunning = true
        addToHistory("Appliance Telemetry: Dishwasher Cycle Started (> ${threshold}W).")
    } else if (pwr < threshold && isRunning) {
        state.dishwasherRunning = false
        addToHistory("Appliance Telemetry: Dishwasher Cycle Complete. Queueing Kitchen Sweep.")
        
        // --- BUTLER INJECTION ---
        sendToButler("The dishwasher has completed its cycle, so I have dispatched the vacuum to sweep the kitchen.", "I had the kitchen swept after the dishwasher finished")
        
        requestDispatch(settings.dishwasherRooms, [ignoreSkip: false])
    }
}

def exteriorDoorHandler(evt) {
    if (isAppPaused()) return
    if (evt.value != "open") return
    if (!mudroomRooms) return
    
    long nowTime = now()
    long windowMs = ((settings.doorTimeWindow ?: 15) as Long) * 60000
    
    List openings = state.doorOpenings ?: []
    openings << nowTime
    openings.removeAll { nowTime - it > windowMs }
    state.doorOpenings = openings
    
    int thresh = settings.doorCountThreshold ?: 3
    if (openings.size() >= thresh) {
        addToHistory("Exterior Accumulator: Threshold Met (${thresh} openings). Queueing Mudroom Sweep.")
        requestDispatch(settings.mudroomRooms, [ignoreSkip: false])
        state.doorOpenings = [] 
    }
}

def bedSensorHandler(evt) {
    if (isAppPaused()) return
    String devId = evt.deviceId.toString()
    for (int i = 1; i <= 12; i++) {
        if (!settings["enableRoom_${i}"]) continue
        
        def bPresence = [settings["roomBedPresence_${i}"]].flatten().findAll{it}
        def bSwitch = [settings["roomBedSwitch_${i}"]].flatten().findAll{it}
        
        if (bPresence.any{it.id.toString() == devId} || bSwitch.any{it.id.toString() == devId}) {
            String rName = settings["roomName_${i}"]
            boolean isActive = (evt.value == "present" || evt.value == "on")
            
            if (isActive) {
                state["bedLockTime_${rName}"] = now()
                state["bedOccupied_${rName}"] = true
                addToHistory("${rName} Bed Active: Locking in for minimum 30 minutes to settle.")
            } else {
                long lockTime = state["bedLockTime_${rName}"] ?: 0
                if (now() - lockTime < 1800000) {
                    addToHistory("${rName} Bed Vacated: Ignored (Within 30m settle lockout).")
                } else {
                    state["bedOccupied_${rName}"] = false
                    addToHistory("${rName} Bed Vacated: Settle period expired, occupancy released.")
                }
            }
            break
        }
    }
}

def virtualOccHandler(evt) {
    if (isAppPaused()) return
    String evtDevId = evt.device.id?.toString() ?: evt.deviceId?.toString()
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"] && settings["roomVirtualOcc_${i}"]?.id?.toString() == evtDevId) {
            String rName = settings["roomName_${i}"]
            
            if (evt.value == "on") {
                state["virtOccStart_${rName}"] = now()
            } else if (evt.value == "off") {
                long start = state["virtOccStart_${rName}"] ?: now()
                long durationMins = (now() - start) / 60000
                int threshold = settings["roomVirtualOccTime_${i}"] ?: 30
                
                if (durationMins >= threshold) {
                    if (canRunAutomated("Virtual Occupancy (${rName})")) {
                        addToHistory("Virtual Occupancy Trigger: ${rName} active for ${durationMins}m. Queueing sweep.")
                        requestDispatch([rName], [ignoreSkip: true])
                    }
                }
            }
            break
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
        else if (part.contains("mop")) sendAlert("Swap Mop Pads", msg)
        else if (part.contains("main") || part.contains("side") || part.contains("brush")) sendAlert("Replace Brushes", msg)
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

def healthHandler(evt) {
    if (isAppPaused()) return
    String val = evt.value?.toString()?.toLowerCase()
    if (val == "not present" || val == "offline") {
        evaluateROIPowerState()
    }
}

def batteryHandler(evt) {
    evaluateROIPowerState()
}

def dockPlugHandler(evt) {
    if (isAppPaused()) return
    
    String eId = evt.device?.id?.toString() ?: evt.deviceId?.toString()
    
    if (evt.value == "off") {
        if (eId == dockPlug1?.id && !state.dock1OffTime) state.dock1OffTime = now()
        if (eId == dockPlug2?.id && !state.dock2OffTime) state.dock2OffTime = now()
    } else if (evt.value == "on") {
        if (eId == dockPlug1?.id && state.dock1OffTime) {
            state.dock1OfflineHours = (state.dock1OfflineHours ?: 0.0) + ((now() - state.dock1OffTime) / 3600000.0)
            state.dock1OffTime = null
        }
        if (eId == dockPlug2?.id && state.dock2OffTime) {
            state.dock2OfflineHours = (state.dock2OfflineHours ?: 0.0) + ((now() - state.dock2OffTime) / 3600000.0)
            state.dock2OffTime = null
        }
    }
}

def wakeDocks() {
    if (isAppPaused()) return
    boolean waked = false
    if (dockPlug1 && dockPlug1.currentValue("switch") != "on") { dockPlug1.on(); waked = true; }
    if (dockPlug2 && dockPlug2.currentValue("switch") != "on") { dockPlug2.on(); waked = true; }
    
    if (waked) {
        addToHistory("Docks Powered ON (Smart ROI Wake-Up)")
        runIn(15, "remountVacuums") 
    }
}

def vacuumStateHandler(evt) {
    if (isAppPaused()) return
    String vState = evt.value?.toString()?.toLowerCase()
    
    if (vState in ["charging", "charged", "docked", "returning dock", "returning to dock", "idle", "full"]) {
        state.ignoreIntrusions = false 
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
    
    evaluateROIPowerState()
}

def triggerFanCountdown(String roomName) {
    if (isAppPaused()) return
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"] && settings["roomName_${i}"] == roomName && settings["roomFan_${i}"]) {
            int delayMins = (settings["roomFanTimer_${i}"] ?: 15) as Integer
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
        unschedule("executePendingDispatch")
    } else {
        addToHistory("<span style='color:green;'>Master Switch: ON (Resumed)</span>")
    }
}

def occupancyHandler(evt) {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    if (state.ignoreIntrusions) return 
    
    String triggeredRoom = ""
    int triggeredIndex = 0
    boolean isAccumulator = false
    String evtDevId = evt.device.id?.toString() ?: evt.deviceId?.toString()
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"]) {
            def motions = [settings["roomMotion_${i}"]].flatten().findAll { it }
            def bypasses = [settings["roomBypass_${i}"]].flatten().findAll { it }
            def lights = [settings["roomLight_${i}"]].flatten().findAll { it }
            def tvs = [settings["roomTV_${i}"]].flatten().findAll { it }
            
            if (motions.any { it.id?.toString() == evtDevId }) {
                triggeredRoom = settings["roomName_${i}"]
                triggeredIndex = i
                isAccumulator = true
            }
            if (bypasses.any { it.id?.toString() == evtDevId } || lights.any { it.id?.toString() == evtDevId } || tvs.any { it.id?.toString() == evtDevId }) {
                triggeredRoom = settings["roomName_${i}"]
                triggeredIndex = i
            }
            if (triggeredRoom) break
        }
    }
    if (!triggeredRoom) return

    boolean isGoodNight = false
    if (goodNightMode && location.mode in goodNightMode) isGoodNight = true
    if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") isGoodNight = true

    boolean v1InRoom = state.v1_maskedRooms?.contains(triggeredRoom)
    boolean v2InRoom = state.v2_maskedRooms?.contains(triggeredRoom)

    if (evt.name == "motion" && isAccumulator && !v1InRoom && !v2InRoom) {
        if (!isGoodNight && !settings["disableML_${triggeredIndex}"]) {
            def motions = [settings["roomMotion_${triggeredIndex}"]].flatten().findAll { it }
            int activeSensors = 0
            
            motions.each { m ->
                if (m.id?.toString() == evtDevId) {
                    if (evt.value == "active") activeSensors++
                } else {
                    if (m.currentValue("motion") == "active") activeSensors++
                }
            }
            
            boolean wasActive = state["isMotionActive_${triggeredRoom}"] ?: false
            
            if (evt.value == "active") {
                state["motionEvents_${triggeredRoom}"] = (state["motionEvents_${triggeredRoom}"] ?: 0) + 1
                if (!wasActive) {
                    state["motionStart_${triggeredRoom}"] = now()
                    state["isMotionActive_${triggeredRoom}"] = true
                }
            } else if (evt.value == "inactive") {
                if (activeSensors == 0 && wasActive) {
                    state["isMotionActive_${triggeredRoom}"] = false
                    long start = state["motionStart_${triggeredRoom}"] ?: now()
                    long deltaSecs = (now() - start) / 1000
                    state["occSecs_${triggeredRoom}"] = (state["occSecs_${triggeredRoom}"] ?: 0) + deltaSecs
                    addToHistory("<span style='#0066cc;'><b>Activity Log:</b> ${triggeredRoom} recorded ${deltaSecs}s of motion.</span>")
                }
            }
        }
    }

    if (evt.value == "active" || evt.value == "on") {
        boolean doPause = settings.pauseOnIntrusion ?: false
        if (!doPause) return 
        
        if (evt.name == "motion" && (v1InRoom || v2InRoom)) {
            return 
        }
        
        if (v1InRoom && vacuum1 && vacuum1.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("<span style='color:orange;'>V1 Paused: Intrusion in ${triggeredRoom}</span>")
            commandVacuum(vacuum1, settings.vac1Brand, "pause")
            unschedule("resumeVacuum1")
        }
        if (v2InRoom && vacuum2 && vacuum2.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("<span style='color:orange;'>V2 Paused: Intrusion in ${triggeredRoom}</span>")
            commandVacuum(vacuum2, settings.vac2Brand, "pause")
            unschedule("resumeVacuum2")
        }
    } else if (evt.value == "inactive" || evt.value == "off") {
        boolean doPause = settings.pauseOnIntrusion ?: false
        if (!doPause) return 

        if (!isRoomCurrentlyOccupied(triggeredIndex)) {
            int delaySeconds = ((settings.pauseGracePeriod ?: 2) as Integer) * 60
            if (v1InRoom && vacuum1 && vacuum1.currentValue("state")?.toLowerCase() == "paused") {
                addToHistory("V1 Timer: ${triggeredRoom} clear. Resuming in ${settings.pauseGracePeriod}m.")
                runIn(delaySeconds, "resumeVacuum1", [overwrite: true])
            }
            if (v2InRoom && vacuum2 && vacuum2.currentValue("state")?.toLowerCase() == "paused") {
                addToHistory("V2 Timer: ${triggeredRoom} clear. Resuming in ${settings.pauseGracePeriod}m.")
                runIn(delaySeconds, "resumeVacuum2", [overwrite: true])
            }
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

def commandActionResume(data) {
    if (isAppPaused()) return
    def devId = data?.vacId
    def brand = data?.brand
    def vac = (devId == vacuum1?.id) ? vacuum1 : ((devId == vacuum2?.id) ? vacuum2 : null)
    if (vac && vac.currentValue("state")?.toLowerCase() == "paused") {
        commandVacuum(vac, brand, "resume")
    }
}

def modeHandler(evt) {
    if (isAppPaused()) return

    if (arrivalModes && evt.value in arrivalModes) {
        if (vacuum1 && vacuum1.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("Mode changed to ${evt.value}: Pausing V1 to prevent disturbance.")
            state.v1_wasInterrupted = true
            commandVacuum(vacuum1, settings.vac1Brand, "pause")
            runIn(5, "forceDockVacuum1", [overwrite: true])
        }
        if (vacuum2 && vacuum2.currentValue("state")?.toLowerCase() in ["cleaning", "room clean", "zone clean"]) {
            addToHistory("Mode changed to ${evt.value}: Pausing V2 to prevent disturbance.")
            state.v2_wasInterrupted = true
            commandVacuum(vacuum2, settings.vac2Brand, "pause")
            runIn(5, "forceDockVacuum2", [overwrite: true])
        }
    }

    if (awayModes && evt.value in awayModes) {
        if (state.v1_wasInterrupted) {
            addToHistory("Departure status active (${evt.value}): Resuming interrupted V1 clean.")
            commandVacuum(vacuum1, settings.vac1Brand, "resume")
            state.v1_wasInterrupted = false
        }
        if (state.v2_wasInterrupted) {
            addToHistory("Departure status active (${evt.value}): Resuming interrupted V2 clean.")
            commandVacuum(vacuum2, settings.vac2Brand, "resume")
            state.v2_wasInterrupted = false
        }
    }
    
    if (fullCleanMode && !(evt.value in fullCleanMode)) {
        unschedule("delayedFullCleanHandler")
        addToHistory("Mode changed to ${evt.value}. Cancelled any pending Settle Delays.")
    }
    
    if (goodNightMode && evt.value in goodNightMode) {
        if (!canRunAutomated("Good Night Sweep")) return
        if (goodNightConflicts) {
            def activeConflicts = goodNightConflicts.findAll { it.currentValue("switch") == "on" }
            if (activeConflicts.size() > 0) {
                def conflictNames = activeConflicts.collect { it.displayName }.join(", ")
                addToHistory("<span style='color:orange;'>Good Night Blocked: Active Devices (${conflictNames})</span>")
                return
            }
        }
        addToHistory("Triggered: Good Night Sweep (Quiet Mode)")
        requestDispatch(goodNightRooms, [
            ignoreSkip: false,
            suction: settings.goodNightSuction ?: "Quiet",
            water: settings.goodNightWater ?: "Off"
        ])
    }
    
    if (fullCleanMode && evt.value in fullCleanMode) {
        int delayMins = (settings.fullCleanDelay ?: 10) as Integer
        int delaySecs = delayMins * 60
        
        if (delaySecs > 0) {
            addToHistory("Mode Changed to '${evt.value}'. Initiating ${delayMins}m settle delay before dispatch.")
            runIn(delaySecs, "delayedFullCleanHandler", [overwrite: true])
        } else {
            delayedFullCleanHandler()
        }
    }
}

def delayedFullCleanHandler() {
    if (isAppPaused()) {
        if (settings.fullCleanNotifyFail != false) sendAlert("Errors", "Mode Clean Aborted: System Paused.")
        return
    }
    
    boolean bypass = settings.fullCleanBypass != null ? settings.fullCleanBypass : true
    if (!canRunAutomated("Configurable Full Clean Mode", bypass)) {
        if (settings.fullCleanNotifyFail != false) sendAlert("Errors", "Mode Clean Aborted: Blocked by Global Constraints.")
        return
    }
    
    String v1State = vacuum1 ? vacuum1.currentValue("state")?.toString()?.toLowerCase() : ""
    String v1Err = vacuum1 ? vacuum1.currentValue("error")?.toString()?.toLowerCase() : ""
    
    if (v1State == "offline" || v1State == "not present") {
         if (settings.fullCleanNotifyFail != false) sendAlert("Errors", "Mode Clean Warning: Vacuum 1 is Offline.")
    } else if (v1Err && v1Err != "no error" && v1Err != "0" && v1Err != "ok") {
         if (settings.fullCleanNotifyFail != false) sendAlert("Errors", "Mode Clean Warning: Vacuum 1 has an active error (${v1Err}).")
    }

    addToHistory("Triggered: Configurable Full Clean Mode (After Settle Delay)")
    
    // --- BUTLER INJECTION ---
    sendToButler("Since the house is currently clear, I am initiating a full house deep clean.", "I took the opportunity to deep clean the floors while the house was empty")
    
    requestDispatch(["All"], [
        ignoreSkip: settings.fullCleanIgnoreSkip, 
        suction: settings.fullCleanSuction, 
        water: settings.fullCleanWater
    ])
}

def fullCleanHandler(evt) {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    addToHistory("Triggered: Full Clean Switch (Manual Override)")
    requestDispatch(["All"], [ignoreSkip: true]) 
}

def roomSwitchHandler(evt) {
    if (isAppPaused()) return
    if (masterSwitch && masterSwitch.currentValue("switch") == "off") return
    for (int i = 1; i <= 12; i++) {
        def sw = settings["roomSwitch_${i}"]
        if (settings["enableRoom_${i}"] && sw && sw.id == evt.deviceId) {
            requestDispatch([settings["roomName_${i}"]], [ignoreSkip: true])
            break
        }
    }
}

def schoolRunHandler(evt) {
    if (isAppPaused()) return
    if (!canRunAutomated("School Run Routine")) return
    addToHistory("Triggered: School Run Routine")
    requestDispatch(schoolRunRooms, [ignoreSkip: false])
}

def postMealHandler(evt) {
    if (isAppPaused()) return
    if (!canRunAutomated("Post-Meal Cleanup")) return
    if (!settings.mealTimeRooms) return

    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    cal.setTime(new Date())
    int currentHour = cal.get(Calendar.HOUR_OF_DAY)
    String todayDate = cal.getTime().format("yyyy-MM-dd", tz)

    if (currentHour < 14) {
        if (state.lastBreakfastCleanDate == todayDate) {
            addToHistory("Post-Meal Cleanup Skipped: Morning sweep already ran today.")
            return
        }
        state.lastBreakfastCleanDate = todayDate
        addToHistory("Triggered: Ecosystem Post-Breakfast Cleanup")
        // --- BUTLER INJECTION ---
        sendToButler("Now that breakfast has concluded, I have dispatched the cleaning staff to sweep the dining areas.", "I had the dining areas swept after breakfast")
    } else {
        if (state.lastDinnerCleanDate == todayDate) {
            addToHistory("Post-Meal Cleanup Skipped: Evening sweep already ran today.")
            return
        }
        state.lastDinnerCleanDate = todayDate
        addToHistory("Triggered: Ecosystem Post-Dinner Cleanup")
        // --- BUTLER INJECTION ---
        sendToButler("Now that the meal has concluded, I have dispatched the cleaning staff to sweep the dining areas.", "I had the dining areas swept after your meal")
    }

    requestDispatch(settings.mealTimeRooms, [ignoreSkip: true])
}

def sickSwitchHandler(evt) {
    if (isAppPaused()) return
    
    boolean anyOn = sickSwitches.any { it.currentValue("switch") == "on" }
    
    if (anyOn) {
        if (!state.sicknessActive) {
            state.sicknessActive = true
            addToHistory("<span style='color:orange;'><b>Sick Mode Active:</b> Tracking sickness duration. Deep clean pending recovery.</span>")
        }
    } else {
        if (state.sicknessActive) {
            state.sicknessActive = false 
            
            if (!canRunAutomated("Post-Sickness Clean")) return
            
            addToHistory("<span style='color:green;'><b>Triggered: Post-Sickness Deep Clean (All Sick Switches OFF)</b></span>")
            requestDispatch(["All"], [ignoreSkip: true, suction: "Max", water: "High"])
        }
    }
}

def errorHandler(evt) {
    if (isAppPaused()) return
    String errorCode = evt.value?.toString()
    String errorMsg = errorCode?.toLowerCase()
    
    if (errorMsg == "no error" || errorMsg == "0") return
    
    List ignoredCodes = settings.ignoredPauseErrors ? settings.ignoredPauseErrors.split(",").collect { it.trim() } : []
    
    if (ignoredCodes.contains(errorCode)) {
        addToHistory("<span style='color:gray;'><i>Ignored Error ${errorCode} (Bypassed Auto-Pause & Alerts)</i></span>")
        
        if (errorCode in ["38", "39", "40"]) {
            def vac = evt.device
            def brand = (vac.id == vacuum1?.id) ? settings.vac1Brand : settings.vac2Brand
            addToHistory("<span style='color:teal;'><b>Water Error Detected: Forcing Water Level to OFF to protect pump.</b></span>")
            dispatchVacuum(vac, brand, "waterOff", "", "", "") 
        }
        return
    }
    
    List navErrors = ["1", "2", "3", "4", "7", "8", "16", "21"]
    if (settings.enableSelfHealing != false && navErrors.contains(errorCode)) {
        String devId = evt.device.id.toString()
        int retries = state."retry_${devId}" ?: 0
        
        if (retries < 3) {
            state."retry_${devId}" = retries + 1
            addToHistory("<span style='color:orange;'><b>Self-Healing:</b> Attempting recovery from Error ${errorCode} (Attempt ${retries + 1}/3).</span>")
            
            def brand = (devId == vacuum1?.id) ? settings.vac1Brand : settings.vac2Brand
            runIn(5, "commandActionResume", [data: [vacId: devId, brand: brand], overwrite: true])
            return 
        } else {
            addToHistory("<span style='color:red;'><b>Self-Healing Failed:</b> Max retries (3) reached for Error ${errorCode}. Halting.</span>")
            state."retry_${devId}" = 0 
        }
    }

    String msg = "${evt.device.displayName} Error: ${errorCode}"
    addToHistory("<span style='color:red;'><b>${msg}</b></span>")
    sendAlert("Errors", msg)
    
    if (settings.autoPauseOnError != false) {
        def brand = (evt.device.id == vacuum1?.id) ? settings.vac1Brand : settings.vac2Brand
        commandVacuum(evt.device, brand, "pause")
    }
}

// ==========================================
// CORE LOGIC: PRE-EVALUATION & DISPATCH
// ==========================================

void executeRoomClean(List selectedRoomNames, Map options = [:]) {
    if (isAppPaused()) return

    state.v1_wasInterrupted = false
    state.v2_wasInterrupted = false

    boolean ignoreSkip = options.ignoreSkip ?: false
    String overrideSuction = options.suction
    String overrideWater = options.water

    if (!selectedRoomNames) return

    boolean isFullCleanRequest = false
    
    if (selectedRoomNames.contains("All")) {
        isFullCleanRequest = true
        List<String> allActive = []
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) allActive << settings["roomName_${i}"]
        }
        selectedRoomNames = allActive
    }

    def v1Queue = []
    def v2Queue = []
    def dispatchLog = [:]

    for (String targetName : selectedRoomNames) {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableRoom_${i}"] && settings["roomName_${i}"] == targetName) {
                
                if (settings["roomContact_${i}"] && settings["roomContact_${i}"].currentValue("contact") == "open") {
                    dispatchLog[targetName] = "<span style='color:red;'>Skipped (Perimeter Open)</span>"
                    continue
                }

                if (settings["roomHumidity_${i}"]) {
                    def currentHum = settings["roomHumidity_${i}"].currentValue("humidity") ?: 0
                    def limitHum = settings["roomHumidityThreshold_${i}"] ?: 75
                    if (currentHum > limitHum) {
                         dispatchLog[targetName] = "<span style='color:orange;'>Skipped (Humidity: ${currentHum}%)</span>"
                        continue
                    }
                }
                
                int occSecs = state["occSecs_${targetName}"] ?: 0
                int occMins = (occSecs / 60) as Integer
                int minThreshold = settings["roomOccupancyThreshold_${i}"] ?: 15
                int heavyThreshold = settings["roomHeavyTraffic_${i}"] ?: 120
                boolean disableML = settings["disableML_${i}"] ?: false
               
                if (!disableML) {
                    if (!ignoreSkip && occMins < minThreshold) {
                        dispatchLog[targetName] = "<span style='color:gray;'>Skipped (Clean: ${occMins}/${minThreshold}m)</span>"
                        continue 
                    }
                }

                if (!ignoreSkip && isRoomCurrentlyOccupied(i)) {
                    dispatchLog[targetName] = "<span style='color:orange;'>Skipped (Currently Occupied)</span>"
                    continue
                }

                String finalSuction = overrideSuction ?: settings["roomSuction_${i}"] ?: "Balanced"
            
                if (settings["roomMedia_${i}"]) {
                    def pState = settings["roomMedia_${i}"].currentValue("status") ?: settings["roomMedia_${i}"].currentValue("state")
                    if (pState?.toString()?.toLowerCase() == "playing") {
                        finalSuction = "Quiet"
                         addToHistory("Acoustic Override: ${targetName} set to Quiet (Media Playing)")
                    }
                } else if (!overrideSuction && occMins >= heavyThreshold && !disableML) {
                    finalSuction = "Turbo"
                    addToHistory("Adaptive Suction: ${targetName} set to Turbo (${occMins}m traffic)")
                }

                dispatchLog[targetName] = "<span style='color:green;'>Cleaned (${finalSuction})</span>"

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

    state.ignoreIntrusions = ignoreSkip 
    state.lastDispatchLog = dispatchLog

    if (v1Queue.size() > 0 || v2Queue.size() > 0) {
        state.lastCleanTime = now()
        if (isFullCleanRequest) {
            state.lastFullCleanTime = now()
        }
        // --- BUTLER INJECTION ---
        broadcastStaffSchedule() 
    }

    if (v1Queue.size() > 0 && vacuum1) {
        v1Queue.sort { it.seq }
        state.v1_maskedRooms = v1Queue.collect { it.name }
        state.v1_intentAction = "Dispatched (Sequence)"
        state.v1_intentRooms = state.v1_maskedRooms.join(", ")
        
        List<String> combinedRoomIdsV1 = []

        v1Queue.each { room ->
            if (settings["roomFan_${room.index}"]) {
                settings["roomFan_${room.index}"].on()
                addToHistory("${room.name} Dust Settler: Fan ON")
            }
            
            if (room.zone) {
                dispatchVacuum(vacuum1, settings.vac1Brand, "zone", room.zone, "", "")
                pauseExecution(2500) 
            } else if (room.id) {
                combinedRoomIdsV1 << room.id.toString()
            }
             
            state["occSecs_${room.name}"] = 0 
            state["motionEvents_${room.name}"] = 0
            state["isMotionActive_${room.name}"] = false
        }
        
        if (combinedRoomIdsV1.size() > 0) {
            String targetRooms = combinedRoomIdsV1.join(",")
            dispatchVacuum(vacuum1, settings.vac1Brand, "room", targetRooms, v1Queue[0].water, v1Queue[0].suction)
        }
    }

    if (v2Queue.size() > 0 && vacuum2) {
        v2Queue.sort { it.seq }
        state.v2_maskedRooms = v2Queue.collect { it.name }
        state.v2_intentAction = "Dispatched (Sequence)"
        state.v2_intentRooms = state.v2_maskedRooms.join(", ")
        
        List<String> combinedRoomIdsV2 = []

        v2Queue.each { room ->
            if (settings["roomFan_${room.index}"]) {
                settings["roomFan_${room.index}"].on()
                addToHistory("${room.name} Dust Settler: Fan ON")
            }
            
            if (room.zone) {
                dispatchVacuum(vacuum2, settings.vac2Brand, "zone", room.zone, "", "")
                pauseExecution(2500)
            } else if (room.id) {
                combinedRoomIdsV2 << room.id.toString()
            }
            
            state["occSecs_${room.name}"] = 0 
            state["motionEvents_${room.name}"] = 0
            state["isMotionActive_${room.name}"] = false
        }
        
        if (combinedRoomIdsV2.size() > 0) {
            String targetRooms = combinedRoomIdsV2.join(",")
            dispatchVacuum(vacuum2, settings.vac2Brand, "room", targetRooms, v2Queue[0].water, v2Queue[0].suction)
        }
    }
}

void logInfo(String msg) {
    if (settings?.logEnable != false) log.info "${app.name}: ${msg}"
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

String generateVacuumTable(vacDevice, dockPlug, String vacTitle, String intentAction, String intentRooms) {
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
    if (vState.toLowerCase() in ["paused", "returning dock", "returning to dock"]) stateColor = "orange"

    String filter = getConsumableVal(vacDevice, "remainingFilter") ?: getConsumableVal(vacDevice, "filter")
    String mBrush = getConsumableVal(vacDevice, "remainingMainBrush") ?: getConsumableVal(vacDevice, "mainBrush")
    String sBrush = getConsumableVal(vacDevice, "remainingSideBrush") ?: getConsumableVal(vacDevice, "sideBrush")
    String sensor = getConsumableVal(vacDevice, "remainingSensors") ?: getConsumableVal(vacDevice, "sensor")
    
    String presence = vacDevice.currentValue("presence")?.toString()?.capitalize() ?: "Online"
    String presenceColor = (presence.toLowerCase() == "not present" || presence.toLowerCase() == "offline") ? "red" : "green"
    
    String powerState = dockPlug ? (dockPlug.currentValue("switch")?.toString()?.toUpperCase() ?: "UNKNOWN") : "UNMANAGED"
    String powerColor = (powerState == "ON") ? "green" : (powerState == "OFF" ? "red" : "gray")
    
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
    
    html += "<tr style='border-bottom: 1px solid #ddd;'>"
    html += "<td style='padding: 8px;'><b>Hardware Error</b></td><td style='padding: 8px; color: ${errColor}; font-weight:bold;'>${vErr.capitalize()}</td>"
    html += "<td style='padding: 8px;'><b>Dock Status</b></td><td style='padding: 8px; color: ${dockErrColor}; font-weight:bold;'>${dockErr.capitalize()}</td>"
    html += "</tr>"

    html += "<tr style='border-bottom: 2px solid #ddd; background-color: #f0f8ff;'>"
    html += "<td style='padding: 8px;'><b>Network Health</b></td><td style='padding: 8px; color: ${presenceColor}; font-weight:bold;'>${presence}</td>"
    html += "<td style='padding: 8px;'><b>Dock Power</b></td><td style='padding: 8px; color: ${powerColor}; font-weight:bold;'>${powerState}</td>"
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

    if (vacuum1) html += generateVacuumTable(vacuum1, dockPlug1, "Vacuum 1 (Primary)", state.v1_intentAction ?: "Idle", state.v1_intentRooms ?: "--")
    if (vacuum2) html += generateVacuumTable(vacuum2, dockPlug2, "Vacuum 2 (Secondary)", state.v2_intentAction ?: "Idle", state.v2_intentRooms ?: "--")
    
    boolean isPaused = masterSwitch ? (masterSwitch.currentValue("switch") == "off") : false
    String globalStatus = isPaused ? "<span style='color: red; font-weight: bold;'>PAUSED (Physical Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
        
    html += "<div style='margin-top: 10px; padding: 8px; background: #e9e9e9; border-radius: 4px; font-size: 13px;'><b>Hardware Interlock:</b> ${globalStatus}</div>"
    
    long daysIdleAny = state.lastCleanTime ? ((now() - state.lastCleanTime) / 86400000) : 0
    long daysIdleFull = state.lastFullCleanTime ? ((now() - state.lastFullCleanTime) / 86400000) : 0
    html += "<div style='margin-top: 10px; padding: 8px; background: #fff3e0; border: 1px solid #ffe0b2; border-radius: 4px; font-size: 13px; color: #e65100;'>"
    html += "<b>Time Since Last Dispatch (Any):</b> ${daysIdleAny} Days<br>"
    html += "<b style='color: #d84315;'>Time Since Last Full House Clean:</b> ${daysIdleFull} Days (Max Allowed: ${settings.maxIdleDays ?: 'Disabled'})"
    html += "</div>"

    String roomHtml = "<div style='margin-top: 15px;'><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
    roomHtml += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room Name</th><th style='padding: 8px;'>Total Activity</th><th style='padding: 8px;'>Motion Events</th><th style='padding: 8px;'>Last Dispatch Status</th></tr>"
    
    boolean roomsExist = false
    for (int i = 1; i <= 12; i++) {
        if (settings["enableRoom_${i}"] && settings["roomName_${i}"]) {
            roomsExist = true
            String rName = settings["roomName_${i}"]
            int occSecs = state["occSecs_${rName}"] ?: 0
            int occMins = (occSecs / 60) as Integer
            int remainderSecs = occSecs % 60
            int mEvents = state["motionEvents_${rName}"] ?: 0
            String dStatus = state.lastDispatchLog ? (state.lastDispatchLog[rName] ?: "--") : "--"
            
            roomHtml += "<tr style='border-bottom: 1px solid #ddd;'>"
            roomHtml += "<td style='padding: 8px;'><b>${rName}</b></td>"
            roomHtml += "<td style='padding: 8px;'>${occMins}m ${remainderSecs}s</td>"
            roomHtml += "<td style='padding: 8px;'>${mEvents} Triggers</td>"
            roomHtml += "<td style='padding: 8px;'>${dStatus}</td>"
            roomHtml += "</tr>"
        }
    }
    
    roomHtml += "</table></div>"
    
    if (roomsExist) html += roomHtml

    double v1Watts = getVacWatts(settings.vac1Model, settings.vac1Watts)
    double v2Watts = getVacWatts(settings.vac2Model, settings.vac2Watts)
    
    double currentV1Off = (state.dock1OffTime) ? ((now() - state.dock1OffTime) / 3600000.0) : 0.0
    double currentV2Off = (state.dock2OffTime) ? ((now() - state.dock2OffTime) / 3600000.0) : 0.0
    
    double totalV1Hours = (state.dock1OfflineHours ?: 0.0) + currentV1Off
    double totalV2Hours = (state.dock2OfflineHours ?: 0.0) + currentV2Off
    
    double v1KwhSaved = (totalV1Hours * v1Watts) / 1000.0
    double v2KwhSaved = (totalV2Hours * v2Watts) / 1000.0
    double totalKwhSaved = v1KwhSaved + v2KwhSaved
    
    double kwRate = settings.kwRate ?: 0.15
    double v1MoneySaved = v1KwhSaved * kwRate
    double v2MoneySaved = v2KwhSaved * kwRate
    double totalMoneySaved = totalKwhSaved * kwRate
    
    if (settings.smartROISavings != false) {
        html += "<div style='margin-top: 10px; padding: 8px; background: #e8f5e9; border: 1px solid #c8e6c9; border-radius: 4px; font-size: 13px; color: #2e7d32;'>"
        html += "<b>Financial & Energy ROI:</b> System has prevented a total of <b>${String.format("%.2f", totalKwhSaved)} kWh</b> of phantom draw, saving <b>\$${String.format("%.2f", totalMoneySaved)}</b>.<br>"
        
        html += "<div style='margin-top: 6px; font-size: 12px; border-top: 1px solid #c8e6c9; padding-top: 6px;'>"
        if (vacuum1) html += "↳ <b>Vacuum 1:</b> ${String.format("%.2f", v1KwhSaved)} kWh saved (\$${String.format("%.2f", v1MoneySaved)})<br>"
        if (vacuum2) html += "↳ <b>Vacuum 2:</b> ${String.format("%.2f", v2KwhSaved)} kWh saved (\$${String.format("%.2f", v2MoneySaved)})"
        html += "</div>"
        
        html += "</div>"
    }

    return html
}

void sendToButler(String contextMsg, String stashSummary) {
    if (settings.enableButlerLink) {
        // Send an invisible location event. The Butler will intercept this.
        sendLocationEvent(name: "voiceButlerMsg", value: "vacuum", descriptionText: contextMsg, data: stashSummary)
        logInfo("Sent to Voice Butler: ${contextMsg}")
    }
}

void broadcastStaffSchedule() {
    if (settings.enableButlerLink) {
        def payload = [
            lastFullClean: state.lastFullCleanTime ?: now(),
            maxIdle: settings.maxIdleDays != null ? settings.maxIdleDays.toInteger() : 3
        ]
        sendLocationEvent(name: "voiceButlerStaffSync", value: "sync", data: groovy.json.JsonOutput.toJson(payload))
    }
}
