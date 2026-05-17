/**
 * Advanced Device Health Monitor
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Device Health Monitor",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Monitors device status, signal quality, battery health, and inactivity across all sensor categories with an Auto-Healing Web Portal.",
    category: "Maintenance",
    iconUrl: "",
    iconX2Url: ""
)

import groovy.transform.Field
@Field static String PREV_STYLE = "margin-top: 15px; padding: 10px; background-color: #e9ecef; border-left: 4px solid #0b3b60; border-radius: 4px; font-size: 13px; line-height: 1.4;"

preferences {
    page(name: "mainPage")
    page(name: "pageSettings")
    page(name: "pageThresholds")
    page(name: "pageDeviceDetails")
}

mappings {
    path("/dashboard") { action: [GET: "serveDashboardPage"] }
    path("/refresh") { action: [GET: "forceRefreshEndpoint"] }
    path("/ping") { action: [GET: "pingDeviceEndpoint"] }
}

def mainPage() {
    ensureStateMaps()
    
    if (!state.accessToken) {
        try { createAccessToken() } catch (Exception e) { log.error "OAuth is not enabled! Please click 'OAuth' at the top of the app code and enable it." }
    }
    
    dynamicPage(name: "mainPage", title: "Device Health Command Center", install: true, uninstall: true) {
        
        section("🌐 Web Health Portal", hideable: false, hidden: false) {
            paragraph "<i>Access your beautifully formatted, live device health dashboard from any browser.</i>"
            if (state.accessToken) {
                def cloudUrl = getFullApiServerUrl()
                def localUrl = getFullLocalApiServerUrl()
                paragraph "<div style='padding:10px; background-color:#d1ecf1; border:1px solid #bee5eb; color:#0c5460; border-radius:4px;'><b>Cloud Dashboard URL (Use anywhere):</b><br><a href='${cloudUrl}/dashboard?access_token=${state.accessToken}' target='_blank' style='color:#0c5460; font-weight:bold; word-wrap:break-word;'>${cloudUrl}/dashboard?access_token=${state.accessToken}</a><br><br><b>Local Dashboard URL (Use at home):</b><br><a href='${localUrl}/dashboard?access_token=${state.accessToken}' target='_blank' style='color:#0c5460; font-weight:bold; word-wrap:break-word;'>${localUrl}/dashboard?access_token=${state.accessToken}</a></div>"
            } else {
                paragraph "<div style='padding:10px; background-color:#f8d7da; border:1px solid #f5c6cb; color:#721c24; border-radius:4px;'><b>OAuth Required:</b> Please enable OAuth in the App Code screen to generate your portal URLs.</div>"
            }
        }
        
        section("Live Health Overview", hideable: false, hidden: false) {
            input "btnRefresh", "button", title: "🔄 Refresh Data Dashboard"
            input "btnRunCheck", "button", title: "🩺 Force Health Scan Now"
            
            def isMasterOff = settings.masterSwitch?.currentValue("switch") == "off"
            def systemState = isMasterOff ? "<span style='color: #c0392b; font-weight: bold;'>PAUSED (Master Switch OFF)</span>" : "<span style='color: #27ae60; font-weight: bold;'>RUNNING AND ACTIVE</span>"
            
            def critCount = state.dashboardData?.count { it.status == "Red" } ?: 0
            def warnCount = state.dashboardData?.count { it.status == "Yellow" } ?: 0
            
            def issueState = ""
            if (critCount > 0) issueState = "<span style='color: #e74c3c; font-weight: bold;'>${critCount} Critical Issues</span>"
            else if (warnCount > 0) issueState = "<span style='color: #f1c40f; font-weight: bold;'>${warnCount} Warnings</span>"
            else issueState = "<span style='color: #27ae60; font-weight: bold;'>All Systems Nominal</span>"
            
            def lastCheckStr = state.lastCheckTime ?: "Never"
            
            def statusText = "<div style='margin-bottom: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            statusText += "<b>System State:</b> ${systemState}<br><b>Network Health:</b> ${issueState}<br><b>Last Scan:</b> ${lastCheckStr}</div>"
            
            def issues = state.dashboardData?.findAll { it.status == "Red" || it.status == "Yellow" }
            if (issues && issues.size() > 0) {
                statusText += "<h4 style='margin-bottom: 5px; margin-top: 15px; color: #333; font-family: sans-serif;'>Active Device Alerts</h4>"
                statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Device</th><th style='padding: 8px;'>Status</th><th style='padding: 8px;'>Details</th></tr>"
                
                issues.each { issue ->
                    def typeColor = issue.status == "Red" ? "#e74c3c" : "#f1c40f"
                    def typeLabel = issue.status == "Red" ? "Critical" : "Warning"
                    def actStr = issue.lastActive ?: "Unknown"
                    def battStr = issue.battChanged ?: ""
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${issue.name}</b><br><span style='font-size:10px; color:#888;'>Last Active: ${actStr}${battStr}</span></td><td style='padding: 8px; color: ${typeColor}; font-weight: bold;'>${typeLabel}</td><td style='padding: 8px;'>${issue.messages.join('<br>')}</td></tr>"
                }
                statusText += "</table>"
            } else {
                statusText += "<h4 style='margin-bottom: 5px; margin-top: 15px; color: #333; font-family: sans-serif;'>Active Device Alerts</h4>"
                statusText += "<div style='padding: 10px; background: #fcfcfc; border: 1px solid #ccc; text-align: center; font-style: italic; color: #888;'>No issues currently detected.</div>"
            }
            
            paragraph statusText
        }
        
        section("Dashboard Integrations", hideable: false, hidden: false) {
            def childExists = getChildDevice("health_monitor_child") != null
            if (!childExists) {
                paragraph "<i>Create a virtual device to display the HTML health dashboard directly on your Hubitat web dashboards.</i>"
                input "btnCreateChild", "button", title: "➕ Create Dashboard Child Device"
            } else {
                paragraph "<div style='${PREV_STYLE}'><b>Child Device Active:</b><br><i>Advanced Device Health Monitor Information</i> is installed and receiving HTML payloads.</div>"
            }
        }

        section("System Event History", hideable: true, hidden: true) {
            paragraph "<i>A running log of the last 30 health scans and alerts.</i>"
            if (state.historyLog && state.historyLog.size() > 0) {
                def histHtml = "<div style='max-height: 250px; overflow-y: auto; background-color: #f4f4f4; border: 1px solid #ccc; padding: 10px; font-family: monospace; font-size: 12px; line-height: 1.4;'>"
                state.historyLog.each { logEntry -> histHtml += "<div style='margin-bottom: 6px; border-bottom: 1px dashed #ddd; padding-bottom: 6px;'>${logEntry}</div>" }
                histHtml += "</div>"
                paragraph histHtml
            }
        }
        
        section("Configuration Menus", hideable: false, hidden: false) {
            href(name: "hrefSettings", page: "pageSettings", title: "⚙️ Core Setup & Devices", description: "Select devices to monitor and set schedules")
            href(name: "hrefDeviceDetails", page: "pageDeviceDetails", title: "📍 Locations, Descriptions, & Folders", description: "Bulk assign locations, battery types, and override folders")
            href(name: "hrefThresholds", page: "pageThresholds", title: "📊 Monitoring Thresholds", description: "Configure battery, inactivity, and signal limits")
        }
    }
}

def pageSettings() {
    dynamicPage(name: "pageSettings", title: "⚙️ Core Setup & Devices", install: false, uninstall: false) {
        section("Global Controls") {
            input "masterSwitch", "capability.switch", title: "Master Enable/Pause Switch", required: false
            input "scanInterval", "enum", title: "Automated Scan Interval", options: ["1 Hour", "3 Hours", "6 Hours", "12 Hours", "24 Hours"], defaultValue: "12 Hours", required: true
            input "enableLiveUpdates", "bool", title: "Enable Live Auto-Updating?", defaultValue: true, submitOnChange: true
            paragraph "<i>Note: Live Auto-Updating will detect if a sensor wakes up and instantly update the dashboard. The Auto-Heal feature runs automatically every 12 hours.</i>"
        }
        
        section("Primary Infrastructure") {
            input "batteryDevices", "capability.battery", title: "Battery-Powered Infrastructure", multiple: true, required: false
            input "actuatorDevices", "capability.actuator", title: "Mains-Powered Actuators (Switches, Dimmer, Locks, Outlets)", multiple: true, required: false
        }

        section("Environmental & Security Sensors") {
            input "smokeDetectors", "capability.smokeDetector", title: "Smoke / Carbon Monoxide Detectors", multiple: true, required: false
            input "waterSensors", "capability.waterSensor", title: "Water / Leak Sensors", multiple: true, required: false
            input "motionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
            input "contactSensors", "capability.contactSensor", title: "Contact Sensors (Doors, Windows, Gates)", multiple: true, required: false
            input "accelerationSensors", "capability.accelerationSensor", title: "Acceleration/Vibration Sensors", multiple: true, required: false
            input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors (Fobs, Mobile Apps)", multiple: true, required: false
        }

        section("Utility & Measurement Sensors") {
            input "temperatureSensors", "capability.temperatureMeasurement", title: "Temperature Sensors", multiple: true, required: false
            input "humiditySensors", "capability.relativeHumidityMeasurement", title: "Humidity Sensors", multiple: true, required: false
            input "illuminanceSensors", "capability.illuminanceMeasurement", title: "Illuminance / Light Sensors", multiple: true, required: false
            input "powerSensors", "capability.powerMeter", title: "Power Meters / Energy Monitors", multiple: true, required: false
            input "valveDevices", "capability.valve", title: "Main Water / Gas Valves", multiple: true, required: false
            input "genericSensors", "capability.sensor", title: "All Other / Generic Device Inputs", multiple: true, required: false
        }
    }
}

def pageDeviceDetails() {
    dynamicPage(name: "pageDeviceDetails", title: "📍 Locations, Descriptions & Folders", install: false, uninstall: false) {
        
        def defaultRooms = "Living Room, Kitchen, Master Bedroom, Garage, Front Yard, Backyard, Hallway, Bathroom"
        def roomListStr = settings.roomList ?: defaultRooms
        def roomOptions = roomListStr.split(',').collect{it.trim()}.findAll{it != ""}
        
        def folderOptions = ["Safety & Alarms", "Switches & Actuators", "Motion Sensors", "Contact Sensors", "Presence Sensors", "Climate Sensors", "Power Meters", "Light Sensors", "Vibration Sensors", "Other Sensors", "General Battery Devices"]
        def battTypes = ["CR2032", "CR2450", "CR2", "CR123A", "AA", "AAA", "AAAA", "9V", "18650", "Custom / Proprietary Pack", "Rechargeable Internal"]

        section("📍 Manage Location Dropdowns") {
            paragraph "<i>Define your standard home locations here (comma separated). This list populates the dropdown menus below.</i>"
            input "roomList", "text", title: "Available Locations", defaultValue: defaultRooms, submitOnChange: true
        }

        def allDevs = getAllMonitoredDevices().sort { it.displayName }
        
        if (allDevs && allDevs.size() > 0) {
            section("⚡ Bulk Apply Location") {
                paragraph "<i>Select a location and multiple devices to quickly apply the same location to all of them at once.</i>"
                
                def devOptions = [:]
                allDevs.each { devOptions[it.id] = it.displayName }
                
                input "bulkLoc", "enum", title: "1. Select Location", options: roomOptions, required: false
                input "bulkDevs", "enum", title: "2. Select Devices", options: devOptions, multiple: true, required: false
                input "btnBulkApplyLoc", "button", title: "✅ Apply to Selected Devices"
            }
            
            def groupedDevs = [:]
            allDevs.each { dev ->
                def loc = settings["loc_${dev.id}"] ?: "Unassigned / Other"
                if (!groupedDevs[loc]) groupedDevs[loc] = []
                groupedDevs[loc] << dev
            }
            
            def sortedLocs = groupedDevs.keySet().sort()
            if (sortedLocs.contains("Unassigned / Other")) {
                sortedLocs.remove("Unassigned / Other")
                sortedLocs.add(0, "Unassigned / Other")
            }

            sortedLocs.each { loc ->
                section("📁 ${loc} (${groupedDevs[loc].size()} Devices)", hideable: true, hidden: true) {
                    groupedDevs[loc].each { dev ->
                        input "folder_${dev.id}", "enum", title: "${dev.displayName} - Dashboard Folder(s)", options: folderOptions, multiple: true, required: false, description: "Leave blank for Auto-placement"
                        input "loc_${dev.id}", "enum", title: "${dev.displayName} - Location", options: roomOptions, required: false
                        input "desc_${dev.id}", "text", title: "${dev.displayName} - Description", required: false
                        input "battType_${dev.id}", "enum", title: "${dev.displayName} - Battery Type", options: battTypes, required: false
                        paragraph "<hr style='background-color:#ccc; height:1px; border:0; margin:10px 0;'/>"
                    }
                }
            }
            
        } else {
            section("No Devices Found") {
                paragraph "Please select devices in the Core Setup page first before configuring locations."
            }
        }
    }
}

def pageThresholds() {
    dynamicPage(name: "pageThresholds", title: "📊 Monitoring Thresholds", install: false, uninstall: false) {
        section("Battery Health") {
            input "enableBatteryCheck", "bool", title: "Enable Battery Monitoring?", defaultValue: true, submitOnChange: true
            if (enableBatteryCheck) {
                input "batteryThreshold", "number", title: "Critical Battery Threshold (%)", defaultValue: 20, range: "1..100", required: true
                paragraph "<i>Note: Batteries within 15% above this threshold will trigger a Yellow Warning.</i>"
            }
        }
        
        section("Device Inactivity (Dead Devices)") {
            input "enableInactivityCheck", "bool", title: "Enable Inactivity Monitoring?", defaultValue: true, submitOnChange: true
            if (enableInactivityCheck) {
                paragraph "<i>Flags devices that have not reported any activity or status updates in the specified timeframe.</i>"
                input "inactivityThreshold", "number", title: "Critical Inactivity Threshold (Hours)", defaultValue: 24, required: true
                input "ignoredInactivityDevices", "capability.*", title: "Ignore Inactivity for these Devices", multiple: true, required: false
                paragraph "<i>Note: Devices inactive for 75% of this threshold will trigger a Yellow Warning.</i>"
            }
        }
        
        section("Signal Quality (Zigbee/Z-Wave)") {
            input "enableSignalCheck", "bool", title: "Enable Signal Monitoring?", defaultValue: false, submitOnChange: true
            if (enableSignalCheck) {
                paragraph "<i>Checks devices that report 'rssi' or 'lqi' attributes.</i>"
                input "rssiThreshold", "number", title: "Critical Minimum RSSI Threshold (e.g. -85)", defaultValue: -85, range: "-120..0", required: true
                paragraph "<i>Note: Signals within 10 dBm above this threshold will trigger a Yellow Warning.</i>"
            }
        }
    }
}

def installed() {
    log.info "Advanced Device Health Monitor Installed."
    initialize()
}

def updated() {
    log.info "Advanced Device Health Monitor Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def ensureStateMaps() {
    if (state.historyLog == null) state.historyLog = []
    if (state.dashboardData == null) state.dashboardData = []
    if (state.lastCheckTime == null) state.lastCheckTime = "Never"
    
    if (state.lastBatteryLevels == null) state.lastBatteryLevels = [:]
    if (state.batteryChangeDates == null) state.batteryChangeDates = [:]
}

def initialize() {
    ensureStateMaps()
    
    def sInt = settings.scanInterval ?: "12 Hours"
    if (sInt == "1 Hour") runEvery1Hour("runHealthCheck")
    else if (sInt == "3 Hours") runEvery3Hours("runHealthCheck")
    else if (sInt == "12 Hours") schedule("0 0 0/12 * * ?", "runHealthCheck")
    else if (sInt == "24 Hours") schedule("0 0 0 * * ?", "runHealthCheck")
    else schedule("0 0 0/6 * * ?", "runHealthCheck")
    
    schedule("0 0 0/12 * * ?", "autoHealNetwork")
    
    if (settings.enableLiveUpdates != false) {
        if (settings.batteryDevices) subscribe(settings.batteryDevices, "battery", liveUpdateHandler)
        if (settings.motionSensors) subscribe(settings.motionSensors, "motion", liveUpdateHandler)
        if (settings.contactSensors) subscribe(settings.contactSensors, "contact", liveUpdateHandler)
        if (settings.actuatorDevices) subscribe(settings.actuatorDevices, "switch", liveUpdateHandler)
        if (settings.temperatureSensors) subscribe(settings.temperatureSensors, "temperature", liveUpdateHandler)
        if (settings.humiditySensors) subscribe(settings.humiditySensors, "humidity", liveUpdateHandler)
        
        addToHistory("SYSTEM: Live Auto-Updating is active. The dashboard will heal automatically on device activity.")
    }
    
    addToHistory("SYSTEM: Initialization complete. Monitoring schedule set to ${sInt}.")
    runIn(2, "runHealthCheck")
}

def liveUpdateHandler(evt) {
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") return
    runIn(15, "runHealthCheck", [overwrite: true])
}

def appButtonHandler(btn) {
    ensureStateMaps()
    if (btn != "btnRefresh") {
        addToHistory("SYSTEM: Action triggered via UI Dashboard - " + btn.toString())
    }
    
    if (btn == "btnCreateChild") {
        createChildDevice()
    } else if (btn == "btnRunCheck") {
        runHealthCheck()
    } else if (btn == "btnBulkApplyLoc") {
        if (settings.bulkLoc && settings.bulkDevs) {
            def devIds = settings.bulkDevs instanceof List ? settings.bulkDevs : [settings.bulkDevs]
            devIds.each { dId ->
                app.updateSetting("loc_${dId}", [type: "string", value: settings.bulkLoc])
            }
            app.removeSetting("bulkDevs") 
            addToHistory("SYSTEM: Bulk applied location '${settings.bulkLoc}' to ${devIds.size()} devices.")
        }
    }
}

def createChildDevice() {
    def child = getChildDevice("health_monitor_child")
    if (!child) {
        try {
            addChildDevice("ShaneAllen", "Advanced Device Health Monitor Information", "health_monitor_child", null, [name: "Advanced Device Health Monitor Information", label: "Device Health Dashboard"])
            addToHistory("SYSTEM: Child HTML device successfully created.")
        } catch (e) {
            log.error "Failed to create child device. Ensure the driver is installed. Error: ${e}"
            addToHistory("ERROR: Failed to create child device. Is the driver installed?")
        }
    } else {
        addToHistory("SYSTEM: Child device already exists.")
    }
}

def getAllMonitoredDevices() {
    def allDevices = []
    if (settings.batteryDevices) allDevices.addAll(settings.batteryDevices)
    if (settings.actuatorDevices) allDevices.addAll(settings.actuatorDevices)
    if (settings.motionSensors) allDevices.addAll(settings.motionSensors)
    if (settings.contactSensors) allDevices.addAll(settings.contactSensors)
    if (settings.accelerationSensors) allDevices.addAll(settings.accelerationSensors)
    if (settings.presenceSensors) allDevices.addAll(settings.presenceSensors)
    if (settings.smokeDetectors) allDevices.addAll(settings.smokeDetectors)
    if (settings.waterSensors) allDevices.addAll(settings.waterSensors)
    if (settings.temperatureSensors) allDevices.addAll(settings.temperatureSensors)
    if (settings.humiditySensors) allDevices.addAll(settings.humiditySensors)
    if (settings.illuminanceSensors) allDevices.addAll(settings.illuminanceSensors)
    if (settings.powerSensors) allDevices.addAll(settings.powerSensors)
    if (settings.valveDevices) allDevices.addAll(settings.valveDevices)
    if (settings.genericSensors) allDevices.addAll(settings.genericSensors)
    
    return allDevices.flatten().findAll { it != null }.unique { it.id }
}

def autoHealNetwork() {
    ensureStateMaps()
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") return
    
    def allDevs = getAllMonitoredDevices()
    def healedCount = 0
    def problemDevIds = state.dashboardData?.findAll { it.status != "Green" }?.collect { it.id } ?: []
    
    allDevs.each { dev ->
        if (problemDevIds.contains(dev.id)) {
            if (dev.hasCommand("refresh")) { 
                try { dev.refresh(); healedCount++ } catch(e){} 
            } else if (dev.hasCommand("ping")) { 
                try { dev.ping(); healedCount++ } catch(e){} 
            }
        }
    }
    
    if (healedCount > 0) {
        addToHistory("AUTO-HEAL: Scheduled self-heal executed. Commands sent to ${healedCount} problematic devices.")
        runIn(30, "runHealthCheck", [overwrite: true])
    }
}

def runHealthCheck() {
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") {
        addToHistory("SCAN: Aborted. Master switch is OFF.")
        return
    }
    
    ensureStateMaps()
    def results = []
    def deviceMap = [:]
    
    def addDev = { list, category ->
        list?.each { d ->
            if (d != null) {
                if (!deviceMap[d.id]) {
                    deviceMap[d.id] = [device: d, categories: []]
                }
                if (!deviceMap[d.id].categories.contains(category)) {
                    deviceMap[d.id].categories << category
                }
            }
        }
    }

    addDev(settings.smokeDetectors, "Safety & Alarms")
    addDev(settings.waterSensors, "Safety & Alarms")
    addDev(settings.valveDevices, "Safety & Alarms")
    addDev(settings.actuatorDevices, "Switches & Actuators")
    addDev(settings.motionSensors, "Motion Sensors")
    addDev(settings.contactSensors, "Contact Sensors")
    addDev(settings.presenceSensors, "Presence Sensors")
    addDev(settings.temperatureSensors, "Climate Sensors")
    addDev(settings.humiditySensors, "Climate Sensors")
    addDev(settings.powerSensors, "Power Meters")
    addDev(settings.illuminanceSensors, "Light Sensors")
    addDev(settings.accelerationSensors, "Vibration Sensors")
    addDev(settings.genericSensors, "Other Sensors")
    addDev(settings.batteryDevices, "General Battery Devices")
    
    def nowMs = new Date().time
    def ignoreListIds = settings.ignoredInactivityDevices?.collect { it.id } ?: []
    
    deviceMap.each { devId, data ->
        def dev = data.device
        
        def finalCategories = []
        def overrideFolders = settings["folder_${dev.id}"]
        if (overrideFolders) {
            finalCategories = overrideFolders instanceof List ? overrideFolders : [overrideFolders]
        } else {
            finalCategories = data.categories
        }
        
        try {
            def devHealth = "Green"
            def msgs = []
            def battChangedStr = ""
            def estDaysLeftStr = "Calculating..."
            def rawBattVal = null
            
            def lastActiveDate = dev.getLastActivity()
            def lastActiveStr = lastActiveDate ? lastActiveDate.format("MM/dd/yy h:mm a", location.timeZone) : "Unknown"
            
            if (settings.enableBatteryCheck && dev.hasAttribute("battery")) {
                def batt = dev.currentValue("battery")
                if (batt != null && batt.toString().isNumber()) {
                    def bVal = batt.toInteger()
                    rawBattVal = bVal
                    
                    def lastB = state.lastBatteryLevels[dev.id]
                    if (lastB != null && bVal > (lastB + 5)) {
                        state.batteryChangeDates[dev.id] = nowMs
                    }
                    state.lastBatteryLevels[dev.id] = bVal
                    
                    if (state.batteryChangeDates[dev.id]) {
                        battChangedStr = " | Batt Replaced: " + new Date(state.batteryChangeDates[dev.id]).format("MM/dd/yy", location.timeZone)
                        
                        long changedMs = state.batteryChangeDates[dev.id]
                        long elapsedMs = nowMs - changedMs
                        long daysElapsed = elapsedMs / 86400000
                        
                        if (daysElapsed > 1 && bVal < 100) {
                            BigDecimal dropRate = (100.0 - bVal) / daysElapsed
                            if (dropRate > 0) {
                                int daysLeft = (bVal / dropRate).toInteger()
                                estDaysLeftStr = "~${daysLeft} Days"
                            }
                        }
                    }

                    def thresh = settings.batteryThreshold ?: 20
                    if (bVal <= thresh) {
                        devHealth = "Red"
                        msgs << "Battery Critical"
                    } else if (bVal <= thresh + 15) {
                        if (devHealth != "Red") devHealth = "Yellow"
                        msgs << "Battery Low"
                    } else {
                        msgs << "Battery OK"
                    }
                }
            }
            
            if (settings.enableInactivityCheck && !ignoreListIds.contains(dev.id)) {
                if (lastActiveDate) {
                    def diffHours = (nowMs - lastActiveDate.time) / 3600000
                    def thresh = settings.inactivityThreshold ?: 24
                    if (diffHours > thresh) {
                        devHealth = "Red"
                        msgs << "Offline (${diffHours.toInteger()} hrs)"
                    } else if (diffHours > (thresh * 0.75)) {
                        if (devHealth != "Red") devHealth = "Yellow"
                        msgs << "Inactive (${diffHours.toInteger()} hrs)"
                    } else {
                        msgs << "Active"
                    }
                } else {
                    devHealth = "Red"
                    msgs << "No Activity Data Found"
                }
            }
            
            if (settings.enableSignalCheck && dev.hasAttribute("rssi")) {
                def rssi = dev.currentValue("rssi")
                if (rssi != null && rssi.toString().isNumber()) {
                    def rVal = rssi.toInteger()
                    def thresh = settings.rssiThreshold ?: -85
                    if (rVal <= thresh) {
                        devHealth = "Red"
                        msgs << "Weak Signal (${rVal} dBm)"
                    } else if (rVal <= thresh + 10) {
                        if (devHealth != "Red") devHealth = "Yellow"
                        msgs << "Fair Signal (${rVal} dBm)"
                    } else {
                        msgs << "Signal OK (${rVal} dBm)"
                    }
                }
            }
            
            if (msgs.size() == 0) msgs << "Monitoring Active"
            def canPingDev = dev.hasCommand("refresh") || dev.hasCommand("ping")

            def devLoc = settings["loc_${dev.id}"] ?: ""
            def devDesc = settings["desc_${dev.id}"] ?: ""
            def batType = settings["battType_${dev.id}"] ?: ""

            results << [
                id: dev.id,
                name: dev.displayName,
                status: devHealth,
                messages: msgs,
                categories: finalCategories,
                canPing: canPingDev,
                lastActive: lastActiveStr,
                battChanged: battChangedStr,
                customLoc: devLoc,
                customDesc: devDesc,
                battVal: rawBattVal,
                estDaysLeft: estDaysLeftStr,
                battType: batType
            ]
            
        } catch (e) {
            log.warn "Health Monitor: Error scanning device ${dev.displayName} - ${e}"
        }
    }
    
    results = results.sort { a, b -> 
        def order = ["Red": 1, "Yellow": 2, "Green": 3]
        def c1 = order[a.status] <=> order[b.status]
        if (c1 != 0) return c1
        return a.name <=> b.name
    }
    
    state.dashboardData = results
    state.lastCheckTime = new Date().format("MM/dd/yyyy h:mm a", location.timeZone)
    
    def critCount = results.count { it.status == "Red" }
    def warnCount = results.count { it.status == "Yellow" }
    
    updateChildHtmlDashboard(results, critCount, warnCount)
}

def updateChildHtmlDashboard(results, critCount, warnCount) {
    def child = getChildDevice("health_monitor_child")
    if (!child) return
    
    StringBuilder html = new StringBuilder()
    html.append("<div style='font-family:sans-serif;background:#151515;padding:15px;border-radius:8px;color:#fff;'><div style='display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid #333;padding-bottom:10px;margin-bottom:15px;'><b style='font-size:16px;'>🩺 Network Health</b>")
    if (critCount == 0 && warnCount == 0) {
        html.append("<span style='background:#27ae60;color:#fff;padding:4px 8px;border-radius:4px;font-size:12px;font-weight:bold;'>All Nominal</span></div><div style='text-align:center;padding:20px;color:#aaa;font-style:italic;'>No critical issues detected.</div>")
    } else {
        def badgeColor = critCount > 0 ? "#e74c3c" : "#f1c40f"
        def badgeText = critCount > 0 ? "${critCount} Critical Issues" : "${warnCount} Warnings"
        html.append("<span style='background:${badgeColor};color:#fff;padding:4px 8px;border-radius:4px;font-size:12px;font-weight:bold;'>${badgeText}</span></div><table style='width:100%;border-collapse:collapse;font-size:13px;'>")
        
        def issues = results.findAll { it.status != "Green" }
        issues.each { issue ->
            def dotColor = issue.status == "Red" ? "#e74c3c" : "#f1c40f"
            def statusList = issue.messages.findAll { it.contains("Critical") || it.contains("Low") || it.contains("Offline") || it.contains("Inactive") || it.contains("Weak") || it.contains("Fair") }.join(", ")
            def customText = ""
            if (issue.customLoc || issue.customDesc) {
                def parts = []
                if (issue.customLoc) parts << "📍 ${issue.customLoc}"
                if (issue.customDesc) parts << "📝 ${issue.customDesc}"
                customText = "<div style='font-size:10px;color:#aaa;margin-top:2px;'>${parts.join(' &nbsp;|&nbsp; ')}</div>"
            }
            def battBarHtml = ""
            if (issue.battVal != null) {
                def barColor = issue.battVal > 50 ? "#27ae60" : (issue.battVal > 20 ? "#f1c40f" : "#e74c3c")
                battBarHtml = "<div style='margin-top:6px;width:100%;'><div style='display:flex;justify-content:space-between;font-size:10px;color:#aaa;margin-bottom:3px;'><span>🔋 ${issue.battVal}%</span><span>⏳ ${issue.estDaysLeft}</span></div><div style='width:100%;background:#333;height:4px;border-radius:2px;overflow:hidden;'><div style='width:${issue.battVal}%;background:${barColor};height:100%;'></div></div></div>"
            }
            html.append("<tr style='border-bottom:1px solid #333;'><td style='padding:8px 0;width:60%;'><div style='display:flex;align-items:center;'><span style='height:10px;width:10px;border-radius:50%;background:${dotColor};display:inline-block;margin-right:8px;box-shadow:0 0 4px ${dotColor};flex-shrink:0;'></span><b>${issue.name}</b></div>${customText}<div style='font-size:10px;color:#777;margin-top:4px;margin-left:18px;'>Last Active: ${issue.lastActive?:'Unknown'}${issue.battChanged?:''}</div>${battBarHtml}</td><td style='padding:8px 0;text-align:right;color:#ccc;vertical-align:top;'>${statusList}</td></tr>")
        }
        html.append("</table>")
    }
    html.append("<div style='margin-top:15px;font-size:11px;color:#666;text-align:right;'>Last Scan: ${state.lastCheckTime}</div></div>")
    
    child.sendEvent(name: "issueCount", value: critCount)
    def issueNames = results.findAll { it.status == "Red" }.collect{it.name}.join(", ")
    if (!issueNames) issueNames = "None"
    child.sendEvent(name: "issueList", value: issueNames)
    child.sendEvent(name: "htmlDashboard", value: html.toString())
}

def getRedirectHtml() {
    return "<!DOCTYPE html><html><head><script>setTimeout(function(){window.location.href=\"dashboard?access_token=${state.accessToken}\";}, 1200);</script></head><body style=\"background:#0d0d0d;color:#fff;text-align:center;padding-top:100px;font-family:sans-serif;\"><h3>🔄 Standard BMS Syncing...</h3><p style='color:#666;'>Processing telemetry changes</p></body></html>"
}

def forceRefreshEndpoint() {
    try {
        runIn(1, "runHealthCheck", [overwrite: true])
        return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
    } catch (e) {
        log.error "Health Monitor Refresh Error: ${e}"
        return render(contentType: "text/html", data: "Error executing refresh.", status: 500)
    }
}

def pingDeviceEndpoint() {
    try {
        def dId = params?.deviceId
        if (dId) {
            def allDevs = getAllMonitoredDevices()
            def dev = allDevs.find { it.id == dId }
            if (dev) {
                def cmdSent = false
                if (dev.hasCommand("refresh")) { try { dev.refresh(); cmdSent = true } catch(e){} } 
                else if (dev.hasCommand("ping")) { try { dev.ping(); cmdSent = true } catch(e){} }
                
                if (cmdSent) {
                    addToHistory("NETWORK HEAL: Manual ping/refresh sent to ${dev.displayName} via Portal.")
                    runIn(2, "runHealthCheck", [overwrite: true]) 
                }
            }
        }
        return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
    } catch(e) { 
        log.error "Ping Endpoint Error: ${e}" 
        return render(contentType: "text/html", data: "Error executing ping: ${e.message}", status: 500)
    }
}

def serveDashboardPage() {
    try {
        ensureStateMaps()
        def results = state.dashboardData ?: []
        
        def critCount = results.count { it.status == "Red" }
        def warnCount = results.count { it.status == "Yellow" }
        def goodCount = results.count { it.status == "Green" }
        def totalCount = results.size()

        def css = "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:20px;background:#0d0d0d;color:#e0e0e0}.container{max-width:800px;margin:0 auto;background:#151515;padding:25px;border-radius:12px;box-sizing:border-box}a.main-btn{background:#1f618d;color:#fff;border:none;padding:14px 20px;border-radius:8px;display:block;text-align:center;text-decoration:none;font-weight:600;margin-bottom:30px}a.main-btn:hover{background:#1a5276}.summary-box{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:25px}.summary-card{flex:1;min-width:100px;box-sizing:border-box;background:#1e1e1e;padding:15px;border-radius:8px;text-align:center;border-bottom:3px solid #333}.summary-card b{display:block;font-size:24px;color:#fff;margin-bottom:5px}.summary-card span{font-size:12px;color:#aaa;text-transform:uppercase}details{margin-bottom:15px}summary{padding:12px 15px;background:#1c1c1c;border-radius:6px;border-left:4px solid #3498db;cursor:pointer;color:#fff;font-weight:bold;font-size:16px}summary:hover{background:#252525}.cat-count{float:right;font-size:12px;color:#888;margin-top:3px}.dev-card{background:#222;padding:15px;border-radius:8px;margin-bottom:12px;border-left:4px solid #333}.status-Red{border-left-color:#e74c3c;background:linear-gradient(90deg,rgba(231,76,60,.1) 0%,#222 30%)}.status-Yellow{border-left-color:#f1c40f;background:linear-gradient(90deg,rgba(241,196,15,.1) 0%,#222 30%)}.status-Green{border-left-color:#27ae60}.dot{height:12px;width:12px;border-radius:50%;display:inline-block;margin-right:12px;flex-shrink:0}.dot-Red{background:#e74c3c;box-shadow:0 0 6px #e74c3c}.dot-Yellow{background:#f1c40f;box-shadow:0 0 6px #f1c40f}.dot-Green{background:#27ae60}.dev-info{flex-grow:1;min-width:0}.dev-name{font-size:15px;font-weight:bold;color:#fff;margin-bottom:2px}.dev-custom{font-size:11px;color:#888;margin-bottom:5px}.dev-details{font-size:13px;color:#aaa}.dev-subtext{font-size:11px;color:#7f8c8d;margin-top:5px;font-style:italic}a.ping-btn{background:#34495e;color:#fff;border:1px solid #2c3e50;padding:6px 12px;font-size:12px;border-radius:4px;font-weight:bold;text-decoration:none;margin-left:10px;flex-shrink:0}a.ping-btn:hover{background:#2c3e50}.dflex{display:flex;align-items:center;width:100%}.batt-wrap{margin-top:8px;width:100%}.batt-info{display:flex;justify-content:space-between;font-size:10px;color:#aaa;margin-bottom:3px}.batt-bg{width:100%;background:#333;height:6px;border-radius:3px;overflow:hidden}.batt-fg{height:100%}.bg-grn{background:#27ae60}.bg-ylw{background:#f1c40f}.bg-red{background:#e74c3c}.shop-card{flex:1;min-width:160px;box-sizing:border-box;background:#1e1e1e;padding:15px;border-radius:8px;border-left:4px solid #9b59b6;box-shadow:0 4px 6px rgba(0,0,0,.3)}.shop-title{color:#fff;font-size:15px;display:block;margin-bottom:8px}.shop-badge{background:#9b59b6;color:#fff;font-size:11px;padding:2px 6px;border-radius:10px;float:right}.shop-list{font-size:11px;color:#888;max-height:80px;overflow-y:auto;line-height:1.4}"

        StringBuilder html = new StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Device Health Portal</title><meta name='viewport' content='width=device-width, initial-scale=1'><script>setTimeout(function(){window.location.reload(1);}, 60000);</script><style>${css}</style></head><body><div class='container'>")
        
        html.append("<div style='text-align:center;margin-bottom:15px;display:flex;justify-content:center;'><svg width='70' height='70' viewBox='0 0 100 100' fill='none'><circle cx='50' cy='50' r='48' fill='#151515' stroke='#333' stroke-width='1'/><path d='M 50 20 L 25 30 V 50 C 25 68 35 80 50 85 C 65 80 75 68 75 50 V 30 L 50 20 Z' stroke='#e0e0e0' stroke-width='3' stroke-linejoin='round'/><path d='M 38 52 L 46 60 L 62 42' stroke='#27ae60' stroke-width='4' stroke-linecap='round' stroke-linejoin='round'/></svg></div><h2 style='text-align:center;color:#fff;margin:0 0 5px 0;'>Estate Health Dashboard</h2><p style='text-align:center;font-size:13px;color:#888;margin-bottom:25px;'>Last Scan: ${state.lastCheckTime}</p>")
        
        html.append("<div class='summary-box'><div class='summary-card' style='border-bottom-color:#e74c3c;'><b>${critCount}</b><span>Critical</span></div><div class='summary-card' style='border-bottom-color:#f1c40f;'><b>${warnCount}</b><span>Warnings</span></div><div class='summary-card' style='border-bottom-color:#27ae60;'><b>${goodCount}</b><span>Healthy</span></div><div class='summary-card' style='border-bottom-color:#3498db;'><b>${totalCount}</b><span>Total Devices</span></div></div>")
        
        html.append("<a href='refresh?access_token=${state.accessToken}' class='main-btn'>🩺 Force Health Scan</a>")

        def buildCard = { dev ->
            def detailsStr = dev.messages.join(" • ")
            def actStr = dev.lastActive ?: "Unknown"
            def battStr = dev.battChanged ?: ""
            
            def customText = ""
            if (dev.customLoc || dev.customDesc) {
                def parts = []
                if (dev.customLoc) parts << "📍 ${dev.customLoc}"
                if (dev.customDesc) parts << "📝 ${dev.customDesc}"
                customText = "<div class='dev-custom'>" + parts.join(" &nbsp;|&nbsp; ") + "</div>"
            }
            
            def battBarHtml = ""
            if (dev.battVal != null) {
                def barCls = dev.battVal > 50 ? "bg-grn" : (dev.battVal > 20 ? "bg-ylw" : "bg-red")
                def bType = dev.battType ? " - ${dev.battType}" : ""
                battBarHtml = "<div class='batt-wrap'><div class='batt-info'><span>🔋 ${dev.battVal}%${bType}</span><span>⏳ ${dev.estDaysLeft}</span></div><div class='batt-bg'><div class='batt-fg ${barCls}' style='width:${dev.battVal}%;'></div></div></div>"
            }

            def pingHtml = ""
            if (dev.canPing) {
                pingHtml = "<a href='ping?deviceId=${dev.id}&access_token=${state.accessToken}' class='ping-btn'>📡 Ping</a>"
            }
            
            return "<div class='dev-card status-${dev.status}'><div class='dflex'><span class='dot dot-${dev.status}'></span><div class='dev-info'><div class='dev-name'>${dev.name}</div>${customText}<div class='dev-details'>${detailsStr}</div><div class='dev-subtext'>Last Active: ${actStr}${battStr}</div>${battBarHtml}</div>${pingHtml}</div></div>"
        }

        def allIssues = results.findAll { it.status == "Red" || it.status == "Yellow" }
        if (allIssues.size() > 0) {
            html.append("<details><summary style='border-left-color:#e74c3c;'>⚠️ Active Issues <span class='cat-count'>${allIssues.size()} Devices</span></summary><div style='padding-top:15px;'>")
            allIssues.each { dev -> html.append(buildCard(dev)) }
            html.append("</div></details>")
        }

        if (results.size() > 0) {
            def categorizedResults = [:]
            results.each { dev ->
                def cats = dev.categories && dev.categories.size() > 0 ? dev.categories : ["Unassigned / Other"]
                cats.each { catName ->
                    if (!categorizedResults[catName]) categorizedResults[catName] = []
                    categorizedResults[catName] << dev
                }
            }
            
            def sortedKeys = categorizedResults.keySet().sort()
            sortedKeys.each { catName ->
                def catResults = categorizedResults[catName]
                html.append("<details><summary style='border-left-color:#3498db;'>📁 ${catName} <span class='cat-count'>${catResults.size()} Devices</span></summary><div style='padding-top:15px;'>")
                catResults.each { dev -> html.append(buildCard(dev)) }
                html.append("</div></details>")
            }
        } else {
            html.append("<div style='text-align:center;color:#888;padding:40px;'>No devices selected for monitoring. Please configure the app in Hubitat.</div>")
        }
        
        def batteryInventory = [:]
        results.each { dev ->
            if (dev.battType && dev.battType != "") {
                if (!batteryInventory[dev.battType]) batteryInventory[dev.battType] = []
                batteryInventory[dev.battType] << dev
            }
        }
        
        if (batteryInventory.size() > 0) {
            html.append("<h3 style='color:#fff;margin-top:35px;border-bottom:1px solid #333;padding-bottom:10px;font-size:18px;'>🔋 Battery Shopping List</h3><div style='display:flex;flex-wrap:wrap;gap:12px;margin-bottom:20px;'>")
            def sortedBatts = batteryInventory.keySet().sort()
            sortedBatts.each { type ->
                def devs = batteryInventory[type]
                def devNames = devs.collect{it.name}.join("<br>• ")
                html.append("<div class='shop-card'><b class='shop-title'>${type} <span class='shop-badge'>${devs.size()}</span></b><div class='shop-list'>• ${devNames}</div></div>")
            }
            html.append("</div>")
        }

        html.append("</div></body></html>")
        
        return render(contentType: "text/html", data: html.toString(), status: 200)
    } catch (Exception e) {
        log.error "Health Portal Crash: ${e}"
        return render(contentType: "text/html", data: "<h3 style='color:white;'>Portal Error:</h3><p style='color:white;'>${e}</p>", status: 500)
    }
}
