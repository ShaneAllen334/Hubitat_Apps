/**
 * Advanced Mold & Dehumidification Manager
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Mold & Dehumidification Manager",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "BMS-grade mold engine featuring Free Dehumidification, Time-of-Use Financials, Window Interlocks, and Advanced Tank Full Diagnostics.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "zoneConfigPage")
    page(name: "globalLogicPage")
    page(name: "alertsPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Mold & Dehumidification Manager</b>", install: true, uninstall: true) {
        
        section("<b>Live Performance & Risk Dashboard</b>") {
            input "refreshDashboardBtn", "button", title: "🔄 Refresh Live Data"

            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 12px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Zone & Status</th><th style='padding: 8px;'>Environment / Goal</th><th style='padding: 8px;'>ASHRAE Risk</th><th style='padding: 8px;'>Active Run</th><th style='padding: 8px;'>Financials</th></tr>"
            
            def hasZones = false
            for (int i = 1; i <= 8; i++) {
                if (settings["z${i}Name"]) {
                    hasZones = true
                    def zData = calculateZoneState(i)
                    def riskColor = getRiskColor(zData.riskScore)
                    def spend = state["z${i}TotalSpend"] ?: 0.0
                    def save = state["z${i}TotalSave"] ?: 0.0
                    
                    def filterDisplay = zData.filterLife <= 5 ? "<span style='color:red; font-weight:bold;'>Filter: ${zData.filterLife}%</span>" : "<span style='color:#555;'>Filter: ${zData.filterLife}%</span>"
                    def ashraeFlag = zData.riskScore > 50 ? "<div style='margin-top:4px;'><span style='background:#c0392b; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>⚠️ ASHRAE WARNING</span></div>" : ""
                    
                    // High-Visibility Badges
                    def pwrBadge = ""
                    if (zData.isLeaking) { pwrBadge = "<span style='background:#c0392b; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>EMERGENCY (WET)</span>" }
                    else if (zData.windowOpen) { pwrBadge = "<span style='background:#34495e; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>PAUSED (WINDOW)</span>" }
                    else if (zData.tankFull) { pwrBadge = "<span style='background:#e74c3c; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>TANK FULL</span>" }
                    else if (zData.freeDehumOn) { pwrBadge = "<span style='background:#1abc9c; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (FREE DEHUM)</span>" }
                    else if (zData.hardwareOn) { pwrBadge = "<span style='background:#27ae60; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>ON (ACTIVE)</span>" }
                    else { pwrBadge = "<span style='background:#95a5a6; color:white; padding:2px 5px; border-radius:3px; font-weight:bold; font-size:10px;'>OFF (STANDBY)</span>" }
                    
                    def envDisplay = "<b>${zData.currHum}% RH</b>"
                    if (zData.currTemp) {
                        envDisplay += " / ${zData.currTemp}°F"
                        if (zData.dewPoint != null) {
                            def spreadColor = zData.spread <= 3.0 ? "#cc0000" : (zData.spread <= 6.0 ? "#e67e22" : "#27ae60")
                            envDisplay += "<br><small style='color:#555;'>Dew Pt: ${zData.dewPoint}°F<br>Spread: <span style='color:${spreadColor}; font-weight:bold;'>+${zData.spread}°F</span> away</small>"
                        }
                    }

                    // Render Tank Reset Button if Full
                    def actionHtml = ""
                    if (zData.tankFull) {
                        actionHtml = "<br><a href='#' onclick='return false;' style='color:#e74c3c; font-weight:bold;'>Need Reset (API)</a>"
                        input "btnResetTank_${i}", "button", title: "💧 Clear Zone ${i} Tank"
                    }
                    
                    statusText += "<tr style='border-bottom: 1px solid #eee;'>"
                    statusText += "<td style='padding: 8px;'><b>${settings["z${i}Name"]}</b><br><div style='margin-top:4px; margin-bottom:4px;'>${pwrBadge}</div><small style='color:#555;'>${zData.status}</small>${ashraeFlag}</td>"
                    statusText += "<td style='padding: 8px;'>${envDisplay}<br><small style='color:#666;'>Target: ${zData.target}%</small></td>"
                    statusText += "<td style='padding: 8px; color:${riskColor}; font-weight:bold;'>${zData.riskLevel}<br><small>${zData.riskScore}%</small></td>"
                    statusText += "<td style='padding: 8px;'>${zData.duration}<br><small>${filterDisplay}</small>${actionHtml}</td>"
                    statusText += "<td style='padding: 8px;'><span style='color:red;'>Spend: &#36;${String.format("%.2f", spend)}</span><br><span style='color:green;'>Saved: &#36;${String.format("%.2f", save)}</span></td>"
                    statusText += "</tr>"
                }
            }
            if (!hasZones) statusText += "<tr><td colspan='5' style='padding:20px; text-align:center;'>No zones configured.</td></tr>"
            statusText += "</table>"
            paragraph statusText
            
            // Global Mode Indicators
            def globalAvg = getHouseAverageHum()
            def outTemp = outdoorTempSensor?.currentValue("temperature")
            def outHum = outdoorHumSensor?.currentValue("humidity")
            def outDp = calculateDewPoint(outTemp, outHum)
            def outDpDisplay = outDp != null ? "${outDp}°F" : "N/A"
            
            def isPeak = isTOUPeak()
            def touMode = isPeak ? "<span style='color:#e74c3c;'><b>PEAK TIER</b></span>" : "<span style='color:#27ae60;'><b>OFF-PEAK</b></span>"
            def curRate = isPeak ? (settings.peakRate ?: 0.20) : (settings.offPeakRate ?: 0.10)
            
            paragraph "<div style='font-size: 12px; background: #f0f4f7; padding: 10px; border-radius: 5px; border: 1px solid #d1d9e1; margin-bottom: 15px;'>" +
                      "<b>Outdoor Dew Point:</b> ${outDpDisplay} | <b>House Avg Hum:</b> ${globalAvg}%<br><b>Utility Status:</b> ${touMode} (&#36;${curRate}/kWh)</div>"
                      
            // Event History
            paragraph "<b>Event History (Last 25 Events)</b>"
            def hist = state.eventLog ?: []
            paragraph "<div style='font-size: 11px; color: #444; max-height: 150px; overflow-y: auto; background: #f9f9f9; padding: 8px; border: 1px solid #ccc; border-radius: 4px;'>${hist.join("<br>")}</div>"
        }

        section("<b>Configuration</b>") {
            href "zoneConfigPage", title: "1. Zone Detail Configuration", description: "Sensors, Setpoints, Windows, Fans, and Tank Logic."
            href "globalLogicPage", title: "2. Global Advanced Logic", description: "Time-of-Use Financials, Averaging System, Winter Shield."
            href "alertsPage", title: "3. Maintenance & Notifications", description: "Granular Time-Based Alerts, Filter tracking, Tank notifications."
            input "appEnableSwitch", "capability.switch", title: "Master Enable Switch (Turn off to halt logic)", required: false
            input "txtEnable", "bool", title: "Enable verbose logging", defaultValue: true
        }
    }
}

def zoneConfigPage() {
    dynamicPage(name: "zoneConfigPage", title: "Zone Configuration") {
        for (int i = 1; i <= 8; i++) {
            section("Zone ${i}: ${settings["z${i}Name"] ?: "New Zone"}", hideable: true, hidden: (i > 1 && !settings["z${i}Name"])) {
                input "z${i}Name", "text", title: "Zone Name", submitOnChange: true
                if (settings["z${i}Name"]) {
                    
                    paragraph "<b>Primary Hardware</b>"
                    input "z${i}Hum", "capability.relativeHumidityMeasurement", title: "Humidity Sensor", required: true
                    input "z${i}Temp", "capability.temperatureMeasurement", title: "Temperature Sensor (For Dew Point & Cooling)", required: true
                    input "z${i}Dehum", "capability.switch", title: "Dehumidifier Smart Plug", required: true
                    input "z${i}Thermostat", "capability.thermostat", title: "HVAC Thermostat (To detect active cooling)", required: false
                    
                    paragraph "<b>Free Dehumidification & Interlocks</b>"
                    input "z${i}ExhaustFan", "capability.switch", title: "Exhaust Fan / ERV (Option 1: Free Dehumidification)", required: false, description: "If outside Dew Point is lower than inside, this turns on instead of the compressor."
                    input "z${i}Window", "capability.contactSensor", title: "Window/Door Interlock (Option 6)", required: false, description: "Pauses dehumidifier if window is open."
                    
                    paragraph "<b>Tank Full Diagnostics</b>"
                    input "z${i}TankMethod", "enum", title: "Detection Method", options: ["None", "Power Meter", "Humidity Stall"], submitOnChange: true
                    if (settings["z${i}TankMethod"] == "Power Meter") {
                        input "z${i}Power", "capability.powerMeter", title: "↳ Smart Plug with Power Meter", required: true
                        input "z${i}ActiveWatts", "number", title: "↳ Active Compressor Watts (Threshold)", defaultValue: 100
                    } else if (settings["z${i}TankMethod"] == "Humidity Stall") {
                        input "z${i}StallMins", "number", title: "↳ Stall Time Limit (Mins)", defaultValue: 90
                        input "z${i}StallDrop", "number", title: "↳ Required Drop (%)", defaultValue: 5
                    }

                    paragraph "<b>Setpoints & Protections</b>"
                    input "z${i}Watts", "number", title: "Dehumidifier Watts (For ROI tracking)", defaultValue: 500
                    input "z${i}Modes", "mode", title: "Allowed Modes (Leave blank for all)", multiple: true, required: false
                    
                    input "z${i}SavePoint", "number", title: "Savings Setpoint (Peak Time / Eco) (%)", defaultValue: 60
                    input "z${i}SaveDB", "number", title: "↳ Savings Deadband (%)", defaultValue: 5
                    input "z${i}ComfortPoint", "number", title: "Comfort Setpoint (Off-Peak) (%)", defaultValue: 45
                    input "z${i}ComfortDB", "number", title: "↳ Comfort Deadband (%)", defaultValue: 3
                }
            }
        }
    }
}

def globalLogicPage() {
    dynamicPage(name: "globalLogicPage", title: "Global Advanced Logic") {
        
        section("<b>Time-of-Use Financial Manager (Option 4)</b>") {
            paragraph "<small>Dynamically shifts targets and tracking costs based on peak utility hours.</small>"
            input "enableTOU", "bool", title: "Enable Time-of-Use logic", defaultValue: false, submitOnChange: true
            if (enableTOU) {
                input "peakStartTime", "time", title: "Peak Rate Start Time", required: true
                input "peakEndTime", "time", title: "Peak Rate End Time", required: true
                input "peakRate", "decimal", title: "Peak Rate (per kWh)", defaultValue: 0.20
                input "offPeakRate", "decimal", title: "Off-Peak Rate (per kWh)", defaultValue: 0.10
                input "forceSaveOnPeak", "bool", title: "Force 'Savings' setpoints during Peak hours?", defaultValue: true
            } else {
                input "offPeakRate", "decimal", title: "Standard Rate (per kWh)", defaultValue: 0.14
            }
        }

        section("<b>Global Tank Full Output</b>") {
            paragraph "<small>Virtual switch that turns on if ANY mapped zone has a full tank. Great for external Dashboards.</small>"
            input "globalTankSwitch", "capability.switch", title: "Global Tank Full Switch", required: false
        }
        
        section("<b>Outdoor Weather Data</b>") {
            input "outdoorTempSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor (Required for Enthalpy)", required: true
            input "outdoorHumSensor", "capability.relativeHumidityMeasurement", title: "Outdoor Humidity Sensor (Required for Enthalpy)", required: true
        }
    }
}

def alertsPage() {
    dynamicPage(name: "alertsPage", title: "Maintenance & Granular Alerts") {
        
        section("<b>Notification Routing</b>") {
            input "tankNotifyDevices", "capability.notification", title: "🚰 Tank Full Notification Devices", multiple: true, required: false
            input "filterNotifyDevices", "capability.notification", title: "⚙️ Filter Maintenance Notification Devices", multiple: true, required: false
        }
        for (int i = 1; i <= 8; i++) {
            if (settings["z${i}Name"]) {
                section("<b>Zone ${i}: ${settings["z${i}Name"]} Limits</b>") {
                    input "z${i}FilterLimit", "number", title: "Filter Run-Time Limit (Hours)", defaultValue: 360
                    input "btnResetFilter_${i}", "button", title: "🔄 Reset Filter Counter"
                }
            }
        }
    }
}

// Button Handler
def appButtonHandler(btn) {
    if (btn == "refreshDashboardBtn") {
        logAction("Dashboard manual refresh triggered by user.")
        evaluateZones()
    } else {
        for (int i = 1; i <= 8; i++) {
            if (btn == "btnResetFilter_${i}") {
                state["z${i}FilterRunHours"] = 0.0
                state["z${i}FilterNotified"] = false
                logAction("MAINTENANCE: Zone ${i} filter manually reset.")
            }
            if (btn == "btnResetTank_${i}") {
                resetTankFullFlag(i, "Manual Dashboard Reset")
            }
        }
    }
}

// --- MATH & LOGIC ENGINE ---
def calculateDewPoint(tempF, hum) {
    if (tempF == null || hum == null) return null
    def tempC = (tempF - 32.0) * 5.0 / 9.0
    def a = 17.625
    def b = 243.04
    def alpha = Math.log(hum / 100.0) + ((a * tempC) / (b + tempC))
    def dpC = (b * alpha) / (a - alpha)
    def dpF = (dpC * 9.0 / 5.0) + 32.0
    return Math.round(dpF * 10.0) / 10.0
}

def getHouseAverageHum() {
    def total = 0.0; def count = 0
    for (int i = 1; i <= 8; i++) {
        if (settings["z${i}Name"] && settings["z${i}Hum"]) {
            def h = settings["z${i}Hum"].currentValue("humidity")
            if (h != null) { total += h; count++ }
        }
    }
    return count > 0 ? (Math.round((total / count) * 10.0) / 10.0) : 0
}

def isTOUPeak() {
    if (!settings.enableTOU || !settings.peakStartTime || !settings.peakEndTime) return false
    return timeOfDayIsBetween(settings.peakStartTime, settings.peakEndTime, new Date(), location.timeZone)
}

def resetTankFullFlag(i, reason) {
    state["z${i}TankFull"] = false
    state["z${i}StallBaseRH"] = null
    state["z${i}TankMaxRH"] = null
    state["z${i}TankNotified"] = false
    logAction("ZONE ${i}: Tank Full flag CLEARED. Reason: ${reason}")
    evaluateZones()
}

def evaluateZones() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return

    def isPeak = isTOUPeak()
    def outT = outdoorTempSensor?.currentValue("temperature")
    def outH = outdoorHumSensor?.currentValue("humidity")
    def outDP = calculateDewPoint(outT, outH)
    def anyTankFull = false

    for (int i = 1; i <= 8; i++) {
        if (!settings["z${i}Name"]) continue
        
        def h = settings["z${i}Hum"]?.currentValue("humidity")
        def t = settings["z${i}Temp"]?.currentValue("temperature")
        def dehum = settings["z${i}Dehum"]
        def isHardwareOn = dehum.currentValue("switch") == "on"
        
        def inDP = calculateDewPoint(t, h)
        def isCooling = settings["z${i}Thermostat"]?.currentValue("thermostatOperatingState")?.toLowerCase()?.contains("cool") ?: false
        def windowOpen = settings["z${i}Window"]?.currentValue("contact") == "open"
        
        // Mode Check
        def allowedModes = settings["z${i}Modes"]
        def modeRestricted = allowedModes ? !allowedModes.contains(location.mode) : false

        // 1. ASHRAE Tracking
        if (h > 70) state["z${i}RiskScore"] = Math.min(100, (state["z${i}RiskScore"] ?: 0) + 2)
        else if (h < 50) state["z${i}RiskScore"] = Math.max(0, (state["z${i}RiskScore"] ?: 0) - 5)

        // 2. TANK FULL DIAGNOSTICS
        def method = settings["z${i}TankMethod"]
        
        if (method == "Power Meter" && isHardwareOn) {
            def watts = settings["z${i}Power"]?.currentValue("power") ?: 0
            def threshold = settings["z${i}ActiveWatts"] ?: 100
            
            if (watts < threshold) {
                if (!state["z${i}LowPowerStart"]) state["z${i}LowPowerStart"] = now()
                else if ((now() - state["z${i}LowPowerStart"]) > 300000) { // 5 mins low power
                    state["z${i}TankFull"] = true
                }
            } else {
                state["z${i}LowPowerStart"] = null
                if (state["z${i}TankFull"]) resetTankFullFlag(i, "Power Meter Spike (Compressor On)")
            }
        } 
        else if (method == "Humidity Stall") {
            if (isHardwareOn && !state["z${i}TankFull"]) {
                if (!state["z${i}StallStart"]) {
                    state["z${i}StallStart"] = now()
                    state["z${i}StallBaseRH"] = h
                } else if (!isCooling) {
                    def baseRH = state["z${i}StallBaseRH"]
                    def reqDrop = settings["z${i}StallDrop"] ?: 5
                    def limitMins = settings["z${i}StallMins"] ?: 90
                    
                    if ((baseRH - h) >= reqDrop) {
                        state["z${i}StallStart"] = now()
                        state["z${i}StallBaseRH"] = h // Progress made, reset timer
                    } else if ((now() - state["z${i}StallStart"]) > (limitMins * 60000)) {
                        state["z${i}TankFull"] = true
                        state["z${i}TankMaxRH"] = h 
                    }
                }
            } else if (!isHardwareOn) {
                 state["z${i}StallStart"] = null
            }
            
            // Auto-Reset Logic (Mold Guardian Method)
            if (state["z${i}TankFull"]) {
                def maxRH = state["z${i}TankMaxRH"] ?: h
                if (h > maxRH) state["z${i}TankMaxRH"] = h // Track the spike
                // If it drops 3% from the spike AND the AC is off, the dehumidifier is working again!
                else if (h <= (maxRH - 3) && !isCooling) {
                    resetTankFullFlag(i, "Humidity Drop (AC Off) - Auto Clear")
                }
            }
        }
        
        if (state["z${i}TankFull"]) {
            anyTankFull = true
            if (!state["z${i}TankNotified"] && settings.tankNotifyDevices) {
                settings.tankNotifyDevices.each { it.deviceNotification("🚰 TANK FULL: Zone ${i} (${settings["z${i}Name"]}) requires emptying.") }
                state["z${i}TankNotified"] = true
            }
        }

        // 3. DETERMINE TARGETS (Time-Of-Use Logic)
        def forceSave = settings.enableTOU && isPeak && settings.forceSaveOnPeak
        def useSaving = forceSave || settings["z${i}EnableSaving"]
        def target = forceSave ? (settings["z${i}SavePoint"] ?: 60) : (settings["z${i}ComfortPoint"] ?: 45)
        def db = forceSave ? (settings["z${i}SaveDB"] ?: 5) : (settings["z${i}ComfortDB"] ?: 3)

        // 4. LOGIC CASCADE
        def shouldRun = false
        def reason = "All targets met."
        def useFreeDehum = false
        
        if (windowOpen) {
            shouldRun = false
            reason = "Window/Door Open."
        } else if (state["z${i}TankFull"]) {
            // Force Smart Plug to stay ON so it can detect when the tank is emptied (power draw returns)
            shouldRun = true 
            reason = "TANK FULL (Idling pending empty)."
        } else if (modeRestricted) {
            shouldRun = false
            reason = "Location Mode Restricted."
        } else {
            if (h >= target) { 
                shouldRun = true; 
                reason = "Target limit (${target}%) exceeded." 
            }
            else if (isHardwareOn && h > (target - db)) { 
                shouldRun = true; 
                reason = "Maintaining Deadband." 
            }
            
            // ENTHALPY / FREE DEHUMIDIFICATION CHECK (Option 1)
            if (shouldRun && settings["z${i}ExhaustFan"] && outDP != null && inDP != null) {
                if (outDP <= (inDP - 2.0)) { 
                    useFreeDehum = true
                    reason += " (Free Dehum via Enthalpy: Out DP ${outDP}° < In DP ${inDP}°)"
                }
            }
        }

        // 5. EXECUTION
        def exFan = settings["z${i}ExhaustFan"]
        
        if (shouldRun) {
            if (state["z${i}TankFull"]) {
                // If tank is full, just leave plug ON but don't run fans
                if (!isHardwareOn) dehum.on() 
                if (exFan && exFan.currentValue("switch") == "on") exFan.off()
                state["z${i}LogicNote"] = "Tank Full (Waiting)"
                state["z${i}FreeDehumOn"] = false
            } else if (useFreeDehum) {
                if (isHardwareOn) dehum.off()
                if (exFan && exFan.currentValue("switch") == "off") exFan.on()
                state["z${i}LogicNote"] = "Free Dehum Active"
                state["z${i}FreeDehumOn"] = true
            } else {
                if (!isHardwareOn) dehum.on()
                if (exFan && exFan.currentValue("switch") == "on") exFan.off()
                state["z${i}LogicNote"] = "Compressor Active: ${reason}"
                state["z${i}FreeDehumOn"] = false
            }
            
            if (!state["z${i}RunStart"]) state["z${i}RunStart"] = now()
            calculateFinancials(i, true)
        } else {
            if (isHardwareOn) {
                dehum.off()
                if (exFan) exFan.off()
                state["z${i}LogicNote"] = "Idle"
                state["z${i}FreeDehumOn"] = false
                calculateFinancials(i, true)
                state["z${i}RunStart"] = now() // Reset timer for standby duration
            } else {
                calculateFinancials(i, false)
            }
        }
    }
    
    // Sync Global Tank Switch
    if (globalTankSwitch) {
        if (anyTankFull && globalTankSwitch.currentValue("switch") == "off") globalTankSwitch.on()
        else if (!anyTankFull && globalTankSwitch.currentValue("switch") == "on") globalTankSwitch.off()
    }
}

def calculateFinancials(zone, isEndingCycle) {
    if (!state["z${zone}RunStart"]) return
    def durationHours = (now() - state["z${zone}RunStart"]) / 3600000.0
    def watts = settings["z${zone}Watts"] ?: 500
    
    // TOU Support
    def isPeak = isTOUPeak()
    def rate = isPeak ? (settings.peakRate ?: 0.20) : (settings.offPeakRate ?: 0.10)
    
    // If Free Dehumidification is running, override watts to roughly 30W (exhaust fan)
    def actualWatts = state["z${zone}FreeDehumOn"] ? 30.0 : watts
    def amt = (actualWatts / 1000.0) * durationHours * rate
    
    // Only track spend if compressor or fan was actually running (not if tank was full & idling)
    if (isEndingCycle && !state["z${zone}TankFull"]) {
        state["z${zone}TotalSpend"] = (state["z${zone}TotalSpend"] ?: 0.0) + amt
        state["z${zone}FilterRunHours"] = (state["z${zone}FilterRunHours"] ?: 0.0) + durationHours
    } else if (!isEndingCycle) {
        state["z${zone}TotalSave"] = (state["z${zone}TotalSave"] ?: 0.0) + amt
    }
}

def calculateZoneState(zone) {
    def currHum = settings["z${zone}Hum"]?.currentValue("humidity") ?: 0
    def currTemp = settings["z${zone}Temp"]?.currentValue("temperature")
    def dehum = settings["z${zone}Dehum"]
    def isHardwareOn = dehum ? dehum.currentValue("switch") == "on" : false
    
    def inDP = calculateDewPoint(currTemp, currHum)
    def outDP = calculateDewPoint(outdoorTempSensor?.currentValue("temperature"), outdoorHumSensor?.currentValue("humidity"))
    
    def target = (settings.enableTOU && isTOUPeak() && settings.forceSaveOnPeak) ? (settings["z${zone}SavePoint"] ?: 60) : (settings["z${zone}ComfortPoint"] ?: 45)

    def rScore = state["z${zone}RiskScore"] ?: 0
    def dur = state["z${zone}RunStart"] ? "${Math.round((now() - state["z${zone}RunStart"]) / 60000)} mins" : "Idle"
    def fLimit = settings["z${zone}FilterLimit"] ?: 360
    def fCurrent = state["z${zone}FilterRunHours"] ?: 0.0
    
    return [
        currHum: currHum,
        currTemp: currTemp,
        dewPoint: inDP,
        spread: (currTemp != null && inDP != null) ? (Math.round((currTemp - inDP) * 10.0) / 10.0) : null,
        target: target,
        riskScore: rScore, 
        riskLevel: (rScore > 80 ? "DANGER" : rScore > 50 ? "WARNING" : "SAFE"), 
        duration: dur, 
        status: state["z${zone}LogicNote"] ?: "Standby", 
        filterLife: Math.max(0, Math.round(100 - ((fCurrent / fLimit) * 100))),
        hardwareOn: isHardwareOn,
        freeDehumOn: state["z${zone}FreeDehumOn"] ?: false,
        tankFull: state["z${zone}TankFull"] ?: false,
        windowOpen: settings["z${zone}Window"]?.currentValue("contact") == "open"
    ]
}

def logAction(msg) {
    if (txtEnable) log.info "${app.label}: ${msg}"
    def hist = state.eventLog ?: []
    hist.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    state.eventLog = hist.take(25)
}

def getRiskColor(score) { return score > 80 ? "#cc0000" : score > 50 ? "#e67e22" : "#27ae60" }
def installed() { initialize() }
def updated() { initialize() }

def initialize() {
    unschedule()
    subscribe(location, "systemStart", "hubRebootHandler")
    subscribe(location, "mode", "handleEnvChange")
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "handleEnvChange")
    
    for (int i = 1; i <= 8; i++) { 
        if (settings["z${i}Hum"]) subscribe(settings["z${i}Hum"], "humidity", "handleEnvChange") 
        if (settings["z${i}Temp"]) subscribe(settings["z${i}Temp"], "temperature", "handleEnvChange")
        if (settings["z${i}Thermostat"]) subscribe(settings["z${i}Thermostat"], "thermostatOperatingState", "handleEnvChange")
        if (settings["z${i}Window"]) subscribe(settings["z${i}Window"], "contact", "handleEnvChange")
        if (settings["z${i}Power"]) subscribe(settings["z${i}Power"], "power", "handleEnvChange")
    }
    if (outdoorTempSensor) subscribe(outdoorTempSensor, "temperature", "handleEnvChange")
    if (outdoorHumSensor) subscribe(outdoorHumSensor, "humidity", "handleEnvChange")
    
    runEvery5Minutes("evaluateZones")
}

def handleEnvChange(evt) { evaluateZones() }
def hubRebootHandler(evt) { logAction("SYSTEM ALERT: Hub reboot detected."); initialize(); evaluateZones() }
