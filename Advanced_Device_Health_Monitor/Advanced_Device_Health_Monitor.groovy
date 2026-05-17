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
    page(name: "pageNotifications")
}

mappings {
    path("/dashboard") { action: [GET: "serveDashboardPage"] }
    path("/refresh") { action: [GET: "forceRefreshEndpoint"] }
    path("/ping") { action: [GET: "pingDeviceEndpoint"] }
    path("/mute") { action: [GET: "muteDeviceEndpoint"] }
    path("/unmute") { action: [GET: "unmuteDeviceEndpoint"] }
    path("/ticket") { action: [GET: "serveMaintenanceTicket"] }
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
            
            def critCount = state.dashboardData?.count { (it.status == "Red" || it.status == "Purple") && !it.isMuted } ?: 0
            def warnCount = state.dashboardData?.count { it.status == "Yellow" && !it.isMuted } ?: 0
            
            def issueState = ""
            if (critCount > 0) issueState = "<span style='color: #e74c3c; font-weight: bold;'>${critCount} Critical Issues</span>"
            else if (warnCount > 0) issueState = "<span style='color: #f1c40f; font-weight: bold;'>${warnCount} Warnings</span>"
            else issueState = "<span style='color: #27ae60; font-weight: bold;'>All Systems Nominal</span>"
            
            def lastCheckStr = state.lastCheckTime ?: "Never"
            
            def statusText = "<div style='margin-bottom: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            statusText += "<b>System State:</b> ${systemState}<br><b>Network Health:</b> ${issueState}<br><b>Last Scan:</b> ${lastCheckStr}</div>"
            
            def issues = state.dashboardData?.findAll { (it.status == "Red" || it.status == "Purple" || it.status == "Yellow") && !it.isMuted }
            if (issues && issues.size() > 0) {
                statusText += "<h4 style='margin-bottom: 5px; margin-top: 15px; color: #333; font-family: sans-serif;'>Active Device Alerts</h4>"
                statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
                statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Device</th><th style='padding: 8px;'>Status</th><th style='padding: 8px;'>Details</th></tr>"
                
                issues.each { issue ->
                    def typeColor = issue.status == "Red" ? "#e74c3c" : (issue.status == "Purple" ? "#9b59b6" : "#f1c40f")
                    def typeLabel = issue.status == "Red" ? "Critical" : (issue.status == "Purple" ? "Flapping" : "Warning")
                    def actStr = issue.lastActive ?: "Unknown"
                    def battStr = issue.battChanged ?: ""
                    def muteStr = issue.isMuted ? "<br><span style='font-size:10px; color:#8e44ad;'><b>🔕 MUTED</b></span>" : ""
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${issue.name}</b><br><span style='font-size:10px; color:#888;'>Last Active: ${actStr}${battStr}</span>${muteStr}</td><td style='padding: 8px; color: ${typeColor}; font-weight: bold;'>${typeLabel}</td><td style='padding: 8px;'>${issue.messages.join('<br>')}</td></tr>"
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
            href(name: "hrefDeviceDetails", page: "pageDeviceDetails", title: "📍 Locations, Descriptions, & Folders", description: "Bulk assign locations, battery types/qty, and override folders")
            href(name: "hrefThresholds", page: "pageThresholds", title: "📊 Monitoring Thresholds", description: "Configure battery, inactivity, stuck states and signal limits")
            href(name: "hrefNotifications", page: "pageNotifications", title: "🔔 Notification Rules", description: "Set up targeted alerts and routing")
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
            
            input "enableQuietHours", "bool", title: "Enable Quiet Hours for Auto-Healing?", defaultValue: false, submitOnChange: true
            if (enableQuietHours) {
                paragraph "<i>During quiet hours, auto-healing pings will be paused to prevent devices from waking up or flashing lights in the middle of the night.</i>"
                input "quietStart", "time", title: "Quiet Hours Start", required: true
                input "quietEnd", "time", title: "Quiet Hours End", required: true
            }
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

def pageNotifications() {
    dynamicPage(name: "pageNotifications", title: "🔔 Notification Rules", install: false, uninstall: false) {
        section("🌍 Global Notification Routing") {
            paragraph "<i>Select the default devices to receive health alerts. This applies to ALL monitored devices automatically. You do not need to configure individual sensors unless you want to override this global setting.</i>"
            input "defaultCritNotifiers", "capability.notification", title: "Notify on CRITICAL / FLAPPING issues", multiple: true, required: false
            input "defaultWarnNotifiers", "capability.notification", title: "Notify on WARNING issues", multiple: true, required: false
            input "notifyOnResolved", "bool", title: "Notify when issues are Resolved (Return to Green)?", defaultValue: false
        }
        
        section("Smart Batching") {
            paragraph "<i>When multiple devices fail at the same time, the app will automatically group them into consolidated messages (up to 5 devices per push) so your phone is not overwhelmed.</i>"
        }
    }
}

def pageDeviceDetails() {
    dynamicPage(name: "pageDeviceDetails", title: "📍 Locations, Descriptions & Folders", install: false, uninstall: false) {
        
        def defaultRooms = "Living Room, Kitchen, Master Bedroom, Garage, Front Yard, Backyard, Hallway, Bathroom"
        def roomListStr = settings.roomList ?: defaultRooms
        def roomOptions = roomListStr.split(',').collect{it.trim()}.findAll{it != ""}
        
        def folderOptions = ["Safety & Alarms", "Switches & Actuators", "Motion Sensors", "Contact Sensors", "Presence Sensors", "Climate Sensors", "Power Meters", "Light Sensors", "Vibration Sensors", "Other Sensors", "General Battery Devices"]
        def battTypes = ["AA", "AAA", "AAAA", "C", "D", "9V", "CR2032", "CR2025", "CR2450", "CR1632", "CR1220", "CR2", "CR123A", "LR44", "A23", "18650", "14500", "Custom / Proprietary Pack", "Rechargeable Internal"]

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
                        input "battQty_${dev.id}", "number", title: "${dev.displayName} - Battery Quantity", defaultValue: 1, range: "1..20", required: false
                        
                        input "notifyOverride_${dev.id}", "capability.notification", title: "${dev.displayName} - Override Alert Target (Bypasses Global Rules)", multiple: true, required: false
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
                input "battCalcThreshold", "number", title: "Minimum % Drop for Battery Forecasting", defaultValue: 5, range: "1..50", required: true
                paragraph "<i>Prevents wildly inaccurate estimations by waiting until the battery has dropped by this percentage before calculating days remaining.</i>"
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
        
        section("Stale State Detection (Stuck Sensors)") {
            input "enableStuckCheck", "bool", title: "Enable Stale State Detection?", defaultValue: true, submitOnChange: true
            if (enableStuckCheck) {
                paragraph "<i>Detects hardware lockups where a sensor is technically online, but its physical state is frozen (e.g., stuck on 'Active' or 'Open').</i>"
                input "stuckMotionHours", "number", title: "Stuck 'Active' Threshold (Hours)", defaultValue: 2, required: true
                input "stuckContactHours", "number", title: "Stuck 'Open' Threshold (Hours)", defaultValue: 24, required: true
            }
        }
        
        section("Signal Quality (Zigbee/Z-Wave)") {
            input "enableSignalCheck", "bool", title: "Enable Signal Monitoring?", defaultValue: false, submitOnChange: true
            if (enableSignalCheck) {
                paragraph "<i>Checks devices that report 'rssi' or 'lqi' attributes. Tracked via inline sparklines.</i>"
                input "rssiThreshold", "number", title: "Critical Minimum RSSI Threshold (e.g. -85)", defaultValue: -85, range: "-120..0", required: true
                input "lqiThreshold", "number", title: "Critical Minimum LQI Threshold (e.g. 100)", defaultValue: 100, range: "0..255", required: true
                paragraph "<i>Note: Signals approaching these thresholds will trigger a Yellow Warning.</i>"
            }
        }
        
        section("Device Flapping (Unstable Network)") {
            input "enableFlapDetection", "bool", title: "Enable Flap Detection?", defaultValue: true, submitOnChange: true
            if (enableFlapDetection) {
                paragraph "<i>Detects if a device repeatedly drops off the network, indicating a bad route or failing radio (Purple Status).</i>"
                input "flapThreshold", "number", title: "Drops per 24hrs to trigger Flapping status", defaultValue: 3, required: true
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
    
    if (state.previousStatus == null) state.previousStatus = [:]
    if (state.flapHistory == null) state.flapHistory = [:]
    if (state.rssiHistory == null) state.rssiHistory = [:]
    
    if (state.muteExpirations == null) state.muteExpirations = [:]
}

def isQuietTime() {
    if (!settings.enableQuietHours || !settings.quietStart || !settings.quietEnd) return false
    try {
        def qS = timeToday(settings.quietStart, location.timeZone)
        def qE = timeToday(settings.quietEnd, location.timeZone)
        return timeOfDayIsBetween(qS, qE, new Date(), location.timeZone)
    } catch (e) {
        log.error "Quiet time calculation error: ${e}"
        return false
    }
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
        
        addToHistory("SYSTEM: Live Auto-Updating is active.")
    }
    
    addToHistory("SYSTEM: Initialization complete. Monitoring schedule set to ${sInt}.".toString())
    runIn(2, "runHealthCheck")
}

def liveUpdateHandler(evt) {
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") return
    runIn(15, "runHealthCheck", [overwrite: true])
}

def appButtonHandler(btn) {
    ensureStateMaps()
    if (btn != "btnRefresh") {
        addToHistory(("SYSTEM: Action triggered via UI Dashboard - " + btn.toString()).toString())
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
            addToHistory("SYSTEM: Bulk applied location '${settings.bulkLoc}' to ${devIds.size()} devices.".toString())
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
    
    if (isQuietTime()) {
        addToHistory("AUTO-HEAL: Skipped scheduled network ping due to active Quiet Hours.")
        return
    }
    
    def allDevs = getAllMonitoredDevices()
    def healedCount = 0
    def problemDevIds = state.dashboardData?.findAll { it.status == "Red" || it.status == "Purple" || it.status == "Yellow" }?.collect { it.id } ?: []
    
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
        addToHistory("AUTO-HEAL: Scheduled self-heal executed. Commands sent to ${healedCount} problematic devices.".toString())
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
    def nowMs = new Date().time
    
    // Purge Expired Mutes
    if (state.muteExpirations) {
        def expired = state.muteExpirations.findAll { k, v -> v < nowMs }.collect { it.key }
        expired.each { state.muteExpirations.remove(it) }
    }
    
    def alertQueue = [:]
    def addAlertToQueue = { targets, msg ->
        if (targets) {
            targets.each { t ->
                if (t != null) {
                    if (!alertQueue[t.id]) alertQueue[t.id] = [device: t, msgs: []]
                    alertQueue[t.id].msgs << msg
                }
            }
        }
    }
    
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
    
    def ignoreListIds = settings.ignoredInactivityDevices?.collect { it.id } ?: []
    
    deviceMap.each { devId, data ->
        def dev = data.device
        def isMuted = state.muteExpirations && state.muteExpirations[dev.id] != null
        def muteUntil = isMuted ? state.muteExpirations[dev.id] : null
        
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
            def dRssiHistory = []
            
            def lastActiveDate = dev.getLastActivity()
            def lastActiveStr = lastActiveDate ? lastActiveDate.format("MM/dd/yy h:mm a", location.timeZone) : "Unknown"
            
            // Native Health Status Override
            if (dev.hasAttribute("healthStatus")) {
                def hStat = dev.currentValue("healthStatus")
                if (hStat && hStat.toString().toLowerCase() == "offline") {
                    devHealth = "Red"
                    msgs << "System reports OFFLINE"
                }
            }
            
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
                        
                        def calcThresh = settings.battCalcThreshold ?: 5
                        def pctDropped = 100 - bVal
                        
                        if (daysElapsed > 1 && pctDropped >= calcThresh) {
                            BigDecimal dropRate = pctDropped / daysElapsed
                            if (dropRate > 0) {
                                int daysLeft = (bVal / dropRate).toInteger()
                                estDaysLeftStr = "~${daysLeft} Days"
                            }
                        } else if (bVal < 100) {
                            estDaysLeftStr = "Waiting for ${calcThresh}% drop..."
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
                        if (msgs.size() == 0 || (msgs.size() == 1 && msgs[0].contains("Battery OK"))) {
                            // Don't add Active if we are logging other major issues, unless we only have "Battery OK"
                            if (!msgs.contains("Active")) msgs << "Active"
                        }
                    }
                } else {
                    devHealth = "Red"
                    msgs << "No Activity Data Found"
                }
            }
            
            // Stale State (Stuck Sensor) Detection
            if (settings.enableStuckCheck) {
                if (dev.hasAttribute("motion") && dev.currentValue("motion") == "active") {
                    def stateDate = dev.currentState("motion")?.date
                    if (stateDate) {
                        def diffHrs = (nowMs - stateDate.time) / 3600000
                        def thresh = settings.stuckMotionHours ?: 2
                        if (diffHrs > thresh) {
                            if (devHealth != "Red" && devHealth != "Purple") devHealth = "Yellow"
                            msgs << "Stuck Active (${diffHrs.toInteger()}h)"
                        }
                    }
                }
                if (dev.hasAttribute("contact") && dev.currentValue("contact") == "open") {
                    def stateDate = dev.currentState("contact")?.date
                    if (stateDate) {
                        def diffHrs = (nowMs - stateDate.time) / 3600000
                        def thresh = settings.stuckContactHours ?: 24
                        if (diffHrs > thresh) {
                            if (devHealth != "Red" && devHealth != "Purple") devHealth = "Yellow"
                            msgs << "Stuck Open (${diffHrs.toInteger()}h)"
                        }
                    }
                }
            }
            
            if (settings.enableSignalCheck) {
                if (dev.hasAttribute("rssi")) {
                    def rssi = dev.currentValue("rssi")
                    if (rssi != null && rssi.toString().isNumber()) {
                        def rVal = rssi.toInteger()
                        
                        if (!state.rssiHistory[dev.id]) state.rssiHistory[dev.id] = []
                        state.rssiHistory[dev.id] << rVal
                        if (state.rssiHistory[dev.id].size() > 10) state.rssiHistory[dev.id].remove(0)
                        dRssiHistory = state.rssiHistory[dev.id]
                        
                        def thresh = settings.rssiThreshold ?: -85
                        if (rVal <= thresh) {
                            devHealth = "Red"
                            msgs << "Weak Signal (${rVal} dBm)"
                        } else if (rVal <= thresh + 10) {
                            if (devHealth != "Red") devHealth = "Yellow"
                            msgs << "Fair Signal (${rVal} dBm)"
                        }
                    }
                }
                if (dev.hasAttribute("lqi")) {
                    def lqi = dev.currentValue("lqi")
                    if (lqi != null && lqi.toString().isNumber()) {
                        def lVal = lqi.toInteger()
                        def thresh = settings.lqiThreshold ?: 100
                        if (lVal <= thresh) {
                            devHealth = "Red"
                            msgs << "Critical LQI (${lVal})"
                        } else if (lVal <= thresh + 50) {
                            if (devHealth != "Red") devHealth = "Yellow"
                            msgs << "Weak LQI (${lVal})"
                        }
                    }
                }
            }
            
            def oldStatus = state.previousStatus[dev.id]
            if (devHealth == "Red" && oldStatus != "Red" && oldStatus != "Purple") {
                if (!state.flapHistory[dev.id]) state.flapHistory[dev.id] = []
                state.flapHistory[dev.id] << nowMs
            }
            
            if (state.flapHistory[dev.id]) {
                state.flapHistory[dev.id] = state.flapHistory[dev.id].findAll { nowMs - it < 86400000 }
                if (settings.enableFlapDetection && state.flapHistory[dev.id].size() >= (settings.flapThreshold ?: 3)) {
                    devHealth = "Purple"
                    msgs.add(0, "Flapping/Unstable (${state.flapHistory[dev.id].size()} drops/24h)")
                }
            }
            
            // Smart Chunked Alert Push
            if (oldStatus != null && oldStatus != devHealth) {
                if (!isMuted) {
                    def msgToSend = null
                    def level = null
                    
                    if (devHealth == "Red") { msgToSend = "🔴 CRITICAL: ${dev.displayName} is Offline/Critical"; level = "Critical" }
                    else if (devHealth == "Purple") { msgToSend = "🟣 FLAPPING: ${dev.displayName} is dropping repeatedly"; level = "Critical" }
                    else if (devHealth == "Yellow") { msgToSend = "🟡 WARNING: ${dev.displayName} is showing degradation"; level = "Warning" }
                    else if (devHealth == "Green" && settings.notifyOnResolved) { msgToSend = "🟢 RESOLVED: ${dev.displayName} returned to nominal"; level = "Resolved" }
                    
                    if (msgToSend) {
                        def targets = settings["notifyOverride_${dev.id}"]
                        if (!targets) {
                            targets = (level == "Critical") ? settings.defaultCritNotifiers : (level == "Warning" ? settings.defaultWarnNotifiers : (settings.defaultCritNotifiers ?: settings.defaultWarnNotifiers))
                        }
                        addAlertToQueue(targets, msgToSend)
                    }
                } else {
                    addToHistory("MUTE: Suppressed status change alert for ${dev.displayName} (Device is currently muted).".toString())
                }
            }
            
            state.previousStatus[dev.id] = devHealth
            // Cleanup Active logic if we didn't add it in the loop
            if (msgs.size() == 0) msgs << "Monitoring Active"
            msgs.removeAll { it == "Battery OK" && msgs.size() > 1 } // Hide Battery OK if we have warnings
            
            def canPingDev = dev.hasCommand("refresh") || dev.hasCommand("ping")

            def devLoc = settings["loc_${dev.id}"] ?: ""
            def devDesc = settings["desc_${dev.id}"] ?: ""
            def batType = settings["battType_${dev.id}"] ?: ""
            def batQty = settings["battQty_${dev.id}"] ?: 1

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
                battType: batType,
                battQty: batQty,
                isMuted: isMuted,
                muteUntil: muteUntil,
                rssiHistory: dRssiHistory
            ]
            
        } catch (e) {
            log.warn "Health Monitor: Error scanning device ${dev.displayName} - ${e}"
        }
    }
    
    // Process and Chunk the Push Notifications
    alertQueue.each { tId, data ->
        def tDev = data.device
        def messages = data.msgs
        if (messages.size() > 0) {
            def chunked = messages.collate(5) // Max 5 devices per notification
            def totalChunks = chunked.size()
            
            chunked.eachWithIndex { chunk, idx ->
                def header = totalChunks > 1 ? "Device Health Alert (${idx+1}/${totalChunks}):\n" : "Device Health Alert:\n"
                def combinedMsg = header + chunk.join("\n")
                tDev.deviceNotification(combinedMsg)
                pauseExecution(500) // Gentle pause between chunks to prevent drops
            }
            addToHistory("NOTIFIED: Sent ${messages.size()} batched alerts to ${tDev.displayName}.".toString())
        }
    }
    
    results = results.sort { a, b -> 
        def order = ["Purple": 1, "Red": 2, "Yellow": 3, "Green": 4]
        def c1 = order[a.status] <=> order[b.status]
        if (c1 != 0) return c1
        return a.name <=> b.name
    }
    
    state.dashboardData = results
    state.lastCheckTime = new Date().format("MM/dd/yyyy h:mm a", location.timeZone)
    
    // Child device counts EXCLUDE muted devices
    def critCount = results.count { (it.status == "Red" || it.status == "Purple") && !it.isMuted }
    def warnCount = results.count { it.status == "Yellow" && !it.isMuted }
    
    updateChildHtmlDashboard(results, critCount, warnCount)
}

// -------------------------------------------------------------------------
// HISTORY LOGGER: Overloaded to ensure Sandbox never fails on GString types
// -------------------------------------------------------------------------
def addToHistory(String msg) {
    def cleanMsg = msg.replaceAll("\\<.*?\\>", "")
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    log.info "SYSTEM: [${timestamp}] ${cleanMsg}"
    
    if (!state.historyLog) state.historyLog = []
    state.historyLog.add(0, "<span style='color: #888; font-size: 11px;'>[${new Date().format("h:mm a", location.timeZone)}]</span> <b>${cleanMsg}</b>")
    if (state.historyLog.size() > 30) state.historyLog = state.historyLog.take(30)
}

def addToHistory(Object msg) {
    addToHistory(msg.toString())
}
// -------------------------------------------------------------------------

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
        
        // Strip out muted devices from the child dashboard entirely
        def issues = results.findAll { it.status != "Green" && !it.isMuted }
        issues.each { issue ->
            def dotColor = issue.status == "Red" ? "#e74c3c" : (issue.status == "Purple" ? "#9b59b6" : "#f1c40f")
            def statusList = issue.messages.findAll { it.contains("Critical") || it.contains("Low") || it.contains("Offline") || it.contains("Inactive") || it.contains("Weak") || it.contains("Fair") || it.contains("Stuck") || it.contains("Flapping") }.join(", ")
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
                def bTypeStr = issue.battType ? " - ${issue.battQty}x ${issue.battType}" : ""
                battBarHtml = "<div style='margin-top:6px;width:100%;'><div style='display:flex;justify-content:space-between;font-size:10px;color:#aaa;margin-bottom:3px;'><span>🔋 ${issue.battVal}%${bTypeStr}</span><span>⏳ ${issue.estDaysLeft}</span></div><div style='width:100%;background:#333;height:4px;border-radius:2px;overflow:hidden;'><div style='width:${issue.battVal}%;background:${barColor};height:100%;'></div></div></div>"
            }
            
            html.append("<tr style='border-bottom:1px solid #333;'><td style='padding:8px 0;width:60%;'><div style='display:flex;align-items:center;'><span style='height:10px;width:10px;border-radius:50%;background:${dotColor};display:inline-block;margin-right:8px;box-shadow:0 0 4px ${dotColor};flex-shrink:0;'></span><b>${issue.name}</b></div>${customText}<div style='font-size:10px;color:#777;margin-top:4px;margin-left:18px;'>Last Active: ${issue.lastActive?:'Unknown'}${issue.battChanged?:''}</div>${battBarHtml}</td><td style='padding:8px 0;text-align:right;color:#ccc;vertical-align:top;'>${statusList}</td></tr>")
        }
        html.append("</table>")
    }
    html.append("<div style='margin-top:15px;font-size:11px;color:#666;text-align:right;'>Last Scan: ${state.lastCheckTime}</div></div>")
    
    child.sendEvent(name: "issueCount", value: critCount)
    // Don't send muted device names to the child device issueList
    def issueNames = results.findAll { (it.status == "Red" || it.status == "Purple") && !it.isMuted }.collect{it.name}.join(", ")
    if (!issueNames) issueNames = "None"
    child.sendEvent(name: "issueList", value: issueNames)
    child.sendEvent(name: "htmlDashboard", value: html.toString())
}

def getRedirectHtml(delayMs, msgText) {
    return "<!DOCTYPE html><html><head><script>setTimeout(function(){window.location.href=\"dashboard?access_token=${state.accessToken}\";}, ${delayMs});</script></head><body style=\"background:#0d0d0d;color:#fff;text-align:center;padding-top:100px;font-family:sans-serif;\"><h3>🔄 Standard BMS Syncing...</h3><p style='color:#666;'>${msgText}</p></body></html>"
}

def forceRefreshEndpoint() {
    try {
        runIn(1, "runHealthCheck", [overwrite: true])
        return render(contentType: "text/html", data: getRedirectHtml(1200, "Processing telemetry changes"), status: 200)
    } catch (e) {
        log.error "Health Monitor Refresh Error: ${e}"
        return render(contentType: "text/html", data: "Error executing refresh.", status: 500)
    }
}

def muteDeviceEndpoint() {
    try {
        def dId = params?.deviceId
        def hrs = params?.hours
        if (dId && hrs && hrs.toString().isNumber()) {
            long muteUntil = new Date().time + (hrs.toInteger() * 3600000)
            if (state.muteExpirations == null) state.muteExpirations = [:]
            state.muteExpirations[dId] = muteUntil
            
            // Instantly update dashboard data without full scan
            def tempData = state.dashboardData
            if (tempData) {
                tempData.each { if (it.id == dId) { it.isMuted = true; it.muteUntil = muteUntil } }
                state.dashboardData = tempData
            }
            
            addToHistory("MUTE: Alerts for device silenced for ${hrs} hours via Portal.".toString())
        }
        return render(contentType: "text/html", data: getRedirectHtml(0, "Muting device..."), status: 200)
    } catch(e) { 
        log.error "Mute error: ${e}" 
        return render(contentType: "text/html", data: "Error executing mute: ${e.message}", status: 500)
    }
}

def unmuteDeviceEndpoint() {
    try {
        def dId = params?.deviceId
        if (dId && state.muteExpirations) {
            state.muteExpirations.remove(dId)
            
            // Instantly update dashboard data without full scan
            def tempData = state.dashboardData
            if (tempData) {
                tempData.each { if (it.id == dId) { it.isMuted = false; it.muteUntil = null } }
                state.dashboardData = tempData
            }
            
            addToHistory("MUTE: Alerts for device unmuted via Portal.".toString())
        }
        return render(contentType: "text/html", data: getRedirectHtml(0, "Unmuting device..."), status: 200)
    } catch(e) { 
        log.error "Unmute error: ${e}"
        return render(contentType: "text/html", data: "Error executing unmute: ${e.message}", status: 500)
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
                    addToHistory("NETWORK HEAL: Manual ping/refresh sent to ${dev.displayName} via Portal.".toString())
                    runIn(2, "runHealthCheck", [overwrite: true]) 
                }
            }
        }
        return render(contentType: "text/html", data: getRedirectHtml(1200, "Pinging node..."), status: 200)
    } catch(e) { 
        log.error "Ping Endpoint Error: ${e}" 
        return render(contentType: "text/html", data: "Error executing ping: ${e.message}", status: 500)
    }
}

def serveMaintenanceTicket() {
    try {
        def results = state.dashboardData ?: []
        def issues = results.findAll { it.status != "Green" }
        
        StringBuilder html = new StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Maintenance Ticket</title>")
        html.append("<style>body{font-family:sans-serif;color:#000;background:#fff;padding:20px;max-width:900px;margin:0 auto;} h1{text-align:center;border-bottom:2px solid #000;padding-bottom:10px;} table{width:100%;border-collapse:collapse;margin-top:20px;} th,td{border:1px solid #000;padding:10px;text-align:left;font-size:14px;} th{background:#eee;} .checkbox{width:20px;height:20px;border:2px solid #000;display:inline-block;} .print-btn{display:block;width:100%;padding:15px;background:#000;color:#fff;text-align:center;text-decoration:none;font-size:18px;font-weight:bold;margin-bottom:20px;cursor:pointer;} @media print{ .print-btn{display:none;} }</style>")
        html.append("</head><body onload='window.print()'>")
        
        html.append("<a href='#' onclick='window.print();return false;' class='print-btn'>🖨️ Print Ticket</a>")
        html.append("<h1>MAINTENANCE WORK ORDER</h1>")
        html.append("<p><strong>Generated:</strong> ${new Date().format("MM/dd/yyyy h:mm a", location.timeZone)}</p>")
        
        if (issues.size() == 0) {
            html.append("<p style='text-align:center;font-size:18px;margin-top:50px;'>No active issues detected. Estate is nominal.</p>")
        } else {
            def groupedByLoc = issues.groupBy { it.customLoc ?: "Unassigned Location" }
            def sortedLocs = groupedByLoc.keySet().sort()
            
            sortedLocs.each { loc ->
                html.append("<h3 style='margin-top:30px;'>📍 ${loc}</h3>")
                html.append("<table><tr><th style='width:50px;'>Done</th><th>Device</th><th>Issue</th><th>Battery Req.</th></tr>")
                
                groupedByLoc[loc].each { dev ->
                    def issueStr = dev.messages.join(", ")
                    def bReq = ""
                    if (dev.battType) {
                        bReq = "${dev.battQty}x ${dev.battType}"
                    } else if (issueStr.contains("Battery")) {
                        bReq = "Unknown Type"
                    } else {
                        bReq = "N/A"
                    }
                    def dName = dev.customDesc ? "<b>${dev.name}</b><br><span style='font-size:11px;color:#555;'>${dev.customDesc}</span>" : "<b>${dev.name}</b>"
                    
                    html.append("<tr>")
                    html.append("<td style='text-align:center;'><div class='checkbox'></div></td>")
                    html.append("<td>${dName}</td>")
                    html.append("<td>${issueStr}</td>")
                    html.append("<td>${bReq}</td>")
                    html.append("</tr>")
                }
                html.append("</table>")
            }
            
            // Generate Hardware Pull List
            def batteryInventory = [:]
            issues.each { dev ->
                if (dev.battType && dev.battType != "") {
                    if (!batteryInventory[dev.battType]) batteryInventory[dev.battType] = 0
                    batteryInventory[dev.battType] += (dev.battQty?.toInteger() ?: 1)
                }
            }
            
            if (batteryInventory.size() > 0) {
                html.append("<h2 style='margin-top:40px;border-bottom:2px solid #000;padding-bottom:5px;'>Hardware Pull List</h2>")
                html.append("<ul style='font-size:16px;'>")
                batteryInventory.keySet().sort().each { type ->
                    html.append("<li>[ &nbsp; ] &nbsp; <b>${batteryInventory[type]}x</b> &nbsp; ${type} Batteries</li>")
                }
                html.append("</ul>")
            }
        }
        
        html.append("</body></html>")
        return render(contentType: "text/html", data: html.toString(), status: 200)
    } catch (e) {
        log.error "Ticket Error: ${e}"
        return render(contentType: "text/html", data: "Error generating ticket.", status: 500)
    }
}

def serveDashboardPage() {
    try {
        ensureStateMaps()
        def nowMs = new Date().time
        
        // Live mute validation before rendering to fix visual lag
        def mutesChanged = false
        if (state.muteExpirations) {
            def expired = state.muteExpirations.findAll { k, v -> v < nowMs }.collect { it.key }
            if (expired.size() > 0) {
                expired.each { state.muteExpirations.remove(it) }
                mutesChanged = true
            }
        }
        
        def results = state.dashboardData ?: []
        if (state.muteExpirations != null) {
            results.each { dev ->
                def shouldBeMuted = state.muteExpirations.containsKey(dev.id)
                if (dev.isMuted != shouldBeMuted) {
                    dev.isMuted = shouldBeMuted
                    dev.muteUntil = shouldBeMuted ? state.muteExpirations[dev.id] : null
                    mutesChanged = true
                }
            }
        }
        
        if (mutesChanged) state.dashboardData = results
        
        // Exclude muted devices from standard counts
        def critCount = results.count { (it.status == "Red" || it.status == "Purple") && !it.isMuted }
        def warnCount = results.count { it.status == "Yellow" && !it.isMuted }
        def goodCount = results.count { it.status == "Green" && !it.isMuted }
        def totalCount = results.size()

        def css = "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:20px;background:#0d0d0d;color:#e0e0e0}.container{max-width:800px;margin:0 auto;background:#151515;padding:25px;border-radius:12px;box-sizing:border-box}a.main-btn{background:#1f618d;color:#fff;border:none;padding:14px 20px;border-radius:8px;display:block;text-align:center;text-decoration:none;font-weight:600;}a.main-btn:hover{background:#1a5276}.summary-box{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:25px}.summary-card{flex:1;min-width:100px;box-sizing:border-box;background:#1e1e1e;padding:15px;border-radius:8px;text-align:center;border-bottom:3px solid #333}.summary-card b{display:block;font-size:24px;color:#fff;margin-bottom:5px}.summary-card span{font-size:12px;color:#aaa;text-transform:uppercase}details{margin-bottom:15px}summary{padding:12px 15px;background:#1c1c1c;border-radius:6px;border-left:4px solid #3498db;cursor:pointer;color:#fff;font-weight:bold;font-size:16px}summary:hover{background:#252525}.cat-count{float:right;font-size:12px;color:#888;margin-top:3px}.dev-card{background:#222;padding:15px;border-radius:8px;margin-bottom:12px;border-left:4px solid #333}.status-Red{border-left-color:#e74c3c;background:linear-gradient(90deg,rgba(231,76,60,.1) 0%,#222 30%)}.status-Purple{border-left-color:#9b59b6;background:linear-gradient(90deg,rgba(155,89,182,.1) 0%,#222 30%)}.status-Yellow{border-left-color:#f1c40f;background:linear-gradient(90deg,rgba(241,196,15,.1) 0%,#222 30%)}.status-Green{border-left-color:#27ae60}.dot{height:12px;width:12px;border-radius:50%;display:inline-block;margin-right:12px;flex-shrink:0}.dot-Red{background:#e74c3c;box-shadow:0 0 6px #e74c3c}.dot-Purple{background:#9b59b6;box-shadow:0 0 6px #9b59b6}.dot-Yellow{background:#f1c40f;box-shadow:0 0 6px #f1c40f}.dot-Green{background:#27ae60}.dev-info{flex-grow:1;min-width:0}.dev-name{font-size:15px;font-weight:bold;color:#fff;margin-bottom:2px}.dev-custom{font-size:11px;color:#888;margin-bottom:5px}.dev-details{font-size:13px;color:#aaa}.dev-subtext{font-size:11px;color:#7f8c8d;margin-top:5px;font-style:italic}a.ping-btn{background:#34495e;color:#fff;border:1px solid #2c3e50;padding:6px 12px;font-size:12px;border-radius:4px;font-weight:bold;text-decoration:none;margin-left:10px;flex-shrink:0}a.ping-btn:hover{background:#2c3e50}.dflex{display:flex;align-items:center;width:100%}.batt-wrap{margin-top:8px;width:100%}.batt-info{display:flex;justify-content:space-between;font-size:10px;color:#aaa;margin-bottom:3px}.batt-bg{width:100%;background:#333;height:6px;border-radius:3px;overflow:hidden}.batt-fg{height:100%}.bg-grn{background:#27ae60}.bg-ylw{background:#f1c40f}.bg-red{background:#e74c3c}.shop-card{flex:1;min-width:160px;box-sizing:border-box;background:#1e1e1e;padding:15px;border-radius:8px;border-left:4px solid #9b59b6;box-shadow:0 4px 6px rgba(0,0,0,.3)}.shop-title{color:#fff;font-size:15px;display:block;margin-bottom:8px}.shop-badge{background:#9b59b6;color:#fff;font-size:11px;padding:2px 6px;border-radius:10px;float:right}.shop-list{font-size:11px;color:#888;max-height:80px;overflow-y:auto;line-height:1.4}.mute-form{display:inline-flex;align-items:center;margin-left:10px;margin-bottom:0}.mute-input{width:45px;padding:0 4px;font-size:11px;border-radius:4px 0 0 4px;border:1px solid #8e44ad;background:#111;color:#fff;height:26px;box-sizing:border-box}.mute-btn{margin-left:0;border-radius:0 4px 4px 0;background:#8e44ad;border-color:#8e44ad;height:26px;color:#fff;border-style:solid;border-width:1px;font-size:12px;font-weight:bold;cursor:pointer}"

        StringBuilder html = new StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Device Health Portal</title><meta name='viewport' content='width=device-width, initial-scale=1'><script>setTimeout(function(){window.location.reload(1);}, 60000);</script><style>${css}</style></head><body><div class='container'>")
        
        html.append("<div style='text-align:center;margin-bottom:15px;display:flex;justify-content:center;'><svg width='70' height='70' viewBox='0 0 100 100' fill='none'><circle cx='50' cy='50' r='48' fill='#151515' stroke='#333' stroke-width='1'/><path d='M 50 20 L 25 30 V 50 C 25 68 35 80 50 85 C 65 80 75 68 75 50 V 30 L 50 20 Z' stroke='#e0e0e0' stroke-width='3' stroke-linejoin='round'/><path d='M 38 52 L 46 60 L 62 42' stroke='#27ae60' stroke-width='4' stroke-linecap='round' stroke-linejoin='round'/></svg></div><h2 style='text-align:center;color:#fff;margin:0 0 5px 0;'>Estate Health Dashboard</h2><p style='text-align:center;font-size:13px;color:#888;margin-bottom:25px;'>Last Scan: ${state.lastCheckTime}</p>")
        
        html.append("<div class='summary-box'><div class='summary-card' style='border-bottom-color:#e74c3c;'><b>${critCount}</b><span>Critical</span></div><div class='summary-card' style='border-bottom-color:#f1c40f;'><b>${warnCount}</b><span>Warnings</span></div><div class='summary-card' style='border-bottom-color:#27ae60;'><b>${goodCount}</b><span>Healthy</span></div><div class='summary-card' style='border-bottom-color:#3498db;'><b>${totalCount}</b><span>Total Devices</span></div></div>")
        
        html.append("<div style='display:flex; gap:10px; margin-bottom:30px;'>")
        html.append("<a href='refresh?access_token=${state.accessToken}' class='main-btn' style='margin-bottom:0; flex:1;'>🩺 Force Health Scan</a>")
        html.append("<a href='ticket?access_token=${state.accessToken}' target='_blank' class='main-btn' style='margin-bottom:0; flex:1; background:#8e44ad;'>🖨️ Print Ticket</a>")
        html.append("</div>")

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
                def bTypeStr = dev.battType ? " - ${dev.battQty}x ${dev.battType}" : ""
                battBarHtml = "<div class='batt-wrap'><div class='batt-info'><span>🔋 ${dev.battVal}%${bTypeStr}</span><span>⏳ ${dev.estDaysLeft}</span></div><div class='batt-bg'><div class='batt-fg ${barCls}' style='width:${dev.battVal}%;'></div></div></div>"
            }
            
            def sparklineHtml = ""
            if (dev.rssiHistory && dev.rssiHistory.size() > 1) {
                def pts = []
                def minR = -100.0
                def maxR = -30.0
                def w = 50.0
                def h = 12.0
                def xStep = w / (dev.rssiHistory.size() - 1)
                
                dev.rssiHistory.eachWithIndex { rVal, idx ->
                    def x = (idx * xStep).setScale(1, BigDecimal.ROUND_HALF_UP)
                    def constrainedR = Math.max(minR, Math.min(maxR, rVal.toDouble()))
                    def y = (h - (((constrainedR - minR) / (maxR - minR)) * h)).setScale(1, BigDecimal.ROUND_HALF_UP)
                    pts << "${x},${y}"
                }
                
                def currRssi = dev.rssiHistory.last()
                def slColor = currRssi > -70 ? "#27ae60" : (currRssi > -85 ? "#f1c40f" : "#e74c3c")
                sparklineHtml = "<div style='margin-top:6px;display:flex;align-items:center;font-size:10px;color:#aaa;'><span>📶 ${currRssi} dBm</span><svg width='50' height='12' style='margin-left:8px;overflow:visible;'><polyline fill='none' stroke='${slColor}' stroke-width='1.5' points='${pts.join(' ')}'/></svg></div>"
            }

            def controlsHtml = ""
            if (dev.canPing) {
                controlsHtml += "<a href='ping?deviceId=${dev.id}&access_token=${state.accessToken}' class='ping-btn'>📡 Ping</a>"
            }
            
            if (dev.isMuted) {
                def mTime = new Date(dev.muteUntil).format("MM/dd h:mma", location.timeZone)
                controlsHtml += "<div style='display:inline-block;margin-left:10px;'><span style='color:#8e44ad;font-size:11px;margin-right:5px;'>🔕 Muted until ${mTime}</span><a href='unmute?deviceId=${dev.id}&access_token=${state.accessToken}' class='ping-btn' style='background:#8e44ad;border-color:#8e44ad;margin-left:0;'>Unmute</a></div>"
            } else if (dev.status != "Green") {
                controlsHtml += "<form action='mute' method='GET' class='mute-form'><input type='hidden' name='deviceId' value='${dev.id}'><input type='hidden' name='access_token' value='${state.accessToken}'><input type='number' name='hours' class='mute-input' placeholder='hrs' required min='1'><button type='submit' class='mute-btn'>🔕 Mute</button></form>"
            }
            
            return "<div class='dev-card status-${dev.status}'><div class='dflex'><span class='dot dot-${dev.status}'></span><div class='dev-info'><div class='dev-name'>${dev.name}</div>${customText}<div class='dev-details'>${detailsStr}</div><div class='dev-subtext'>Last Active: ${actStr}${battStr}</div>${battBarHtml}${sparklineHtml}</div>${controlsHtml}</div></div>"
        }

        // Active Issues (Unmuted only)
        def allIssues = results.findAll { (it.status == "Red" || it.status == "Purple" || it.status == "Yellow") && !it.isMuted }
        if (allIssues.size() > 0) {
            html.append("<details open><summary style='border-left-color:#e74c3c;'>⚠️ Active Issues <span class='cat-count'>${allIssues.size()} Devices</span></summary><div style='padding-top:15px;'>")
            allIssues.each { dev -> html.append(buildCard(dev)) }
            html.append("</div></details>")
        }
        
        // Muted Quarantined Menu
        def mutedDevs = results.findAll { it.isMuted }
        if (mutedDevs.size() > 0) {
            html.append("<details><summary style='border-left-color:#8e44ad;'>🔕 Muted Devices <span class='cat-count'>${mutedDevs.size()} Devices</span></summary><div style='padding-top:15px;'>")
            mutedDevs.each { dev -> html.append(buildCard(dev)) }
            html.append("</div></details>")
        }

        // Normal Categories (Unmuted only)
        def normalDevs = results.findAll { !it.isMuted }
        if (normalDevs.size() > 0) {
            def categorizedResults = [:]
            normalDevs.each { dev ->
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
            html.append("<div style='text-align:center;color:#888;padding:40px;'>No unmuted devices to display.</div>")
        }
        
        // --- BATTERY SHOPPING LIST / INVENTORY ---
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
                def devNames = devs.collect{ it.battQty > 1 ? "${it.name} (${it.battQty})" : it.name }.join("<br>• ")
                def totalBatteries = devs.sum { it.battQty?.toInteger() ?: 1 }
                
                html.append("<div class='shop-card'><b class='shop-title'>${type} <span class='shop-badge'>${totalBatteries}</span></b><div class='shop-list'>• ${devNames}</div></div>")
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
