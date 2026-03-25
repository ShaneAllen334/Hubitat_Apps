/**
 * Advanced Overcast Detector
 *
 * Author: Custom
 */
definition(
    name: "Advanced Overcast Detector",
    namespace: "custom",
    author: "User",
    description: "Advanced environmental lux monitor with Proportional Dimming, Universal Darkness enforcement, and Astro Countdowns.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced Overcast Detector", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Environment</th><th style='padding: 8px;'>System Evaluation</th><th style='padding: 8px;'>Outputs</th></tr>"
            
            def currentLux = luxSensor ? "${luxSensor.currentValue('illuminance')} lx" : "-- lx"
            def sState = state.currentCondition ?: "Awaiting Sync..."
            
            def sColor = "orange"
            if (sState == "Overcast") sColor = "blue"
            if (sState == "Clear") sColor = "green"
            if (sState == "Nighttime") sColor = "purple"
            
            def pendingMsg = ""
            if (state.pendingOvercast) pendingMsg = "<br><span style='font-size: 11px; color: #555;'>Verifying Overcast...</span>"
            if (state.pendingClear) pendingMsg = "<br><span style='font-size: 11px; color: #555;'>Verifying Clear...</span>"
            if (state.isNight) pendingMsg = "<br><span style='font-size: 11px; color: purple;'>Nighttime Hard-Lock Active</span>"

            def vSwitch = targetSwitch ? targetSwitch.currentValue("switch")?.toUpperCase() : "NOT SET"
            def switchColor = (vSwitch == "ON") ? "green" : "black"
            
            def vDim = targetDimmer ? (targetDimmer.currentValue("switch") == "on" ? "${targetDimmer.currentValue('level')}%" : "OFF") : "NOT SET"
            def dimColor = (targetDimmer && targetDimmer.currentValue("switch") == "on") ? "blue" : "black"
            
            def outputsDisplay = "<b>Switch:</b> <span style='color: ${switchColor};'>${vSwitch}</span><br><b>Dimmer:</b> <span style='color: ${dimColor};'>${vDim}</span>"
            
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${currentLux}</b></td><td style='padding: 8px; color: ${sColor}; font-weight: bold;'>${sState}${pendingMsg}</td><td style='padding: 8px;'>${outputsDisplay}</td></tr>"
            statusText += "</table>"
           
            // System Status Evaluation
            def sysStatus = "<span style='color: green; font-weight: bold;'>ACTIVE</span>"
            if (isSystemPaused()) sysStatus = "<span style='color: red; font-weight: bold;'>PAUSED (Master Switch Off)</span>"
            else if (!isModeAllowed()) sysStatus = "<span style='color: orange; font-weight: bold;'>PAUSED (Restricted Mode)</span>"
            
            // Astro Countdown Calculation
            def astroMsg = "Awaiting Astro Data..."
            if (useAstro) {
                def sunInfo = getSunriseAndSunset()
                if (sunInfo && sunInfo.sunset && sunInfo.sunrise) {
                    def now = new Date()
                    def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
                    def actualSunset = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
                    
                    if (now.before(actualSunset) && now.after(sunInfo.sunrise)) {
                        def diffMillis = actualSunset.time - now.time
                        def h = (diffMillis / 3600000).toInteger()
                        def m = ((diffMillis % 3600000) / 60000).toInteger()
                        astroMsg = "<b>Sunset Hard-Lock in:</b> <span style='color: #d2691e;'>${h}h ${m}m</span>"
                    } else {
                        astroMsg = "<b>Astro Status:</b> <span style='color: purple;'>Currently Nighttime</span>"
                    }
                }
            } else {
                astroMsg = "<b>Astro Status:</b> Disabled"
            }
            
            statusText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; display: flex; flex-wrap: wrap; gap: 15px; border: 1px solid #ccc;'>"
            statusText += "<div><b>System:</b> ${sysStatus}</div>"
            statusText += "<div style='border-left: 1px solid #ccc; padding-left: 15px;'>${astroMsg}</div>"
            statusText += "</div>"

            paragraph statusText
        }
        
        section("Application History (Last 20 Events)") {
            if (state.historyLog && state.historyLog.size() > 0) {
                def logText = state.historyLog.join("<br>")
                paragraph "<div style='font-size: 13px; font-family: monospace; background-color: #f4f4f4; padding: 10px; border-radius: 5px; border: 1px solid #ccc;'>${logText}</div>"
            } else {
                paragraph "<i>No history available yet. The log will populate as the app takes action.</i>"
            }
        }
        
        section("Sensor & Control Targets") {
            input "luxSensor", "capability.illuminanceMeasurement", title: "Outdoor Master Lux Sensor", required: true
            
            paragraph "<b>Control Targets:</b> Select one or both. The Virtual Switch handles binary logic (ON/OFF). The Virtual Dimmer scales brightness based on storm severity."
            input "targetSwitch", "capability.switch", title: "Virtual Switch (ON = Overcast/Dark)", required: false
            input "targetDimmer", "capability.switchLevel", title: "Virtual Dimmer (Proportional Brightness)", required: false
            
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false
            input "activeModes", "mode", title: "Active Modes (App only runs in these)", multiple: true, required: false
        }
        
        section("Proportional Dimming Setup") {
            paragraph "Maps the virtual dimmer level between your Overcast Threshold and Heavy Storm Limit. Only applies if Virtual Dimmer is selected above."
            input "heavyStormLux", "number", title: "Heavy Storm Limit (Lux)", defaultValue: 500, required: true,
                description: "If lux drops to this level, the dimmer hits Max Brightness."
            input "maxDimLevel", "number", title: "Max Brightness Level (%)", defaultValue: 100, required: true, range: "1..100"
            input "minDimLevel", "number", title: "Min Brightness Level (%)", defaultValue: 20, required: true, range: "1..100",
                description: "The starting brightness when it just barely crosses the Overcast threshold."
            input "nightDimLevel", "number", title: "Nighttime Brightness Level (%)", defaultValue: 100, required: true, range: "1..100"
        }
        
        section("Hysteresis & Thresholds (The Deadband)") {
            input "overcastThreshold", "number", title: "Overcast Drop Threshold (Lux)", defaultValue: 2000, required: true,
                description: "If lux drops below this, start the Overcast timer."
                
            input "clearThreshold", "number", title: "Clear Sky Recovery Threshold (Lux)", defaultValue: 4000, required: true,
                description: "If lux rises above this, start the Clear Sky timer. Keep this higher than the Overcast threshold to prevent yo-yoing."
                
            input "debounceTime", "number", title: "Anti-Yo-Yo Debounce Time (Minutes)", defaultValue: 10, required: true,
                description: "How long the sky must stay below/above the threshold before flipping the virtual outputs."
        }
        
        section("Universal Darkness (Nighttime Logic)") {
            input "useAstro", "bool", title: "Apply Nighttime Logic?", defaultValue: true, submitOnChange: true
            
            if (useAstro) {
                input "nightAction", "enum", title: "When the sun sets, force the virtual outputs:", options: ["Turn OFF (Clear/Night)", "Turn ON (Dark/Overcast)", "Do Nothing (Leave as is)"], defaultValue: "Turn ON (Dark/Overcast)", required: true,
                    description: "Select 'Turn ON' to ensure your motion lighting automations function properly all night."
                input "sunriseOffset", "number", title: "Sunrise Offset (Minutes, +/-)", defaultValue: 0
                input "sunsetOffset", "number", title: "Sunset Offset (Minutes, +/-)", defaultValue: 0
            }
        }
    }
}

def installed() {
    log.info "Advanced Overcast Detector Installed."
    initialize()
}

def updated() {
    log.info "Advanced Overcast Detector Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.historyLog = state.historyLog ?: []
    state.currentCondition = "Evaluating..."
    state.pendingOvercast = false
    state.pendingClear = false
    state.isNight = false
    
    if (luxSensor) subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(location, "mode", modeHandler)
   
    if (useAstro) {
        scheduleAstro()
        schedule("0 1 0 * * ?", scheduleAstro)
        checkInitialAstroState()
    } else {
        state.isNight = false
    }
    
    // Force immediate evaluation on boot to fix "Evaluating" hang
    forceImmediateEvaluation()
}

// --- UTILITY: HISTORY LOGGER ---
def addToHistory(String msg) {
    if (!state.historyLog) state.historyLog = []
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "<b>[${timestamp}]</b> ${msg}")
    
    if (state.historyLog.size() > 20) {
        state.historyLog = state.historyLog.take(20)
    }
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    log.info "HISTORY: [${timestamp}] ${cleanMsg}"
}

// --- SYSTEM CHECKS ---
def isSystemPaused() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return true
    return false
}

def isModeAllowed() {
    if (!activeModes) return true
    return activeModes.contains(location.mode)
}

def modeHandler(evt) {
    if (!isModeAllowed()) {
        addToHistory("GLOBAL: Hub entered restricted mode (${evt.value}). Pausing detector.")
        unschedule("triggerOvercast")
        unschedule("triggerClear")
        state.pendingOvercast = false
        state.pendingClear = false
    } else {
        addToHistory("GLOBAL: Hub entered allowed mode (${evt.value}). Resuming detector.")
        evaluateLuxCondition()
    }
}

// --- PROPORTIONAL DIMMING ENGINE ---
def updateDimmerLevel(currentLux) {
    if (!targetDimmer || isSystemPaused() || !isModeAllowed() || state.isNight) return

    def overLimit = overcastThreshold ?: 2000
    def stormLimit = heavyStormLux ?: 500
    def maxLvl = maxDimLevel ?: 100
    def minLvl = minDimLevel ?: 20

    def targetLevel = minLvl
    
    if (currentLux <= stormLimit) {
        targetLevel = maxLvl
    } else if (currentLux >= overLimit) {
        targetLevel = minLvl
    } else {
        // Inverse interpolation: Lower Lux = Higher Level
        def luxRange = overLimit - stormLimit
        def levelRange = maxLvl - minLvl
        def luxDrop = overLimit - currentLux
        
        def calcLevel = minLvl + ((luxDrop / luxRange) * levelRange)
        targetLevel = calcLevel.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
    }

    def currentDimmerLevel = targetDimmer.currentValue("level")?.toInteger() ?: 0
    def currentDimmerState = targetDimmer.currentValue("switch")

    // Mesh Protection: Only adjust if it's off, or if the level changed by more than 2%
    if (currentDimmerState != "on" || Math.abs(currentDimmerLevel - targetLevel) > 2) {
        addToHistory("DIMMER: Dynamic adjustment to ${targetLevel}% (Lux: ${currentLux}).")
        targetDimmer.setLevel(targetLevel)
    }
}

// --- DYNAMIC CLEAR SKY CALCULATOR ---
def getDynamicClearThreshold() {
    def baseClear = clearThreshold ?: 4000
    def baseOvercast = overcastThreshold ?: 2000
    
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunrise || !sunInfo.sunset) return baseClear
    
    def now = new Date().time
    def sunrise = sunInfo.sunrise.time
    def sunset = sunInfo.sunset.time
    
    // Outside daylight hours, return base
    if (now < sunrise || now > sunset) return baseClear
    
    // 1. Calculate Time-of-Day Arc (0.0 to 1.0)
    def totalDaylightMillis = sunset - sunrise
    def currentDaylightMillis = now - sunrise
    def dayPercentage = currentDaylightMillis / totalDaylightMillis
    def timeMultiplier = Math.sin(dayPercentage * Math.PI)
    
    // 2. Calculate Seasonal Arc (Day of Year)
    def cal = Calendar.getInstance()
    def dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    
    // Approx Summer Solstice (172) as Peak, Winter Solstice (355) as Trough
    def seasonalOffset = ((dayOfYear - 172) / 365.0) * (Math.PI * 2)
    def seasonMultiplier = 0.7 + (0.3 * Math.cos(seasonalOffset))
    
    // 3. Combine Math (Using the user's set clearThreshold as the absolute summer peak)
    def dynamicLimit = (baseClear * timeMultiplier * seasonMultiplier).toInteger()
    
    // Keep a sensible minimum deadband so Clear Sky doesn't dip below the Overcast Threshold
    def safeMinimum = baseOvercast + 500
    
    return Math.max(dynamicLimit, safeMinimum)
}

// --- CORE LUX EVALUATION ---
def luxHandler(evt) {
    evaluateLuxCondition()
}

def forceImmediateEvaluation() {
    if (!luxSensor || isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    def lux = luxSensor.currentValue("illuminance")?.toInteger() ?: 0
    def overLimit = overcastThreshold ?: 2000
    def clearLimit = getDynamicClearThreshold()
    
    if (lux <= overLimit) {
        state.currentCondition = "Overcast"
        if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
        if (targetDimmer) updateDimmerLevel(lux)
    } else if (lux >= clearLimit) {
        state.currentCondition = "Clear"
        if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
        if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
    } else {
        state.currentCondition = "Deadband (Waiting for shift)"
    }
}

def evaluateLuxCondition() {
    if (isSystemPaused() || !isModeAllowed()) return
    if (state.isNight && useAstro) return 
    
    if (!luxSensor) return
    
    def lux = luxSensor.currentValue("illuminance")?.toInteger() ?: 0
    def overLimit = overcastThreshold ?: 2000
    def clearLimit = getDynamicClearThreshold()
    def debounceSecs = (debounceTime ?: 10) * 60
    
    // Dynamic Dimming Hook (If already overcast, constantly adjust level based on new lux)
    if (state.currentCondition == "Overcast") {
        updateDimmerLevel(lux)
    }
    
    // Scenario 1: Entering Overcast Conditions
    if (lux <= overLimit && state.currentCondition != "Overcast") {
        if (state.pendingClear) {
            unschedule("triggerClear")
            state.pendingClear = false
            addToHistory("Sky darkened back to ${lux} lx. Canceled Clear Verification.")
        }
        
        if (!state.pendingOvercast) {
            state.pendingOvercast = true
            runIn(debounceSecs, "triggerOvercast", [overwrite: true])
            addToHistory("Lux dropped to ${lux}. Starting ${(debounceSecs/60).toInteger()}m Overcast verification timer.")
        }
    } 
    // Scenario 2: Entering Clear Conditions
    else if (lux >= clearLimit && state.currentCondition != "Clear") {
        if (state.pendingOvercast) {
            unschedule("triggerOvercast")
            state.pendingOvercast = false
            addToHistory("Sky brightened back to ${lux} lx. Canceled Overcast Verification.")
        }
        
        if (!state.pendingClear) {
            state.pendingClear = true
            runIn(debounceSecs, "triggerClear", [overwrite: true])
            addToHistory("Lux rose to ${lux}. Starting ${(debounceSecs/60).toInteger()}m Clear verification timer.")
        }
    }
    // Scenario 3: The Deadband (Between Thresholds)
    else if (lux > overLimit && lux < clearLimit) {
        // If we are lingering in the middle, cancel any pending timers to enforce strict adherence to limits
        if (state.pendingOvercast) {
            unschedule("triggerOvercast")
            state.pendingOvercast = false
            addToHistory("Lux recovered into deadband (${lux} lx). Canceled Overcast verification.")
        }
        if (state.pendingClear) {
            unschedule("triggerClear")
            state.pendingClear = false
            addToHistory("Lux dropped into deadband (${lux} lx). Canceled Clear verification.")
        }
    }
}

// --- STATE EXECUTION ---
def triggerOvercast() {
    if (isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    state.pendingOvercast = false
    state.currentCondition = "Overcast"
    
    addToHistory("CONFIRMED: Conditions remained Overcast. Activating targets.")
    
    if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
    
    if (targetDimmer && luxSensor) {
        def currentLux = luxSensor.currentValue("illuminance")?.toInteger() ?: 0
        updateDimmerLevel(currentLux)
    }
}

def triggerClear() {
    if (isSystemPaused() || !isModeAllowed() || (state.isNight && useAstro)) return
    
    state.pendingClear = false
    state.currentCondition = "Clear"
    
    addToHistory("CONFIRMED: Conditions remained Clear. Deactivating targets.")
    
    if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
    if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
}

// --- ASTRO & TIME SCHEDULER ---
def scheduleAstro() {
    def sunInfo = getSunriseAndSunset()
    if (sunInfo && sunInfo.sunrise) {
        def sRiseOffset = sunriseOffset ? sunriseOffset.toInteger() : 0
        def sunriseTime = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
        if (sunriseTime.after(new Date())) runOnce(sunriseTime, executeSunrise, [overwrite: true])
    }
   
    if (sunInfo && sunInfo.sunset) {
        def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
        def sunsetTime = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
        if (sunsetTime.after(new Date())) runOnce(sunsetTime, executeSunset, [overwrite: true])
    }
}

def checkInitialAstroState() {
    def sunInfo = getSunriseAndSunset()
    if (!sunInfo || !sunInfo.sunset || !sunInfo.sunrise) return
    def now = new Date()
    
    def sRiseOffset = sunriseOffset ? sunriseOffset.toInteger() : 0
    def sSetOffset = sunsetOffset ? sunsetOffset.toInteger() : 0
    def actualSunrise = new Date(sunInfo.sunrise.time + (sRiseOffset * 60000))
    def actualSunset = new Date(sunInfo.sunset.time + (sSetOffset * 60000))
    
    if (now >= actualSunset || now <= actualSunrise) {
        state.isNight = true
        state.currentCondition = "Nighttime"
        enforceNightAction()
    } else {
        state.isNight = false
    }
}

def executeSunset() {
    if (!useAstro) return
  
    state.isNight = true
    state.currentCondition = "Nighttime"
    state.pendingOvercast = false
    state.pendingClear = false
    unschedule("triggerOvercast")
    unschedule("triggerClear")
    
    addToHistory("ASTRO: Sun has set. Suspending lux detection and applying Nighttime Logic.")
    enforceNightAction()
}

def enforceNightAction() {
    if (isModeAllowed() && !isSystemPaused()) {
        def action = nightAction ?: "Turn ON (Dark/Overcast)"
        
        if (action == "Turn OFF (Clear/Night)") {
            if (targetSwitch && targetSwitch.currentValue("switch") != "off") targetSwitch.off()
            if (targetDimmer && targetDimmer.currentValue("switch") != "off") targetDimmer.off()
            addToHistory("ASTRO: Forced Virtual Targets OFF for nighttime.")
        } 
        else if (action == "Turn ON (Dark/Overcast)") {
            if (targetSwitch && targetSwitch.currentValue("switch") != "on") targetSwitch.on()
            if (targetDimmer) {
                def nLevel = nightDimLevel ?: 100
                targetDimmer.setLevel(nLevel)
            }
            addToHistory("ASTRO: Forced Virtual Targets ON for nighttime.")
        }
    }
}

def executeSunrise() {
    if (!useAstro) return
    
    state.isNight = false
    addToHistory("ASTRO: Sun has risen. Resuming Overcast detection.")
    forceImmediateEvaluation()
}
