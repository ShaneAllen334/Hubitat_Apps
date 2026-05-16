/**
 * Advanced Smart Blind Controller
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Smart Blind Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Predictive thermal engine with Financial ROI tracking, Virtual Aggregate Sensor, and Telemetry Dashboards.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            
            if (numRooms > 0) {
                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room</th><th style='padding: 8px;'>Environment</th><th style='padding: 8px;'>Verified State</th><th style='padding: 8px;'>Target & Reason</th><th style='padding: 8px;'>Active Locks & Timers</th></tr>"
                
                def now = new Date().time

                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    def dirDisplay = ""
                    if (enableSolarTracking && settings["windowAzimuth_${i}"] != null) {
                        dirDisplay = "Facing: ${settings["windowAzimuth_${i}"]}°"
                    } else {
                        dirDisplay = "Facing: ${settings["direction_${i}"] ?: "Unset"}"
                    }
                    def blind = settings["blind_${i}"]
                    
                    def rNameDisplay = "<b>${rName}</b><br><span style='font-size: 11px; color: #555;'>${dirDisplay}</span>"
                    
                    if (!blind) {
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${rNameDisplay}</td><td style='padding: 8px; color: #888;'>-</td><td style='padding: 8px; color: #888;'>Not Configured</td><td style='padding: 8px;'>-</td><td style='padding: 8px;'>-</td></tr>"
                        continue
                    }
                    
                    def tSensor = settings["tempSensor_${i}"]
                    def lSensor = settings["luxSensor_${i}"]
                    def rTemp = tSensor ? "${tSensor.currentValue('temperature')}°" : "--°"
                    def rLux = lSensor ? "${lSensor.currentValue('illuminance')} lx" : "-- lx"
                    def envDisplay = "<b>${rTemp}</b><br><span style='font-size: 11px; color: #555;'>${rLux}</span>"
                    
                    def vState = state.verifiedState?."${i}"?.toUpperCase() ?: "UNKNOWN"
                    def stateColor = (vState == "OPEN") ? "green" : (vState == "CLOSED" ? "blue" : "black")
                 
                    def tState = state.targetState?."${i}"?.toUpperCase() ?: "UNKNOWN"
                    def tReason = state.targetReason?."${i}" ?: "Awaiting Initial Sync..."
                 
                    def targetDisplay = "<b>${tState}</b><br><span style='font-size: 11px; color: #555;'>${tReason}</span>"
                    
                    def locks = []
                    if (state.manualHold?."${i}") {
                        def holdExpiry = state.manualHoldExpireTime?."${i}" ?: 0
                        if (holdExpiry > now) {
                            def timeLeftSecs = ((holdExpiry - now) / 1000).toInteger()
                            def timeLeftMins = (timeLeftSecs / 60).toInteger()
                            def remSecs = timeLeftSecs % 60
                            locks << "<span style='color: red; font-weight: bold;'>Manual Hold (${timeLeftMins}m ${remSecs}s)</span>"
                        } else {
                            locks << "<span style='color: red; font-weight: bold;'>Manual Hold</span>"
                        }
                    }
                    
                    if (state.windLock?."${i}") locks << "<span style='color: orange; font-weight: bold;'>Storm Shield</span>"
                    if (state.fortressLocked?."${i}") locks << "<span style='color: purple; font-weight: bold;'>Fortress Lock</span>"
                    if (settings["goodNightSwitch_${i}"]?.currentValue("switch") == "on") locks << "<span style='color: darkblue; font-weight: bold;'>Nap Lock</span>"
                    if (dndSwitch && dndSwitch.currentValue("switch") == "on") locks << "<span style='color: #e83e8c; font-weight: bold;'>DND Lock</span>"
                    
                    // Anti-Yo-Yo Cooldown Timer
                    def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
                    def debounceMillis = (environmentalDebounce != null ? environmentalDebounce.toInteger() : 15) * 60000
                    if ((now - lastMove) < debounceMillis) {
                        def timeLeftSecs = ((debounceMillis - (now - lastMove)) / 1000).toInteger()
                        def timeLeftMins = (timeLeftSecs / 60).toInteger()
                        def remSecs = timeLeftSecs % 60
                        locks << "<span style='color: #888; font-size: 11px;'>Cooldown: ${timeLeftMins}m ${remSecs}s</span>"
                    }
                    
                    // Master Timeout / Retry Timer
                    def cmdStart = state.commandStartTime["${i}"] ?: 0
                    def timeoutMins = settings["retryTimeoutMinutes"] != null ? settings["retryTimeoutMinutes"].toInteger() : 15
                    def timeoutMillis = timeoutMins * 60000
                    if (state.targetState["${i}"] != state.verifiedState["${i}"] && (now - cmdStart) < timeoutMillis && cmdStart > 0 && tReason != "TIMEOUT FAILED") {
                         def timeLeftSecs = ((timeoutMillis - (now - cmdStart)) / 1000).toInteger()
                         def timeLeftMins = (timeLeftSecs / 60).toInteger()
                         def remSecs = timeLeftSecs % 60
                         locks << "<span style='color: #d35400; font-size: 11px;'>Syncing: ${timeLeftMins}m ${remSecs}s left</span>"
                    }

                    def lockStr = locks ? locks.join("<br>") : "<span style='color: green;'>Clear</span>"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${rNameDisplay}</td><td style='padding: 8px;'>${envDisplay}</td><td style='padding: 8px; color: ${stateColor}; font-weight: bold;'>${vState}</td><td style='padding: 8px;'>${targetDisplay}</td><td style='padding: 8px;'>${lockStr}</td></tr>"
                }
                statusText += "</table>"
  
                def globalStatus = (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED</span>" : "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
                if (dndSwitch && dndSwitch.currentValue("switch") == "on") globalStatus += " <span style='color: #e83e8c; font-weight:bold;'>(DND LOCKED)</span>"
                if (state.preCoolingActive) globalStatus += " <span style='color: #0055aa; font-weight:bold;'>(PRE-COOLING ENGAGED)</span>"
                
                def outTemp = outdoorTempSensor ? "${outdoorTempSensor.currentValue('temperature')}°" : "--°"
                def outLux = outdoorLuxSensor ? "${outdoorLuxSensor.currentValue('illuminance')} lx" : "-- lx"
                def avgTemp = getAverageIndoorTemp()
                def hvac = mainThermostat ? mainThermostat.currentValue("thermostatOperatingState")?.capitalize() : "--"
                 
                statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
                statusText += "<div><b>System:</b> ${globalStatus}</div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>Outdoor:</b> ${outTemp} | ${outLux}</div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>House Avg:</b> ${avgTemp}°</div>"
                statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'><b>HVAC:</b> ${hvac}</div>"
                
                if (enableSolarTracking && state.currentSunPos) {
                    statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px; color: #d35400;'><b>Sun Pos:</b> Az: ${state.currentSunPos.azimuth}° | El: ${state.currentSunPos.elevation}°</div>"
                }
                statusText += "</div>"
                
                def lifetimeSavings = "\$" + (state.lifetimeSavings ?: 0.00).toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP)
                def todaySavings = "\$" + (state.todaySavings ?: 0.00).toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP)
                def effDisplay = (enableDynamicROI && state.dynamicEfficiency) ? "${state.dynamicEfficiency} (Self-Tuned)" : "${hvacEfficiency ?: 0.25} (Static)"
                
                statusText += "<div style='margin-top: 5px; padding: 10px; background: #e9f5ff; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #add8e6;'>"
                statusText += "<div><b>Estimated ROI:</b> <span style='color: #008800;'>Today: ${todaySavings}</span> | <span style='color: #0055aa;'>Total: ${lifetimeSavings}</span></div>"
                statusText += "<div style='border-left: 1px solid #add8e6; padding-left: 15px;'><b>Active Savings Rate:</b> ${effDisplay} kWh/hr</div>"
                statusText += "</div>"

                paragraph statusText
            } else {
                paragraph "<i>Configure rooms below to see live system status.</i>"
            }
        }
        
        if (enableTelemetryTracking) {
            section("Hardware Health & Telemetry") {
                def telText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                telText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Room ID</th><th style='padding: 8px;'>Commands Sent<br><span style='font-size:10px; font-weight:normal;'>(Today / 7D / All)</span></th><th style='padding: 8px;'>Movements<br><span style='font-size:10px; font-weight:normal;'>(Today / 7D / All)</span></th><th style='padding: 8px;'>Errors & Issues<br><span style='font-size:10px; font-weight:normal;'>(Today / 7D / All)</span></th></tr>"
                
                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def tData = state.telemetry?."${i}"
                    if (!tData || !tData.today) continue
                    
                    def cmdsT = tData.today.commands ?: 0; def cmds7 = get7DayMetric(i, "commands"); def cmdsA = tData.overall?.commands ?: 0
                    def opnT = tData.today.opens ?: 0; def opn7 = get7DayMetric(i, "opens"); def opnA = tData.overall?.opens ?: 0
                    def clsT = tData.today.closes ?: 0; def cls7 = get7DayMetric(i, "closes"); def clsA = tData.overall?.closes ?: 0
                    
                    def retT = tData.today.retries ?: 0; def ret7 = get7DayMetric(i, "retries"); def retA = tData.overall?.retries ?: 0
                    def wigT = tData.today.wiggles ?: 0; def wig7 = get7DayMetric(i, "wiggles"); def wigA = tData.overall?.wiggles ?: 0
                    def failT = tData.today.timeouts ?: 0; def fail7 = get7DayMetric(i, "timeouts"); def failA = tData.overall?.timeouts ?: 0
                    
                    def rName = settings["roomName_${i}"] ?: "Room ${i}"
                    
                    def rowColor = (fail7 > 0) ? "color: #d32f2f; font-weight: bold;" : ((ret7 > 5 || wig7 > 2) ? "color: #e65100; font-weight: bold;" : "color: #333;")
                    if (failA == 0 && retA == 0 && wigA == 0) rowColor = "color: #666;"
                    
                    def cmdsStr = "<b>${cmdsT}</b> / ${cmds7} / ${cmdsA}"
                    def movesStr = "<span style='color:green;'>Op: <b>${opnT}</b> / ${opn7} / ${opnA}</span><br><span style='color:blue;'>Cl: <b>${clsT}</b> / ${cls7} / ${clsA}</span>"
                    def errorStr = "Ret: <b>${retT}</b> / ${ret7} / ${retA}<br>Wig: <b>${wigT}</b> / ${wig7} / ${wigA}<br>Fail: <b>${failT}</b> / ${fail7} / ${failA}"
                    
                    telText += "<tr style='border-bottom: 1px solid #ddd; ${rowColor}'><td style='padding: 8px; color: black;'><b>${rName}</b></td><td style='padding: 8px;'>${cmdsStr}</td><td style='padding: 8px;'>${movesStr}</td><td style='padding: 8px;'>${errorStr}</td></tr>"
                }
                telText += "</table>"
                paragraph telText
                input "btnResetTelemetry", "button", title: "🧹 Reset Telemetry Data"
            }
        }
    
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.collect { entry -> 
                    def splitIdx = entry.indexOf(']') + 1
                    if (splitIdx > 0) {
                        return "<b>${entry.substring(0, splitIdx)}</b> ${entry.substring(splitIdx + 1)}"
                    } else return entry
                }.join("<br>")
                
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
        }
        
        section("Global Settings & Modes") {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false, description: "The Global Pause. ON = Application Runs. OFF = Application Paused."
            input "dndSwitch", "capability.switch", title: "Global Do Not Disturb (DND) Override Switch", required: false, description: "ON = Close all blinds & lock system. OFF = Resume automation."
            input "numRooms", "number", title: "Number of Rooms to Configure (1-12)", required: true, defaultValue: 1, range: "1..12", submitOnChange: true
            input "retryTimeoutMinutes", "number", title: "Max Sync Retry Duration (Minutes)", defaultValue: 15, required: true
            input "manualHoldTimeout", "number", title: "Auto-Release Manual Hold (Minutes, 0 = Never)", defaultValue: 120, required: true
            input "aggregateSensor", "capability.contactSensor", title: "Virtual Contact Sensor (All Blinds Status)", required: false
            input "masterBlind", "capability.windowShade", title: "Master Bond Device (For 'Open All' / 'Close All')", required: false
            
            input "activeModes", "mode", title: "Master Active Modes (App only runs in these)", multiple: true, required: false
            input "openOnModes", "mode", title: "Modes that trigger Global Open", multiple: true, required: false
            input "closeOnModes", "mode", title: "Modes that trigger Global Close", multiple: true, required: false
            
            paragraph "<b>Manual Controls & Overrides</b>"
            input "btnForceAllOpen", "button", title: "🔼 Force ALL Blinds OPEN (Engage Manual Override)"
            input "btnForceAllClose", "button", title: "🔽 Force ALL Blinds CLOSE (Engage Manual Override)"
            input "btnReleaseAllHolds", "button", title: "❌ Release All Manual Holds Now (And Sync House)"
            input "btnForceSync", "button", title: "🔄 Force System Re-evaluation & Sync Now"
       
            input "releaseOnAnyMode", "bool", title: "Release Manual Holds on ANY Mode Change?", defaultValue: false
            input "autoReleaseHoldModes", "mode", title: "Specific Modes that Auto-Release Manual Holds", multiple: true, required: false
            input "vacationModes", "mode", title: "Vacation Modes (Triggers random open/close presence)", multiple: true, required: false
        }
        
        section("Advanced Features (Toggles)") {
            input "enableSolarTracking", "bool", title: "Enable Solar Geometry & Azimuth Engine?", defaultValue: false, submitOnChange: true, description: "Calculates precise sun position. Requires window degrees."
            input "enableTelemetryTracking", "bool", title: "Enable Hardware Telemetry & Health Tracking?", defaultValue: false, submitOnChange: true
            input "enablePreCooling", "bool", title: "Enable Predictive Weather Pre-Cooling?", defaultValue: false, submitOnChange: true
            input "enableDynamicROI", "bool", title: "Enable Dynamic / Self-Tuning ROI Engine?", defaultValue: false, submitOnChange: true
        }
        
        section("Time & Solar Settings") {
            input "useSunriseSunset", "bool", title: "Enable Sunrise/Sunset automations?", defaultValue: false, submitOnChange: true
            
            if (useSunriseSunset) {
                input "releaseHoldSunrise", "bool", title: "Auto-Release Manual Holds at Sunrise?", defaultValue: false
                input "sunriseOffset", "number", title: "Sunrise Offset (Minutes, +/-)", defaultValue: 0
                input "sunriseModes", "mode", title: "Modes allowed for Auto-Sunrise Open", multiple: true, required: false
                input "releaseHoldSunset", "bool", title: "Auto-Release Manual Holds at Sunset?", defaultValue: false
                input "sunsetOffset", "number", title: "Sunset Offset (Minutes, +/-)", defaultValue: 0
                input "maxCloseTime", "time", title: "Maximum Evening Close Time", required: false
                input "sunsetModes", "mode", title: "Modes allowed for Auto-Sunset/Time Close", multiple: true, required: false
                input "sunsetDeadband", "number", title: "Sunset Deadband / Motor Saver (Minutes)", defaultValue: 30
                input "darkArrivalLockout", "bool", title: "Enable Dark Arrival Lockout?", defaultValue: true
                input "circadianWake", "bool", title: "Enable Circadian Gradual Wakeup?", defaultValue: false
            }
        }
        
        section("Exterior Weather & Master Solar Override") {
            input "windSensor", "capability.sensor", title: "Weather Station / Wind Sensor", required: false
            input "windThreshold", "number", title: "Storm Shield Wind Threshold (mph)", defaultValue: 15
            input "outdoorLuxSensor", "capability.illuminanceMeasurement", title: "Master Outdoor Lux Sensor", required: false
            input "highSolarRadiationThreshold", "number", title: "High Solar Radiation Threshold (Lux)", defaultValue: 10000
            input "luxHysteresis", "number", title: "Solar Radiation Hysteresis (Deadband Lux)", defaultValue: 500
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor", required: false
            input "outdoorHighTempThreshold", "number", title: "Outdoor High Temp Lockout (°)", defaultValue: 92
        }
        
        section("Environmental Controls & Predictive ROI") {
            input "mainThermostat", "capability.thermostat", title: "Main Thermostat (Syncs blinds with AC/Heat states)", required: false
            
            if (enablePreCooling) {
                paragraph "<div style='background:#e8f4f8; padding:8px; border:1px solid #bce8f1; border-radius:4px;'><b>Predictive Pre-Cooling</b><br>Locks down the house early based on the forecast high to trap morning cool air.</div>"
                input "meteorologistDevice", "capability.sensor", title: "Select Advanced Meteorologist Report Device", required: true
                input "preCoolingThreshold", "number", title: "Forecast High Threshold (°)", defaultValue: 90, required: true
                input "preCoolingTime", "time", title: "Time to Evaluate & Engage Defense", required: true
                paragraph "<hr>"
            }
            
            input "elecRate", "decimal", title: "Electricity Rate (per kWh)", defaultValue: 0.14, required: true
            input "hvacEfficiency", "decimal", title: "Est. kWh Saved per Hour of Defense", defaultValue: 0.25, required: true
            
            if (enableDynamicROI) {
                paragraph "<div style='background:#e8f8eb; padding:8px; border:1px solid #bce8cf; border-radius:4px;'><b>Self-Tuning ROI Active</b><br>The app will map your actual AC runtime on defended vs. undefended days to calculate your true efficiency.</div>"
                input "acPowerKW", "decimal", title: "Est. AC Unit Power Draw (kW) (e.g., 3.5)", defaultValue: 3.5, required: true
                paragraph "<hr>"
            }
            
            input "environmentalDebounce", "number", title: "Environmental Anti-Yo-Yo Hold Time (Minutes)", defaultValue: 15
            input "tempHysteresis", "decimal", title: "Temperature Hysteresis (Deadband °)", defaultValue: 1.0
            input "activeCoolingDefense", "bool", title: "Active Cooling Defense (Close sun-facing blinds when AC cools)?", defaultValue: true, submitOnChange: true
            input "enableFortressMode", "bool", title: "Enable Unoccupied Fortress Mode?", defaultValue: false, submitOnChange: true
                
            if (enableFortressMode) {
                input "fortressAutoReopen", "bool", title: "Auto-Reopen on Motion?", defaultValue: false
            }
            
            input "summerEnergyMode", "bool", title: "Summer Mode (Close shades to block heat)?", defaultValue: false, submitOnChange: true
            if (summerEnergyMode) {
                input "summerTempThreshold", "number", title: "Summer Indoor Temp Threshold (°)", defaultValue: 75
                input "summerOutdoorTempThreshold", "number", title: "Summer Outdoor Temp Trigger (Preemptive °)", defaultValue: 82, required: false
                input "summerAllowedModes", "mode", title: "Modes allowed for Summer Mode", multiple: true, required: false
            }
            
            input "winterHeatingMode", "bool", title: "Winter Mode (Open shades to harvest free solar heat)?", defaultValue: false, submitOnChange: true
            if (winterHeatingMode) {
                input "winterTempThreshold", "number", title: "Winter Indoor Temp Threshold (Open if below this °)", defaultValue: 68
                input "winterOutdoorTempThreshold", "number", title: "Winter Outdoor Temp Trigger (Preemptive °)", defaultValue: 45, required: false
                input "winterMaxOutdoorTemp", "number", title: "Winter Max Outdoor Temp Lockout (°)", defaultValue: 75, required: false
                input "winterAllowedModes", "mode", title: "Modes allowed for Winter Mode", multiple: true, required: false
            }
        }
        
        if (numRooms > 0 && numRooms <= 12) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def roomNum = i
                def rName = settings["roomName_${i}"] ?: "Room ${i}"
                section("${rName}") {
                    href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "Configure ${rName}")
                }
            }
        }
    }
}

def roomPage(params) {
    def rNum = params?.roomNum ?: state.currentRoom ?: 1
    state.currentRoom = rNum
    def currentName = settings["roomName_${rNum}"] ?: "Room ${rNum}"
    
    dynamicPage(name: "roomPage", title: "${currentName} Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Room Identification") {
            input "roomName_${rNum}", "text", title: "Custom Room Name", required: false, defaultValue: "Room ${rNum}", submitOnChange: true
        }
        
        section("Control Devices") {
            input "blind_${rNum}", "capability.windowShade", title: "Blind / Shade Device (Bond)", required: false
            input "blindSensor_${rNum}", "capability.contactSensor", title: "Blind State Sensor (Manual override detection)", required: false
            input "sensorDebounce_${rNum}", "number", title: "Sensor Stabilization Time (Seconds)", defaultValue: 15, required: false, description: "Increase this if ceiling fans or drafts cause false manual overrides."
            
            if (enableSolarTracking) {
                input "windowAzimuth_${rNum}", "number", title: "Window Azimuth Degree (0-360)", required: true, defaultValue: 180
            } else {
                input "direction_${rNum}", "enum", title: "Window Facing Direction", options: ["North", "South", "East", "West"], required: false
            }
        }
        
        section("Physical Buttons / Remotes") {
            paragraph "<i><b>Button Actions:</b><br>• <b>Push:</b> Toggles blind (Open/Close) & engages Manual Hold.<br>• <b>Hold:</b> Releases Manual Hold & resumes automation.</i>"
            input "roomButton_${rNum}", "capability.pushableButton", title: "Room Button Controller", required: false
            input "buttonNumber_${rNum}", "number", title: "Button Number", defaultValue: 1, required: false
            input "buttonModes_${rNum}", "mode", title: "Allowed Modes for Button", multiple: true, required: false
 
            input "buttonStartTime_${rNum}", "time", title: "Button Active Start Time", required: false
            input "buttonEndTime_${rNum}", "time", title: "Button Active End Time", required: false
        }
        
        section("Sensors & Triggers") {
            input "tempSensor_${rNum}", "capability.temperatureMeasurement", title: "Indoor Temperature Sensor", required: false
            input "luxSensor_${rNum}", "capability.illuminanceMeasurement", title: "Indoor Lux (Light) Sensor", required: false
            input "humiditySensor_${rNum}", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (For Privacy Triggers)", required: false
            input "contactSensor_${rNum}", "capability.contactSensor", title: "Window Open/Close Sensor", required: false
            
            if (enableFortressMode) {
                input "motionSensor_${rNum}", "capability.motionSensor", title: "Motion Sensor (For Unoccupied Fortress)", required: false
                input "unoccupiedTimeout_${rNum}", "number", title: "Unoccupied Timeout (Minutes)", defaultValue: 60
            }
        }
        
        section("Overrides (Hard-Locks)") {
            input "goodNightSwitch_${rNum}", "capability.switch", title: "Nap Time / Good Night Hard-Lock Switch", required: false
            input "releaseHoldSwitch_${rNum}", "capability.switch", title: "Switch to Manually Release Control Hold", required: false
            input "privacyHumidityThreshold_${rNum}", "number", title: "Privacy Humidity Threshold (%)", defaultValue: 65
        }
    }
}

def installed() {
    log.info "Smart Blind Controller Installed."
    initialize()
}

def updated() {
    log.info "Smart Blind Controller Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.targetState = state.targetState ?: [:]
    state.targetReason = state.targetReason ?: [:]
    state.verifiedState = state.verifiedState ?: [:]
    state.manualHold = state.manualHold ?: [:]
    state.manualHoldExpireTime = state.manualHoldExpireTime ?: [:]
    state.lastAutoMoveTime = state.lastAutoMoveTime ?: [:] 
    state.windLock = state.windLock ?: [:]
    state.fortressLocked = state.fortressLocked ?: [:]
    state.historyLog = state.historyLog ?: []
    state.telemetry = state.telemetry ?: [:]
    state.roiMinutes = state.roiMinutes ?: 0
    state.commandStartTime = state.commandStartTime ?: [:]
    state.preCoolingActive = false
    
    if (enableSolarTracking && !state.currentSunPos) {
        state.currentSunPos = calculateSolarPosition()
    }
    
    for (int i = 1; i <= 12; i++) {
        if (!state.telemetry["${i}"] || !state.telemetry["${i}"].today) {
            state.telemetry["${i}"] = [
                today: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                overall: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                history: []
            ]
        }
    }
    
    if (useSunriseSunset) {
        scheduleAstro()
        schedule("0 1 0 * * ?", scheduleAstro) 
        if (maxCloseTime) schedule(maxCloseTime, "executeMaxCloseTime")
    }
    
    if (enableSolarTracking) schedule("0 0/15 * * * ?", "evaluateSolarChanges")
    if (enablePreCooling && preCoolingTime) schedule(preCoolingTime, "evaluatePreCooling")
    
    unschedule("calculateROIStep")
    calculateROIStep()
    schedule("0 0 0 * * ?", "midnightReset") 
    runIn(10, "bootSync", [overwrite: true]) 
    
    subscribe(location, "mode", modeHandler)
    if (mainThermostat) subscribe(mainThermostat, "thermostatOperatingState", hvacHandler)
    
    if (dndSwitch) {
        subscribe(dndSwitch, "switch.on", dndSwitchOnHandler)
        subscribe(dndSwitch, "switch.off", dndSwitchOffHandler)
    }
    
    if (windSensor) subscribe(windSensor, "windSpeed", weatherHandler)
    if (outdoorLuxSensor) subscribe(outdoorLuxSensor, "illuminance", weatherHandler)
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", weatherHandler)
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["tempSensor_${i}"]) subscribe(settings["tempSensor_${i}"], "temperature", tempHandler)
        if (settings["luxSensor_${i}"]) subscribe(settings["luxSensor_${i}"], "illuminance", luxHandler)
        if (settings["humiditySensor_${i}"]) subscribe(settings["humiditySensor_${i}"], "relativeHumidity", humidityHandler)
        if (settings["motionSensor_${i}"]) subscribe(settings["motionSensor_${i}"], "motion", motionHandler)
        if (settings["contactSensor_${i}"]) subscribe(settings["contactSensor_${i}"], "contact", windowContactHandler)
        
        if (settings["roomButton_${i}"]) {
            subscribe(settings["roomButton_${i}"], "pushed", buttonPushedHandler)
            subscribe(settings["roomButton_${i}"], "held", buttonHeldHandler)
        }
        
        if (settings["goodNightSwitch_${i}"]) {
            subscribe(settings["goodNightSwitch_${i}"], "switch.on", hardLockOnHandler)
            subscribe(settings["goodNightSwitch_${i}"], "switch.off", hardLockOffHandler)
        }
        
        if (settings["blindSensor_${i}"]) subscribe(settings["blindSensor_${i}"], "contact", blindSensorHandler)
        if (settings["releaseHoldSwitch_${i}"]) subscribe(settings["releaseHoldSwitch_${i}"], "switch.on", releaseHoldHandler)
    }
}

// --- GLOBAL DND HANDLERS ---
def dndSwitchOnHandler(evt) {
    addToHistory("GLOBAL: Do Not Disturb engaged. Closing all blinds and locking system.")
    operateAllShades("close", true, "Global Do Not Disturb Active")
}

def dndSwitchOffHandler(evt) {
    addToHistory("GLOBAL: Do Not Disturb released. System resuming normal operations.")
    if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
}

// --- SOLAR GEOMETRY MATH ---
def calculateSolarPosition() {
    def lat = (location.latitude ?: 32.5393).toDouble()
    def lon = (location.longitude ?: -86.2078).toDouble()
    
    Calendar cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
    int dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    double hour = cal.get(Calendar.HOUR_OF_DAY) + (cal.get(Calendar.MINUTE) / 60.0)
    
    double declination = 23.45 * Math.sin(Math.toRadians((360.0 / 365.0) * (dayOfYear + 284.0)))
    
    double b = Math.toRadians((360.0 / 365.0) * (dayOfYear - 81.0))
    double eot = 9.87 * Math.sin(2 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b)
    
    double tzOffsetHours = (location.timeZone?.getOffset(cal.getTimeInMillis()) ?: 0) / 3600000.0
    double timeOffset = eot + (4.0 * lon) - (60.0 * tzOffsetHours)
    double tst = hour + (timeOffset / 60.0)
    
    double ha = (tst - 12.0) * 15.0
    
    double latRad = Math.toRadians(lat)
    double decRad = Math.toRadians(declination)
    double haRad = Math.toRadians(ha)
    
    double elevationRad = Math.asin(Math.sin(latRad) * Math.sin(decRad) + Math.cos(latRad) * Math.cos(decRad) * Math.cos(haRad))
    double elevation = Math.toDegrees(elevationRad)
    
    double azimuthRad = Math.acos((Math.sin(decRad) - Math.sin(elevationRad) * Math.sin(latRad)) / (Math.cos(elevationRad) * Math.cos(latRad)))
    double azimuth = Math.toDegrees(azimuthRad)
    
    if (Double.isNaN(azimuth)) azimuth = 180.0
    if (ha > 0) azimuth = 360.0 - azimuth
    
    return [azimuth: azimuth.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP), elevation: elevation.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)]
}

def evaluateSolarChanges() {
    if (isSystemPaused()) return
    state.currentSunPos = calculateSolarPosition()
    runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
}

// --- PREDICTIVE WEATHER PRE-COOLING ---
def evaluatePreCooling() {
    if (isSystemPaused() || !enablePreCooling || !meteorologistDevice) return
    
    def highsAttr = meteorologistDevice.currentValue("highs")
    def todayHigh = extractFirstNumber(highsAttr)
    
    if (todayHigh != null && todayHigh >= (preCoolingThreshold ?: 90)) {
        state.preCoolingActive = true
        state.todayForecastHigh = todayHigh
        addToHistory("PRE-COOLING: Forecast high is ${todayHigh}°. Engaging predictive thermal lockdown early.")
        runIn(2, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
    }
}

def extractFirstNumber(val) {
    if (val == null) return null
    if (val instanceof List) return val[0]?.toBigDecimal()
    def str = val.toString().replaceAll(/[^0-9.,-]/, "")
    def parts = str.split(",")
    if (parts.size() > 0 && parts[0].trim() != "") return parts[0].toBigDecimal()
    return null
}

// --- DYNAMIC ROI ENGINE ---
def calculateROIStep() {
    if (isSystemPaused()) return
    
    def rate = settings["elecRate"] != null ? settings["elecRate"].toBigDecimal() : 0.14
    def staticFactor = settings["hvacEfficiency"] != null ? settings["hvacEfficiency"].toBigDecimal() : 0.25
    def activeFactor = (enableDynamicROI && state.dynamicEfficiency != null) ? state.dynamicEfficiency : staticFactor
    def totalDefenseRooms = 0
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def reason = state.targetReason?."${i}" ?: ""
        if (reason.contains("Summer Mode") || reason.contains("Pre-Cooling") || reason.contains("High Solar") || reason.contains("HVAC Active Cooling")) {
            totalDefenseRooms++
        }
    }
    
    // Record empirical data for self-tuning
    if (enableDynamicROI) {
        def isCooling = mainThermostat?.currentValue("thermostatOperatingState") == "cooling"
        if (isCooling) state.todayACMinutes = (state.todayACMinutes ?: 0) + 5
        if (totalDefenseRooms > 0) state.todayDefenseMinutes = (state.todayDefenseMinutes ?: 0) + 5
        
        def outTemp = outdoorTempSensor?.currentValue("temperature")?.toBigDecimal() ?: 0.0
        if (outTemp > (state.todayHighTemp ?: 0.0)) state.todayHighTemp = outTemp
    }
    
    if (totalDefenseRooms > 0) {
        def earned = (totalDefenseRooms * (activeFactor / 12)) * rate
        state.todaySavings = (state.todaySavings ?: 0.0) + earned
        state.lifetimeSavings = (state.lifetimeSavings ?: 0.0) + earned
    }
    
    runIn(300, "calculateROIStep") // Run every 5 minutes
}

def recalculateDynamicROI() {
    if (!state.roiHistory || state.roiHistory.size() < 2) return
    
    def brackets = [:] 
    state.roiHistory.each { day ->
        def bracket = (day.high / 5).toInteger() * 5
        if (!brackets[bracket]) brackets[bracket] = [defended: [], undefended: []]
        
        if (day.defMins > 120) brackets[bracket].defended << day.acMins
        else if (day.defMins < 60) brackets[bracket].undefended << day.acMins
    }
    
    def totalCalculatedEfficiency = 0.0
    def validBrackets = 0
    
    brackets.each { temp, data ->
        if (data.defended.size() > 0 && data.undefended.size() > 0) {
            def avgDef = data.defended.sum() / data.defended.size()
            def avgUndef = data.undefended.sum() / data.undefended.size()
            def minsSavedPerDay = avgUndef - avgDef
            
            if (minsSavedPerDay > 0) {
                def actualDefMins = state.roiHistory.findAll { it.defMins > 120 && (it.high / 5).toInteger() * 5 == temp }.defMins.sum() / data.defended.size()
                def hoursDefended = actualDefMins / 60.0
                if (hoursDefended > 0) {
                    def kwhSavedPerDay = (minsSavedPerDay / 60.0) * (settings["acPowerKW"]?.toBigDecimal() ?: 3.5)
                    def kwhSavedPerHourOfDefense = kwhSavedPerDay / hoursDefended
                    totalCalculatedEfficiency += kwhSavedPerHourOfDefense
                    validBrackets++
                }
            }
        }
    }
    
    if (validBrackets > 0) {
        def newEff = (totalCalculatedEfficiency / validBrackets).setScale(2, BigDecimal.ROUND_HALF_UP)
        if (newEff > 0.05 && newEff < 2.0) {
            state.dynamicEfficiency = newEff
            addToHistory("ROI ENGINE: Self-tuned HVAC efficiency to ${newEff} kWh/hr based on historical AC runtime.")
        }
    }
}

// --- TELEMETRY HELPERS ---
def logTelemetryEvent(roomNum, eventType) {
    if (!enableTelemetryTracking) return
    if (!state.telemetry) state.telemetry = [:]
    
    if (!state.telemetry["${roomNum}"] || !state.telemetry["${roomNum}"].today) {
        state.telemetry["${roomNum}"] = [
            today: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
            overall: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
            history: []
        ]
    }
    
    state.telemetry["${roomNum}"].today[eventType] = (state.telemetry["${roomNum}"].today[eventType] ?: 0) + 1
    state.telemetry["${roomNum}"].overall[eventType] = (state.telemetry["${roomNum}"].overall[eventType] ?: 0) + 1
}

def get7DayMetric(roomNum, metric) {
    def tData = state.telemetry?."${roomNum}"
    if (!tData || !tData.today) return 0
    def total = (tData.today?."${metric}" ?: 0)
    if (tData.history) {
        tData.history.each { dayMap ->
            total += (dayMap?."${metric}" ?: 0)
        }
    }
    return total
}

def updateAggregateSensor() {
    if (!aggregateSensor) return
    
    def allOpen = true
    def allClosed = true
    def configuredCount = 0
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["blind_${i}"]) {
            configuredCount++
            def vState = state.verifiedState?."${i}" ?: state.targetState?."${i}"
            
            if (vState == "open") {
                allClosed = false
            } else if (vState == "closed") {
                allOpen = false
            } else {
                allClosed = false
                allOpen = false
            }
        }
    }
    
    if (configuredCount == 0) return
    
    def currentState = aggregateSensor.currentValue("contact")
    
    if (allClosed && currentState != "closed") {
        addToHistory("SYSTEM: All blinds are verified closed. Updating Virtual Aggregate Sensor.")
        if (aggregateSensor.hasCommand("close")) aggregateSensor.close()
    } else if (allOpen && currentState != "open") {
        addToHistory("SYSTEM: All blinds are verified open. Updating Virtual Aggregate Sensor.")
        if (aggregateSensor.hasCommand("open")) aggregateSensor.open()
    }
}

// --- MANUAL HOLD TIMEOUT ENGINE ---
def engageManualHold(roomNum) {
    state.manualHold["${roomNum}"] = true
    state.fortressLocked["${roomNum}"] = false
    
    def timeoutMinutes = settings["manualHoldTimeout"] != null ? settings["manualHoldTimeout"].toInteger() : 120
    if (timeoutMinutes > 0) {
        def expireTime = new Date().time + (timeoutMinutes * 60000)
        state.manualHoldExpireTime["${roomNum}"] = expireTime
        runIn(timeoutMinutes * 60, "autoReleaseHold", [data: [roomNum: roomNum], overwrite: false])
    } else {
        state.manualHoldExpireTime["${roomNum}"] = 0
    }
}

def autoReleaseHold(data) {
    def rNum = data.roomNum
    if (state.manualHold["${rNum}"]) {
        def expireTime = state.manualHoldExpireTime["${rNum}"] ?: 0
        def now = new Date().time
        if (now >= (expireTime - 5000)) { // 5 second buffer to allow precise firing
            state.manualHold["${rNum}"] = false
            state.manualHoldExpireTime["${rNum}"] = 0
            addToHistory("${getRoomName(rNum)}: Manual Hold auto-released after timeout. Syncing room state.")
            syncSingleRoom(rNum, true)
        }
    }
}

def midnightReset() {
    state.todaySavings = 0.0
    state.manualHold = [:]
    state.manualHoldExpireTime = [:]
    
    if (enableDynamicROI && state.todayHighTemp && state.todayACMinutes != null) {
        if (!state.roiHistory) state.roiHistory = []
        state.roiHistory.add([high: state.todayHighTemp, acMins: state.todayACMinutes, defMins: state.todayDefenseMinutes ?: 0])
        if (state.roiHistory.size() > 30) state.roiHistory = state.roiHistory.take(30)
        recalculateDynamicROI()
    }
    
    state.todayACMinutes = 0
    state.todayDefenseMinutes = 0
    state.todayHighTemp = 0.0
    state.preCoolingActive = false
    
    if (enableTelemetryTracking && state.telemetry) {
        for (int i = 1; i <= 12; i++) {
            if (state.telemetry["${i}"] && state.telemetry["${i}"].today) {
                if (!state.telemetry["${i}"].history) state.telemetry["${i}"].history = []
                def todayCopy = state.telemetry["${i}"].today.clone()
                state.telemetry["${i}"].history.add(0, todayCopy)
                if (state.telemetry["${i}"].history.size() > 7) state.telemetry["${i}"].history = state.telemetry["${i}"].history.take(7)
                state.telemetry["${i}"].today = [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0]
            }
        }
    }
    
    if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
}

def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        log.info "Dashboard data manually refreshed by user."
    } else if (btn == "btnForceAllOpen") {
        for (int i = 1; i <= (numRooms as Integer); i++) {
            engageManualHold(i)
        }
        addToHistory("GLOBAL: 'Force ALL Open' button pressed. Engaging Manual Hold for the entire house.")
        operateAllShades("open", true, "App Global Force Open (Manual Hold)")
    } else if (btn == "btnForceAllClose") {
        for (int i = 1; i <= (numRooms as Integer); i++) {
            engageManualHold(i)
        }
        addToHistory("GLOBAL: 'Force ALL Close' button pressed. Engaging Manual Hold for the entire house.")
        operateAllShades("close", true, "App Global Force Close (Manual Hold)")
    } else if (btn == "btnReleaseAllHolds") {
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
        state.fortressLocked = [:]
        addToHistory("GLOBAL: 'Release All Holds' button pressed. Wiping locks and auto-syncing house.")
        if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    } else if (btn == "btnForceSync") {
        addToHistory("GLOBAL: 'Force Sync' button pressed. Re-evaluating and syncing all rooms.")
        if (!isSystemPaused()) runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
    } else if (btn == "btnResetTelemetry") {
        for (int i = 1; i <= 12; i++) {
            state.telemetry["${i}"] = [
                today: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                overall: [commands: 0, opens: 0, closes: 0, retries: 0, wiggles: 0, timeouts: 0],
                history: []
            ]
        }
        addToHistory("TELEMETRY: Hardware health data has been manually wiped clean.")
    }
}

def executeSyncStaggered(data) {
    syncSingleRoom(data.roomNum, data.ignoreDebounce ?: false)
}

def bootSync() {
    addToHistory("SYSTEM REBOOT: Hub restarted. Auto-syncing the entire house to recover missed events.")
    if (!isSystemPaused()) {
        for (int i = 1; i <= (numRooms as Integer); i++) {
            def bSensor = settings["blindSensor_${i}"]
            if (bSensor) state.verifiedState["${i}"] = bSensor.currentValue("contact")
            else state.verifiedState["${i}"] = state.targetState["${i}"] ?: "unknown"
        }
        runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: true], overwrite: true])
        runIn(10, "updateAggregateSensor", [overwrite: true])
    }
}

def windowContactHandler(evt) {
    def deviceId = evt.device.id
    def isClosed = evt.value == "closed"
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["contactSensor_${i}"]?.id == deviceId) {
            if (isClosed) {
                def rName = getRoomName(i)
                addToHistory("${rName}: Physical window was closed. Re-evaluating room state to recover any blocked actions.")
                runIn(5, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
            }
        }
    }
}

def getAverageIndoorTemp() {
    def totalTemp = 0.0
    def count = 0
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def tSensor = settings["tempSensor_${i}"]
        if (tSensor) {
            totalTemp += (tSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0)
            count++
        }
    }
    return count > 0 ? (totalTemp / count).toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP) : 70.0
}

def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def tz = location.timeZone ?: TimeZone.getDefault()
    def timestamp = new Date().format("MM/dd HH:mm:ss", tz)
    
    state.historyLog.add(0, "[${timestamp}] ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    log.info "HISTORY: [${timestamp}] ${msg}"
}

def getRoomName(rNum) {
    return settings["roomName_${rNum}"] ?: "Room ${rNum}"
}

def scheduleAstro() {
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise) {
        def sRiseOffset = sunriseOffset != null ? sunriseOffset.toInteger() : 0
        def sunriseTime = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
        if (sunriseTime.after(new Date())) runOnce(sunriseTime, executeSunrise, [overwrite: true])
    }
    if (sunInfo && sunInfo.sunset) {
        def sSetOffset = sunsetOffset != null ? sunsetOffset.toInteger() : 0
        def sunsetTime = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
        if (sunsetTime.after(new Date())) runOnce(sunsetTime, executeSunset, [overwrite: true])
    }
}

def executeMaxCloseTime() {
    if (isSystemPaused()) return
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunsetModes && !sunsetModes.contains(location.mode)) return
    
    def roomsNeedClosing = false
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["blind_${i}"] && state.targetState["${i}"] != "close" && !state.manualHold["${i}"]) {
            roomsNeedClosing = true
            break
        }
    }

    if (roomsNeedClosing) {
        addToHistory("GLOBAL: Maximum Evening Close Time reached. Closing eligible blinds.")
        operateAllShades("close", false, "Max Evening Close Time")
    }
}

def weatherHandler(evt) {
    if (isSystemPaused()) return
    def eventName = evt.name
    
    if (eventName == "windSpeed") {
        def currentWind = evt.value?.toBigDecimal() ?: 0.0
        def threshold = windThreshold != null ? windThreshold.toBigDecimal() : 15.0
        
        if (currentWind >= threshold) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def contact = settings["contactSensor_${i}"]
                if (contact && contact.currentValue("contact") == "open" && !state.windLock["${i}"]) {
                    state.windLock["${i}"] = true
                    def rName = getRoomName(i)
                    addToHistory("STORM SHIELD ACTIVE: High wind (${currentWind} mph). Forced ${rName} blind open.")
                    singleBlindAction(i, "open", true, "Storm Shield (Wind: ${currentWind}mph >= ${threshold}mph)", true) 
                }
            }
        } else {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                if (state.windLock["${i}"]) {
                   state.windLock["${i}"] = false
                   addToHistory("${getRoomName(i)}: Storm Shield lock lifted. Restoring room state.")
                   runIn(i * 2, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
                }
            }
        }
    } else if (eventName == "illuminance") {
        def oldVal = state.lastOutLux ?: 0
        def newVal = evt.value?.toInteger() ?: 0
        if (Math.abs(newVal - oldVal) >= 200) {
            state.lastOutLux = newVal
            runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
        }
    } else if (eventName == "temperature") {
        def oldVal = state.lastOutTemp ?: 0.0
        def newVal = evt.value?.toBigDecimal() ?: 0.0
        if (Math.abs(newVal - oldVal) >= 0.5) {
            state.lastOutTemp = newVal
            runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
        }
    }
}

def hvacHandler(evt) {
    if (isSystemPaused()) return
    runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
}

def luxHandler(evt) {
    if (isSystemPaused()) return
    def oldVal = state.lastIndLux ?: 0
    def newVal = evt.value?.toInteger() ?: 0
    if (Math.abs(newVal - oldVal) >= 200) {
        state.lastIndLux = newVal
        runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    }
}

def tempHandler(evt) {
    if (isSystemPaused()) return
    def oldVal = state.lastIndTemp ?: 0.0
    def newVal = evt.value?.toBigDecimal() ?: 0.0
    if (Math.abs(newVal - oldVal) >= 0.5) {
        state.lastIndTemp = newVal
        runIn(5, "orchestrateHouseSync", [data: [ignoreDebounce: false], overwrite: true])
    }
}

def motionHandler(evt) {
    if (isSystemPaused()) return
    if (!enableFortressMode) return
  
    def deviceId = evt.device.id
    def isActive = evt.value == "active"
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def mSensor = settings["motionSensor_${i}"]
        if (mSensor && mSensor.id == deviceId) {
            def rName = getRoomName(i)
            if (isActive) {
                unschedule("executeFortressClose_${i}")
                if (fortressAutoReopen && state.fortressLocked["${i}"]) {
                    addToHistory("${rName}: Motion detected. Unlocking Unoccupied Fortress mode.")
                    state.fortressLocked["${i}"] = false
                    runIn(2, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: false], overwrite: false])
                }
            } else {
                def timeout = settings["unoccupiedTimeout_${i}"] != null ? settings["unoccupiedTimeout_${i}"].toInteger() : 60
                runIn(timeout * 60, "executeFortressClose", [data: [roomNum: i], overwrite: false])
            }
        }
    }
}

def executeFortressClose(data) {
    if (isSystemPaused()) return
    def rNum = data.roomNum
    addToHistory("${getRoomName(rNum)}: Unoccupied Fortress triggered. Room empty for timeout period. Closing blind.")
    state.fortressLocked["${rNum}"] = true
    singleBlindAction(rNum, "close", false, "Unoccupied Fortress", false)
}

def isButtonAllowed(roomNum) {
    def allowedModes = settings["buttonModes_${roomNum}"]
    if (allowedModes && !allowedModes.contains(location.mode)) {
        addToHistory("${getRoomName(roomNum)}: Physical Button ignored. Hub is not in an allowed mode.")
        return false
    }
    
    def startTime = settings["buttonStartTime_${roomNum}"]
    def endTime = settings["buttonEndTime_${roomNum}"]
    if (startTime && endTime) {
        def between = timeOfDayIsBetween(startTime, endTime, new Date(), location.timeZone)
        if (!between) {
            addToHistory("${getRoomName(roomNum)}: Physical Button ignored. Outside of allowed active time window.")
            return false
        }
    }
    return true
}

def buttonPushedHandler(evt) {
    def deviceId = evt.device.id
    def btnVal = evt.value
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def btn = settings["roomButton_${i}"]
        def targetBtn = settings["buttonNumber_${i}"]?.toString() ?: "1"
        
        if (btn && btn.id == deviceId && btnVal == targetBtn) {
            if (!isButtonAllowed(i)) return
            
            def rName = getRoomName(i)
            def currentState = state.verifiedState["${i}"] ?: state.targetState["${i}"] ?: "closed"
            def nextAction = (currentState == "open" || currentState == "opening") ? "close" : "open"
            
            addToHistory("${rName}: Physical Button PUSHED. Toggling blind to ${nextAction.toUpperCase()} and engaging Manual Hold.")
            engageManualHold(i)
            singleBlindAction(i, nextAction, true, "Physical Button Toggle", true) 
        }
    }
}

def buttonHeldHandler(evt) {
    def deviceId = evt.device.id
    def btnVal = evt.value
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def btn = settings["roomButton_${i}"]
        def targetBtn = settings["buttonNumber_${i}"]?.toString() ?: "1"
        
        if (btn && btn.id == deviceId && btnVal == targetBtn) {
            if (!isButtonAllowed(i)) return
            
            def rName = getRoomName(i)
            if (state.manualHold["${i}"]) {
                addToHistory("${rName}: Physical Button HELD. Releasing Manual Hold and syncing room state.")
                state.manualHold["${i}"] = false
                state.manualHoldExpireTime["${i}"] = 0
                syncSingleRoom(i, true)
            } else {
                addToHistory("${rName}: Physical Button HELD, but no manual hold was active. Ignoring.")
            }
        }
    }
}

def modeHandler(evt) {
    def currentMode = evt.value
    
    if (releaseOnAnyMode || autoReleaseHoldModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Mode changed to ${currentMode}. Auto-releasing all manual holds.")
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
        state.fortressLocked = [:] 
    }
    
    if (isSystemPaused()) return
    
    if (vacationModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Vacation Mode active. Random presence routines engaged.")
        scheduleRandomPresence()
        return
    } else {
        unschedule("triggerRandomBlind")
    }
   
    if (activeModes && !activeModes.contains(currentMode)) return
    
    if (openOnModes?.contains(currentMode)) {
        if (darkArrivalLockout && isDarkOut()) {
            addToHistory("GLOBAL: Mode changed to ${currentMode}, but Dark Arrival Lockout blocked OPEN command.")
        } else if (isExteriorUnsafeToOpen()) {
            // Engine handles block logic silently
        } else {
            addToHistory("GLOBAL: Mode changed to ${currentMode}. Global OPEN routine triggered.")
            operateAllShades("open", false, "Global Open Mode")
        }
    }
    else if (closeOnModes?.contains(currentMode)) {
        addToHistory("GLOBAL: Mode changed to ${currentMode}. Global CLOSE routine triggered.")
        operateAllShades("close", false, "Global Close Mode")
    }
}

def executeSunrise() {
    if (isSystemPaused()) return
    
    if (releaseHoldSunrise) {
        addToHistory("GLOBAL: Sunrise triggered. Auto-releasing all manual holds.")
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
        state.fortressLocked = [:]
    }
    
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunriseModes && !sunriseModes.contains(location.mode)) return
    if (isExteriorUnsafeToOpen()) return 
    
    addToHistory("GLOBAL: Sunrise routine triggered.")
    
    if (circadianWake) {
        state.circadianStep = 10
        runCircadianStep()
    } else {
        operateAllShades("open", false, "Sunrise Routine")
    }
}

def runCircadianStep() {
    if (isSystemPaused()) return
    def step = state.circadianStep ?: 10
    if (step > 100) return
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def blind = settings["blind_${i}"]
        def gnSwitch = settings["goodNightSwitch_${i}"]
        
        if (blind && (!gnSwitch || gnSwitch.currentValue("switch") != "on") && !state.manualHold["${i}"] && !state.windLock["${i}"]) {
            state.targetState["${i}"] = "open"
            state.targetReason["${i}"] = "Circadian Wakeup Cycle"
            if (blind.hasCommand("setPosition")) blind.setPosition(step)
            else if (step == 100) blind.open()
        }
    }
    state.circadianStep = step + 10
    runIn(300, "runCircadianStep", [overwrite: true])
}

def executeSunset() {
    if (isSystemPaused()) return
    
    if (releaseHoldSunset) {
        addToHistory("GLOBAL: Sunset triggered. Auto-releasing all manual holds.")
        state.manualHold = [:]
        state.manualHoldExpireTime = [:]
        state.fortressLocked = [:]
    }
    
    if (activeModes && !activeModes.contains(location.mode)) return
    if (sunsetModes && !sunsetModes.contains(location.mode)) return
    
    addToHistory("GLOBAL: Sunset routine triggered. Closing all eligible blinds.")
    operateAllShades("close", false, "Sunset Routine")
}

def humidityHandler(evt) {
    if (isSystemPaused()) return
    def currentHum = evt.value?.toInteger() ?: 0
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def hSensor = settings["humiditySensor_${i}"]
        def threshold = settings["privacyHumidityThreshold_${i}"] != null ? settings["privacyHumidityThreshold_${i}"].toInteger() : 65
        
        if (hSensor && hSensor.id == deviceId) {
            if (currentHum >= threshold && state.targetState["${i}"] != "close") {
                addToHistory("${getRoomName(i)}: Privacy Override. Humidity spiked to ${currentHum}%. Closing blind.")
                singleBlindAction(i, "close", false, "Privacy (High Hum: ${currentHum}% >= ${threshold}%)", false)
            } else if (currentHum < threshold && state.targetReason["${i}"]?.startsWith("Privacy (High Hum")) {
                addToHistory("${getRoomName(i)}: Humidity cleared. Restoring room to normal environment state.")
                runIn(2, "executeSyncStaggered", [data: [roomNum: i, ignoreDebounce: true], overwrite: false])
            }
        }
    }
}

// --- DYNAMIC PREDICTIVE THERMAL ENGINE & ORCHESTRATOR ---
def orchestrateHouseSync(data = null) {
    def ignoreDebounce = data?.ignoreDebounce ?: false
    if (isSystemPaused()) return
    
    def houseTargets = [:]
    def allNeedToClose = true
    def eligibleCount = 0
    def allReasons = []
    def actionRequiredCount = 0 
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def roomTarget = determineRoomTarget(i)
        houseTargets[i] = roomTarget
        
        if (settings["blind_${i}"]) {
            if (roomTarget.locked) {
                allNeedToClose = false 
            } else {
                eligibleCount++
                if (roomTarget.action != "close") {
                    allNeedToClose = false
                }
                if (roomTarget.reason && !allReasons.contains(roomTarget.reason)) {
                    allReasons << roomTarget.reason
                }
                
                def currentState = settings["blindSensor_${i}"]?.currentValue("contact") ?: state.verifiedState["${i}"]
                def expectedState = (roomTarget.action == "close") ? "closed" : roomTarget.action
                
                if (currentState != expectedState || state.targetState["${i}"] != roomTarget.action) {
                    actionRequiredCount++ 
                } else if (state.targetReason["${i}"] != roomTarget.reason) {
                    state.targetReason["${i}"] = roomTarget.reason
                }
            }
        }
    }
    
    if (actionRequiredCount == 0) return
    
    if (eligibleCount > 0 && allNeedToClose && masterBlind) {
        def combinedReason = allReasons.join(" / ")
        addToHistory("ORCHESTRATOR: Entire house evaluated to CLOSE. Intercepting and routing to Master Blind.")
        operateAllShades("close", false, combinedReason)
    } else {
        def delayMultiplier = 0
        houseTargets.each { rNum, target ->
            if (!target.locked && target.action) {
                def delaySec = delayMultiplier * 2
                runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: rNum, action: target.action, reason: target.reason, ignoreDebounce: ignoreDebounce], overwrite: false])
                delayMultiplier++
            }
        }
    }
}

def determineRoomTarget(roomNum) {
    def target = [action: null, reason: null, locked: false]
    def blind = settings["blind_${roomNum}"]
    if (!blind) {
        target.locked = true
        return target
    }
    
    if (state.manualHold["${roomNum}"]) {
        target.locked = true
        target.reason = "Manual Hold Active"
        return target
    }
    
    if (state.windLock["${roomNum}"]) {
        target.locked = true
        target.reason = "Storm Shield Wind Lock"
        return target
    }

    if (dndSwitch && dndSwitch.currentValue("switch") == "on") {
        target.locked = true
        target.reason = "Global Do Not Disturb Active"
        return target
    }
    
    def gnSwitch = settings["goodNightSwitch_${roomNum}"]
    if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
        target.locked = true
        target.reason = "Nap Time/Hard Lock Active"
        return target
    }
    
    if (state.fortressLocked["${roomNum}"]) {
        target.locked = true
        target.reason = "Unoccupied Fortress Lock"
        return target
    }

    def currentMode = location.mode
    def isNight = isDarkOut() || isPastMaxCloseTime()

    if (isNight && (!sunsetModes || sunsetModes.contains(currentMode))) {
        target.action = "close"
        target.reason = "Nighttime Secure"
        return target
    }
    
    if (closeOnModes?.contains(currentMode)) {
        target.action = "close"
        target.reason = "Global Close Mode"
        return target
    }

    def shouldBeOpen = false
    if (openOnModes?.contains(currentMode) && !isExteriorUnsafeToOpen()) {
        shouldBeOpen = true
    } else if (useSunriseSunset && !isNight && (!sunriseModes || sunriseModes.contains(currentMode)) && !isExteriorUnsafeToOpen()) {
        shouldBeOpen = true
    }

    def envTarget = evaluateEnvironmentTarget(roomNum, isNight, currentMode)
    if (envTarget.action) {
        target.action = envTarget.action
        target.reason = envTarget.reason
        return target
    }

    if (shouldBeOpen) {
        target.action = "open"
        target.reason = "Normal Daytime Condition"
        return target
    }

    if (isExteriorUnsafeToOpen() && state.targetState["${roomNum}"] != "open") {
        target.action = "close"
        target.reason = "Blocked: Exterior conditions unsafe (High Temp or Lux)"
        return target
    }

    target.action = state.targetState["${roomNum}"] ?: "close" 
    target.reason = state.targetReason["${roomNum}"] ?: "Maintaining State"
    return target
}

def evaluateEnvironmentTarget(roomNum, isNight, currentHubMode) {
    def target = [action: null, reason: null]
    
    def isSunFacing = false
    def dirName = "these"
    
    if (enableSolarTracking && state.currentSunPos) {
        def roomAzimuth = settings["windowAzimuth_${roomNum}"] != null ? settings["windowAzimuth_${roomNum}"].toBigDecimal() : null
        dirName = roomAzimuth != null ? "${roomAzimuth}°" : "these"
        
        if (roomAzimuth != null) {
            def sunPos = state.currentSunPos
            if (sunPos.elevation > 0) {
                def diff = Math.abs(sunPos.azimuth - roomAzimuth)
                if (diff > 180) diff = 360 - diff
                if (diff <= 60) {
                    isSunFacing = true
                }
            }
        }
    } else {
        def dir = settings["direction_${roomNum}"]
        dirName = dir ?: "these"
        def tz = location.timeZone ?: TimeZone.getDefault()
        def hour = new Date().format("HH", tz).toInteger()
        def isMorning = (hour < 12)
        
        if (dir == "South") isSunFacing = true
        if (dir == "East" && isMorning) isSunFacing = true
        if (dir == "West" && !isMorning) isSunFacing = true
    }
    
    def tempSensor = settings["tempSensor_${roomNum}"]
    def currentTemp = tempSensor ? (tempSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0) : 70.0
    def outTemp = outdoorTempSensor ? (outdoorTempSensor.currentValue("temperature")?.toBigDecimal() ?: 70.0) : 70.0
    def hvacState = mainThermostat ? (mainThermostat.currentValue("thermostatOperatingState") ?: "idle") : "idle"
    
    def luxHysteresisOffset = settings["luxHysteresis"] != null ? settings["luxHysteresis"].toInteger() : 500
    def tempHysteresisOffset = settings["tempHysteresis"] != null ? settings["tempHysteresis"].toBigDecimal() : 1.0
    def currentRoomReason = state.targetReason["${roomNum}"] ?: ""
    
    def outLux = outdoorLuxSensor ? (outdoorLuxSensor.currentValue("illuminance")?.toInteger() ?: 0) : 0
    def highRadiationLimit = settings["highSolarRadiationThreshold"] != null ? settings["highSolarRadiationThreshold"].toInteger() : 10000
    
    def isHighRadiation = false
    if (outdoorLuxSensor) {
        if (currentRoomReason?.startsWith("High Solar")) {
            isHighRadiation = (outLux >= (highRadiationLimit - luxHysteresisOffset))
        } else {
            isHighRadiation = (outLux >= highRadiationLimit)
        }
    }

    if (!isNight && isHighRadiation && isSunFacing) {
        target.action = "close"
        target.reason = "High Solar Radiation: Closing ${dirName} blinds to block damage [Lux: ${outLux} >= ${highRadiationLimit}]"
        return target
    }

    // Predictive Weather Pre-Cooling Override
    def isPreCoolingAllowed = enablePreCooling && state.preCoolingActive && summerEnergyMode && !isNight
    if (isPreCoolingAllowed && isSunFacing) {
        target.action = "close"
        target.reason = "Predictive Pre-Cooling: Forecast high is ${state.todayForecastHigh ?: '--'}°, locking down ${dirName} blinds early."
        return target
    }

    def coolingDefense = settings["activeCoolingDefense"] != null ? settings["activeCoolingDefense"] : true
    if (coolingDefense && hvacState == "cooling" && !isNight && isSunFacing) {
        target.action = "close"
        target.reason = "HVAC Active Cooling: Closing ${dirName} blinds to assist AC [State: ${hvacState.capitalize()}]"
        return target
    }

    def maxWinterOut = settings["winterMaxOutdoorTemp"] != null ? settings["winterMaxOutdoorTemp"].toBigDecimal() : 75.0
    def isActuallyWinter = outdoorTempSensor ? (outTemp <= maxWinterOut) : true
    def winterThresh = settings["winterTempThreshold"] != null ? settings["winterTempThreshold"].toBigDecimal() : 68.0
    def indoorWinterTrigger = currentTemp <= winterThresh
    def winterOutThresh = settings["winterOutdoorTempThreshold"] != null ? settings["winterOutdoorTempThreshold"].toBigDecimal() : null
    
    def outdoorWinterTrigger = false
    if (winterOutThresh != null) {
        if (currentRoomReason?.startsWith("Winter Mode")) {
            outdoorWinterTrigger = (outTemp <= (winterOutThresh + tempHysteresisOffset))
        } else {
            outdoorWinterTrigger = (outTemp <= winterOutThresh)
        }
    }

    if (winterHeatingMode && !isNight && isActuallyWinter) {
        if (!winterAllowedModes || winterAllowedModes.contains(currentHubMode)) {
            if (indoorWinterTrigger || outdoorWinterTrigger) {
                if (isSunFacing && (hvacState == "heating" || hvacState == "idle")) {
                    target.action = "open"
                    def wReason = indoorWinterTrigger ? "In: ${currentTemp}° <= ${winterThresh}°" : "Out: ${outTemp}° <= ${winterOutThresh}°"
                    target.reason = "Winter Mode: Opening ${dirName} facing blinds to harvest active solar heat [${wReason}]"
                    return target
                }
            }
        }
    }

    def summerThresh = settings["summerTempThreshold"] != null ? settings["summerTempThreshold"].toBigDecimal() : 75.0
    def indoorSummerTrigger = currentTemp >= summerThresh
    def summerOutThresh = settings["summerOutdoorTempThreshold"] != null ? settings["summerOutdoorTempThreshold"].toBigDecimal() : null
    
    def outdoorSummerTrigger = false
    if (summerOutThresh != null) {
        if (currentRoomReason?.startsWith("Summer Mode")) {
            outdoorSummerTrigger = (outTemp >= (summerOutThresh - tempHysteresisOffset))
        } else {
            outdoorSummerTrigger = (outTemp >= summerOutThresh)
        }
    }

    if (summerEnergyMode && !isNight) {
        if (!summerAllowedModes || summerAllowedModes.contains(currentHubMode)) {
            if (indoorSummerTrigger || outdoorSummerTrigger) {
                def avgTemp = getAverageIndoorTemp()
                def houseIsHot = avgTemp >= summerThresh
                def sReason = indoorSummerTrigger ? "In: ${currentTemp}° >= ${summerThresh}°" : "Out: ${outTemp}° >= ${summerOutThresh}°"

                if (hvacState != "heating") {
                    if (houseIsHot) {
                        target.action = "close"
                        target.reason = "Summer Mode: House is hot, closing ${dirName} blinds to defend against heat [AvgIn: ${avgTemp}° >= ${summerThresh}°]"
                        return target
                    } else if (isSunFacing) {
                        target.action = "close"
                        target.reason = "Summer Mode: Closing ${dirName} blinds because direct sun is heating this side [${sReason}]"
                        return target
                    }
                }
            }
        }
    } 
    
    return target
}

def syncSingleRoom(roomNum, ignoreDebounce = false) {
    def target = determineRoomTarget(roomNum)
    if (!target.locked && target.action) {
        singleBlindAction(roomNum, target.action, false, target.reason, ignoreDebounce)
    }
}

// --- HARD-LOCK OVERRIDE HANDLERS ---
def hardLockOnHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            addToHistory("${getRoomName(i)}: NAP TIME / HARD-LOCK ENGAGED. Room forced closed.")
            singleBlindAction(i, "close", true, "Nap Time/Hard Lock Active", true) 
        }
    }
}

def hardLockOffHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            addToHistory("${getRoomName(i)}: Hard-Lock released. Syncing room state.")
            syncSingleRoom(i, true) 
        }
    }
}

// --- UTILITY & VERIFICATION ---
def isDarkOut() {
    if (!useSunriseSunset) return false
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset || !sunInfo.sunrise) return false
    def now = new Date()
    return (now >= sunInfo.sunset || now <= sunInfo.sunrise)
}

def isPastMaxCloseTime() {
    if (!maxCloseTime) return false
    def tz = location.timeZone ?: TimeZone.getDefault()
    def maxTime = timeToday(maxCloseTime, tz)
    def now = new Date()
    return (now >= maxTime)
}

def isExteriorUnsafeToOpen() {
    def outTemp = outdoorTempSensor ? (outdoorTempSensor.currentValue("temperature")?.toBigDecimal() ?: 0.0) : 0.0
    def outLux = outdoorLuxSensor ? (outdoorLuxSensor.currentValue("illuminance")?.toInteger() ?: 0) : 0
    def limitTemp = settings["outdoorHighTempThreshold"] != null ? settings["outdoorHighTempThreshold"].toBigDecimal() : 92.0
    def limitLux = settings["highSolarRadiationThreshold"] != null ? settings["highSolarRadiationThreshold"].toInteger() : 10000
    
    if (outdoorTempSensor && (outTemp >= limitTemp)) {
        return true
    }
    if (outdoorLuxSensor && (outLux >= limitLux)) {
        return true
    }
    return false
}

def isWithinSunsetDeadband() {
    if (!useSunriseSunset || !sunsetDeadband) return false
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset) return false
    def now = new Date()
    def deadbandStart = new Date(sunInfo.sunset.time - (sunsetDeadband.toInteger() * 60000))
    return (now >= deadbandStart && now < sunInfo.sunset)
}

def singleBlindAction(roomNum, action, bypassLock = false, reason = "Automated Sync", ignoreDebounce = false) {
    def rName = getRoomName(roomNum)
    def blind = settings["blind_${roomNum}"]
    
    if (!blind) {
        log.warn "${rName}: ABORTED. No Blind Device is selected in the app settings!"
        return
    }
    
    if (state.windLock["${roomNum}"] && action == "close") {
        log.warn "${rName}: ABORTED CLOSE. Storm Shield wind lock is active."
        return
    }
    
    if (!bypassLock && settings["goodNightSwitch_${roomNum}"]?.currentValue("switch") == "on") {
        return
    }
    
    if (action == "open" && !bypassLock && isWithinSunsetDeadband()) {
        return
    }
    
    if (action == "close" && settings["contactSensor_${roomNum}"]?.currentValue("contact") == "open") {
        if (state.targetState["${roomNum}"] != action) addToHistory("${rName}: Aborted CLOSE command. Physical window is OPEN.")
        return
    }

    def currentState = settings["blindSensor_${roomNum}"]?.currentValue("contact") ?: state.verifiedState["${roomNum}"]
    def mappedActionState = (action == "close") ? "closed" : action
    
    if (currentState == mappedActionState && state.targetState["${roomNum}"] == action) {
        if (state.targetReason["${roomNum}"] != reason) {
            state.targetReason["${roomNum}"] = reason
            runIn(2, "updateAggregateSensor", [overwrite: true])
        }
        return 
    }

    state.targetReason["${roomNum}"] = reason

    if (!bypassLock && !ignoreDebounce) {
        def now = new Date().time
        def lastMove = state.lastAutoMoveTime["${roomNum}"] ?: 0
        def debounceMillis = (environmentalDebounce != null ? environmentalDebounce.toInteger() : 15) * 60000
        
        if ((now - lastMove) < debounceMillis) {
            def timeLeft = ((debounceMillis - (now - lastMove)) / 1000).toInteger()
            
            if (state.targetState["${roomNum}"] != action) {
                addToHistory("${rName}: ${action.toUpperCase()} delayed. Anti-Yo-Yo cooldown active (${(timeLeft/60).toInteger()}m left).")
            }
            
            runIn(timeLeft + 2, "executeSyncStaggered", [data: [roomNum: roomNum, ignoreDebounce: false], overwrite: true])
            return
        }
    }
    
    if (state.targetState["${roomNum}"] != action) {
        state.commandStartTime["${roomNum}"] = new Date().time
    }
    
    state.targetState["${roomNum}"] = action
    state.lastAutoMoveTime["${roomNum}"] = new Date().time
    
    addToHistory("${rName}: Executing ${action.toUpperCase()} command. Reason: ${reason}")
    logTelemetryEvent(roomNum, "commands")
    
    if (action == "open") blind.open() else blind.close()
    
    state.retryCount = 0
    runIn(90, "verifyAndRetry", [overwrite: true]) 
}

// --- BLIND SENSOR DEBOUNCE HANDLERS ---
def blindSensorHandler(evt) {
    def deviceId = evt.device.id
    def now = new Date().time
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["blindSensor_${i}"]?.id == deviceId) {
            
            // --- Sensor Health & Auto-Bypass Logic ---
            if (!state.sensorFlapCount) state.sensorFlapCount = [:]
            if (!state.lastSensorFlapTime) state.lastSensorFlapTime = [:]
            
            def lastFlap = state.lastSensorFlapTime["${i}"] ?: 0
            
            // If the sensor changes state again within 2 minutes, count it as a flap
            if ((now - lastFlap) < 120000) { 
                state.sensorFlapCount["${i}"] = (state.sensorFlapCount["${i}"] ?: 0) + 1
            } else {
                // It has been stable for over 2 minutes, reset the health counter
                state.sensorFlapCount["${i}"] = 1
            }
            state.lastSensorFlapTime["${i}"] = now
            
            // If it flaps 5 times quickly, it's environmental noise or a hardware failure
            if (state.sensorFlapCount["${i}"] >= 5) {
                if (state.sensorFlapCount["${i}"] == 5) {
                    def rName = getRoomName(i)
                    addToHistory("SENSOR HEALTH ALERT: ${rName} blind sensor is rapidly flapping. Temporarily bypassing manual hold detection to prevent system lockup.")
                }
                // Abort the evaluation. The app will ignore the sensor and rely on target state.
                return
            }
            
            // --- Configurable Stabilization Timer ---
            def debounceSecs = settings["sensorDebounce_${i}"] != null ? settings["sensorDebounce_${i}"].toInteger() : 15
            runIn(debounceSecs, "evalSensor${i}", [overwrite: true])
        }
    }
}

// Fixed routing functions to handle up to 12 rooms with dynamic overwrites cleanly
def evalSensor1() { evaluateSensorEvent(1) }
def evalSensor2() { evaluateSensorEvent(2) }
def evalSensor3() { evaluateSensorEvent(3) }
def evalSensor4() { evaluateSensorEvent(4) }
def evalSensor5() { evaluateSensorEvent(5) }
def evalSensor6() { evaluateSensorEvent(6) }
def evalSensor7() { evaluateSensorEvent(7) }
def evalSensor8() { evaluateSensorEvent(8) }
def evalSensor9() { evaluateSensorEvent(9) }
def evalSensor10() { evaluateSensorEvent(10) }
def evalSensor11() { evaluateSensorEvent(11) }
def evalSensor12() { evaluateSensorEvent(12) }

def evaluateSensorEvent(i) {
    def sensor = settings["blindSensor_${i}"]
    if (!sensor) return
    
    def actualState = sensor.currentValue("contact")
    def verified = state.verifiedState["${i}"]
    def target = state.targetState["${i}"]
    def rName = getRoomName(i)
    def expectedState = (target == "close") ? "closed" : target

    def now = new Date().time
    def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
    
    if ((now - lastMove) < 90000) {
        if (actualState == expectedState) {
            if (state.verifiedState["${i}"] != actualState) {
                state.verifiedState["${i}"] = actualState
                if (actualState == "open") logTelemetryEvent(i, "opens")
                if (actualState == "closed") logTelemetryEvent(i, "closes")
                runIn(2, "updateAggregateSensor", [overwrite: true])
            }
        }
        return 
    }
    
    if (actualState == expectedState) {
        if (state.verifiedState["${i}"] != actualState) {
            state.verifiedState["${i}"] = actualState
            if (actualState == "open") logTelemetryEvent(i, "opens")
            if (actualState == "closed") logTelemetryEvent(i, "closes")
            runIn(2, "updateAggregateSensor", [overwrite: true])
        }
        return 
    }
    
    if (verified == "closed" && actualState == "open") {
        logTelemetryEvent(i, "opens")
        addToHistory("${rName}: Blind was manually opened. Activating Manual Hold.")
        engageManualHold(i)
        state.targetState["${i}"] = "open"
        state.targetReason["${i}"] = "Manual Physical Override"
        state.verifiedState["${i}"] = actualState 
        runIn(2, "updateAggregateSensor", [overwrite: true])
    } else if (verified == "open" && actualState == "closed") {
        logTelemetryEvent(i, "closes")
        addToHistory("${rName}: Blind was manually closed. Activating Manual Hold.")
        engageManualHold(i)
        state.targetState["${i}"] = "close"
        state.targetReason["${i}"] = "Manual Physical Override"
        state.verifiedState["${i}"] = actualState 
        runIn(2, "updateAggregateSensor", [overwrite: true])
    }
}

def releaseHoldHandler(evt) {
    def deviceId = evt.device.id
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["releaseHoldSwitch_${i}"]?.id == deviceId) {
            state.manualHold["${i}"] = false
            state.manualHoldExpireTime["${i}"] = 0
            addToHistory("${getRoomName(i)}: Manual Hold released by user switch. Syncing room state.")
            syncSingleRoom(i, true)
        }
    }
}

def scheduleRandomPresence() {
    def randomMinutes = new Random().nextInt(45) + 15
    runIn(randomMinutes * 60, "triggerRandomBlind", [overwrite: false])
}

def triggerRandomBlind() {
    if (isSystemPaused()) return
    if (!vacationModes?.contains(location.mode)) return
    
    def rNum = new Random().nextInt(numRooms as Integer) + 1
    def action = new Random().nextBoolean() ? "open" : "close"
    
    if (settings["goodNightSwitch_${rNum}"]?.currentValue("switch") == "on" || state.windLock["${rNum}"]) {
        scheduleRandomPresence() 
        return
    }
    
    addToHistory("VACATION PRESENCE: Randomly executing ${action.toUpperCase()} on ${getRoomName(rNum)}.")
    singleBlindAction(rNum, action, false, "Vacation Mode", true)
    scheduleRandomPresence()
}

def executeStaggeredCommand(data) {
    singleBlindAction(data.roomNum, data.action, false, data.reason ?: "Automated Sync", data.ignoreDebounce ?: false)
}

def operateAllShades(action, force = false, reason = "Global Command") {
    if (action == "open" && !force && isWithinSunsetDeadband()) {
        addToHistory("GLOBAL: Aborted global OPEN command (within sunset deadband).")
        return
    }

    def allEligible = true
    def roomsToCommand = []
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def blind = settings["blind_${i}"]
        def windowContact = settings["contactSensor_${i}"]
        def gnSwitch = settings["goodNightSwitch_${i}"]
        
        if (!blind) continue
        if (!force && state.manualHold["${i}"]) { allEligible = false; continue }
        if (state.windLock["${i}"]) { allEligible = false; continue } 
        if (gnSwitch && gnSwitch.currentValue("switch") == "on") { allEligible = false; continue }
        if (action == "close" && windowContact && windowContact.currentValue("contact") == "open") { allEligible = false; continue }
        
        if (state.targetState["${i}"] != action) {
            state.commandStartTime["${i}"] = new Date().time
        }
        
        state.targetState["${i}"] = action
        state.targetReason["${i}"] = reason
        roomsToCommand << i
    }
    
    if (roomsToCommand.size() == 0) return
    
    if (allEligible && masterBlind) {
        addToHistory("GLOBAL: Master Blind device triggered. Executing ${action.toUpperCase()} on all rooms.")
        if (action == "open") masterBlind.open() else masterBlind.close()
        
        state.masterRetryCount = 0
        runIn(90, "verifyMasterAndRetry", [data: [action: action, reason: reason], overwrite: true])
    } else {
        def delayMultiplier = 0
        
        roomsToCommand.each { rNum ->
            def delaySec = delayMultiplier * 2
            runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: rNum, action: action, reason: reason, ignoreDebounce: true], overwrite: false])
            delayMultiplier++
        }
        state.retryCount = 0
        runIn((roomsToCommand.size() * 2) + 90, "verifyAndRetry", [overwrite: true])
    }
}

// --- MASTER VERIFY & RETRY LOOP ---
def verifyMasterAndRetry(data) {
    if (isSystemPaused()) return
    
    def action = data.action
    def reason = data.reason
    def needsMasterRetry = false
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def target = state.targetState["${i}"]
        if (!target) continue
        
        def blindSensor = settings["blindSensor_${i}"]
        if (blindSensor) {
            def currentState = blindSensor.currentValue("contact")
            def expectedState = (target == "close") ? "closed" : target
            
            if (currentState != expectedState) {
                needsMasterRetry = true
                break 
            }
        }
    }
    
    if (needsMasterRetry) {
        state.masterRetryCount = (state.masterRetryCount ?: 0) + 1
        
        if (state.masterRetryCount <= 2) {
            addToHistory("GLOBAL: Some blinds failed to sync. Retrying Master ${action.toUpperCase()} command (${state.masterRetryCount + 1}/3).")
            if (action == "open") masterBlind.open() else masterBlind.close()
            runIn(90, "verifyMasterAndRetry", [data: data, overwrite: true])
        } else {
            addToHistory("GLOBAL: Master Blind failed after 3 attempts. Falling back to individual shade sync.")
            
            def delayMultiplier = 0
            for (int i = 1; i <= (numRooms as Integer); i++) {
                def target = state.targetState["${i}"]
                if (!target || state.manualHold["${i}"]) continue
                
                def delaySec = delayMultiplier * 2
                runIn(delaySec, "executeStaggeredCommand", [data: [roomNum: i, action: target, reason: reason + " (Master Fallback)", ignoreDebounce: true], overwrite: false])
                delayMultiplier++
            }
            
            runIn((delayMultiplier * 2) + 90, "verifyAndRetry", [overwrite: true])
        }
    } else {
        addToHistory("GLOBAL: Master Blind sync verified successfully.")
        runIn(2, "updateAggregateSensor", [overwrite: true])
    }
}

// --- VERIFY & PERSISTENT RETRY LOOP ---
def verifyAndRetry() {
    if (isSystemPaused()) return
    def needsRetry = false
    
    def timeoutMinutes = settings["retryTimeoutMinutes"] != null ? settings["retryTimeoutMinutes"].toInteger() : 15
    def timeoutMillis = timeoutMinutes * 60000
    def now = new Date().time
    
    def delayMultiplier = 0
    for (int i = 1; i <= (numRooms as Integer); i++) {
        def target = state.targetState["${i}"]
        if (!target) continue
        
        def blindSensor = settings["blindSensor_${i}"]
        if (blindSensor) {
            def currentState = blindSensor.currentValue("contact")
            def expectedState = (target == "close") ? "closed" : target
            
            if (currentState != expectedState) {
                
                def startTime = state.commandStartTime["${i}"] ?: now
        
                if ((now - startTime) >= timeoutMillis) {
                    if (state.targetReason["${i}"] != "TIMEOUT FAILED") {
                        def rName = getRoomName(i)
                        addToHistory("TIMEOUT ERROR: ${rName} failed to reach ${target.toUpperCase()} after ${timeoutMinutes} minutes. Abandoning retries.")
                        state.targetReason["${i}"] = "TIMEOUT FAILED"
                        logTelemetryEvent(i, "timeouts")
                    }
                    continue 
                }
  
                needsRetry = true 
                
                def lastMove = state.lastAutoMoveTime["${i}"] ?: 0
        
                if ((now - lastMove) >= 90000) {
                    def delaySec = delayMultiplier * 3 
                    def tReason = state.targetReason["${i}"] ?: "Persistent Retry Sync"
            
                    logTelemetryEvent(i, "retries")
                    
                    if (target == "open") {
                        runIn(delaySec, "executeWiggleOpen", [data: [roomNum: i, reason: tReason], overwrite: false])
                    } else if (target == "close") {
                        runIn(delaySec, "executeWiggleClose", [data: [roomNum: i, reason: tReason], overwrite: false])
                    }
                    delayMultiplier++
                }
            } else {
                state.verifiedState["${i}"] = currentState
            }
        } else {
            state.verifiedState["${i}"] = target
        }
    }
    
    if (needsRetry) {
        runIn(90, "verifyAndRetry", [overwrite: true]) 
    } else {
        runIn(2, "updateAggregateSensor", [overwrite: true])
    }
}

// --- WIGGLE RECOVERY MANEUVERS ---
def executeWiggleOpen(data) {
    def rNum = data.roomNum
    def blind = settings["blind_${rNum}"]
    if (!blind) return
    
    logTelemetryEvent(rNum, "wiggles")
    addToHistory("${getRoomName(rNum)}: Wiggle maneuver engaged. Forcing CLOSE, then re-issuing OPEN. Reason: ${data.reason}")
    
    if (blind.hasCommand("close")) blind.close()
    
    state.lastAutoMoveTime["${rNum}"] = new Date().time
    runIn(5, "finalizeWiggleOpen", [data: [roomNum: rNum], overwrite: false])
}

def finalizeWiggleOpen(data) {
    def blind = settings["blind_${data.roomNum}"]
    if (blind && blind.hasCommand("open")) blind.open()
}

def executeWiggleClose(data) {
    def rNum = data.roomNum
    def blind = settings["blind_${rNum}"]
    if (!blind) return
    
    logTelemetryEvent(rNum, "wiggles")
    addToHistory("${getRoomName(rNum)}: Wiggle maneuver engaged. Forcing OPEN, then re-issuing CLOSE. Reason: ${data.reason}")
    
    if (blind.hasCommand("open")) blind.open()
    
    state.lastAutoMoveTime["${rNum}"] = new Date().time
    runIn(5, "finalizeWiggleClose", [data: [roomNum: rNum], overwrite: false])
}

def finalizeWiggleClose(data) {
    def blind = settings["blind_${data.roomNum}"]
    if (blind && blind.hasCommand("close")) blind.close()
}
