/**
 * Advanced Sleep Metrics Dashboard Device
 *
 * Author: ShaneAllen
 */
metadata {
    definition(
        name: "ASM Dashboard Device", 
        namespace: "ShaneAllen", 
        author: "ShaneAllen",
        description: "Child device designed to catch and render BMS-grade HTML sleep telemetry."
    ) {
        capability "Sensor"
        
        attribute "status", "string"
        attribute "sleepScore", "number"
        attribute "html", "string"
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

def installed() {
    if (logEnable) log.debug "${device.label ?: device.name} Installed..."
    
    // Set default baseline values so the dashboard tile isn't blank while waiting for the first app cycle
    sendEvent(name: "status", value: "INITIALIZING")
    sendEvent(name: "sleepScore", value: 0)
    sendEvent(name: "html", value: "<div style='background:#111;color:#888;padding:12px;border-radius:8px;text-align:center;font-family:sans-serif;font-size:12px;border:1px solid #333;'>Awaiting Initial Telemetry...</div>")
}

def updated() {
    if (logEnable) log.debug "${device.label ?: device.name} Updated..."
}

def parse(String description) {
    // Virtual devices managed directly by a parent app do not require parsing logic
    if (logEnable) log.debug "Parse called with: ${description}"
}
