/**
 * Advanced Lighting Circadian Rhythm
 */

definition(
    name: "Advanced Lighting Circadian Rhythm",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade Circadian Rhythm engine. Calculates natural solar color temperature based on local sunrise/sunset and outputs to a Hub Variable. Features live diagnostics, Daytime Storm Compensation (Lux tracking), and manual overrides. This is free-use code.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Lighting Circadian Rhythm</b>", install: true, uninstall: true) {
        
        section("<b>Live Circadian Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of the sun's position, outdoor lux, and the exact color temperature (Kelvin) and dimmer level (%) the engine is transmitting to your outputs.</div>"
            
            def statusExplanation = getHumanReadableStatus()
        
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #f39c12;'>" +
                      "<b>Engine Status:</b> ${statusExplanation}</div>"

            // Gather Core Metrics
            def currentLux = luxSensor ? luxSensor.currentValue("illuminance") ?: "--" : "N/A"
            def currentCT = state.calculatedCT ?: "--"
            def currentLevel = state.calculatedLevel ?: "--"
           
            def overrideModeStr = overrideMode ?: "Normal"
            
            // Sun Position Math for Dashboard
            def sunData = getSunriseAndSunset()
            def riseTime = sunData.sunrise ? sunData.sunrise.format("h:mm a", location.timeZone) : "--"
            def setTime = sunData.sunset ? sunData.sunset.format("h:mm a", location.timeZone) : "--"
        
            def phase = "Nighttime"
            if (state.stormModeActive) {
                phase = "<span style='color:#d35400;'><b>Daytime Storm (Cozy Mode Active)</b></span>"
            } else if (sunData.sunrise && sunData.sunset) {
                def nowMs = now()
       
                def riseMs = sunData.sunrise.time
                def setMs = sunData.sunset.time
                def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
                
                if (nowMs >= riseMs && nowMs < solarNoonMs) phase = "Morning (Ramping Up)"
                else if (nowMs >= solarNoonMs && nowMs < setMs) phase = "Afternoon (Ramping Down)"
                else if (nowMs >= setMs) phase = "Post-Sunset (Locked to Night Settings)"
                else phase = "Pre-Sunrise (Locked to Night Settings)"
            }

            // Unified Dashboard HTML
            def levelOutputDisplay = "<span style='color:red;'>Not Configured</span>"
            if (levelOutputType == "Virtual Dimmer Device" && outDimmer) levelOutputDisplay = outDimmer.displayName
            else if (levelOutputType == "Hub Variable" && levelVariable) levelOutputDisplay = levelVariable
            else if (levelOutputType == "Both") levelOutputDisplay = "${levelVariable ?: 'Missing Var'} & ${outDimmer ? outDimmer.displayName : 'Missing Device'}"

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; font-weight:bold; }
            </style>
            <table class="dash-table">
                <thead><tr><th colspan="2">Real-Time Lighting Metrics</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Calculated Color Temp</td><td class="dash-val" style="color:#e67e22; font-size: 16px;">${currentCT}K</td></tr>
                    <tr><td class="dash-hl">Calculated Dimmer Level</td><td class="dash-val" style="color:#f1c40f; font-size: 16px;">${currentLevel}%</td></tr>
         
                    <tr><td class="dash-hl">Outdoor Illuminance (Lux)</td><td class="dash-val">${currentLux}</td></tr>
                    <tr><td class="dash-hl">Active Override Mode</td><td class="dash-val">${overrideModeStr}</td></tr>
                    
                    <tr><td colspan="2" class="dash-subhead">Solar Positioning & Weather</td></tr>
                
                    <tr><td class="dash-hl">Current Solar Phase</td><td class="dash-val">${phase}</td></tr>
                    <tr><td class="dash-hl">Local Sunrise</td><td class="dash-val">${riseTime}</td></tr>
                    <tr><td class="dash-hl">Local Sunset</td><td class="dash-val">${setTime}</td></tr>
                    
                    <tr><td colspan="2" class="dash-subhead">System Connections</td></tr>
   
                    <tr><td class="dash-hl">Target CT Variable</td><td class="dash-val">${ctVariable ?: "<span style='color:red;'>Not Configured</span>"}</td></tr>
                    <tr><td class="dash-hl">Target Level Output</td><td class="dash-val">${levelOutputDisplay}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>App Control & Master Kill Switch</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The master toggle for the application. If disabled, the app stops updating the Hub Variables completely, allowing you to manually control your lights without interference.</div>"
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
        }

        section("<b>1. Manual Overrides</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Instantly locks the color temperature and dimmer to a specific value, bypassing the sun logic entirely. Useful for tasks requiring bright white light at night, or forcing cozy lighting during a dark storm.</div>"
            input "overrideMode", "enum", title: "<b>Operating Mode</b>", options: ["Normal (Track Sun)", "Force Cool & Bright (6500K / Max Level)", "Force Warm & Dim (2500K / Min Level)"], required: true, defaultValue: "Normal (Track Sun)", submitOnChange: true
        }

        section("<b>2. Environmental Sensors (Storm Compensation)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Connect an outdoor Lux (Illuminance) sensor. If it gets unusually dark during the middle of the day (like a heavy rainstorm), the app will temporarily drop the color temperature and brightness to a cozy, warm setting until the sun comes back out.</div>"
            input "luxSensor", "capability.illuminanceMeasurement", title: "Outdoor Lux Sensor (Optional)", required: false, submitOnChange: true
            
            if (luxSensor) {
                input "enableLuxOverride", "bool", title: "<b>Enable Daytime Storm Compensation</b>", defaultValue: false, submitOnChange: true
                if (enableLuxOverride) {
                    input "luxThreshold", "number", title: "Lux Threshold (Drop values when Lux falls below this number)", required: false, defaultValue: 1000
                    input "luxTargetCT", "number", title: "Cozy Color Temp for Storms (Kelvin)", required: false, defaultValue: 3000
                    input "luxTargetLevel", "number", title: "Cozy Dimmer Level for Storms (%)", required: false, defaultValue: 50
                }
            }
        }

        section("<b>3. Output Destinations</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> The app continuously calculates the perfect Kelvin and Brightness percentage and injects them into your chosen destinations.</div>"
            
            input "ctVariable", "text", title: "Exact Name of Color Temp Hub Variable (Required)", required: true
            
            paragraph "<b>Dimmer Level Destination:</b>"
            input "levelOutputType", "enum", title: "Where should the app send the calculated Dimmer Level?", options: ["Hub Variable", "Virtual Dimmer Device", "Both"], required: true, defaultValue: "Hub Variable", submitOnChange: true
            
            if (levelOutputType == "Hub Variable" || levelOutputType == "Both") {
                input "levelVariable", "text", title: "Exact Name of Dimmer Level Hub Variable", required: true
            }
            if (levelOutputType == "Virtual Dimmer Device" || levelOutputType == "Both") {
                input "outDimmer", "capability.switchLevel", title: "Select Virtual Dimmer Device", required: true, multiple: false
            }
        }

        section("<b>4. Circadian Boundaries & Curves</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Defines the absolute floor and ceiling for your color temperature and brightness. Also allows you to invert the dimming logic so lights get dimmer as the evening approaches.</div>"
            
            paragraph "<b>Color Temperature Range:</b>"
            input "minCT", "number", title: "Minimum Warmth (Kelvin) - Used at Night", required: true, defaultValue: 2500
            input "maxCT", "number", title: "Maximum Coolness (Kelvin) - Used at Solar Noon", required: true, defaultValue: 6500
            
            paragraph "<b>Dimmer Level Range:</b>"
            input "minLevel", "number", title: "Minimum Brightness (%)", required: true, defaultValue: 10
            input "maxLevel", "number", title: "Maximum Brightness (%)", required: true, defaultValue: 100
            input "dimCurveType", "enum", title: "Dimmer Tracking Logic", options: [
                "Standard (Bright Midday, Dim Night)", 
                "Inverted (Dim Midday, Bright Night)"
            ], required: true, defaultValue: "Standard (Bright Midday, Dim Night)"
        }

        section("<b>5. Update Frequency</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> How often the app recalculates the sun's position and updates your outputs. 15 minutes provides a smooth, unnoticeable transition throughout the day.</div>"
            input "updateInterval", "enum", title: "Calculation Interval", options: ["1":"Every 1 Minute", "5":"Every 5 Minutes", "15":"Every 15 Minutes", "30":"Every 30 Minutes"], required: false, defaultValue: "15"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    state.calculatedCT = null
    state.calculatedLevel = null
    state.stormModeActive = false
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", enableSwitchHandler)
    if (luxSensor) subscribe(luxSensor, "illuminance", luxHandler)
    
    // Schedule the heartbeat sweep
    def interval = updateInterval ?: "15"
    if (interval == "1") runEvery1Minute(routineSweep)
    else if (interval == "5") runEvery5Minutes(routineSweep)
    else if (interval == "15") runEvery15Minutes(routineSweep)
    else if (interval == "30") runEvery30Minutes(routineSweep)
    
    logAction("Circadian Engine Initialized. Sun tracking active.")
    evaluateSystem()
}

def enableSwitchHandler(evt) { 
    if (evt.value == "off") {
        logAction("Circadian App Paused via Master Switch.")
        state.stormModeActive = false
    } else {
        evaluateSystem() 
    }
}

def luxHandler(evt) {
    if (txtEnable) log.info "${app.label}: Outdoor Lux updated to ${evt.value}"
    
    // If Lux override is enabled, evaluate instantly when brightness changes rapidly
    if (enableLuxOverride) {
        evaluateSystem()
    }
}

def routineSweep() {
    evaluateSystem()
}

def getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "The application is suspended via the Master Switch."
    if (overrideMode == "Force Cool & Bright (6500K / Max Level)") return "<span style='color:blue;'><b>Manual Override:</b></span> Locked to 6500K and Max Brightness."
    if (overrideMode == "Force Warm & Dim (2500K / Min Level)") return "<span style='color:#d35400;'><b>Manual Override:</b></span> Locked to 2500K and Min Brightness."
    if (state.stormModeActive) return "Tracking normally, but <span style='color:#d35400;'><b>Storm Compensation</b></span> has engaged to warm and dim the house due to low outdoor light."
    
    return "Tracking normally. Calculating color and brightness curves based on solar position."
}

def evaluateSystem() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    if (!ctVariable) return // CT Var is the only absolute requirement
    
    def targetCT = 2700 // Fallback default
    def targetLevel = 100 // Fallback default
    state.stormModeActive = false // Reset prior to logic check
    
    // 1. Handle Manual Overrides
    if (overrideMode == "Force Cool & Bright (6500K / Max Level)") {
        targetCT = 6500
        targetLevel = maxLevel ?: 100
        if (txtEnable && (state.calculatedCT != targetCT || state.calculatedLevel != targetLevel)) {
            logAction("Override Active: Forcing 6500K / Max Level.")
        }
    } 
    else if (overrideMode == "Force Warm & Dim (2500K / Min Level)") {
        targetCT = 2500
        targetLevel = minLevel ?: 10
        if (txtEnable && (state.calculatedCT != targetCT || state.calculatedLevel != targetLevel)) {
            logAction("Override Active: Forcing 2500K / Min Level.")
        }
    } 
    // 2. Handle Normal Sun Tracking & Lux Override
    else {
        targetCT = calculateNaturalCT()
        targetLevel = calculateNaturalLevel()
        
        // Storm Compensation Override Logic
        if (luxSensor && enableLuxOverride) {
            def currentLux = luxSensor.currentValue("illuminance")
            def luxThresh = luxThreshold ?: 1000
            
            if (currentLux != null && currentLux <= luxThresh) {
                def sunData = getSunriseAndSunset()
                def nowMs = now()
                
                // Only engage Storm Compensation if it is actually daytime
                if (sunData.sunrise && sunData.sunset && nowMs > sunData.sunrise.time && nowMs < sunData.sunset.time) {
                    def stormCT = luxTargetCT ?: 3000
                    def stormLevel = luxTargetLevel ?: 50
                    
                    // Override if storm settings are cozier than current sun curve expectations
                    def engaged = false
                    if (stormCT < targetCT) {
                        targetCT = stormCT
                        engaged = true
                    }
                    if (dimCurveType != "Inverted (Dim Midday, Bright Night)" && stormLevel < targetLevel) {
                        targetLevel = stormLevel
                        engaged = true
                    }
                    
                    if (engaged) state.stormModeActive = true
                }
            }
        }
    }
    
    // 3. Push to Outputs
    if (state.calculatedCT != targetCT) {
        state.calculatedCT = targetCT
        def success = setGlobalVar(ctVariable, targetCT)
        if (success) {
            logAction("BMS Command -> CT Variable '${ctVariable}' updated to ${targetCT}K" + (state.stormModeActive ? " (Storm Mode)" : ""))
        } else {
            logAction("ERROR: Hubitat rejected the update for '${ctVariable}'. Check exact spelling and ensure it is a Number variable.")
        }
    }
    
    if (state.calculatedLevel != targetLevel) {
        state.calculatedLevel = targetLevel
        
        // Push to Hub Variable if selected
        if (levelOutputType == "Hub Variable" || levelOutputType == "Both") {
            if (levelVariable) {
                def success = setGlobalVar(levelVariable, targetLevel)
                if (success) {
                    logAction("BMS Command -> Level Variable '${levelVariable}' updated to ${targetLevel}%" + (state.stormModeActive ? " (Storm Mode)" : ""))
                } else {
                    logAction("ERROR: Hubitat rejected the update for '${levelVariable}'. Check exact spelling and ensure it is a Number variable.")
                }
            }
        }
        
        // Push to Virtual Dimmer if selected
        if (levelOutputType == "Virtual Dimmer Device" || levelOutputType == "Both") {
            if (outDimmer && outDimmer.currentValue("level") != targetLevel) {
                outDimmer.setLevel(targetLevel)
                logAction("BMS Command -> Virtual Dimmer '${outDimmer.displayName}' updated to ${targetLevel}%" + (state.stormModeActive ? " (Storm Mode)" : ""))
            }
        }
    }
}

def calculateNaturalCT() {
    def sunData = getSunriseAndSunset()
    def minTemp = minCT ?: 2500
    def maxTemp = maxCT ?: 6500
    
    if (!sunData.sunrise || !sunData.sunset) {
        logInfo("Could not retrieve Hubitat solar data. Defaulting to max coolness.")
        return maxTemp
    }
    
    def nowMs = now()
    def riseMs = sunData.sunrise.time
    def setMs = sunData.sunset.time
    def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
    def calculatedTemp = minTemp
    
    if (nowMs < riseMs) {
        calculatedTemp = minTemp
    } 
    else if (nowMs >= riseMs && nowMs <= solarNoonMs) {
        def percentage = (nowMs - riseMs) / (solarNoonMs - riseMs)
        calculatedTemp = minTemp + ((maxTemp - minTemp) * percentage)
    } 
    else if (nowMs > solarNoonMs && nowMs <= setMs) {
        def percentage = (nowMs - solarNoonMs) / (setMs - solarNoonMs)
        calculatedTemp = maxTemp - ((maxTemp - minTemp) * percentage)
    } 
    else if (nowMs > setMs) {
        calculatedTemp = minTemp
    }
    
    return (Math.round(calculatedTemp / 50.0) * 50).toInteger()
}

def calculateNaturalLevel() {
    def sunData = getSunriseAndSunset()
    def rawMin = minLevel ?: 10
    def rawMax = maxLevel ?: 100
    
    // Determine bounds based on inverse selection
    def isStandard = (dimCurveType == "Standard (Bright Midday, Dim Night)")
    def nightLevel = isStandard ? rawMin : rawMax
    def noonLevel = isStandard ? rawMax : rawMin
    
    if (!sunData.sunrise || !sunData.sunset) {
        return nightLevel
    }
    
    def nowMs = now()
    def riseMs = sunData.sunrise.time
    def setMs = sunData.sunset.time
    def solarNoonMs = riseMs + ((setMs - riseMs) / 2)
    def calculatedLvl = nightLevel
    
    if (nowMs < riseMs) {
        calculatedLvl = nightLevel
    } 
    else if (nowMs >= riseMs && nowMs <= solarNoonMs) {
        def percentage = (nowMs - riseMs) / (solarNoonMs - riseMs)
        calculatedLvl = nightLevel + ((noonLevel - nightLevel) * percentage)
    } 
    else if (nowMs > solarNoonMs && nowMs <= setMs) {
        def percentage = (nowMs - solarNoonMs) / (setMs - solarNoonMs)
        calculatedLvl = noonLevel - ((noonLevel - nightLevel) * percentage)
    } 
    else if (nowMs > setMs) {
        calculatedLvl = nightLevel
    }
    
    return Math.round(calculatedLvl).toInteger()
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
def logInfo(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}" 
}
