/**
 * Advanced Mini Split Controls
 */

definition(
    name: "Advanced Mini Split Controls",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-Grade BMS. Features: Master App Switch, Mode Restrictions, RF State Enforcement, Peak Shedding, Delta T Alerting, Ambient Lockout, and Cost Tracking.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomConfigPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Mini Split Controls (BMS)</b>", install: true, uninstall: true) {
        
        section("<b>Live BMS, Health & Cost Dashboard</b>") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"
            
            def hasZones = false
            def rate = kwhCost ?: 0.13
            def totalAppDailyCost = 0.0
            def totalAppWeeklyCost = 0.0

            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone</th><th style='padding: 8px;'>Current State</th><th style='padding: 8px;'>Climate & Health</th><th style='padding: 8px;'>BMS Guards & Maint</th><th style='padding: 8px;'>Est. Cost</th></tr>"

            def outTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
            def isPeak = checkPeakShedding()
            def currentModeStr = location.mode
            def isRestricted = restrictedModes && (restrictedModes as List).contains(currentModeStr)

            for (int i = 1; i <= 2; i++) {
                if (settings["enableZ${i}"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Room ${i}"
                    def currentMode = state."currentMode_z${i}" ?: "OFF"
                    def tempDev = settings["z${i}Temp"]
                    def humDev = settings["z${i}Humidity"]
                    def datDev = settings["z${i}DATSensor"]
                    def secDehumDev = settings["z${i}SecDehum"]
                    def auxHeatDev = settings["z${i}AuxHeater"]
                    
                    def acWatts = settings["z${i}Watts"] ?: 1000.0
                    def secWatts = settings["z${i}SecDehumWatts"] ?: 400.0

                    // Calculate Active Setpoint & Ranges
                    def activeSetpoint = "--"
                    def pOff = isPeak ? (settings["peakOffset"] ?: 3) : 0
                    
                    if (currentMode.contains("COOL") && currentMode != "TURBO COOL" && currentMode != "DRY MODE") {
                        def base = 74
                        if (currentMode == "OCC COOL") base = settings["z${i}OccCool"] ?: 74
                        if (currentMode == "NIGHT COOL") base = settings["z${i}NightCool"] ?: 70
                        if (currentMode == "UNOCC COOL") base = settings["z${i}UnoccCool"] ?: 78
                        activeSetpoint = "${base + pOff}°"
                    } else if (currentMode.contains("HEAT") && currentMode != "TURBO HEAT" && currentMode != "AUX HEAT") {
                        def base = 68
                        if (currentMode == "OCC HEAT") base = settings["z${i}OccHeat"] ?: 68
                        if (currentMode == "NIGHT HEAT") base = settings["z${i}NightHeat"] ?: 65
                        if (currentMode == "UNOCC HEAT") base = settings["z${i}UnoccHeat"] ?: 62
                        activeSetpoint = "${base - pOff}°"
                    } else {
                        switch(currentMode) {
                            case "TURBO COOL": activeSetpoint = "${settings["z${i}TurboHigh"] ?: 82}°"; break
                            case "TURBO HEAT": activeSetpoint = "${settings["z${i}TurboLow"] ?: 58}°"; break
                            case "DRY MODE":
                            case "MILDEW PREVENTION": activeSetpoint = "Dry / Dehum"; break
                            case "AUX HEAT": activeSetpoint = "Aux Limit"; break
                            case "FAILSAFE OFF": activeSetpoint = "Failsafe (Doors Open)"; break
                            default: 
                                // Unit is OFF or in Standby. Show the active float bracket it's enforcing.
                                def isOcc = (settings["z${i}OccSwitch"]?.currentValue("switch") == "on") || state."pendingPreStage_z${i}"
                                def isNight = (settings["z${i}NightSwitch"]?.currentValue("switch") == "on")
                                
                                if (isNight) {
                                    activeSetpoint = "Float: ${(settings["z${i}NightHeat"] ?: 65) - pOff}° - ${(settings["z${i}NightCool"] ?: 70) + pOff}°"
                                } else if (isOcc) {
                                    activeSetpoint = "Float: ${(settings["z${i}OccHeat"] ?: 68) - pOff}° - ${(settings["z${i}OccCool"] ?: 74) + pOff}°"
                                } else {
                                    activeSetpoint = "Float: ${(settings["z${i}UnoccHeat"] ?: 62) - pOff}° - ${(settings["z${i}UnoccCool"] ?: 78) + pOff}°"
                                }
                                break
                        }
                    }

                    // Stats & Cost Math
                    def dailyStats = state."dailyStats_z${i}" ?: [:]
                    def todayStr = getTodayDateString()
                    def tStats = dailyStats[todayStr] ?: [runtime: 0, secRuntime: 0]
                    def lastUpd = state."lastStatUpdate_z${i}" ?: now()
                    def liveDelta = ((now() - lastUpd) / 1000).toLong()
                    if (liveDelta > 600) liveDelta = 0 
                    
                    def isRunning = (currentMode != "OFF" && currentMode != "FAILSAFE OFF" && currentMode != "BMS COMPRESSOR LOCKOUT" && currentMode != "CHANGEOVER DELAY")
                    def isSecRunning = (secDehumDev && secDehumDev.currentValue("switch") == "on")
                    
                    def liveTodayRuntime = tStats.runtime + (isRunning ? liveDelta : 0)
                    def liveTodaySecRuntime = tStats.secRuntime + (isSecRunning ? liveDelta : 0)
                    
                    def weeklyRuntime = isRunning ? liveDelta : 0
                    def weeklySecRuntime = isSecRunning ? liveDelta : 0
                    dailyStats.each { k, v -> 
                        weeklyRuntime += v.runtime
                        weeklySecRuntime += (v.secRuntime ?: 0)
                    }

                    def dailyCost = ((liveTodayRuntime * acWatts) + (liveTodaySecRuntime * secWatts)) / 3600.0 / 1000.0 * rate
                    def weeklyCost = ((weeklyRuntime * acWatts) + (weeklySecRuntime * secWatts)) / 3600.0 / 1000.0 * rate
                    
                    totalAppDailyCost += dailyCost
                    totalAppWeeklyCost += weeklyCost

                    // Maintenance Tracker
                    def totalRuntimeSecs = (state."lifetimeRuntimeSecs_z${i}" ?: 0) + (isRunning ? liveDelta : 0)
                    def runtimeHrs = Math.floor(totalRuntimeSecs / 3600).toInteger()
                    def filterColor = runtimeHrs > 250 ? "red" : "green"
                    def maintDisplay = "<span style='font-size:11px; color:${filterColor};'>Filter: ${runtimeHrs}/250 hrs</span>"
                    if(runtimeHrs > 250) maintDisplay += "<br><span style='font-size:10px; color:red; font-weight:bold;'>⚠️ CLEAN FILTER</span>"

                    // Delta T & DAT Logic
                    def deltaTDisplay = "--"
                    if (datDev) {
                        def currentDat = datDev.currentValue("temperature")
                        def datStr = currentDat != null ? "${currentDat}°" : "--°"
                        def deltaT = state."deltaT_z${i}" ?: 0.0
                        def deltaColor = (isRunning && !currentMode.contains("DRY") && !currentMode.contains("FAN") && !currentMode.contains("AUX") && Math.abs(deltaT) < 12.0 && (now() - (state."modeStartTime_z${i}" ?: 0) > 900000)) ? "red" : "green"
                        deltaTDisplay = "DAT: ${datStr} | <b><span style='color:${deltaColor};'>ΔT: ${String.format('%.1f', deltaT)}°</span></b>"
                    }

                    // Mode & Status Displays
                    def modeColor = "gray"
                    if (currentMode.contains("COOL")) modeColor = "#007BFF"
                    if (currentMode.contains("HEAT")) modeColor = "#FF4500"
                    if (currentMode.contains("AUX HEAT")) modeColor = "#FF8C00"
                    if (currentMode.contains("TURBO")) modeColor = "purple"
                    if (currentMode.contains("DRY") || currentMode.contains("MILDEW")) modeColor = "teal"
                    if (currentMode.contains("LOCKOUT") || currentMode.contains("DELAY") || currentMode == "FAILSAFE OFF") modeColor = "red"
                    
                    def modeDisplay = "<b><span style='color:${modeColor};'>${currentMode}</span></b>"
                    
                    if (isRestricted) {
                        modeDisplay += "<br><span style='font-size:11px; color:orange; font-weight:bold;'>PAUSED (Restricted Mode)</span>"
                    } else if (currentMode == "FAILSAFE OFF") {
                        modeDisplay += "<br><span style='font-size:11px; color:red; font-weight:bold;'>Window/Door Open!</span>"
                    } else if (currentMode == "BMS COMPRESSOR LOCKOUT") {
                        def left = Math.ceil(((state."compressorLockoutTime_z${i}" ?: now()) - now()) / 60000).toInteger()
                        modeDisplay += "<br><span style='font-size:11px; color:red;'>Rest Timer: ${left}m left</span>"
                    } else if (currentMode == "CHANGEOVER DELAY") {
                        modeDisplay += "<br><span style='font-size:11px; color:red;'>Changeover Guard Active</span>"
                    } else if (currentMode == "MILDEW PREVENTION") {
                        def elapsedMs = now() - (state."modeStartTime_z${i}" ?: now())
                        def leftMins = Math.ceil((1800000 - elapsedMs) / 60000).toInteger()
                        if (leftMins < 0) leftMins = 0
                        modeDisplay += "<br><span style='font-size:11px; color:teal; font-weight:bold;'>Drying Coils: ${leftMins}m left</span>"
                    } else if (isRunning) {
                        def activeMins = Math.floor((now() - (state."modeStartTime_z${i}" ?: now())) / 60000).toInteger()
                        modeDisplay += "<br><span style='font-size:11px; color:#888;'>Active for ${activeMins}m</span>"
                    } else {
                        modeDisplay += "<br><span style='font-size:11px; color:#888;'>Standby</span>"
                    }

                    if (secDehumDev) {
                        def secOnTime = state."secDehumOnTime_z${i}" ?: now()
                        def secMins = isSecRunning ? Math.floor((now() - secOnTime) / 60000).toInteger() : 0
                        def secState = isSecRunning ? "<span style='color:teal; font-weight:bold;'>ON (${secMins}m)</span>" : "<span style='color:gray;'>OFF</span>"
                        modeDisplay += "<br><span style='font-size:11px;'>Stage 1 Dehum: ${secState}</span>"
                    }

                    if (state."pendingPreStage_z${i}") {
                        modeDisplay += "<br><span style='font-size:11px; color:#007BFF; font-weight:bold;'>⏰ Pre-Staging Active</span>"
                    }

                    def guardDisplay = maintDisplay
                    if (auxHeatDev && outTemp != null && outTemp <= (settings["z${i}LockoutTemp"] ?: 15)) {
                        guardDisplay += "<br><span style='font-size:11px; color:#FF8C00;'>Outdoor Lockout Active</span>"
                    }
                    
                    statusText += "<tr style='border-bottom: 2px solid #ccc;'>"
                    statusText += "<td style='padding: 8px;'><b>${zName}</b></td>"
                    statusText += "<td style='padding: 8px;'>${modeDisplay}</td>"
                    statusText += "<td style='padding: 8px; font-size:11px;'>🎯 <b>Target: ${activeSetpoint}</b><br>🌡️ ${tempDev?.currentValue("temperature")}° | 💧 ${humDev?.currentValue("humidity")}%<br>${deltaTDisplay}</td>"
                    statusText += "<td style='padding: 8px;'>${guardDisplay}</td>"
                    statusText += "<td style='padding: 8px; font-size:11px;'><b>Today:</b> ${String.format('$%.2f', dailyCost)}<br><b>7-Day:</b> ${String.format('$%.2f', weeklyCost)}</td>"
                    statusText += "</tr>"
                }
            }
            statusText += "</table>"
            
            if (hasZones) {
                def globalStatus = (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
                
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>Global System Mode:</b> ${globalStatus}</div>"
                statusText += "</div>"
                
                def peakHtml = isPeak ? "<span style='color:#FF4500; font-weight:bold; margin-left: 15px;'>⚡ Peak Load Shedding Active</span>" : ""
                statusText += "<div style='margin-top: 5px; padding: 10px; background: #fff3e0; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ffcc80;'>"
                statusText += "<div style='width:100%;'><b>Outdoor Temp:</b> <span style='color:#555;'>${outTemp != null ? outTemp + '°F' : 'No Sensor'}</span> ${peakHtml}</div>"
                statusText += "<div style='width:100%; display:flex; gap: 20px;'>"
                statusText += "<div><b>System Est. Cost Today:</b> <span style='color: #d32f2f; font-weight:bold;'>${String.format('$%.2f', totalAppDailyCost)}</span></div>"
                statusText += "</div></div>"
                paragraph statusText
            } else {
                paragraph "<i>No zones configured yet.</i>"
            }
        }

        section("<b>Zone Management</b>") {
            for (int i = 1; i <= 2; i++) {
                def zName = settings["z${i}Name"] ?: "Zone ${i}"
                href(name: "roomPage${i}", page: "roomConfigPage", params: [roomId: i], title: "▶ Configure ${zName}", description: settings["enableZ${i}"] ? "Active" : "Disabled")
            }
        }

        section("<b>Global BMS Configuration</b>") {
            input "kwhCost", "decimal", title: 'Electricity Cost ($ per kWh)', defaultValue: 0.13
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<b>Global Mode Overrides</b>"
            input "restrictedModes", "mode", title: "Restricted Modes (Pause all HVAC automation)", multiple: true, required: false
            input "forceOffModes", "mode", title: "Force OFF Modes (Immediately turns off all units when entering these modes)", multiple: true, required: false
            
            paragraph "<b>Outdoor & Demand Response (Peak Load Shedding)</b>"
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Global Outdoor Temperature Sensor", required: false, submitOnChange: true
            input "peakStartTime", "time", title: "Peak Energy Start Time", required: false
            input "peakEndTime", "time", title: "Peak Energy End Time", required: false
            input "peakOffset", "number", title: "Peak Demand Setpoint Offset (°F)", defaultValue: 3, description: "During peak hours, Cooling targets are raised and Heating targets are lowered by this amount to save energy."
            
            paragraph "<b>Active Alerts & Notifications</b>"
            input "notificationDevice", "capability.notification", title: "Send Health & Maintenance Alerts to:", multiple: true, required: false
            
            paragraph "<b>Data Management & Overrides</b>"
            input "forceSync", "bool", title: "Manually Force Hardware Sync", defaultValue: false, submitOnChange: true
            if (settings["forceSync"]) {
                logAction("MANUAL OVERRIDE: Forcing hardware sync...")
                evaluateAllRooms()
                app.updateSetting("forceSync", false)
            }
            
            input "resetMaint", "bool", title: "Reset Filter/Maintenance Timers", defaultValue: false, submitOnChange: true
            if (settings["resetMaint"]) {
                for (int i = 1; i <= 2; i++) {
                    state."lifetimeRuntimeSecs_z${i}" = 0
                    state."filterNotified_z${i}" = false
                }
                logAction("Maintenance timers manually reset.")
                app.updateSetting("resetMaint", false)
            }
            
            input "resetCost", "bool", title: "Reset All Running Cost Data", defaultValue: false, submitOnChange: true
            if (settings["resetCost"]) {
                for (int i = 1; i <= 2; i++) {
                    state."dailyStats_z${i}" = [:]
                    state."lastStatUpdate_z${i}" = now()
                }
                logAction("Cost data reset.")
                app.updateSetting("resetCost", false)
            }
        }

        section("<b>Action History & Debugging</b>") {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false
            if (state.actionHistory) paragraph "<span style='font-size: 13px; font-family: monospace;'>${state.actionHistory.join("<br>")}</span>"
        }
    }
}

def roomConfigPage(params) {
    if (params?.roomId) state.editingRoom = params.roomId
    def i = state.editingRoom ?: 1
    def zName = settings["z${i}Name"] ?: "Zone ${i}"

    dynamicPage(name: "roomConfigPage", title: "<b>Configuration: ${zName}</b>", install: false, uninstall: false) {
        section("<b>▶ Basic Setup</b>") {
            input "enableZ${i}", "bool", title: "<b>Enable this Zone</b>", submitOnChange: true
             
            if (settings["enableZ${i}"]) {
                input "z${i}Name", "text", title: "Zone Name", defaultValue: "Zone ${i}", submitOnChange: true
                
                paragraph "<b>Climate Sensors</b>"
                input "z${i}Temp", "capability.temperatureMeasurement", title: "Main Room Temperature Sensor", required: true
                input "z${i}DATSensor", "capability.temperatureMeasurement", title: "Discharge Air Temp (DAT) Sensor (For Delta T)", required: false
                input "z${i}Humidity", "capability.relativeHumidityMeasurement", title: "Humidity Sensor", required: false, submitOnChange: true
                
                paragraph "<b>Occupancy & Schedules</b>"
                input "z${i}OccSwitch", "capability.switch", title: "Room Occupied Switch", required: true
                input "z${i}NightSwitch", "capability.switch", title: "Good Night Switch", required: false

                input "z${i}PreStageEnable", "bool", title: "Enable Predictive Pre-Staging", submitOnChange: true, description: "Will force 'Occupied' setpoints before you actually enter the room."
                if (settings["z${i}PreStageEnable"]) {
                    input "z${i}SchedDays", "enum", title: "↳ Scheduled Days", options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], multiple: true, required: true
                    input "z${i}SchedTime", "time", title: "↳ Pre-Stage Start Time", required: true
                    input "z${i}SchedDuration", "number", title: "↳ Pre-Stage Duration (Minutes)", defaultValue: 60, required: true
                }

                paragraph "<b>Temperature Swing / Hysteresis</b>"
                input "z${i}Deadband", "decimal", title: "Deadband / Swing (°F)", defaultValue: 2.0

                paragraph "<b>Ceiling Compensation Offset</b>"
                input "z${i}CoolOffset", "number", title: "Cooling Offset (Subtract from Target)", defaultValue: 3, submitOnChange: true
                input "z${i}HeatOffset", "number", title: "Heating Offset (Add to Target)", defaultValue: 4, submitOnChange: true

                paragraph "<b>BMS Protection Guards</b>"
                input "z${i}CompressorRest", "number", title: "Compressor Rest Delay (Minutes)", defaultValue: 3, required: true
                input "z${i}ChangeoverDelay", "number", title: "Auto-Changeover Guard (Minutes)", defaultValue: 60, required: true
                input "z${i}EnforceState", "number", title: "RF State Enforcement Interval (Minutes)", defaultValue: 30, description: "Re-transmits the active RF code periodically to fix dropped packets or remote overrides. (0 to disable)."
                
                if (outdoorTempSensor) {
                    paragraph "<b>Outdoor Ambient Lockout</b>"
                    input "z${i}LockoutTemp", "number", title: "Compressor Lockout Temp (°F)", defaultValue: 15, required: true
                    input "z${i}AuxHeater", "capability.switch", title: "Auxiliary Heater Switch (Optional)", required: false
                }

                paragraph "<b>Extreme Limits (Turbo Mode)</b>"
                input "z${i}TurboHigh", "number", title: "Turbo High Limit (°F)", defaultValue: 82, submitOnChange: true
                input "z${i}TurboLow", "number", title: "Turbo Low Limit (°F)", defaultValue: 58, submitOnChange: true

                paragraph "<b>Preferred Setpoints (Occupied)</b>"
                input "z${i}OccCool", "number", title: "Occupied Cooling Target (°F)", defaultValue: 74, submitOnChange: true
                input "z${i}OccHeat", "number", title: "Occupied Heating Target (°F)", defaultValue: 68, submitOnChange: true
                
                paragraph "<b>Preferred Setpoints (Good Night)</b>"
                input "z${i}NightCool", "number", title: "Night Cooling Target (°F)", defaultValue: 70, submitOnChange: true
                input "z${i}NightHeat", "number", title: "Night Heating Target (°F)", defaultValue: 65, submitOnChange: true

                paragraph "<b>Preferred Setpoints (Unoccupied)</b>"
                input "z${i}UnoccCool", "number", title: "Unoccupied Cooling Target (°F)", defaultValue: 78, submitOnChange: true
                input "z${i}UnoccHeat", "number", title: "Unoccupied Heating Target (°F)", defaultValue: 62, submitOnChange: true

                if (settings["z${i}Humidity"]) {
                    paragraph "<b>Two-Stage Humidity Control</b>"
                    input "z${i}HumidityTarget", "number", title: "High Humidity Limit (%)", defaultValue: 60
                    input "z${i}SecDehum", "capability.switch", title: "Stage 1 Dehumidifier (Passive Monitor)", required: false, submitOnChange: true
                    if (settings["z${i}SecDehum"]) {
                        input "z${i}AcDehumDelay", "number", title: "Stage 2 Delay (Minutes)", defaultValue: 60
                        input "z${i}SecDehumWatts", "decimal", title: "Stage 1 Dehumidifier Wattage", defaultValue: 400.0
                    }
                }

                input "z${i}MildewPrevent", "bool", title: "Enable Mildew Prevention Intercept", defaultValue: true
                input "z${i}Contacts", "capability.contactSensor", title: "Window/Door Failsafe Contacts", multiple: true, required: false
                input "z${i}ContactTimeout", "number", title: "Failsafe Timeout (Minutes)", defaultValue: 5

                def cOff = settings["z${i}CoolOffset"] ?: 3
                def hOff = settings["z${i}HeatOffset"] ?: 4

                paragraph "<b>Broadlink Integration (by tomw)</b>"
                input "z${i}Broadlink", "capability.actuator", title: "Broadlink Device", required: true, submitOnChange: true
                
                if (settings["z${i}Broadlink"]) {
                    input "z${i}CodeTurboCool", "text", title: "Turbo Cool Code", description: "Rec: COOL at ${(settings["z${i}TurboHigh"] ?: 82) - cOff}°"
                    input "testBtn_${i}_CodeTurboCool", "button", title: "▶ Test Turbo Cool Code"

                    input "z${i}CodeTurboHeat", "text", title: "Turbo Heat Code", description: "Rec: HEAT at ${(settings["z${i}TurboLow"] ?: 58) + hOff}°"
                    input "testBtn_${i}_CodeTurboHeat", "button", title: "▶ Test Turbo Heat Code"

                    input "z${i}CodeOccCool", "text", title: "Occupied Cool Code", description: "Rec: COOL at ${(settings["z${i}OccCool"] ?: 74) - cOff}°"
                    input "testBtn_${i}_CodeOccCool", "button", title: "▶ Test Occupied Cool Code"

                    input "z${i}CodeOccHeat", "text", title: "Occupied Heat Code", description: "Rec: HEAT at ${(settings["z${i}OccHeat"] ?: 68) + hOff}°"
                    input "testBtn_${i}_CodeOccHeat", "button", title: "▶ Test Occupied Heat Code"

                    input "z${i}CodeNightCool", "text", title: "Night Cool Code", description: "Rec: COOL at ${(settings["z${i}NightCool"] ?: 70) - cOff}°"
                    input "testBtn_${i}_CodeNightCool", "button", title: "▶ Test Night Cool Code"

                    input "z${i}CodeNightHeat", "text", title: "Night Heat Code", description: "Rec: HEAT at ${(settings["z${i}NightHeat"] ?: 65) + hOff}°"
                    input "testBtn_${i}_CodeNightHeat", "button", title: "▶ Test Night Heat Code"

                    input "z${i}CodeUnoccCool", "text", title: "Unoccupied Cool Code", description: "Rec: COOL at ${(settings["z${i}UnoccCool"] ?: 78) - cOff}°"
                    input "testBtn_${i}_CodeUnoccCool", "button", title: "▶ Test Unoccupied Cool Code"

                    input "z${i}CodeUnoccHeat", "text", title: "Unoccupied Heat Code", description: "Rec: HEAT at ${(settings["z${i}UnoccHeat"] ?: 62) + hOff}°"
                    input "testBtn_${i}_CodeUnoccHeat", "button", title: "▶ Test Unoccupied Heat Code"

                    input "z${i}CodeDry", "text", title: "Mildew / Dry Code", description: "Rec: DRY MODE"
                    input "testBtn_${i}_CodeDry", "button", title: "▶ Test Mildew / Dry Code"

                    input "z${i}CodeOff", "text", title: "System OFF Code"
                    input "testBtn_${i}_CodeOff", "button", title: "▶ Test OFF Code"
                }

                input "z${i}Watts", "decimal", title: "Estimated Active Wattage of Mini Split (Watts)", defaultValue: 1000.0
            }
        }
    }
}

// ==============================================================================
// INTERNAL BMS LOGIC ENGINE
// ==============================================================================

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    
    for (int i = 1; i <= 2; i++) {
        if (!state."currentMode_z${i}") state."currentMode_z${i}" = "OFF"
        if (!state."dailyStats_z${i}") state."dailyStats_z${i}" = [:]
        state."lastStatUpdate_z${i}" = now()
        state."deltaT_z${i}" = 0.0
        state."secDehumOnTime_z${i}" = null
        state."pendingPreStage_z${i}" = false
        state."filterNotified_z${i}" = false
        state."deltaTNotified_z${i}" = false
        
        // BMS Trackers
        state."lastOffTime_z${i}" = now()
        state."lastCoolTime_z${i}" = 0
        state."lastHeatTime_z${i}" = 0
        state."lastRfSentTime_z${i}" = 0
    }
    
    subscribe(location, "mode", modeChangeHandler)
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", masterSwitchHandler)
    
    for (int i = 1; i <= 2; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", sensorHandler)
            if (settings["z${i}DATSensor"]) subscribe(settings["z${i}DATSensor"], "temperature", sensorHandler)
            if (settings["z${i}Humidity"]) subscribe(settings["z${i}Humidity"], "humidity", sensorHandler)
            if (settings["z${i}OccSwitch"]) subscribe(settings["z${i}OccSwitch"], "switch", sensorHandler)
            if (settings["z${i}NightSwitch"]) subscribe(settings["z${i}NightSwitch"], "switch", sensorHandler)
            if (settings["z${i}Contacts"]) subscribe(settings["z${i}Contacts"], "contact", contactHandler)
            if (settings["z${i}SecDehum"]) subscribe(settings["z${i}SecDehum"], "switch", secDehumHandler)
            
            if (settings["z${i}PreStageEnable"] && settings["z${i}SchedTime"]) {
                schedule(settings["z${i}SchedTime"], "triggerPreStageZ${i}")
            }
        }
    }
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", sensorHandler)
    
    runEvery5Minutes("evaluateAllRooms")
    logAction("Advanced Mini Split Controls (BMS Edition) Initialized.")
    evaluateAllRooms()
}

def isMasterEnabled() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def masterSwitchHandler(evt) {
    logAction("Master Enable Switch turned ${evt.value}.")
    if (evt.value == "on") {
        evaluateAllRooms()
    } else {
        unschedule() 
    }
}

def modeChangeHandler(evt) {
    if (!isMasterEnabled()) return
    logAction("Location mode changed to: ${evt.value}")
    
    def isForceOff = forceOffModes && (forceOffModes as List).contains(evt.value)
    if (isForceOff) {
        logAction("GLOBAL FORCE OFF TRIGGERED: Mode changed to ${evt.value}.")
        for (int i = 1; i <= 2; i++) {
            if (settings["enableZ${i}"]) {
                state."currentMode_z${i}" = "OFF"
                state."lastOffTime_z${i}" = now()
                executeRFCommand(i, "OFF", true)
            }
        }
    }
    evaluateAllRooms()
}

def sensorHandler(evt) { runIn(2, "evaluateAllRooms") }
def contactHandler(evt) { runIn(2, "evaluateAllRooms") }

def secDehumHandler(evt) {
    for (int i = 1; i <= 2; i++) {
        if (settings["enableZ${i}"] && settings["z${i}SecDehum"]?.id == evt.device.id) {
            if (evt.value == "on") state."secDehumOnTime_z${i}" = now()
            else state."secDehumOnTime_z${i}" = null
        }
    }
    runIn(2, "evaluateAllRooms")
}

// Pre-Stage Scheduling
def triggerPreStageZ1() { executePreStage(1) }
def triggerPreStageZ2() { executePreStage(2) }

def executePreStage(roomId) {
    if (!isMasterEnabled()) return
    
    def currentModeStr = location.mode
    if (restrictedModes && (restrictedModes as List).contains(currentModeStr)) return
    if (forceOffModes && (forceOffModes as List).contains(currentModeStr)) return
    
    def activeDays = settings["z${roomId}SchedDays"]
    if (activeDays) {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)
        if (!activeDays.contains(df.format(new Date()))) return 
    }
    logAction("${settings["z${roomId}Name"]}: Executing Predictive Pre-Staging. Conditioning room prior to arrival.")
    state."pendingPreStage_z${roomId}" = true
    evaluateRoom(roomId)
    
    def dur = settings["z${roomId}SchedDuration"] ?: 60
    runIn(dur * 60, "cancelPreStageZ${roomId}")
}

def cancelPreStageZ1() { state."pendingPreStage_z1" = false; evaluateRoom(1) }
def cancelPreStageZ2() { state."pendingPreStage_z2" = false; evaluateRoom(2) }

def checkPeakShedding() {
    def isPeak = false
    if (peakStartTime && peakEndTime) {
        def start = timeToday(peakStartTime, location.timeZone).time
        def end = timeToday(peakEndTime, location.timeZone).time
        def curr = now()
        if (start <= end) isPeak = (curr >= start && curr <= end)
        else isPeak = (curr >= start || curr <= end)
    }
    return isPeak
}

def evaluateAllRooms() {
    if (!isMasterEnabled()) return
    
    for (int i = 1; i <= 2; i++) {
        if (settings["enableZ${i}"]) evaluateRoom(i)
    }
}

def evaluateRoom(roomId) {
    if (!isMasterEnabled()) return

    def currentModeStr = location.mode
    def isRestricted = restrictedModes && (restrictedModes as List).contains(currentModeStr)
    def isForceOff = forceOffModes && (forceOffModes as List).contains(currentModeStr)

    def tempDev = settings["z${roomId}Temp"]
    def humDev = settings["z${roomId}Humidity"]
    def datDev = settings["z${roomId}DATSensor"]
    def secDehumDev = settings["z${roomId}SecDehum"]
    
    def temp = tempDev ? (tempDev.currentValue("temperature") ?: 72.0).toFloat() : 72.0
    def humidity = humDev ? (humDev.currentValue("humidity") ?: 50).toInteger() : 50
    def outTemp = outdoorTempSensor ? outdoorTempSensor.currentValue("temperature") : null
    
    // Ensure accurate Stage 1 tracking
    if (secDehumDev && secDehumDev.currentValue("switch") == "on" && !state."secDehumOnTime_z${roomId}") {
        def lastEvt = secDehumDev.events(max: 1)?.find{ it.value == "on" }
        state."secDehumOnTime_z${roomId}" = lastEvt ? lastEvt.date.time : now()
    } else if (secDehumDev && secDehumDev.currentValue("switch") == "off") {
        state."secDehumOnTime_z${roomId}" = null
    }

    def cMode = state."currentMode_z${roomId}" ?: "OFF"

    // Delta T Check & Health Notification
    if (datDev) {
        def dat = (datDev.currentValue("temperature") ?: temp).toFloat()
        if (cMode.contains("COOL") && !cMode.contains("DRY")) state."deltaT_z${roomId}" = temp - dat
        else if (cMode.contains("HEAT")) state."deltaT_z${roomId}" = dat - temp
        else state."deltaT_z${roomId}" = 0.0
    }

    def isOcc = (settings["z${roomId}OccSwitch"]?.currentValue("switch") == "on")
    def isNight = (settings["z${roomId}NightSwitch"]?.currentValue("switch") == "on")
    
    if (isOcc && state."pendingPreStage_z${roomId}") {
        state."pendingPreStage_z${roomId}" = false
        logDebug("Pre-staging goal met (Occupancy detected). Resuming normal control.")
    }
    
    // Apply Global Overrides to target mode early
    def targetMode = "OFF"
    if (isForceOff) {
        targetMode = "OFF"
    } else if (isRestricted) {
        // Paused. Let the room float manually, skip all evaluations.
        updateRoomStats(roomId, (cMode != "OFF" && cMode != "FAILSAFE OFF" && !cMode.contains("LOCKOUT") && !cMode.contains("DELAY")), (secDehumDev?.currentValue("switch") == "on"))
        return
    } else {
        targetMode = determineTargetMode(roomId, temp, humidity, isOcc, isNight, outTemp)
    }

    def currentMode = state."currentMode_z${roomId}" ?: "OFF"
    
    // 1. Mildew Intercept Check
    if (!isForceOff && settings["z${roomId}MildewPrevent"] && currentMode.contains("COOL") && !currentMode.contains("DRY") && currentMode != targetMode && (targetMode == "OFF" || targetMode == "UNOCC COOL" || targetMode == "UNOCC HEAT")) {
        def dryCode = settings["z${roomId}CodeDry"]
        def broadlinkDev = settings["z${roomId}Broadlink"]
        if (dryCode && broadlinkDev) {
            logAction("${settings["z${roomId}Name"]}: Mildew Prevention Intercept! Running FAN/DRY for 30 minutes before switching down.")
            state."currentMode_z${roomId}" = "MILDEW PREVENTION"
            state."modeStartTime_z${roomId}" = now()
            executeRFCommand(roomId, "MILDEW PREVENTION", true)
            runIn(1800, "finishMildewCycle", [data: [room: roomId, nextMode: targetMode]])
            return
        }
    }
    if (currentMode == "MILDEW PREVENTION") {
        if (isForceOff || targetMode == "TURBO COOL" || targetMode == "TURBO HEAT" || targetMode == "FAILSAFE OFF" || targetMode == "OCC COOL" || targetMode == "OCC HEAT") {
            logAction("${settings["z${roomId}Name"]}: Priority climate demand detected. Canceling Mildew cycle.")
            unschedule("finishMildewCycle")
        } else return 
    }
    
    // 2. BMS GUARDS: Rest Timer & Changeover (Skip guards if Force Off)
    if (!isForceOff && targetMode != "OFF" && targetMode != "FAILSAFE OFF" && targetMode != "AUX HEAT") {
        
        // A. Anti-Short Cycle
        def restMins = settings["z${roomId}CompressorRest"] ?: 3
        def lastOff = state."lastOffTime_z${roomId}" ?: 0
        if (currentMode == "OFF" || currentMode == "FAILSAFE OFF") {
            def elapsedMins = (now() - lastOff) / 60000
            if (elapsedMins < restMins) {
                if (currentMode != "BMS COMPRESSOR LOCKOUT") {
                    logAction("${settings["z${roomId}Name"]}: BMS Guard - Compressor Rest Delay active. Delaying ON command for ${restMins - Math.floor(elapsedMins).toInteger()} minutes.")
                    state."currentMode_z${roomId}" = "BMS COMPRESSOR LOCKOUT"
                    state."compressorLockoutTime_z${roomId}" = lastOff + (restMins * 60000)
                }
                runIn(60, "evaluateAllRooms")
                return
            }
        }
        
        // B. Changeover Delay
        def changeDelay = settings["z${roomId}ChangeoverDelay"] ?: 60
        def lastC = state."lastCoolTime_z${roomId}" ?: 0
        def lastH = state."lastHeatTime_z${roomId}" ?: 0
        
        def wantsCool = targetMode.contains("COOL") || targetMode.contains("DRY")
        def wantsHeat = targetMode.contains("HEAT")
        
        if (wantsCool && lastH > 0 && ((now() - lastH) / 60000) < changeDelay) {
            if (currentMode != "CHANGEOVER DELAY") {
                logAction("${settings["z${roomId}Name"]}: BMS Guard - Auto-Changeover Delay active. Preventing switch from Heat to Cool.")
                state."currentMode_z${roomId}" = "CHANGEOVER DELAY"
                executeRFCommand(roomId, "OFF", true)
            }
            runIn(60, "evaluateAllRooms")
            return
        }
        if (wantsHeat && lastC > 0 && ((now() - lastC) / 60000) < changeDelay) {
            if (currentMode != "CHANGEOVER DELAY") {
                logAction("${settings["z${roomId}Name"]}: BMS Guard - Auto-Changeover Delay active. Preventing switch from Cool to Heat.")
                state."currentMode_z${roomId}" = "CHANGEOVER DELAY"
                executeRFCommand(roomId, "OFF", true)
            }
            runIn(60, "evaluateAllRooms")
            return
        }
    }
    
    updateRoomStats(roomId, (currentMode != "OFF" && currentMode != "FAILSAFE OFF" && !currentMode.contains("LOCKOUT") && !currentMode.contains("DELAY")), (secDehumDev?.currentValue("switch") == "on"))

    if (currentMode != targetMode) {
        def zName = settings["z${roomId}Name"] ?: "Room ${roomId}"
        if (!isForceOff) logAction("${zName}: Mode changing from ${currentMode} to ${targetMode}.")
        
        if (targetMode == "OFF" || targetMode == "FAILSAFE OFF") state."lastOffTime_z${roomId}" = now()
        if (targetMode.contains("COOL") || targetMode.contains("DRY")) state."lastCoolTime_z${roomId}" = now()
        if (targetMode.contains("HEAT")) state."lastHeatTime_z${roomId}" = now()
        
        state."currentMode_z${roomId}" = targetMode
        state."modeStartTime_z${roomId}" = now()
        
        executeRFCommand(roomId, targetMode, true) // Force true on change
    } else {
        if (targetMode.contains("COOL") || targetMode.contains("DRY")) state."lastCoolTime_z${roomId}" = now()
        if (targetMode.contains("HEAT")) state."lastHeatTime_z${roomId}" = now()
        
        // 3. BMS State Enforcement (Blind Sync)
        def enforceMins = settings["z${roomId}EnforceState"] != null ? settings["z${roomId}EnforceState"] : 30
        if (enforceMins > 0 && targetMode != "BMS COMPRESSOR LOCKOUT" && targetMode != "CHANGEOVER DELAY") {
            def lastSent = state."lastRfSentTime_z${roomId}" ?: 0
            if (((now() - lastSent) / 60000) >= enforceMins) {
                logAction("${settings["z${roomId}Name"]}: BMS Guard - State Enforcement active. Re-transmitting ${targetMode} code.")
                executeRFCommand(roomId, targetMode, true)
            }
        }
    }
}

def finishMildewCycle(data) {
    def roomId = data.room
    def nextMode = data.nextMode
    logAction("${settings["z${roomId}Name"]}: Mildew Prevention complete. Resuming schedule (${nextMode}).")
    state."currentMode_z${roomId}" = nextMode
    state."modeStartTime_z${roomId}" = now()
    state."lastOffTime_z${roomId}" = now() 
    executeRFCommand(roomId, nextMode, true)
}

def determineTargetMode(roomId, temp, humidity, isOcc, isNight, outTemp) {
    def contacts = settings["z${roomId}Contacts"]
    if (contacts && contacts.any { it.currentValue("contact") == "open" }) {
        def anyOpen = contacts.find { it.currentValue("contact") == "open" }
        def openTime = anyOpen.events(max: 1)[0]?.date?.time ?: now()
        def timeoutMs = (settings["z${roomId}ContactTimeout"] ?: 5) * 60000
        if ((now() - openTime) >= timeoutMs) return "FAILSAFE OFF"
    }

    def turboH = settings["z${roomId}TurboHigh"] ?: 82
    def turboL = settings["z${roomId}TurboLow"] ?: 58
    def occC = settings["z${roomId}OccCool"] ?: 74
    def occH = settings["z${roomId}OccHeat"] ?: 68
    def nightC = settings["z${roomId}NightCool"] ?: 70
    def nightH = settings["z${roomId}NightHeat"] ?: 65
    def unoccC = settings["z${roomId}UnoccCool"] ?: 78
    def unoccH = settings["z${roomId}UnoccHeat"] ?: 62
    def deadband = settings["z${roomId}Deadband"] ?: 2.0
    def humTarget = settings["z${roomId}HumidityTarget"] ?: 60
    
    // Apply Peak Load Shedding Demand Offsets
    if (checkPeakShedding()) {
        def pOff = settings["peakOffset"] ?: 3
        occC += pOff; unoccC += pOff; nightC += pOff;
        occH -= pOff; unoccH -= pOff; nightH -= pOff;
    }

    def lockoutTemp = settings["z${roomId}LockoutTemp"] ?: 15
    def hasAuxHeater = settings["z${roomId}AuxHeater"] != null
    def currentMode = state."currentMode_z${roomId}" ?: "OFF"
    
    if (state."pendingPreStage_z${roomId}") isOcc = true 

    // 1. Extreme Limits
    if (temp >= turboH) return "TURBO COOL"
    if (currentMode == "TURBO COOL" && temp > (turboH - deadband)) return "TURBO COOL"

    if (temp <= turboL) {
        if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
        return "TURBO HEAT"
    }
    if (currentMode == "TURBO HEAT" || currentMode == "AUX HEAT") {
        if (temp < (turboL + deadband)) {
            if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
            return "TURBO HEAT"
        }
    }

    // 2. Humidity Override
    def activeCoolTarget = isNight ? nightC : (isOcc ? occC : unoccC)
    def secDehumDev = settings["z${roomId}SecDehum"]
    def acDelayMins = settings["z${roomId}AcDehumDelay"] ?: 60
    
    def allowAcDry = false
    if (humidity > humTarget && temp >= activeCoolTarget) {
        if (secDehumDev && secDehumDev.currentValue("switch") == "on") {
            def onTime = state."secDehumOnTime_z${roomId}" ?: now()
            if ((now() - onTime) >= (acDelayMins * 60000)) allowAcDry = true
        } else if (!secDehumDev) {
            allowAcDry = true
        }
    } else if (currentMode == "DRY MODE" && humidity > (humTarget - 5) && temp >= (activeCoolTarget - deadband)) {
        allowAcDry = true 
    }

    if (allowAcDry) return "DRY MODE"

    // 3. Good Night Priority (Continuous Fan/Operation)
    if (isNight) {
        def prefersHeat = (currentMode.contains("HEAT") || currentMode == "AUX HEAT")
        
        if (currentMode == "OFF" || currentMode == "FAILSAFE OFF" || currentMode == "STANDBY") {
             // If turning on from OFF, guess the best mode based on which setpoint is closer
             if (Math.abs(temp - nightH) < Math.abs(temp - nightC)) prefersHeat = true
        }

        if (prefersHeat) {
            // Keep in Heat mode unless it gets way too hot
            if (temp >= (nightC + deadband)) return "NIGHT COOL"
            
            if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
            return "NIGHT HEAT"
        } else {
            // Keep in Cool mode unless it gets way too cold
            if (temp <= (nightH - deadband)) {
                if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
                return "NIGHT HEAT"
            }
            return "NIGHT COOL"
        }
    }

    // 4. Standard Occupied
    if (isOcc) {
        if (temp >= (occC + deadband)) return "OCC COOL"
        if (currentMode == "OCC COOL" && temp > (occC - deadband)) return "OCC COOL"

        if (temp <= (occH - deadband)) {
            if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
            return "OCC HEAT"
        }
        if (currentMode == "OCC HEAT" || currentMode == "AUX HEAT") {
            if (temp < (occH + deadband)) {
                if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
                return "OCC HEAT"
            }
        }
        return "OFF"
    }
    
    // 5. Unoccupied Float
    if (!isOcc && !isNight) {
        if (temp >= (unoccC + deadband)) return "UNOCC COOL"
        if (currentMode == "UNOCC COOL" && temp > (unoccC - deadband)) return "UNOCC COOL"
        
        if (temp <= (unoccH - deadband)) {
            if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
            return "UNOCC HEAT"
        }
        if (currentMode == "UNOCC HEAT" || currentMode == "AUX HEAT") {
            if (temp < (unoccH + deadband)) {
                if (outTemp != null && outTemp <= lockoutTemp && hasAuxHeater) return "AUX HEAT"
                return "UNOCC HEAT"
            }
        }
        return "OFF"
    }
    
    return "OFF"
}

def executeRFCommand(roomId, targetMode, forceAction = false) {
    def broadlinkDev = settings["z${roomId}Broadlink"]
    def auxHeater = settings["z${roomId}AuxHeater"]
    
    if (targetMode == "AUX HEAT") {
        if (auxHeater) safeOn(auxHeater)
        if (broadlinkDev && forceAction) {
            state."lastRfSentTime_z${roomId}" = now()
            broadlinkDev.sendSavedCode(settings["z${roomId}CodeOff"])
        }
        return
    } else {
        if (auxHeater) safeOff(auxHeater)
    }

    if (!broadlinkDev) return
    
    def targetCode = null
    switch(targetMode) {
        case "TURBO COOL": targetCode = settings["z${roomId}CodeTurboCool"]; break
        case "TURBO HEAT": targetCode = settings["z${roomId}CodeTurboHeat"]; break
        case "OCC COOL": targetCode = settings["z${roomId}CodeOccCool"]; break
        case "OCC HEAT": targetCode = settings["z${roomId}CodeOccHeat"]; break
        case "NIGHT COOL": targetCode = settings["z${roomId}CodeNightCool"] ?: settings["z${roomId}CodeOccCool"]; break
        case "NIGHT HEAT": targetCode = settings["z${roomId}CodeNightHeat"] ?: settings["z${roomId}CodeOccHeat"]; break
        case "UNOCC COOL": targetCode = settings["z${roomId}CodeUnoccCool"]; break
        case "UNOCC HEAT": targetCode = settings["z${roomId}CodeUnoccHeat"]; break
        case "DRY MODE": 
        case "MILDEW PREVENTION": targetCode = settings["z${roomId}CodeDry"]; break
        case "FAILSAFE OFF": 
        case "OFF": targetCode = settings["z${roomId}CodeOff"]; break
    }

    if (forceAction) {
        state."lastRfSentTime_z${roomId}" = now()
        if (targetCode) {
            try {
                broadlinkDev.sendSavedCode(targetCode)
                logDebug("Sent RF code '${targetCode}' to ${broadlinkDev.displayName} for ${targetMode}")
            } catch (e) { log.error "RF Error Room ${roomId}: ${e.message}" }
        }
    }
}

// === BUTTON HANDLER ===
def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        // Automatically reloads the page, refreshing the dash stats
        return
    }
    
    if (btn.startsWith("testBtn_")) {
        def parts = btn.split("_")
        if (parts.size() == 3) {
            def roomId = parts[1]
            def codeSuffix = parts[2]
            def codeSettingName = "z${roomId}${codeSuffix}"
            def targetCode = settings[codeSettingName]
            def broadlinkDev = settings["z${roomId}Broadlink"]

            if (broadlinkDev && targetCode) {
                logInfo("TEST COMMAND: Sending ${codeSuffix} to ${broadlinkDev.displayName} (Room ${roomId})")
                try {
                    broadlinkDev.sendSavedCode(targetCode)
                } catch (e) { 
                    log.error "Test RF Error Room ${roomId}: ${e.message}" 
                }
            } else {
                log.warn("TEST COMMAND FAILED: Missing Broadlink device or saved code for ${codeSuffix} in Room ${roomId}.")
            }
        }
    }
}

// === HARDWARE SAFE WRAPPERS ===
def safeOn(dev) { if (dev && dev.currentValue("switch") != "on") { try { dev.on() } catch (e) {} } }
def safeOff(dev) { if (dev && dev.currentValue("switch") != "off") { try { dev.off() } catch (e) {} } }

// === STATS, NOTIFICATIONS & TRACKING ===
def getTodayDateString() {
    def df = new java.text.SimpleDateFormat("yyyy-MM-dd")
    df.setTimeZone(location.timeZone)
    return df.format(new Date())
}

def updateRoomStats(roomId, isRunning, isSecRunning) {
    def lastUpdate = state."lastStatUpdate_z${roomId}" ?: now()
    def nowMs = now()
    def deltaSecs = ((nowMs - lastUpdate) / 1000).toLong()
    if (deltaSecs > 300 || deltaSecs < 0) deltaSecs = 0

    def today = getTodayDateString()
    def stats = state."dailyStats_z${roomId}" ?: [:]
    def daily = stats[today] ?: [runtime: 0, secRuntime: 0]

    if (isRunning) daily.runtime += deltaSecs
    if (isSecRunning) daily.secRuntime += deltaSecs

    stats[today] = daily
    
    def keysToRemove = []
    def cal = Calendar.getInstance(location.timeZone)
    cal.add(Calendar.DAY_OF_YEAR, -7)
    def sevenDaysAgoStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(cal.getTime())
    stats.each { k, v -> if (k < sevenDaysAgoStr) keysToRemove << k }
    keysToRemove.each { stats.remove(it) }
 
    state."dailyStats_z${roomId}" = stats
    state."lastStatUpdate_z${roomId}" = nowMs
    
    // Process Notifications
    if (isRunning) state."lifetimeRuntimeSecs_z${roomId}" = (state."lifetimeRuntimeSecs_z${roomId}" ?: 0) + deltaSecs
    def runtimeHrs = Math.floor((state."lifetimeRuntimeSecs_z${roomId}" ?: 0) / 3600).toInteger()
    def currentMode = state."currentMode_z${roomId}" ?: "OFF"
    def deltaT = state."deltaT_z${roomId}" ?: 0.0

    if (notificationDevice) {
        if (runtimeHrs >= 250 && !state."filterNotified_z${roomId}") {
            notificationDevice.deviceNotification("BMS Alert: ${settings["z${roomId}Name"]} Filter has reached 250 hours. Please clean it.")
            state."filterNotified_z${roomId}" = true
        }
        
        if (isRunning && !currentMode.contains("DRY") && !currentMode.contains("FAN") && !currentMode.contains("AUX")) {
            if (Math.abs(deltaT) < 12.0 && (nowMs - (state."modeStartTime_z${roomId}" ?: 0) > 900000)) {
                if (!state."deltaTNotified_z${roomId}") {
                    notificationDevice.deviceNotification("BMS Critical: ${settings["z${roomId}Name"]} Delta T is severely low (${String.format('%.1f', deltaT)}°). Check for frozen coils, dirty filter, or low refrigerant.")
                    state."deltaTNotified_z${roomId}" = true
                }
            }
        } else {
            state."deltaTNotified_z${roomId}" = false // Reset flag if it turns off or corrects itself
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
def logDebug(msg) { if (debugEnable) log.debug "${app.label}: ${msg}" }
def disableDebugLog() { app.updateSetting("debugEnable", [value: "false", type: "bool"]) }
