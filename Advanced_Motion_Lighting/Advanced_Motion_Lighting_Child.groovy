/**
 * Advanced Motion Lighting Child
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Motion Lighting Child",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade Lighting Engine: Multiple Disable Switches, Triple-Check Offs, Boot Recovery, ROI Telemetry, and Dynamic Variable Tracking.",
    category: "Convenience",
    parent: "ShaneAllen:Advanced Motion Lighting",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Motion Lighting Rule</b>", install: true, uninstall: true) {
       
        section("Live System Dashboard") {
            if (motionSensors || triggerSwitches) {
                def mState = isPrimaryActive() ? "<span style='color: blue; font-weight: bold;'>ACTIVE</span>" : "<span style='color: grey;'>INACTIVE</span>"
                
                def isLightOn = false
                if (lightType == "Simple On/Off") isLightOn = switches?.any { it.currentValue("switch") == "on" }
                else if (lightType == "Adjustable Bulb / Dimmer") isLightOn = dimmers?.any { it.currentValue("switch") == "on" }
                else if (lightType == "Color / CT Bulb") isLightOn = colorBulbs?.any { it.currentValue("switch") == "on" }
                
                def lState = isLightOn ? "<span style='color: green; font-weight: bold;'>ON</span>" : "OFF"
                
                def wState = atomicState.warningPhase ? "<span style='color: orange; font-weight: bold;'> (WARNING PHASE)</span>" : ""
                def manualLock = atomicState.manuallyTurnedOn ? "<span style='color: #0055aa; font-weight: bold;'> (MANUAL TIMEOUT)</span>" : ""
                def arrivalLock = atomicState.arrivalActive ? "<span style='color: #800080; font-weight: bold;'> (ARRIVAL OVERRIDE)</span>" : ""
                def modeInfo = location.mode
                
                def overrides = []
                if (atomicState.partyLock) overrides << "<span style='color: magenta; font-weight: bold;'>PARTY LOCK ON</span>"
            
                if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) overrides << "<span style='color: red;'>ON Disabled</span>"
                if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) overrides << "<span style='color: darkred;'>OFF Disabled</span>"
                if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") overrides << "<span style='color: darkblue; font-weight: bold;'>Nap Lock Active</span>"
              
                if (isKeepAliveActive()) {
                    overrides << "<span style='color: #00aadd; font-weight: bold;'>Keep-Alive Active</span>"
                }
                
                if (contactSensors?.any { it.currentValue("contact") == "open" }) {
                    if (overcastSwitch && overcastSwitch.currentValue("switch") == "on") {
                        overrides << "<span style='color: purple;'>Overcast Override Active</span>"
                    } else {
                        overrides << "<span style='color: orange;'>Blocked by Open Window/Door</span>"
                    }
                }
                def oText = overrides ? overrides.join(" | ") : "<span style='color: green;'>Clear</span>"

                def statusText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Primary Trigger</th><th style='padding: 8px;'>Light State</th><th style='padding: 8px;'>Current Mode</th><th style='padding: 8px;'>Active Blocks/Overrides</th></tr>"
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'>${mState}</td><td style='padding: 8px;'>${lState}${wState}${manualLock}${arrivalLock}</td><td style='padding: 8px;'>${modeInfo}</td><td style='padding: 8px;'>${oText}</td></tr>"
                statusText += "</table>"
                
                // --- TIMER DASHBOARD ---
                def timerText = ""
                def tz = location.timeZone ?: TimeZone.getDefault()
                def currentTime = now()
                if (atomicState.stdTaskTime && atomicState.stdTaskTime > currentTime) {
                    def diff = atomicState.stdTaskTime - currentTime
                    timerText += "<b>Standard Timeout:</b> ${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s"
                }
                if (timerText) {
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #fff3e0; border: 1px solid #ffcc80; font-size: 12px;'><b>Active Timers:</b> ${timerText}</div>"
                }

                if (luxSensor) {
                    def curLux = luxSensor.currentValue("illuminance") ?: 0
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #fdf5e6; border: 1px solid #ffebcd; font-size: 12px;'><b>Current Light Level:</b> ${curLux} lx</div>"
                }
                
                if (enableTelemetry) {
                    def liveCalc = calculateLiveSavings()
                    statusText += "<div style='margin-top: 5px; padding: 5px; background: #eef9ee; border: 1px solid #aaddaa; font-size: 12px;'><b>Predictive ROI:</b> Today: &#36;${liveCalc.today} | Total: &#36;${liveCalc.lifetime}</div>"
                }
                
                paragraph statusText
            } else {
                paragraph "<i>Configure sensors below to see the live dashboard.</i>"
            }
        }

        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>1. Core Configuration</div>", hideable: true, hidden: false) {
            label title: "Rule Name", required: true
            input "activeModes", "mode", title: "Active Modes (Allowed to turn ON)", multiple: true, required: false
            
            input "adjustOnModeChange", "bool", title: "Adjust lights dynamically on Mode change?", defaultValue: true
            input "dynamicAdjust", "bool", title: "Adjust lights dynamically if Hub Variable / Virtual Dimmer changes while ON?", defaultValue: true, submitOnChange: true
            
            input "lightType", "enum", title: "What type of lights are we controlling?", options: ["Simple On/Off", "Adjustable Bulb / Dimmer", "Color / CT Bulb"], required: true, submitOnChange: true
            
            if (dynamicAdjust && lightType == "Color / CT Bulb") {
                input "dynamicTransition", "number", title: "Dynamic Shift Fade Time (Seconds)", defaultValue: 5, description: "How slowly the bulbs will fade to the new setpoint."
            }
            
            if (lightType == "Simple On/Off") {
                input "switches", "capability.switch", title: "Switches to control", multiple: true, required: true
            } else if (lightType == "Adjustable Bulb / Dimmer") {
                input "dimmers", "capability.switchLevel", title: "Dimmers to control", multiple: true, required: true
            } else if (lightType == "Color / CT Bulb") {
                input "colorBulbs", "capability.colorTemperature", title: "Color/CT Bulbs to control", multiple: true, required: true
            }
            
            if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                input "isSmartBulbOnRelay", "bool", title: "Are these smart bulbs on a power-cutting Smart Switch?", defaultValue: false, submitOnChange: true
                if (isSmartBulbOnRelay) {
                    input "relaySwitch", "capability.switch", title: "The Relay providing power", required: true
                    input "relayDelay", "number", title: "Boot Delay (ms)", defaultValue: 1500
                }
            }
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>2. Triggers & Debounce</div>", hideable: true, hidden: false) {
            input "motionSensors", "capability.motionSensor", title: "Primary Motion Sensors", multiple: true, required: false
            input "triggerSwitches", "capability.switch", title: "Primary Trigger Switches (Virtual Occupancy)", multiple: true, required: false, description: "<b>WARNING:</b> DO NOT select the lights you are controlling here!"
            input "motionDebounceSeconds", "number", title: "Debounce (Seconds of continuous motion required)", defaultValue: 0
            input "luxSensor", "capability.illuminanceMeasurement", title: "Illuminance (Lux) Sensor", required: false
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>3. Turn ON Settings</div>", hideable: true, hidden: true) {
            input "useModeSettings", "bool", title: "Use specific settings per mode?", defaultValue: false, submitOnChange: true
         
            if (useModeSettings) {
                location.modes.each { m ->
                    paragraph "<b>${m.name} Settings</b>"
                    
                    if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                        input "useLevelDimmer_${m.id}", "bool", title: "Follow a Virtual Dimmer for Level?", defaultValue: false, submitOnChange: true
                        if (settings["useLevelDimmer_${m.id}"]) {
                            input "levelDimmer_${m.id}", "capability.switchLevel", title: "Select Virtual Dimmer to follow", required: true
                        } else {
                            input "useLevelVar_${m.id}", "bool", title: "Use a Hub Variable for Level?", defaultValue: false, submitOnChange: true
                            if (settings["useLevelVar_${m.id}"]) {
                                input "levelVarName_${m.id}", "string", title: "Hub Variable Name (Exact text)"
                            } else {
                                input "level_${m.id}", "number", title: "Dim Level (%)", range: "1..100"
                            }
                        }
                    }
                    
                    if (lightType == "Color / CT Bulb") {
                        input "useCTVar_${m.id}", "bool", title: "Use a Hub Variable for Color Temp?", defaultValue: false, submitOnChange: true
                        if (settings["useCTVar_${m.id}"]) {
                            input "ctVarName_${m.id}", "string", title: "Hub Variable Name (Exact text)"
                        } else {
                            input "ct_${m.id}", "number", title: "Color Temp (K)", range: "2000..7000"
                        }
                    }
                    input "delay_${m.id}", "number", title: "Turn Off Delay (Minutes)"
                    input "lux_${m.id}", "number", title: "Lux Threshold (Only ON if below this)"
                }
            } else {
                if (lightType == "Adjustable Bulb / Dimmer" || lightType == "Color / CT Bulb") {
                    input "useLevelDimmer", "bool", title: "Follow a Virtual Dimmer for Level?", defaultValue: false, submitOnChange: true
                    if (useLevelDimmer) {
                        input "levelDimmer", "capability.switchLevel", title: "Select Virtual Dimmer to follow", required: true
                    } else {
                        input "useLevelVar", "bool", title: "Use a Hub Variable for Level?", defaultValue: false, submitOnChange: true
                        if (useLevelVar) {
                            input "levelVarName", "string", title: "Hub Variable Name (Exact text)"
                        } else {
                            input "defaultLevel", "number", title: "Default Dim Level (%)", defaultValue: 100
                        }
                    }
                }
               
                if (lightType == "Color / CT Bulb") {
                    input "useCTVar", "bool", title: "Use a Hub Variable for Color Temp?", defaultValue: false, submitOnChange: true
                    if (useCTVar) {
                        input "ctVarName", "string", title: "Hub Variable Name (Exact text)"
                    } else {
                        input "defaultCT", "number", title: "Default Color Temp (K)", defaultValue: 2700
                    }
                }
                input "defaultDelay", "number", title: "Standard Turn Off Delay (Minutes)", defaultValue: 5, required: true
            }
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>4. Turn OFF Behaviors</div>", hideable: true, hidden: true) {
            input "gracePeriod", "number", title: "Grace Period after Manual Off (Seconds)", defaultValue: 15
            input "manualTimeoutMinutes", "number", title: "Manual Override Timeout (Minutes)", defaultValue: 30
            input "physicalOverrideDebounce", "number", title: "Physical Switch Override Lockout (Seconds)", defaultValue: 10, description: "Wait time before a physical button press is considered a manual override. MUST be higher than your fade transition times!"
            input "turnOffOnModes", "mode", title: "Force OFF when Hub enters these Modes", multiple: true
            
            if (isSmartBulbOnRelay) {
                input "turnOffRelay", "bool", title: "Turn off Relay Power when lights turn off?", defaultValue: true
            }
  
            paragraph "<b>Absolute Force-Off (Sweep)</b>"
            input "enableForceOff", "bool", title: "Enable Absolute Force-Off?", defaultValue: false, submitOnChange: true
            if (enableForceOff) input "forceOffMinutes", "number", title: "Sweep Delay (Minutes)", defaultValue: 60
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>5. Advanced Restrictions</div>", hideable: true, hidden: true) {
            input "disableOnSwitches", "capability.switch", title: "Switches to Disable Turning ON", multiple: true, required: false, description: "If ANY of these are ON, the light will not turn on automatically."
            input "disableOffSwitches", "capability.switch", title: "Switches to Disable Turning OFF", multiple: true, required: false, description: "If ANY of these are ON, the automation will not turn the light off."
            
            input "goodNightSwitch", "capability.switch", title: "Nap Time / Hard-Lock Switch", required: false
            input "startTime", "time", title: "Only active AFTER", required: false
            input "endTime", "time", title: "Only active BEFORE", required: false
            
            paragraph "<b>Custom Contact Logic</b>"
            input "contactSensors", "capability.contactSensor", title: "Do NOT turn on if these contacts are OPEN", multiple: true, required: false
            input "overcastSwitch", "capability.switch", title: "Virtual Overcast Switch (Ignores open contacts)", required: false
            
            paragraph "<b>Keep-Alive / Overrides (Keeps lights ON, won't trigger ON)</b>"
            input "keepAliveMotionSensors", "capability.motionSensor", title: "Secondary Motion Sensors", multiple: true, required: false
            input "keepAliveSwitches", "capability.switch", title: "Keep-Alive Switches (e.g., TV is ON)", multiple: true, required: false
            input "keepAlivePower", "capability.powerMeter", title: "Keep-Alive Power Meters", multiple: true, required: false
            input "keepAlivePowerThreshold", "number", title: "Power Threshold for Keep-Alive (Watts)", defaultValue: 10
            
            paragraph "<b>Override Exceptions & Locks</b>"
            input "ignoreOverrideSwitches", "capability.switch", title: "Ignore Manual Overrides when these are ON", multiple: true, required: false, description: "e.g., A virtual switch turned on by a Shower Monitor. Prevents manual holds."
            input "colorLockSwitches", "capability.switch", title: "Color/Level Lock Switches", multiple: true, required: false, description: "Do NOT change color or brightness if these are ON (Select your Mail / School Pickup switches here)."
        }

        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>6. Telemetry & ROI</div>", hideable: true, hidden: true) {
            input "enableTelemetry", "bool", title: "Enable Energy Tracking?", defaultValue: false, submitOnChange: true
            if (enableTelemetry) {
                input "totalWattage", "decimal", title: 'Total Wattage (e.g. 60.5)', required: true
                input "kwhRate", "decimal", title: 'Electricity Rate ($ per kWh)', defaultValue: 0.14, required: true
                input "baselineHours", "decimal", title: "Baseline ON Hours (Assumed daily without automation)", defaultValue: 8.0
            }
        }
        
        section("<div style='background-color:#333; color:white; padding:5px; font-size:16px;'>7. Arrival Lighting Strategy</div>", hideable: true, hidden: false) {
            input "enableArrivalLighting", "bool", title: "Enable Full Arrival Lighting?", defaultValue: false, submitOnChange: true, description: "If enabled, the parent app will force these lights ON when the Arrival scenario is triggered."
            
            if (enableArrivalLighting && lightType == "Color / CT Bulb") {
                input "arrivalColorOverride", "bool", title: "Override color to 6500K during Arrival?", defaultValue: true, submitOnChange: true, description: "Lights will revert to their assigned color temp when the arrival timer ends."
                
                if (arrivalColorOverride) {
                    input "arrivalTransitionTime", "number", title: "Revert Transition Time (Seconds)", defaultValue: 3, description: "How gradually the lights shift back from 6500K to standard color."
                }
            }
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    atomicState.historyLog = atomicState.historyLog ?: []
    atomicState.appTurnedOn = atomicState.appTurnedOn ?: false
    atomicState.manuallyTurnedOn = atomicState.manuallyTurnedOn ?: false
    atomicState.arrivalActive = atomicState.arrivalActive ?: false
    atomicState.offRetryCount = 0
    
    // Primary Triggers
    subscribe(motionSensors, "motion", triggerHandler)
    subscribe(triggerSwitches, "switch", triggerHandler)
    
    // Keep-Alives
    subscribe(keepAliveMotionSensors, "motion", keepAliveHandler)
    subscribe(keepAliveSwitches, "switch", keepAliveHandler)
    subscribe(keepAlivePower, "power", keepAliveHandler)
    
    // Restrictions & System
    subscribe(disableOnSwitches, "switch", restrictionHandler)
    subscribe(disableOffSwitches, "switch", restrictionHandler)
    subscribe(contactSensors, "contact", restrictionHandler)
    subscribe(overcastSwitch, "switch", restrictionHandler)
    subscribe(goodNightSwitch, "switch", restrictionHandler)
    subscribe(location, "mode", modeChangeHandler)
    subscribe(location, "systemStart", bootHandler)
    
    // Listen for the lock switches turning off to re-apply color
    subscribe(colorLockSwitches, "switch.off", colorLockOffHandler)
    
    if (lightType == "Simple On/Off" && switches) {
        subscribe(switches, "switch.off", physicalOffHandler)
        subscribe(switches, "switch.on", physicalOnHandler)
    } else if (lightType == "Adjustable Bulb / Dimmer" && dimmers) {
        if (isSmartBulbOnRelay && relaySwitch) {
            subscribe(relaySwitch, "switch.off", physicalOffHandler)
            subscribe(relaySwitch, "switch.on", physicalOnHandler)
        } else {
            subscribe(dimmers, "switch.off", physicalOffHandler)
            subscribe(dimmers, "switch.on", physicalOnHandler)
        }
    } else if (lightType == "Color / CT Bulb" && colorBulbs) {
        if (isSmartBulbOnRelay && relaySwitch) {
            subscribe(relaySwitch, "switch.off", physicalOffHandler)
            subscribe(relaySwitch, "switch.on", physicalOnHandler)
        } else {
            subscribe(colorBulbs, "switch.off", physicalOffHandler)
            subscribe(colorBulbs, "switch.on", physicalOnHandler)
        }
    }
    
    // Subscribe to Dynamic Variables and Dimmers
    if (dynamicAdjust) {
        def dimmersToSub = []
        def varsToSub = []
        
        if (useModeSettings) {
            location.modes.each { m ->
                if (settings["useLevelDimmer_${m.id}"] && settings["levelDimmer_${m.id}"]) dimmersToSub << settings["levelDimmer_${m.id}"]
                if (settings["useLevelVar_${m.id}"] && settings["levelVarName_${m.id}"]) varsToSub << settings["levelVarName_${m.id}"]
                if (settings["useCTVar_${m.id}"] && settings["ctVarName_${m.id}"]) varsToSub << settings["ctVarName_${m.id}"]
            }
        } else {
            if (useLevelDimmer && levelDimmer) dimmersToSub << levelDimmer
            if (useLevelVar && levelVarName) varsToSub << levelVarName
            if (useCTVar && ctVarName) varsToSub << ctVarName
        }
        
        dimmersToSub.findAll { it }.unique { it.id }.each { dev ->
            subscribe(dev, "level", dynamicAdjustmentHandler)
        }
        varsToSub.findAll { it }.unique().each { vName ->
            subscribe(location, "variable:${vName}", dynamicAdjustmentHandler)
        }
    }
    
    schedule("0 0 0 * * ?", "midnightReset")
    runIn(10, "bootSync")
}

// --- STATE HELPERS ---
def isPrimaryActive() {
    return (motionSensors?.any { it.currentValue("motion") == "active" } || triggerSwitches?.any { it.currentValue("switch") == "on" })
}

def isKeepAliveActive() {
    def mActive = keepAliveMotionSensors?.any { it.currentValue("motion") == "active" }
    def sActive = keepAliveSwitches?.any { it.currentValue("switch") == "on" }
    def pActive = keepAlivePower?.any { it.currentValue("power")?.toBigDecimal() >= (keepAlivePowerThreshold ?: 10) }
    return (mActive || sActive || pActive)
}

def bootHandler(evt) { runIn(30, "bootSync", [overwrite: true]) }

def bootSync() {
    def anyOn = false
    if (lightType == "Simple On/Off") anyOn = switches?.any { it.currentValue("switch") == "on" }
    else if (lightType == "Adjustable Bulb / Dimmer") anyOn = dimmers?.any { it.currentValue("switch") == "on" }
    else if (lightType == "Color / CT Bulb") anyOn = colorBulbs?.any { it.currentValue("switch") == "on" }
    
    if (anyOn && !isPrimaryActive() && !isKeepAliveActive() && !atomicState.arrivalActive) {
        def randomStagger = new Random().nextInt(5000) + 500
        runInMillis(randomStagger.toLong(), "startTurnOffTimer")
    }
}

def recordTurnOnTime() { if (!atomicState.onTimeStart) atomicState.onTimeStart = now() }
def recordTurnOffTime() { if (atomicState.onTimeStart) { atomicState.todayOnMillis = (atomicState.todayOnMillis ?: 0) + (now() - atomicState.onTimeStart); atomicState.onTimeStart = null } }

def calculateLiveSavings() {
    def currentMillis = (atomicState.todayOnMillis ?: 0) + (atomicState.onTimeStart ? (now() - atomicState.onTimeStart) : 0)
    def hoursOn = currentMillis / 3600000.0
    def savedKwh = ((totalWattage ?: 0) * ((baselineHours ?: 8.0) - hoursOn)) / 1000.0
    def todaySaved = (savedKwh > 0 ? savedKwh : 0.0) * (kwhRate ?: 0.14)
    return [today: todaySaved.toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP), lifetime: (atomicState.lifetimeSavings ?: 0.0).toBigDecimal().setScale(2, BigDecimal.ROUND_HALF_UP)]
}

def midnightReset() {
    atomicState.lifetimeSavings = (atomicState.lifetimeSavings ?: 0.0) + calculateLiveSavings().today.toBigDecimal()
    atomicState.todayOnMillis = 0
    if (atomicState.onTimeStart) atomicState.onTimeStart = now()
}

def resetROI() {
    atomicState.lifetimeSavings = 0.0
    atomicState.todayOnMillis = 0
    if (atomicState.appTurnedOn) atomicState.onTimeStart = now()
    else atomicState.onTimeStart = null
}

def triggerHandler(evt) {
    if (evt.value == "active" || evt.value == "on") {
        if (atomicState.gracePeriodEnd && now() < atomicState.gracePeriodEnd) return
        cancelAllTurnOffTimers()
        if (motionDebounceSeconds && !atomicState.appTurnedOn) runIn(motionDebounceSeconds, "evaluateTurnOn")
        else evaluateTurnOn()
    } else {
        if (!isPrimaryActive() && !isKeepAliveActive()) startTurnOffTimer()
    }
}

def keepAliveHandler(evt) {
    if (isKeepAliveActive()) {
        if (atomicState.appTurnedOn) cancelAllTurnOffTimers()
    } else {
        if (atomicState.appTurnedOn && !isPrimaryActive()) startTurnOffTimer()
    }
}

def restrictionHandler(evt) {
    if (isPrimaryActive()) evaluateTurnOn()
}

def colorLockOffHandler(evt) {
    if (atomicState.appTurnedOn && !(colorLockSwitches?.any { it.currentValue("switch") == "on" })) {
        applyLightingSettings()
    }
}

def dynamicAdjustmentHandler(evt) {
    if (atomicState.appTurnedOn) {
        def randomStagger = new Random().nextInt(2000) + 100
        runInMillis(randomStagger.toLong(), "applyDynamicSettings")
    }
}

def applyDynamicSettings() {
    def trans = dynamicTransition != null ? dynamicTransition : 5
    applyLightingSettings(trans)
}

def startTurnOffTimer() {
    if (atomicState.arrivalActive) return
    def delay = atomicState.manuallyTurnedOn ? (manualTimeoutMinutes ?: 30) : (getModeSetting("delay") ?: (defaultDelay ?: 5))
    atomicState.stdTaskTime = now() + (delay * 60000)
    runIn(delay * 60, "processTurnOff")
}

def cancelAllTurnOffTimers() { unschedule("processTurnOff"); atomicState.stdTaskTime = null }

def evaluateTurnOn() {
    atomicState.offRetryCount = 0 
    
    if (activeModes && !activeModes.contains(location.mode)) return
    
    if (luxSensor) {
        def currentLux = luxSensor.currentValue("illuminance") ?: 0
        def targetLux = getModeSetting("lux")
        if (targetLux != null && currentLux >= targetLux) return
    }
    
    if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) return
    if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") return
    
    // FIX: Convert strings to DateTime objects for Hubitat
    if (startTime && endTime && !timeOfDayIsBetween(toDateTime(startTime), toDateTime(endTime), new Date(), location.timeZone)) return
    
    if (contactSensors?.any { it.currentValue("contact") == "open" } && overcastSwitch?.currentValue("switch") != "on") return

    atomicState.appTurnedOn = true
    atomicState.lastAutoCommand = now()
    recordTurnOnTime()
    applyLightingSettings()
}

def getTargetLevel() {
    def isMode = useModeSettings
    def m = location.modes.find { it.name == location.mode }
    
    def useDimmer = isMode ? settings["useLevelDimmer_${m?.id}"] : useLevelDimmer
    def dimmerDev = isMode ? settings["levelDimmer_${m?.id}"] : levelDimmer
    
    def useVar = isMode ? settings["useLevelVar_${m?.id}"] : useLevelVar
    def varName = isMode ? settings["levelVarName_${m?.id}"] : levelVarName
    
    def staticLvl = isMode ? settings["level_${m?.id}"] : defaultLevel
    
    if (useDimmer && dimmerDev) {
        def curLvl = dimmerDev.currentValue("level")
        if (curLvl != null) return curLvl.toInteger()
    } else if (useVar && varName) {
        def hubVar = getGlobalVar(varName)
        if (hubVar != null && hubVar.value != null) return hubVar.value.toInteger()
    }
    
    return staticLvl != null ? staticLvl : 100
}

def getTargetColorTemp() {
    def isMode = useModeSettings
    def m = location.modes.find { it.name == location.mode }
    
    def useVar = isMode ? settings["useCTVar_${m?.id}"] : useCTVar
    def varName = isMode ? settings["ctVarName_${m?.id}"] : ctVarName
    def staticCT = isMode ? settings["ct_${m?.id}"] : defaultCT
    
    if (useVar && varName) {
        def hubVar = getGlobalVar(varName)
        if (hubVar != null && hubVar.value != null) return hubVar.value.toInteger()
    }
    return staticCT ?: 2700
}

def applyLightingSettings(transition = null) {
    atomicState.lastAutoCommand = now() 
    def refreshNeeded = false
    def isColorLocked = colorLockSwitches?.any { it.currentValue("switch") == "on" }

    if (lightType == "Simple On/Off") {
        switches?.each { 
            if (it.currentValue("switch") != "on") {
                it.on()
                refreshNeeded = true
            }
        }
    } else if (lightType == "Adjustable Bulb / Dimmer") {
        if (isSmartBulbOnRelay && relaySwitch?.currentValue("switch") != "on") { 
            relaySwitch.on()
            pauseExecution(relayDelay ?: 1500) 
        }
        
        if (isColorLocked) {
            dimmers?.each { 
                if (it.currentValue("switch") != "on") {
                    it.on()
                    refreshNeeded = true
                }
            }
        } else {
            def lvl = getTargetLevel()
            dimmers?.each { 
                if (it.currentValue("switch") != "on" || it.currentValue("level") != lvl) {
                    it.setLevel(lvl)
                    refreshNeeded = true
                }
            }
        }
    } else if (lightType == "Color / CT Bulb") {
        if (isSmartBulbOnRelay && relaySwitch?.currentValue("switch") != "on") { 
             relaySwitch.on()
            pauseExecution(relayDelay ?: 1500) 
        }
        
        if (isColorLocked) {
            colorBulbs?.each { 
                if (it.currentValue("switch") != "on") {
                    it.on()
                    refreshNeeded = true
                }
            }
        } else {
            def lvl = getTargetLevel()
            def ct = getTargetColorTemp()
            
            colorBulbs?.each { 
                if (it.currentValue("switch") != "on" || it.currentValue("level") != lvl || it.currentValue("colorTemperature") != ct) {
                    if (transition != null) it.setColorTemperature(ct, lvl, transition)
                    else it.setColorTemperature(ct, lvl)
                    refreshNeeded = true
                }
            }
        }
    }

    if (refreshNeeded) runIn(2, "executeRefresh")
}

def processTurnOff() {
    if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) return
    if (isPrimaryActive() || isKeepAliveActive()) return 
    
    atomicState.appTurnedOn = false
    atomicState.manuallyTurnedOn = false
    atomicState.arrivalActive = false
    recordTurnOffTime()
    
    sendOffCommands()
    runIn(10, "verifyTurnOff")
}

def sendOffCommands() {
    atomicState.lastAutoOffCommand = now() 
    atomicState.lastAutoCommand = now() 
    
    def refreshNeeded = false

    if (lightType == "Simple On/Off") {
        switches?.each { 
            if (it.currentValue("switch") != "off") { 
                it.off()
                refreshNeeded = true 
            } 
        }
    } else if (lightType == "Adjustable Bulb / Dimmer") {
        dimmers?.each { 
            if (it.currentValue("switch") != "off") { 
                it.setLevel(1)
                pauseExecution(400)
                it.off()
                refreshNeeded = true 
            } 
        }
    } else if (lightType == "Color / CT Bulb") {
        colorBulbs?.each { 
            if (it.currentValue("switch") != "off") { 
                it.setLevel(1)
                pauseExecution(400)
                it.off()
                refreshNeeded = true 
            } 
        }
    }
    
    if (isSmartBulbOnRelay && turnOffRelay && relaySwitch?.currentValue("switch") != "off") {
        relaySwitch?.off()
    }

    if (refreshNeeded) runIn(2, "executeRefresh")
}

def executeRefresh() {
    atomicState.lastAutoCommand = now() 
    atomicState.lastAutoOffCommand = now()

    if (lightType == "Simple On/Off") switches?.each { if (it.hasCommand("refresh")) it.refresh() }
    else if (lightType == "Adjustable Bulb / Dimmer") dimmers?.each { if (it.hasCommand("refresh")) it.refresh() }
    else if (lightType == "Color / CT Bulb") colorBulbs?.each { if (it.hasCommand("refresh")) it.refresh() }
}

def verifyTurnOff() {
    def anyOn = false
    
    if (isSmartBulbOnRelay && turnOffRelay) {
        anyOn = (relaySwitch?.currentValue("switch") == "on")
    } else {
        if (lightType == "Simple On/Off") anyOn = switches?.any { it.currentValue("switch") == "on" }
        else if (lightType == "Adjustable Bulb / Dimmer") anyOn = dimmers?.any { it.currentValue("switch") == "on" }
        else if (lightType == "Color / CT Bulb") anyOn = colorBulbs?.any { it.currentValue("switch") == "on" }
    }
  
    if (isPrimaryActive() || isKeepAliveActive() || atomicState.appTurnedOn) {
        atomicState.offRetryCount = 0
        return 
    }

    if (anyOn && atomicState.offRetryCount < 3) {
        atomicState.offRetryCount++
        sendOffCommands()
        runIn(10, "verifyTurnOff")
    } else { 
        atomicState.offRetryCount = 0 
        atomicState.manuallyTurnedOn = false 
    }
}

def physicalOffHandler(evt) {
    if (ignoreOverrideSwitches?.any { it.currentValue("switch") == "on" }) return
    if (!atomicState.appTurnedOn && !atomicState.manuallyTurnedOn) return

    def userDebounce = physicalOverrideDebounce != null ? physicalOverrideDebounce : 10
    def shiftTime = (dynamicAdjust && dynamicTransition != null) ? dynamicTransition : 5
    def safeDebounce = Math.max(userDebounce, shiftTime + 3) * 1000
    
    if (now() - (atomicState.lastAutoOffCommand ?: 0) > safeDebounce) { 
        def gp = (gracePeriod ?: 15)
        atomicState.gracePeriodEnd = now() + (gp * 1000)
        atomicState.appTurnedOn = false
        atomicState.manuallyTurnedOn = false
        atomicState.arrivalActive = false 
        cancelAllTurnOffTimers()
        recordTurnOffTime() 
    }
}

def physicalOnHandler(evt) {
    if (ignoreOverrideSwitches?.any { it.currentValue("switch") == "on" }) return
    if (atomicState.appTurnedOn && !atomicState.manuallyTurnedOn) return

    def userDebounce = physicalOverrideDebounce != null ? physicalOverrideDebounce : 10
    def shiftTime = (dynamicAdjust && dynamicTransition != null) ? dynamicTransition : 5
    def safeDebounce = Math.max(userDebounce, shiftTime + 3) * 1000
    
    if (now() - (atomicState.lastAutoCommand ?: 0) > safeDebounce) {
        atomicState.manuallyTurnedOn = true
        atomicState.appTurnedOn = true
        cancelAllTurnOffTimers()
        recordTurnOnTime() 
    }
}

def modeChangeHandler(evt) {
    def forceOffList = [turnOffOnModes].flatten().findAll { it }
    
    if (forceOffList.contains(evt.value)) {
        cancelAllTurnOffTimers() 
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger.toLong(), "processTurnOff")
    } else if (adjustOnModeChange && atomicState.appTurnedOn) {
        def randomStagger = new Random().nextInt(4000) + 100
        runInMillis(randomStagger.toLong(), "applyLightingSettings")
    }
}

def getModeSetting(type) {
    if (!useModeSettings) return null
    def m = location.modes.find { it.name == location.mode }
    return settings["${type}_${m.id}"]
}

def isArrivalEnabled() { return enableArrivalLighting == true }

def turnOnArrival() {
    atomicState.arrivalActive = true
    atomicState.appTurnedOn = true
    recordTurnOnTime()
    atomicState.lastAutoCommand = now() 
    
    if (lightType == "Color / CT Bulb" && arrivalColorOverride) {
        def lvl = getTargetLevel()
        colorBulbs?.each { it.setColorTemperature(6500, lvl, 0) }
    } else applyLightingSettings()
}

def revertFromArrival() {
    atomicState.arrivalActive = false
    if (isPrimaryActive() || isKeepAliveActive()) {
        startTurnOffTimer()
        if (lightType == "Color / CT Bulb" && arrivalColorOverride) applyLightingSettings(arrivalTransitionTime != null ? arrivalTransitionTime : 3)
    } else processTurnOff()
}

def executeParentSweep(delayMs = 0) {
    if (!isPrimaryActive() && !isKeepAliveActive()) {
        if (delayMs > 0) runInMillis(delayMs.toLong(), "processTurnOff")
        else processTurnOff()
    }
}

def clearManualOverride() {
    if (atomicState.manuallyTurnedOn) {
        atomicState.manuallyTurnedOn = false
        if (!isPrimaryActive() && !isKeepAliveActive()) startTurnOffTimer()
        return true
    }
    return false
}

def getZoneStatus() {
    def isLightOn = false
    
    if (lightType == "Simple On/Off") isLightOn = switches?.any { it.currentValue("switch") == "on" }
    else if (lightType == "Adjustable Bulb / Dimmer") isLightOn = dimmers?.any { it.currentValue("switch") == "on" }
    else if (lightType == "Color / CT Bulb") isLightOn = colorBulbs?.any { it.currentValue("switch") == "on" }
    
    def primaryActive = isPrimaryActive()
    def keepAliveActive = isKeepAliveActive()
    
    def statusText = "Standby"
    if (atomicState.arrivalActive) statusText = "<span style='color: #800080;'>Arrival Override</span>"
    else if (atomicState.manuallyTurnedOn) statusText = "<span style='color: #0055aa;'>Manual Override</span>"
    else if (disableOnSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: red;'>Disabled (ON Block)</span>"
    else if (disableOffSwitches?.any { it.currentValue("switch") == "on" }) statusText = "<span style='color: darkred;'>Disabled (OFF Block)</span>"
    else if (goodNightSwitch && goodNightSwitch.currentValue("switch") == "on") statusText = "<span style='color: darkblue;'>Nap Lock Active</span>"
    else if (contactSensors?.any { it.currentValue("contact") == "open" } && overcastSwitch?.currentValue("switch") != "on") statusText = "<span style='color: orange;'>Blocked (Open Contact)</span>"
    else if (isLightOn && (primaryActive || keepAliveActive)) statusText = "Occupied"
    else if (isLightOn && !primaryActive && !keepAliveActive) statusText = "Counting Down"
    else if (!isLightOn && primaryActive) statusText = "Motion Ignored"
    
    def timerText = "--"
    if (isLightOn && !primaryActive && !keepAliveActive && atomicState.stdTaskTime && atomicState.stdTaskTime > now()) {
        def diff = atomicState.stdTaskTime - now()
        timerText = "${(diff / 60000).toInteger()}m ${((diff % 60000) / 1000).toInteger()}s"
    }
    
    def lightDetails = isLightOn ? "ON" : "OFF"
    
    if (isLightOn) {
        if (lightType == "Adjustable Bulb / Dimmer") {
            def activeDev = dimmers?.find { it.currentValue("switch") == "on" }
            if (activeDev) {
                def lvl = activeDev.currentValue("level")
                if (lvl != null) lightDetails += " <span style='font-size: 11px; color: #666;'><br>(${lvl}%)</span>"
            }
        } else if (lightType == "Color / CT Bulb") {
            def activeDev = colorBulbs?.find { it.currentValue("switch") == "on" }
            if (activeDev) {
                def lvl = activeDev.currentValue("level")
                def ct = activeDev.currentValue("colorTemperature")
                def extras = []
                if (lvl != null) extras << "${lvl}%"
                if (ct != null) extras << "${ct}K"
                if (extras) lightDetails += " <span style='font-size: 11px; color: #666;'><br>(" + extras.join(" @ ") + ")</span>"
            }
        }
    }
    
    return [
        name: app.label ?: "Unnamed Zone",
        light: lightDetails,
        motion: primaryActive ? "ACTIVE" : (keepAliveActive ? "KEEP-ALIVE" : "INACTIVE"),
        status: statusText,
        timer: timerText
    ]
}
