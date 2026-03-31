/**
 * Advanced Energy Management Controller
 */
definition(
    name: "Advanced Energy Management Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "An eye-opening energy dashboard that tracks cost, detects power spikes, and warns of creeping averages indicating hardware failure.",
    category: "Green Living",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Energy Management Core", install: true, uninstall: true) {
        
        section("Live Financial & Health Dashboard") {
            def kwhRate = settings["costPerKwh"]?.toString()?.toDouble() ?: 0.15
            def statusText = "<b>Appliance Status & Analytics</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Appliance</th><th style='padding: 8px;'>Current Power</th><th style='padding: 8px;'>7-Day Cost</th><th style='padding: 8px;'>Health Status</th></tr>"
            
            def appliances = ["refrigerator": "Refrigerator", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
            
            appliances.each { key, name ->
                def pMeter = settings["${key}Power"]
                def eMeter = settings["${key}Energy"]
                
                if (pMeter && eMeter) {
                    def currentPower = pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0
                    def currentEnergy = eMeter.currentValue('energy')?.toString()?.toDouble() ?: 0.0
                    
                    // Cost Math
                    def baselineEnergy = state["${key}_startEnergy"] ?: currentEnergy
                    def usedKwh = currentEnergy - baselineEnergy
                    if (usedKwh < 0) usedKwh = 0 
                    def estCost = usedKwh * kwhRate
                    def costStr = String.format("\$%.2f", estCost)
                    
                    // Health Check & Compressor Status
                    def health = "<span style='color: green;'>GOOD</span>"
                    def struggle = state["${key}_struggleCount"] ?: 0
                    
                    if (state["${key}_creepWarning"]) health = "<span style='color: orange;'>CREEPING WATTS</span>"
                    if (struggle >= 3 && struggle < 6) health = "<span style='color: orange;'>CLEAN COILS</span>"
                    if (struggle >= 6) health = "<span style='color: red;'>STRUGGLING</span>"
                    if (state["${key}_spikeWarning"]) health = "<span style='color: red;'>SPIKE DETECTED</span>"
                    
                    def powerColor = currentPower > 10 ? "blue" : "black"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${name}</b></td><td style='padding: 8px; color: ${powerColor};'>${currentPower} W</td><td style='padding: 8px; color: red;'>${costStr}</td><td style='padding: 8px; font-weight: bold;'>${health}</td></tr>"
                }
            }
            statusText += "</table>"
            
            // ROI Table for Scheduled Savings
            statusText += "<b>Scheduled Savings (ROI)</b><br><table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #eef9f0; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #dcedc8; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Appliance</th><th style='padding: 8px;'>Est. Savings (7-Day)</th></tr>"
            
            def totalSavings = 0.0
            appliances.each { key, name ->
                def savings = state["${key}_roiSavings"] ?: 0.0
                totalSavings += savings
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${name}</b></td><td style='padding: 8px; color: green;'>\$${String.format("%.2f", savings)}</td></tr>"
            }
            statusText += "<tr style='background-color: #c5e1a5;'><td style='padding: 8px;'><b>Total Saved</b></td><td style='padding: 8px; color: green; font-weight: bold;'>\$${String.format("%.2f", totalSavings)}</td></tr>"
            statusText += "</table>"
            
            paragraph statusText
        }

        section("Global Core Settings") {
            input "costPerKwh", "decimal", title: "Electricity Cost (\$ per kWh)", required: true, defaultValue: 0.15, description: "National average is \$0.15."
            input "pushNotification", "capability.notification", title: "Push Notification Device (For Warnings)", required: false, multiple: true
        }
        
        section("Mode-Based Vampire Power Shutdown") {
            input "vampireModes", "mode", title: "Turn off appliances during these modes (e.g., Away, Night)", required: false, multiple: true
            input "vampireRestore", "bool", title: "Automatically turn them back ON when exiting these modes?", defaultValue: true, required: false
            input "vampireAppliance_microwave", "bool", title: "Include Microwave in Mode Shutdown", defaultValue: true
            input "vampireAppliance_dishwasher", "bool", title: "Include Dishwasher in Mode Shutdown", defaultValue: true
            input "vampireAppliance_washerDryer", "bool", title: "Include Washer/Dryer in Mode Shutdown", defaultValue: true
            paragraph "Note: The Refrigerator is permanently excluded from mode-based shutdowns to protect perishables."
        }

        section("Appliance Scheduling & Shutdown") {
            input "scheduleStart", "time", title: "Time to turn appliances OFF", required: false
            input "scheduleEnd", "time", title: "Time to turn appliances ON", required: false
            input "safeShutdownThreshold", "number", title: "Safe Shutdown Power Threshold (Watts)", defaultValue: 15, required: true, description: "Appliance stays on if drawing more than this."
            paragraph "Note: Refrigerator is permanently excluded from schedule shutdowns."
        }
        
        section("Manual Override Button") {
            input "overrideButton", "capability.button", title: "Override Button", required: false, multiple: false
            input "buttonNumber", "number", title: "Button Number", defaultValue: 1, required: false
            input "buttonAction", "enum", title: "Button Action", options: ["pushed", "held", "doubleTapped", "released"], defaultValue: "pushed", required: false
            input "overrideModes", "mode", title: "Only allow override in specific modes?", required: false, multiple: true
        }
        
        section("Refrigerator / Freezer") {
            input "refrigeratorSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "refrigeratorPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "refrigeratorEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "refrigeratorSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 800, required: false
            input "refrigeratorRunWatts", "number", title: "Compressor Running Threshold (Watts)", defaultValue: 80, required: false, description: "Watts required to consider the compressor 'running'."
            input "refrigeratorMaintenanceHours", "number", title: "Maintenance Alert Interval (Total Run Hours)", defaultValue: 2000, required: false
            paragraph "Active Protection: If this switch is ever turned OFF manually or by another app, it will be instantly forced back ON."
        }
        
        section("Washer / Dryer Combo") {
            input "washerDryerSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "washerDryerPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "washerDryerEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "washerDryerSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 3000, required: false
            input "washerDryerRunWatts", "number", title: "Cycle Running Threshold (Watts)", defaultValue: 20, required: false
            input "washerDryerCycleAlert", "bool", title: "Send Push Notification when cycle finishes?", defaultValue: true, required: false
        }
        
        section("Dishwasher") {
            input "dishwasherSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "dishwasherPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "dishwasherEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "dishwasherSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 1800, required: false
            input "dishwasherRunWatts", "number", title: "Cycle Running Threshold (Watts)", defaultValue: 15, required: false
            input "dishwasherCycleAlert", "bool", title: "Send Push Notification when cycle finishes?", defaultValue: true, required: false
        }
        
        section("Microwave") {
            input "microwaveSwitch", "capability.switch", title: "Appliance Switch", required: false
            input "microwavePower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "microwaveEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "microwaveSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 2000, required: false
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    def appliances = ["refrigerator", "washerDryer", "dishwasher", "microwave"]
    
    appliances.each { key ->
        def pMeter = settings["${key}Power"]
        def eMeter = settings["${key}Energy"]
        
        if (!state["${key}_startEnergy"] && eMeter) {
            state["${key}_startEnergy"] = eMeter.currentValue("energy")?.toString()?.toDouble() ?: 0.0
        }
        if (!state["${key}_avgPower"]) state["${key}_avgPower"] = 0.0
        if (!state["${key}_roiSavings"]) state["${key}_roiSavings"] = 0.0
        if (!state["${key}_totalRunHours"]) state["${key}_totalRunHours"] = 0.0
        if (!state["${key}_struggleCount"]) state["${key}_struggleCount"] = 0
        
        if (pMeter) subscribe(pMeter, "power", powerHandler)
    }

    // Always-On Protection for Critical Hardware
    if (settings["refrigeratorSwitch"]) {
        subscribe(settings["refrigeratorSwitch"], "switch", alwaysOnProtectionHandler)
    }
    
    // Scheduling
    if (scheduleStart) schedule(scheduleStart, triggerTurnOff)
    if (scheduleEnd) schedule(scheduleEnd, triggerTurnOn)
    
    // Mode Tracking for Vampire Energy
    subscribe(location, "mode", modeChangeHandler)
    
    // Button Override
    if (overrideButton) subscribe(overrideButton, "button.${buttonAction}", buttonHandler)
    
    schedule("0 5 0 ? * SUN", resetWeeklyCounters)
    schedule("0 0 2 * * ?", dailyHealthCheck) 
}

def alwaysOnProtectionHandler(evt) {
    if (evt.value == "off") {
        log.warn "Protection Triggered: Refrigerator turned off. Forcing ON."
        settings["refrigeratorSwitch"]?.on()
        sendAlert("🚨 CRITICAL PROTECTION: Your Refrigerator switch was turned OFF! The system has automatically forced it back ON to prevent food spoilage.")
    }
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    def isVampireMode = vampireModes?.contains(newMode)
    
    if (isVampireMode) {
        log.info "Entering Vampire Mode Shutdown for mode: ${newMode}"
        executeVampireShutdown()
    } else if (vampireRestore && state.wasVampireShutdown) {
        log.info "Exiting Vampire Mode. Restoring power to appliances."
        executeVampireRestore()
        state.wasVampireShutdown = false
    }
}

def executeVampireShutdown() {
    def safeThreshold = settings["safeShutdownThreshold"]?.toString()?.toDouble() ?: 15.0
    def needsRetry = false
    def anyTurnedOff = false
    
    def targets = [
        [key: "microwave", enabled: settings["vampireAppliance_microwave"]],
        [key: "dishwasher", enabled: settings["vampireAppliance_dishwasher"]],
        [key: "washerDryer", enabled: settings["vampireAppliance_washerDryer"]]
    ]
    
    targets.each { target ->
        if (target.enabled) {
            def key = target.key
            def sw = settings["${key}Switch"]
            def pMeter = settings["${key}Power"]
            
            if (sw) {
                def currentPower = pMeter ? (pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0) : 0.0
                
                if (currentPower > safeThreshold) {
                    log.info "${key} is currently running (${currentPower}W). Delaying Vampire Shutdown."
                    needsRetry = true
                } else {
                    if (sw.currentValue("switch") != "off") {
                        sw.off()
                        state["${key}_offTimeStart"] = now()
                        anyTurnedOff = true
                    }
                }
            }
        }
    }
    
    if (anyTurnedOff || state.wasVampireShutdown) {
        state.wasVampireShutdown = true
    }
    
    // Retry in 5 minutes if someone is doing laundry when the house shifts to Away/Night
    if (needsRetry) {
        runIn(300, executeVampireShutdown)
    }
}

def executeVampireRestore() {
    def targets = [
        [key: "microwave", enabled: settings["vampireAppliance_microwave"]],
        [key: "dishwasher", enabled: settings["vampireAppliance_dishwasher"]],
        [key: "washerDryer", enabled: settings["vampireAppliance_washerDryer"]]
    ]
    def kwhRate = settings["costPerKwh"]?.toString()?.toDouble() ?: 0.15
    
    targets.each { target ->
        if (target.enabled) {
            def key = target.key
            def sw = settings["${key}Switch"]
            
            if (sw) {
                sw.on()
                
                // Track ROI for the mode-based shutdown
                if (state["${key}_offTimeStart"]) {
                    def offTimeMs = now() - state["${key}_offTimeStart"]
                    def offTimeHours = offTimeMs / 3600000.0
                    def baselineAvgPower = state["${key}_baselineAvg"]?.toString()?.toDouble() ?: 0.0
                    
                    def savingsThisCycle = (baselineAvgPower / 1000.0) * offTimeHours * kwhRate
                    state["${key}_roiSavings"] = (state["${key}_roiSavings"] ?: 0.0) + savingsThisCycle
                    state["${key}_offTimeStart"] = null
                }
            }
        }
    }
}

def buttonHandler(evt) {
    if (overrideModes && !overrideModes.contains(location.mode)) return
    
    def btnNumber = evt.jsonData?.buttonNumber ?: 1
    if (btnNumber.toString() != settings.buttonNumber.toString()) return
    
    log.info "Override activated! Turning all appliances ON for 2 hours."
    triggerTurnOn()
    
    runIn(7200, endOverrideAndCheckSchedule)
}

def endOverrideAndCheckSchedule() {
    def currTime = now()
    def start = timeToday(scheduleStart).time
    def end = timeToday(scheduleEnd).time
    
    if (currTime >= start && currTime < end) {
        triggerTurnOff()
    }
}

def triggerTurnOff() {
    // Refrigerator explicitly removed from schedule shutdown targets
    def appliances = ["washerDryer", "dishwasher", "microwave"]
    def safeThreshold = settings["safeShutdownThreshold"]?.toString()?.toDouble() ?: 15.0
    def needsRetry = false
    
    appliances.each { key ->
        def sw = settings["${key}Switch"]
        def pMeter = settings["${key}Power"]
        
        if (sw) {
            def currentPower = pMeter ? (pMeter.currentValue('power')?.toString()?.toDouble() ?: 0.0) : 0.0
            
            if (currentPower > safeThreshold) {
                log.info "${key} is currently running (${currentPower}W). Delaying shutdown."
                needsRetry = true
            } else {
                if (sw.currentValue("switch") != "off") {
                    sw.off()
                    state["${key}_offTimeStart"] = now()
                }
            }
        }
    }
    
    if (needsRetry) {
        runIn(300, triggerTurnOff)
    }
}

def triggerTurnOn() {
    // Refrigerator explicitly removed from schedule restore targets (it should never be off anyway)
    def appliances = ["washerDryer", "dishwasher", "microwave"]
    def kwhRate = settings["costPerKwh"]?.toString()?.toDouble() ?: 0.15
    
    appliances.each { key ->
        def sw = settings["${key}Switch"]
        if (sw) {
            sw.on()
            
            if (state["${key}_offTimeStart"]) {
                def offTimeMs = now() - state["${key}_offTimeStart"]
                def offTimeHours = offTimeMs / 3600000.0
                def baselineAvgPower = state["${key}_baselineAvg"]?.toString()?.toDouble() ?: 0.0
                
                def savingsThisCycle = (baselineAvgPower / 1000.0) * offTimeHours * kwhRate
                state["${key}_roiSavings"] = (state["${key}_roiSavings"] ?: 0.0) + savingsThisCycle
                state["${key}_offTimeStart"] = null
            }
        }
    }
}

def powerHandler(evt) {
    def meterId = evt.device.id
    def currentPower = evt.value.toString().toDouble()
    def appliances = ["refrigerator": "Refrigerator", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
    
    appliances.each { key, name ->
        if (settings["${key}Power"]?.id == meterId) {
            
            // 1. Spike Detection
            def spikeThreshold = settings["${key}Spike"]?.toString()?.toDouble() ?: 5000
            if (currentPower > spikeThreshold) {
                if (!state["${key}_spikeWarning"]) {
                    state["${key}_spikeWarning"] = true
                    sendAlert("⚡ ${name} just spiked to ${currentPower}W! Check for failing components.")
                }
            } else if (currentPower < (spikeThreshold * 0.8)) {
                state["${key}_spikeWarning"] = false 
            }
            
            // 2. State & Cycle Tracking 
            def runThreshold = settings["${key}RunWatts"]?.toString()?.toDouble() ?: 15.0
            def isCurrentlyRunning = (currentPower > runThreshold)
            def wasRunning = state["${key}_isRunning"] ?: false
            
            if (isCurrentlyRunning && !wasRunning) {
                state["${key}_isRunning"] = true
                state["${key}_cycleStartTime"] = now()
            } 
            else if (!isCurrentlyRunning && wasRunning) {
                state["${key}_isRunning"] = false
                
                if (state["${key}_cycleStartTime"]) {
                    def cycleDurationMs = now() - state["${key}_cycleStartTime"]
                    def cycleDurationHours = cycleDurationMs / 3600000.0
                    def cycleDurationMins = cycleDurationMs / 60000.0
                    
                    state["${key}_totalRunHours"] = (state["${key}_totalRunHours"] ?: 0.0) + cycleDurationHours
                    
                    if (settings["${key}CycleAlert"] && cycleDurationMins > 3) {
                        sendAlert("✅ Your ${name} cycle is complete!")
                    }
                    
                    if (key == "refrigerator") {
                        trackCompressorHealth(key, name, cycleDurationMins)
                    }
                }
            }
            
            // 3. Power Averaging
            if (currentPower > 10) {
                def currentAvg = state["${key}_avgPower"]?.toString()?.toDouble() ?: currentPower
                state["${key}_avgPower"] = (currentAvg * 0.95) + (currentPower * 0.05)
            }
        }
    }
}

def trackCompressorHealth(key, name, cycleDurationMins) {
    if (cycleDurationMins < 5) return
    
    def baselineDuration = state["${key}_baselineCycleMins"]?.toString()?.toDouble() ?: cycleDurationMins
    
    if (baselineDuration == 0.0 || baselineDuration == cycleDurationMins) {
        state["${key}_baselineCycleMins"] = cycleDurationMins
    } else {
        if (cycleDurationMins > (baselineDuration * 1.30)) {
            state["${key}_struggleCount"] = (state["${key}_struggleCount"] ?: 0) + 1
            
            if (state["${key}_struggleCount"] == 3) {
                sendAlert("🧹 ${name} Maintenance: Compressor is running 30% longer than normal. Please clean the condenser coils and check airflow to prevent failure.")
            } else if (state["${key}_struggleCount"] >= 7) {
                sendAlert("⚠️ ${name} CRITICAL Warning: Unit is severely struggling to cool. Compressor cycles are continuously extended. Hardware failure may be imminent.")
                state["${key}_struggleCount"] = 0 
            }
        } else {
            state["${key}_baselineCycleMins"] = (baselineDuration * 0.98) + (cycleDurationMins * 0.02)
            if (state["${key}_struggleCount"] > 0) {
                state["${key}_struggleCount"] = state["${key}_struggleCount"] - 1
            }
        }
    }
}

def dailyHealthCheck() {
    def appliances = ["refrigerator": "Refrigerator", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
    
    appliances.each { key, name ->
        def avgPower = state["${key}_avgPower"]?.toString()?.toDouble() ?: 0.0
        def baselineAvg = state["${key}_baselineAvg"]?.toString()?.toDouble() ?: avgPower
        
        if (baselineAvg == 0.0) {
            state["${key}_baselineAvg"] = avgPower
        } else {
            if (avgPower > (baselineAvg * 1.20)) {
                state["${key}_creepWarning"] = true
                sendAlert("⚠️ ${name} average power is creeping up (${Math.round(avgPower)}W vs baseline ${Math.round(baselineAvg)}W). A motor/vent check is recommended.")
            } else {
                state["${key}_creepWarning"] = false
                state["${key}_baselineAvg"] = (baselineAvg * 0.98) + (avgPower * 0.02)
            }
        }
        
        if (key == "refrigerator") {
            def totalHours = state["${key}_totalRunHours"]?.toString()?.toDouble() ?: 0.0
            def maintenanceInterval = settings["${key}MaintenanceHours"]?.toString()?.toDouble() ?: 2000.0
            
            if (totalHours > maintenanceInterval) {
                sendAlert("🔧 Routine Maintenance: Your ${name} has reached ${Math.round(totalHours)} run hours. Consider scheduling a preventative maintenance check.")
                settings["${key}MaintenanceHours"] = maintenanceInterval + 2000.0 
            }
        }
    }
}

def resetWeeklyCounters() {
    def appliances = ["refrigerator", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        def eMeter = settings["${key}Energy"]
        if (eMeter) {
            state["${key}_startEnergy"] = eMeter.currentValue("energy")?.toString()?.toDouble() ?: 0.0
        }
        state["${key}_roiSavings"] = 0.0
    }
}

def sendAlert(msg) {
    if (settings["pushNotification"]) {
        settings["pushNotification"].deviceNotification(msg)
    }
}
