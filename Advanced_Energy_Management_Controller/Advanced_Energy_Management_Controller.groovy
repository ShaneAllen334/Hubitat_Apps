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
                    if (usedKwh < 0) usedKwh = 0 // Handle meter resets
                    def estCost = usedKwh * kwhRate
                    def costStr = String.format("\$%.2f", estCost)
                    
                    // Health Check
                    def health = "<span style='color: green;'>GOOD</span>"
                    if (state["${key}_creepWarning"]) health = "<span style='color: orange;'>CREEPING UP</span>"
                    if (state["${key}_spikeWarning"]) health = "<span style='color: red;'>SPIKE DETECTED</span>"
                    
                    def powerColor = currentPower > 10 ? "blue" : "black"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${name}</b></td><td style='padding: 8px; color: ${powerColor};'>${currentPower} W</td><td style='padding: 8px; color: red;'>${costStr}</td><td style='padding: 8px; font-weight: bold;'>${health}</td></tr>"
                }
            }
            statusText += "</table>"
            paragraph statusText
        }

        section("Global Core Settings") {
            input "costPerKwh", "decimal", title: "Electricity Cost (\$ per kWh)", required: true, defaultValue: 0.15, description: "National average is \$0.15."
            input "pushNotification", "capability.notification", title: "Push Notification Device (For Warnings)", required: false, multiple: true
        }
        
        section("Refrigerator / Freezer") {
            input "refrigeratorPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "refrigeratorEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "refrigeratorSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 800, required: false
        }
        
        section("Washer / Dryer Combo") {
            input "washerDryerPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "washerDryerEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "washerDryerSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 3000, required: false
        }
        
        section("Dishwasher") {
            input "dishwasherPower", "capability.powerMeter", title: "Power Meter (Watts)", required: false
            input "dishwasherEnergy", "capability.energyMeter", title: "Energy Meter (kWh)", required: false
            input "dishwasherSpike", "number", title: "Spike Warning Threshold (Watts)", defaultValue: 1800, required: false
        }
        
        section("Microwave") {
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
        
        // Initialize Baseline State if missing
        if (!state["${key}_startEnergy"] && eMeter) {
            state["${key}_startEnergy"] = eMeter.currentValue("energy")?.toString()?.toDouble() ?: 0.0
        }
        if (!state["${key}_avgPower"]) state["${key}_avgPower"] = 0.0
        
        if (pMeter) subscribe(pMeter, "power", powerHandler)
    }
    
    // Run nightly health analysis and cost reset logic
    schedule("0 5 0 ? * SUN", resetWeeklyCounters)
    schedule("0 0 2 * * ?", dailyHealthCheck) 
}

def powerHandler(evt) {
    def meterId = evt.device.id
    def currentPower = evt.value.toString().toDouble()
    
    def appliances = ["refrigerator": "Refrigerator", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
    
    appliances.each { key, name ->
        if (settings["${key}Power"]?.id == meterId) {
            
            // 1. Check for Spikes
            def spikeThreshold = settings["${key}Spike"]?.toString()?.toDouble() ?: 5000
            if (currentPower > spikeThreshold) {
                if (!state["${key}_spikeWarning"]) {
                    state["${key}_spikeWarning"] = true
                    sendAlert("⚡ ${name} just spiked to ${currentPower}W! Check for failing components.")
                }
            } else if (currentPower < (spikeThreshold * 0.8)) {
                // Reset spike warning once it drops well below threshold
                state["${key}_spikeWarning"] = false 
            }
            
            // 2. Track Running Average (Simple Exponential Moving Average for Sandboxes)
            // Only calculate average when device is actually running (e.g., drawing > 10W)
            if (currentPower > 10) {
                def currentAvg = state["${key}_avgPower"]?.toString()?.toDouble() ?: currentPower
                state["${key}_avgPower"] = (currentAvg * 0.95) + (currentPower * 0.05)
            }
        }
    }
}

def dailyHealthCheck() {
    // This runs every night at 2:00 AM to check if the running average is creeping too high
    def appliances = ["refrigerator": "Refrigerator", "washerDryer": "Washer/Dryer", "dishwasher": "Dishwasher", "microwave": "Microwave"]
    
    appliances.each { key, name ->
        def avgPower = state["${key}_avgPower"]?.toString()?.toDouble() ?: 0.0
        def baselineAvg = state["${key}_baselineAvg"]?.toString()?.toDouble() ?: avgPower
        
        // Set baseline if it's the first few days
        if (baselineAvg == 0.0) {
            state["${key}_baselineAvg"] = avgPower
        } else {
            // If the current average is 20% higher than the historical baseline, flag it!
            if (avgPower > (baselineAvg * 1.20)) {
                state["${key}_creepWarning"] = true
                sendAlert("⚠️ ${name} average power is creeping up (${Math.round(avgPower)}W vs baseline ${Math.round(baselineAvg)}W). A compressor/vent check is recommended.")
            } else {
                state["${key}_creepWarning"] = false
                // Slowly update baseline to account for natural aging, but very slowly
                state["${key}_baselineAvg"] = (baselineAvg * 0.98) + (avgPower * 0.02)
            }
        }
    }
}

def resetWeeklyCounters() {
    // Resets the cost counters every Sunday morning
    def appliances = ["refrigerator", "washerDryer", "dishwasher", "microwave"]
    appliances.each { key ->
        def eMeter = settings["${key}Energy"]
        if (eMeter) {
            state["${key}_startEnergy"] = eMeter.currentValue("energy")?.toString()?.toDouble() ?: 0.0
        }
    }
}

def sendAlert(msg) {
    if (settings["pushNotification"]) {
        settings["pushNotification"].deviceNotification(msg)
    }
}
