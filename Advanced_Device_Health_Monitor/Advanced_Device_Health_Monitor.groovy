/**
 * Advanced Device Health Monitor
 *
 * Author: ShaneAllen
 *
 * Version 1.5.1
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
import groovy.json.JsonOutput

@Field static String PREV_STYLE = "margin-top: 15px; padding: 10px; background-color: #e9ecef; border-left: 4px solid #0b3b60; border-radius: 4px; font-size: 13px; line-height: 1.4;"

preferences {
    page(name: "mainPage")
    page(name: "pageSettings")
    page(name: "pageThresholds")
    page(name: "pageDeviceDetails")
    page(name: "pageBatteryLocker")
    page(name: "pageNotifications")
}

mappings {
    path("/dashboard") { action: [GET: "serveDashboardPage"] }
    path("/data") { action: [GET: "serveDataEndpoint"] }
    path("/refresh") { action: [GET: "forceRefreshEndpoint"] }
    path("/ping") { action: [GET: "pingDeviceEndpoint"] }
    path("/mute") { action: [GET: "muteDeviceEndpoint"] }
    path("/unmute") { action: [GET: "unmuteDeviceEndpoint"] }
    path("/ticket") { action: [GET: "serveMaintenanceTicket"] }
    path("/json") { action: [GET: "serveJsonEndpoint"] }
    path("/updateDevice") { action: [GET: "updateDeviceEndpoint"] }
    path("/updateFolders") { action: [GET: "updateFoldersEndpoint"] }
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
                paragraph "<i>Developer Note: JSON Export endpoint available at <b>/json?access_token=${state.accessToken}</b> for external dashboarding (Grafana/Home Assistant).</i>"
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
            def scanInProgressStr = state.isScanning ? "<br><span style='color: #3498db; font-weight:bold;'>🔄 Staggered Scan in Progress... (${state.scanQueue?.size() ?: 0} chunks remaining)</span>" : ""
            
            def statusText = "<div style='margin-bottom: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            statusText += "<b>System State:</b> ${systemState}<br><b>Network Health:</b> ${issueState}<br><b>Last Scan:</b> ${lastCheckStr}${scanInProgressStr}</div>"
            
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
            href(name: "hrefDeviceDetails", page: "pageDeviceDetails", title: "📍 Locations, Profiles & Folders", description: "Assign locations, outdoor thermal profiles, and battery configs")
            href(name: "hrefBatteryLocker", page: "pageBatteryLocker", title: "🔋 Battery Locker Inventory", description: "Manage spare inventory and view 12-month supply forecasts")
            href(name: "hrefThresholds", page: "pageThresholds", title: "📊 Monitoring Thresholds", description: "Configure global thresholds, inactivity, stuck states, and signal limits")
            href(name: "hrefNotifications", page: "pageNotifications", title: "🔔 Notification Rules & Routing", description: "Set up targeted alerts and routing")
        }
    }
}

def pageSettings() {
    dynamicPage(name: "pageSettings", title: "⚙️ Core Setup & Devices", install: false, uninstall: false) {
        section("Global Controls") {
            input "masterSwitch", "capability.switch", title: "Master Enable/Pause Switch", required: false
            input "scanInterval", "enum", title: "Automated Scan Interval", options: ["1 Hour", "3 Hours", "6 Hours", "12 Hours", "24 Hours"], defaultValue: "12 Hours", required: true
            
            input "scanChunkSize", "number", title: "Devices Processed per Staggered Chunk", defaultValue: 40, required: true
            paragraph "<i>Note: To prevent slamming the hub with 300+ device queries at once, the app scans in chunks and yields back to the hub between batches. 40-50 is the recommended size.</i>"
            
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
            input "actuatorDevices", "capability.actuator", title: "Hardwired / In-Wall Actuators (Locks, Motors)", multiple: true, required: false
        }

        section("Lighting, Switches & Power") {
            paragraph "<i>Note: Devices assigned to the Lighting & Power section will inherit the Mains-Power Override and automatically ignore ghost battery attributes from generic drivers.</i>"
            input "lightSwitches", "capability.switch", title: "In-Wall Light Switches & Dimmers", multiple: true, required: false
            input "smartPlugs", "capability.switch", title: "Smart Plugs & Outlets", multiple: true, required: false
            input "hueLights", "capability.switchLevel", title: "Smart Bulbs & LED Strips", multiple: true, required: false
        }
        
        section("External Hubs & Integrations") {
            input "hubBridges", "capability.*", title: "Hue Bridges & External Hubs", multiple: true, required: false
        }

        section("Remotes & Buttons (Sleepy Devices)") {
            paragraph "<div style='background:#fff3cd; color:#856404; padding:8px; border-radius:4px; border:1px solid #ffeeba;'><b>Developer Note:</b> These devices (like Tuya 4-Button switches) turn their radios completely off to conserve battery. They do not do routine check-ins, and they completely ignore network pings. By assigning them here, the application can apply a separate, much longer inactivity threshold so they don't falsely report as offline in your dashboard.</div>"
            input "buttonControllers", "capability.pushableButton", title: "Button Controllers & Remotes", multiple: true, required: false
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
    dynamicPage(name: "pageNotifications", title: "🔔 Notification Rules & Routing", install: false, uninstall: false) {
        section("🌍 Targeted Alert Routing") {
            paragraph "<i>Set up dedicated notification streams so alerts go to the right people without spamming the whole group. Assign these targets to specific devices or locations in the Details page via Override Alert Target.</i>"
            input "notifyShane", "capability.notification", title: "📱 Route to Shane", multiple: true, required: false
            input "notifyChristy", "capability.notification", title: "📱 Route to Christy", multiple: true, required: false
            input "notifyLeanne", "capability.notification", title: "📱 Route to Leanne", multiple: true, required: false
            input "notifyTyler", "capability.notification", title: "📱 Route to Tyler", multiple: true, required: false
            
            paragraph "<hr>"
            input "defaultCritNotifiers", "capability.notification", title: "Fallback: Notify on CRITICAL / FLAPPING issues", multiple: true, required: false
            input "defaultWarnNotifiers", "capability.notification", title: "Fallback: Notify on WARNING issues", multiple: true, required: false
            input "notifyOnResolved", "bool", title: "Notify when issues are Resolved (Return to Green)?", defaultValue: false
        }
        
        section("Smart Batching") {
            paragraph "<i>When multiple devices fail simultaneously, the app consolidates messages (up to 5 per push) to prevent overwhelming the selected targets.</i>"
        }
    }
}

def pageDeviceDetails() {
    dynamicPage(name: "pageDeviceDetails", title: "📍 Locations, Profiles & Folders", install: false, uninstall: false) {
        
        def defaultRooms = "Living Room, Kitchen, Master Bedroom, Garage, Front Yard, Backyard, Hallway, Bathroom"
        def roomListStr = settings.roomList ?: defaultRooms
        def roomOptions = roomListStr.split(',').collect{it.trim()}.findAll{it != ""}
        
        def folderOptions = ["Safety & Alarms", "Locks & Actuators", "Light Switches", "Smart Bulbs", "Smart Plugs & Outlets", "Hubs & Bridges", "Motion Sensors", "Contact Sensors", "Presence Sensors", "Climate Sensors", "Power Meters", "Light Sensors", "Vibration Sensors", "Other Sensors", "General Battery Devices", "Button Controllers", "Firmware Updates Required"]
        
        def battTypes = ["AA", "AAA", "AAAA", "C", "D", "9V", "CR2032", "CR2025", "CR2450", "CR2477", "CR1632", "CR1220", "CR2", "CR123A", "LR44", "A23", "18650", "14500", "Custom", "Rechargeable Internal"]

        section("📁 Global Folder Organization") {
            paragraph "<i>Set the exact order you want your categories to appear on the web dashboard. Enter a comma-separated list of folder names. Any unlisted folders will sort alphabetically at the bottom.</i>"
            input "customFolderOrder", "text", title: "Custom Folder Priority Order", required: false, description: "e.g., Safety & Alarms, Smart Plugs & Outlets, Climate Sensors"
        }

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
                        
                        input "overrideBattThresh_${dev.id}", "number", title: "${dev.displayName} - Override Global Critical Battery %", range: "1..99", required: false
                        input "outdoorProfile_${dev.id}", "bool", title: "${dev.displayName} - Use Outdoor Thermal Profile (Dampens EWMA temp swings)", defaultValue: false
                        
                        input "battType_${dev.id}", "enum", title: "${dev.displayName} - Battery Type", options: battTypes, required: false
                        input "customBattType_${dev.id}", "text", title: "${dev.displayName} - Custom Type (If 'Custom' selected above)", required: false
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

def pageBatteryLocker() {
    dynamicPage(name: "pageBatteryLocker", title: "🔋 Battery Locker Inventory", install: false, uninstall: false) {
        def allDevs = getAllMonitoredDevices()
        def estateBatts = [:]

        allDevs.each { dev ->
            def rawType = settings["battType_${dev.id}"]
            def cType = settings["customBattType_${dev.id}"]
            def bType = (rawType == "Custom" && cType) ? cType : (rawType ?: "")

            if (bType && bType != "" && bType != "Rechargeable Internal" && bType != "Mains / Hardwired") {
                def qty = settings["battQty_${dev.id}"] ?: 1
                if (!estateBatts[bType]) estateBatts[bType] = 0
                estateBatts[bType] += qty
            }
        }

        if (estateBatts.size() > 0) {
            section("Estate Battery Overview & Spares") {
                paragraph "<i>This is your Battery Locker. It dynamically calculates every battery required to power your entire smart home. Enter your current spare inventory below so the dashboard can warn you when it's time to re-order.</i>"

                def sortedTypes = estateBatts.keySet().sort()
                sortedTypes.each { type ->
                    def safeTypeName = type.replaceAll("[^a-zA-Z0-9]+", "_")
                    input "spareBatt_${safeTypeName}", "number", title: "Spare ${type}s on hand (Estate requires: ${estateBatts[type]})", defaultValue: 0, required: false
                }
            }
        } else {
            section("No Batteries Configured") {
                paragraph "You have not assigned any battery types to your devices in the 'Locations, Descriptions & Folders' menu."
            }
        }
    }
}

def pageThresholds() {
    dynamicPage(name: "pageThresholds", title: "📊 Monitoring Thresholds", install: false, uninstall: false) {
        
        section("Auto-Heal Power Cycling") {
            paragraph "<i>If a smart plug or switch goes offline and standard pings fail, the system can attempt to toggle its physical power state (Off-On / On-Off) to force it back onto the mesh. This is limited to once every 24 hours per device.</i>"
            input "optOutPowerCycle", "capability.switch", title: "Devices to EXCLUDE from Auto-Power Cycling", multiple: true, required: false
        }
        
        section("Battery Health & Parasitic Anomaly Controls") {
            input "enableBatteryCheck", "bool", title: "Enable Battery Monitoring?", defaultValue: true, submitOnChange: true
            if (enableBatteryCheck) {
                input "batteryThreshold", "number", title: "Global Critical Battery Threshold (%)", defaultValue: 20, range: "1..100", required: true
                input "battCalcThreshold", "number", title: "Minimum % Drop for Battery Forecasting", defaultValue: 5, range: "1..50", required: true
                
                input "enableParasiticCheck", "bool", title: "Enable Parasitic Battery Drain Detection?", defaultValue: true, submitOnChange: true
                if (enableParasiticCheck) {
                    input "parasiticThreshold", "number", title: "Max Allowable Battery Drop / 24 Hours (%)", defaultValue: 10, range: "1..50", required: true
                    paragraph "<i>Triggers a yellow warning instantly if a device drops faster than this percentage in 24 hours, alerting you to internal hardware shorts or mesh pairing loops.</i>"
                }
            }
        }
        
        section("Device Inactivity (Dead Devices)") {
            input "enableInactivityCheck", "bool", title: "Enable Inactivity Monitoring?", defaultValue: true, submitOnChange: true
            if (enableInactivityCheck) {
                paragraph "<i>Flags devices that have not reported any activity or status updates in the specified timeframe.</i>"
                input "inactivityThreshold", "number", title: "Standard Inactivity Threshold (Hours)", defaultValue: 24, required: true
                
                input "buttonInactivityThreshold", "number", title: "Sleepy Device (Remotes/Buttons) Threshold (Hours)", defaultValue: 168, required: true
                paragraph "<i>Note: 168 Hours = 7 Days. Remotes inactive for 75% of this extended threshold will trigger a Yellow Warning.</i>"
                
                input "ignoreMainsInactivity", "bool", title: "Ignore Inactivity for Mains-Powered Devices?", defaultValue: true, submitOnChange: true
                paragraph "<i>Prevents lights, switches, and plugs from showing 'Offline' simply because they haven't been turned on recently. They will only flag if their network health check explicitly fails.</i>"
                
                input "ignoredInactivityDevices", "capability.*", title: "Ignore Inactivity completely for these Devices", multiple: true, required: false
            }
        }
        
        section("Stale State Detection (Stuck Sensors)") {
            input "enableStuckCheck", "bool", title: "Enable Stale State Detection?", defaultValue: true, submitOnChange: true
            if (enableStuckCheck) {
                paragraph "<i>Detects hardware lockups where a sensor is technically online, but its physical state is frozen (e.g., stuck on 'Active' or 'Open').</i>"
                input "stuckMotionHours", "number", title: "Stuck 'Active' Threshold (Hours)", defaultValue: 2, required: true
                input "stuckContactHours", "number", title: "Stuck 'Open' Threshold (Hours)", defaultValue: 24, required: true
                input "ignoredStuckDevices", "capability.*", title: "Ignore Stuck State for these Devices", multiple: true, required: false
                paragraph "<i>Note: Useful for multipurpose sensors used only for acceleration/temperature without a magnet.</i>"
            }
        }

        section("Wi-Fi Signal Quality") {
            input "enableWifiSignalCheck", "bool", title: "Enable Wi-Fi Monitoring?", defaultValue: false, submitOnChange: true
            if (enableWifiSignalCheck) {
                paragraph "<i>Checks Wi-Fi devices reporting 'wifiSignal' or 'rssi'. Tracked via inline sparklines.</i>"
                input "wifiRssiThreshold", "number", title: "Critical Minimum Wi-Fi RSSI (e.g. -75)", defaultValue: -75, range: "-100..0", required: true
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

        section("Spatial Radio Interference Detection") {
            input "enableInterferenceCheck", "bool", title: "Enable Topology/Interference Analysis?", defaultValue: false, submitOnChange: true
            if (enableInterferenceCheck) {
                paragraph "<i>Calculates an 'Interference Index' by grouping RSSI drops and flap events by physical location to detect localized RF black holes.</i>"
                input "chattyDeviceThreshold", "number", title: "Max State Changes / Hour (Detects Mesh Saturation)", defaultValue: 100, required: true
            }
        }

        section("Firmware Consistency Auditor") {
            input "enableFirmwareAuditor", "bool", title: "Enable Automated Firmware Audits?", defaultValue: true
            paragraph "<i>Groups your identical hardware endpoints together and automatically identifies firmware trailing anomalies across the estate.</i>"
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
    if (state.batteryHistory == null) state.batteryHistory = [:]
    if (state.batteryEwma == null) state.batteryEwma = [:]
    if (state.lastBatteryTime == null) state.lastBatteryTime = [:]
    
    if (state.previousStatus == null) state.previousStatus = [:]
    if (state.flapHistory == null) state.flapHistory = [:]
    if (state.rssiHistory == null) state.rssiHistory = [:]
    
    if (state.muteExpirations == null) state.muteExpirations = [:]
    if (state.lastPowerCycle == null) state.lastPowerCycle = [:]
    
    if (state.isScanning == null) state.isScanning = false
    if (state.scanQueue == null) state.scanQueue = []
    if (state.tempResults == null) state.tempResults = []
    if (state.pendingAlerts == null) state.pendingAlerts = []
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
        if (settings.smartPlugs) subscribe(settings.smartPlugs, "switch", liveUpdateHandler)
        if (settings.lightSwitches) subscribe(settings.lightSwitches, "switch", liveUpdateHandler)
        if (settings.hueLights) subscribe(settings.hueLights, "switch", liveUpdateHandler)
        if (settings.temperatureSensors) subscribe(settings.temperatureSensors, "temperature", liveUpdateHandler)
        if (settings.humiditySensors) subscribe(settings.humiditySensors, "humidity", liveUpdateHandler)
        
        app.addToHistory("SYSTEM: Live Auto-Updating is active.")
    }
    
    app.addToHistory("SYSTEM: Initialization complete. Monitoring schedule set to ${sInt}.")
    runIn(2, "runHealthCheck")
}

def liveUpdateHandler(evt) {
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") return
    if (state.isScanning) {
        runIn(30, "runHealthCheck")
    } else {
        runIn(15, "runHealthCheck")
    }
}

def appButtonHandler(btn) {
    ensureStateMaps()
    if (btn != "btnRefresh") {
        app.addToHistory("SYSTEM: Action triggered via UI Dashboard - ${btn}")
    }
    
    if (btn == "btnCreateChild") {
        createChildDevice()
    } else if (btn == "btnRunCheck") {
        runHealthCheck()
    } else if (btn == "btnBulkApplyLoc") {
        if (settings.bulkLoc && settings.bulkDevs) {
            def devIds = settings.bulkDevs instanceof List ? settings.bulkDevs : [settings.bulkDevs]
            devIds.each { dId ->
                app.updateSetting("loc_${dId}", [type: "enum", value: settings.bulkLoc])
            }
            app.removeSetting("bulkDevs") 
            app.addToHistory("SYSTEM: Bulk applied location '${settings.bulkLoc}' to ${devIds.size()} devices.")
        }
    }
}

def createChildDevice() {
    def child = getChildDevice("health_monitor_child")
    if (!child) {
        try {
            addChildDevice("ShaneAllen", "Advanced Device Health Monitor Information", "health_monitor_child", null, [name: "Advanced Device Health Monitor Information", label: "Device Health Dashboard"])
            app.addToHistory("SYSTEM: Child HTML device successfully created.")
        } catch (e) {
            log.error "Failed to create child device. Ensure the driver is installed. Error: ${e}"
            app.addToHistory("ERROR: Failed to create child device. Is the driver installed?")
        }
    } else {
        app.addToHistory("SYSTEM: Child device already exists.")
    }
}

def getAllMonitoredDevices() {
    def allDevices = []
    if (settings.batteryDevices) allDevices.addAll(settings.batteryDevices)
    if (settings.actuatorDevices) allDevices.addAll(settings.actuatorDevices)
    if (settings.smartPlugs) allDevices.addAll(settings.smartPlugs)
    if (settings.lightSwitches) allDevices.addAll(settings.lightSwitches)
    if (settings.hueLights) allDevices.addAll(settings.hueLights)
    if (settings.hubBridges) allDevices.addAll(settings.hubBridges)
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
    if (settings.buttonControllers) allDevices.addAll(settings.buttonControllers)
    
    return allDevices.flatten().findAll { it != null }.unique { it.id }
}

def autoHealNetwork() {
    ensureStateMaps()
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") return
    
    if (isQuietTime()) {
        app.addToHistory("AUTO-HEAL: Skipped scheduled network ping due to active Quiet Hours.")
        return
    }
    
    def allDevs = getAllMonitoredDevices()
    def healedCount = 0
    def problemDevIds = state.dashboardData?.findAll { it.status == "Red" || it.status == "Purple" || it.status == "Yellow" }?.collect { it.id } ?: []
    def nowMs = new Date().time
    
    allDevs.each { dev ->
        if (problemDevIds.contains(dev.id)) {
            def pingSuccess = false
            if (dev.hasCommand("refresh")) { 
                try { dev.refresh(); healedCount++; pingSuccess = true } catch(e){} 
            } else if (dev.hasCommand("ping")) { 
                try { dev.ping(); healedCount++; pingSuccess = true } catch(e){} 
            }

            if (dev.hasCommand("on") && dev.hasCommand("off")) {
                def isOptOut = settings.optOutPowerCycle?.find { it.id == dev.id } != null
                def lastCycle = state.lastPowerCycle[dev.id] ?: 0
                
                if (!isOptOut && (nowMs - lastCycle > 86400000)) { 
                    def currState = dev.currentValue("switch")
                    if (currState == "on") {
                        try {
                            dev.off()
                            runIn(5, "turnDevOn", [data: [id: dev.id]])
                            state.lastPowerCycle[dev.id] = nowMs
                            app.addToHistory("AUTO-HEAL: Power cycled offline actuator ${dev.displayName} (Off -> On).")
                        } catch(e) {}
                    } else {
                        try {
                            dev.on()
                            runIn(5, "turnDevOff", [data: [id: dev.id]])
                            state.lastPowerCycle[dev.id] = nowMs
                            app.addToHistory("AUTO-HEAL: Power cycled offline actuator ${dev.displayName} (On -> Off).")
                        } catch(e) {}
                    }
                }
            }
        }
    }
    
    if (healedCount > 0) {
        app.addToHistory("AUTO-HEAL: Scheduled self-heal executed. Commands sent to ${healedCount} problematic devices.")
        runIn(30, "runHealthCheck", [overwrite: true])
    }
}

def turnDevOn(data) { def dev = getAllMonitoredDevices().find{it.id == data.id}; if(dev) dev.on() }
def turnDevOff(data) { def dev = getAllMonitoredDevices().find{it.id == data.id}; if(dev) dev.off() }

def runHealthCheck() {
    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") {
        app.addToHistory("SCAN: Aborted. Master switch is OFF.")
        return
    }
    
    def nowMs = new Date().time
    
    if (state.isScanning && state.scanStartTime && (nowMs - state.scanStartTime > 60000)) {
        log.warn "Health Monitor: Previous scan appears stuck. Resetting."
        state.isScanning = false
    }

    if (state.isScanning) {
        log.info "Health Monitor: Scan already in progress. Skipping duplicate request."
        return
    }
    
    ensureStateMaps()
    state.isScanning = true
    state.scanStartTime = nowMs
    state.tempResults = []
    state.pendingAlerts = []
    
    if (state.muteExpirations) {
        def expired = state.muteExpirations.findAll { k, v -> v < nowMs }.collect { it.key }
        expired.each { state.muteExpirations.remove(it) }
    }
    
    def deviceMap = [:]
    def addDev = { list, category ->
        list?.each { d ->
            if (d != null) {
                if (!deviceMap[d.id]) {
                    deviceMap[d.id] = []
                }
                if (!deviceMap[d.id].contains(category)) {
                    deviceMap[d.id] << category
                }
            }
        }
    }

    addDev(settings.smokeDetectors, "Safety & Alarms")
    addDev(settings.waterSensors, "Safety & Alarms")
    addDev(settings.valveDevices, "Safety & Alarms")
    addDev(settings.actuatorDevices, "Locks & Actuators")
    addDev(settings.smartPlugs, "Smart Plugs & Outlets")
    addDev(settings.lightSwitches, "Light Switches")
    addDev(settings.hueLights, "Smart Bulbs")
    addDev(settings.hubBridges, "Hubs & Bridges")
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
    addDev(settings.buttonControllers, "Button Controllers")
    
    state.scanQueue = deviceMap.collect { k, v -> [id: k, categories: v] }
    
    if (state.scanQueue.size() > 0) {
        app.addToHistory("SCAN: Starting staggered scan for ${state.scanQueue.size()} devices.")
        runIn(1, "processScanChunk")
    } else {
        finalizeScan()
    }
}

def processScanChunk() {
    if (!state.isScanning) return
    
    def queue = state.scanQueue ?: []
    if (queue.size() == 0) {
        finalizeScan()
        return
    }
    
    def chunkSize = (settings.scanChunkSize ?: 40).toInteger()
    def chunk = queue.take(chunkSize)
    state.scanQueue = queue.drop(chunkSize)
    
    def allDevs = getAllMonitoredDevices()
    def nowMs = new Date().time
    def ignoreListIds = settings.ignoredInactivityDevices?.collect { it.id } ?: []
    def ignoreStuckListIds = settings.ignoredStuckDevices?.collect { it.id } ?: []
    
    chunk.each { qItem ->
        def dev = allDevs.find { it.id == qItem.id }
        if (!dev) return 
        
        def finalCategories = []
        def overrideFolders = settings["folder_${dev.id}"]
        if (overrideFolders) {
            finalCategories = overrideFolders instanceof List ? overrideFolders : [overrideFolders]
        } else {
            finalCategories = qItem.categories
        }
        
        def isMuted = state.muteExpirations && state.muteExpirations[dev.id] != null
        def muteUntil = isMuted ? state.muteExpirations[dev.id] : null
        
        try {
            def devHealth = "Green"
            def msgs = []
            def battChangedStr = ""
            def estDaysLeftStr = "Calculating..."
            def rawBattVal = null
            def rawEwmaRate = 0.0
            def dRssiHistory = []
            
            def dManufacturer = "Generic/Unknown"
            try { dManufacturer = dev.getDataValue("manufacturer") ?: "Generic/Unknown" } catch(ignore){}
            
            def dModel = "Generic/Unknown"
            try { dModel = dev.getDataValue("model") ?: "Generic/Unknown" } catch(ignore){}
            
            def dFirmware = "Unknown"
            try { dFirmware = dev.getDataValue("application") ?: dev.getDataValue("softwareBuild") ?: "Unknown" } catch(ignore){}
            
            def dTypeName = "Unknown"
            try { dTypeName = dev.typeName ?: "Unknown" } catch(ignore){}
            
            def lastActiveDate = dev.getLastActivity()
            def lastActiveStr = lastActiveDate ? lastActiveDate.format("MM/dd/yy h:mm a", location.timeZone) : "Unknown"
            
            if (dev.hasAttribute("healthStatus")) {
                def hStat = dev.currentValue("healthStatus")
                if (hStat && hStat.toString().toLowerCase() == "offline") {
                    devHealth = "Red"
                    msgs << "System reports OFFLINE"
                }
            }
            
            def isMainsPowered = settings.actuatorDevices?.find { it.id == dev.id } != null || 
                                 settings.smartPlugs?.find { it.id == dev.id } != null || 
                                 settings.lightSwitches?.find { it.id == dev.id } != null || 
                                 settings.hueLights?.find { it.id == dev.id } != null || 
                                 settings.hubBridges?.find { it.id == dev.id } != null
            
            if (settings.enableBatteryCheck && dev.hasAttribute("battery") && !isMainsPowered) {
                def batt = dev.currentValue("battery")
                if (batt != null && batt.toString().isNumber()) {
                    def bVal = batt.toInteger()
                    rawBattVal = bVal
                    
                    def isOutdoor = settings["outdoorProfile_${dev.id}"] == "true" || settings["outdoorProfile_${dev.id}"] == true
                    
                    long changedMs = state.batteryChangeDates[dev.id] ?: nowMs
                    long lastTime = state.lastBatteryTime[dev.id] ?: changedMs
                    
                    if (nowMs > lastTime) {
                        def lastB = state.lastBatteryLevels[dev.id] ?: bVal
                        if (bVal < lastB) { 
                            long elapsedMs = nowMs - lastTime
                            double daysElapsed = elapsedMs / 86400000.0
                            if (daysElapsed > 0.05) { 
                                double currentDropRate = (lastB - bVal) / daysElapsed
                                double alpha = isOutdoor ? 0.05 : 0.3 
                                double oldEwma = state.batteryEwma[dev.id] != null ? state.batteryEwma[dev.id] : currentDropRate
                                double newEwma = (alpha * currentDropRate) + ((1.0 - alpha) * oldEwma)
                                state.batteryEwma[dev.id] = newEwma
                            }
                        }
                    }

                    if (state.lastBatteryLevels[dev.id] != null && bVal > (state.lastBatteryLevels[dev.id] + 5)) {
                        state.batteryChangeDates[dev.id] = nowMs
                        state.batteryEwma[dev.id] = null 
                    }
                    
                    state.lastBatteryLevels[dev.id] = bVal
                    state.lastBatteryTime[dev.id] = nowMs
                    
                    if (settings.enableParasiticCheck) {
                        if (!state.batteryHistory) state.batteryHistory = [:]
                        if (!state.batteryHistory[dev.id]) state.batteryHistory[dev.id] = []
                        
                        state.batteryHistory[dev.id] << [time: nowMs, val: bVal]
                        state.batteryHistory[dev.id] = state.batteryHistory[dev.id].findAll { nowMs - it.time <= 86400000 }
                        
                        if (state.batteryHistory[dev.id].size() > 1) {
                            def oldestReading = state.batteryHistory[dev.id].first()
                            def dropInDay = oldestReading.val - bVal
                            def maxDropAllowed = settings.parasiticThreshold ?: 10
                            
                            if (dropInDay >= maxDropAllowed) {
                                if (devHealth != "Red" && devHealth != "Purple") devHealth = "Yellow"
                                msgs << "Parasitic Drain Detected (-${dropInDay}% / 24h)"
                            }
                        }
                    }
                    
                    if (state.batteryChangeDates[dev.id]) {
                        battChangedStr = " | Batt Replaced: " + new Date(state.batteryChangeDates[dev.id]).format("MM/dd/yy", location.timeZone)
                        
                        def ewmaRate = state.batteryEwma[dev.id]
                        if (ewmaRate != null && ewmaRate > 0.01) {
                            rawEwmaRate = ewmaRate
                            int daysLeft = (bVal / ewmaRate).toInteger()
                            estDaysLeftStr = "~${daysLeft} Days"
                        } else {
                            def calcThresh = settings.battCalcThreshold ?: 5
                            estDaysLeftStr = "Calculating Trend..."
                        }
                    }

                    def specificThresh = settings["overrideBattThresh_${dev.id}"]
                    def thresh = (specificThresh != null && specificThresh.toString().isNumber()) ? specificThresh.toInteger() : (settings.batteryThreshold ?: 20)
                    
                    if (bVal <= thresh) {
                        devHealth = "Red"
                        msgs << "Battery Critical"
                    } else if (bVal <= thresh + 15) {
                        if (devHealth != "Red" && !msgs.any{it.contains("Parasitic")}) devHealth = "Yellow"
                        msgs << "Battery Low"
                    } else {
                        if (!msgs.any{it.contains("Parasitic")}) msgs << "Battery OK"
                    }
                }
            }
            
            def skipInactivity = ignoreListIds.contains(dev.id) || (settings.ignoreMainsInactivity && isMainsPowered)
            
            if (settings.enableInactivityCheck && !skipInactivity) {
                if (lastActiveDate) {
                    def diffHours = (nowMs - lastActiveDate.time) / 3600000
                    
                    def isSleepy = settings.buttonControllers?.find { it.id == dev.id } != null
                    def thresh = isSleepy ? (settings.buttonInactivityThreshold ?: 168) : (settings.inactivityThreshold ?: 24)
                    
                    if (diffHours > thresh) {
                        devHealth = "Red"
                        msgs << "Offline (${diffHours.toInteger()} hrs)"
                    } else if (diffHours > (thresh * 0.75)) {
                        if (devHealth != "Red") devHealth = "Yellow"
                        msgs << "Inactive (${diffHours.toInteger()} hrs)"
                    } else {
                        if (msgs.size() == 0 || (msgs.size() == 1 && msgs[0].contains("Battery OK"))) {
                            if (!msgs.contains("Active")) msgs << "Active"
                        }
                    }
                } else {
                    devHealth = "Red"
                    msgs << "No Activity Data Found"
                }
            } else if (msgs.size() == 0 || (msgs.size() == 1 && msgs[0].contains("Battery OK"))) {
                if (!msgs.contains("Active")) msgs << "Monitoring Active"
            }
            
            if (settings.enableStuckCheck && !ignoreStuckListIds.contains(dev.id)) {
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
            
            if (settings.enableWifiSignalCheck) {
                if (dev.hasAttribute("wifiSignal") || dev.hasAttribute("rssi")) {
                    if (!dev.hasAttribute("lqi")) { 
                        def wifiRssi = dev.currentValue("wifiSignal") ?: dev.currentValue("rssi")
                        if (wifiRssi != null && wifiRssi.toString().isNumber()) {
                            def rVal = wifiRssi.toInteger()
                            
                            if (!state.rssiHistory[dev.id]) state.rssiHistory[dev.id] = []
                            state.rssiHistory[dev.id] << rVal
                            if (state.rssiHistory[dev.id].size() > 10) state.rssiHistory[dev.id].remove(0)
                            dRssiHistory = state.rssiHistory[dev.id]
                            
                            def thresh = settings.wifiRssiThreshold ?: -75
                            if (rVal <= thresh) {
                                devHealth = "Red"
                                msgs << "Weak Wi-Fi (${rVal} dBm)"
                            } else if (rVal <= thresh + 10) {
                                if (devHealth != "Red") devHealth = "Yellow"
                                msgs << "Fair Wi-Fi (${rVal} dBm)"
                            }
                        }
                    }
                }
            }

            if (settings.enableSignalCheck) {
                if (dev.hasAttribute("rssi") && dev.hasAttribute("lqi")) { 
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
            
            if (oldStatus != null && oldStatus != devHealth) {
                if (!isMuted) {
                    def msgToSend = null
                    def level = null
                    
                    if (devHealth == "Red") { msgToSend = "🔴 CRITICAL: ${dev.displayName} is Offline/Critical"; level = "Critical" }
                    else if (devHealth == "Purple") { msgToSend = "🟣 FLAPPING: ${dev.displayName} is dropping repeatedly"; level = "Critical" }
                    else if (devHealth == "Yellow") { msgToSend = "🟡 WARNING: ${dev.displayName} is showing degradation"; level = "Warning" }
                    else if (devHealth == "Green" && settings.notifyOnResolved) { msgToSend = "🟢 RESOLVED: ${dev.displayName} returned to nominal"; level = "Resolved" }
                    
                    if (msgToSend) {
                        state.pendingAlerts << [deviceId: dev.id, devName: dev.displayName, msg: msgToSend, level: level]
                    }
                } else {
                    app.addToHistory("MUTE: Suppressed status change alert for ${dev.displayName} (Device is currently muted).")
                }
            }
            
            state.previousStatus[dev.id] = devHealth
            msgs.removeAll { it == "Battery OK" && msgs.size() > 1 } 
            
            def canPingDev = dev.hasCommand("refresh") || dev.hasCommand("ping")
            def devLoc = settings["loc_${dev.id}"] ?: ""
            def devDesc = settings["desc_${dev.id}"] ?: ""
            def batQty = settings["battQty_${dev.id}"] ?: 1
            
            def rawBatType = settings["battType_${dev.id}"] ?: ""
            def cBatType = settings["customBattType_${dev.id}"] ?: ""
            def batType = (rawBatType == "Custom" && cBatType) ? cBatType : rawBatType
            
            def isOutdoorUI = settings["outdoorProfile_${dev.id}"] == "true" || settings["outdoorProfile_${dev.id}"] == true
            def overrideUI = settings["overrideBattThresh_${dev.id}"] ?: ""

            state.tempResults << [
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
                ewmaRate: rawEwmaRate,
                estDaysLeft: estDaysLeftStr,
                battType: batType,
                battQty: batQty,
                isMuted: isMuted,
                muteUntil: muteUntil,
                rssiHistory: dRssiHistory,
                manufacturer: dManufacturer,
                model: dModel,
                firmware: dFirmware,
                driverName: dTypeName,
                isMains: isMainsPowered,
                isOutdoor: isOutdoorUI,
                battThreshOverride: overrideUI
            ]
            
        } catch (e) {
            log.warn "Health Monitor: Error scanning device ${dev.displayName} - ${e}"
        }
    }
    
    if (state.scanQueue.size() > 0) {
        runIn(2, "processScanChunk") 
    } else {
        runIn(1, "finalizeScan")
    }
}

def finalizeScan() {
    if (!state.isScanning) return
    
    def results = state.tempResults ?: []
    
    if (settings.enableFirmwareAuditor != false) {
        results = runFirmwareConsistencyCheck(results)
    }
    
    results = analyzeSpatialInterference(results)
    
    def alertQueue = [:]
    state.pendingAlerts?.each { alert ->
        def targets = settings["notifyOverride_${alert.deviceId}"]
        if (!targets) {
            targets = (alert.level == "Critical") ? settings.defaultCritNotifiers : (alert.level == "Warning" ? settings.defaultWarnNotifiers : (settings.defaultCritNotifiers ?: settings.defaultWarnNotifiers))
        }
        
        targets?.each { t ->
            if (t) {
                if (!alertQueue[t.id]) alertQueue[t.id] = [device: t, msgs: []]
                alertQueue[t.id].msgs << alert.msg
            }
        }
    }
    
    alertQueue.each { tId, data ->
        def tDev = data.device
        def messages = data.msgs
        if (messages.size() > 0) {
            def chunked = messages.collate(5) 
            def totalChunks = chunked.size()
            
            chunked.eachWithIndex { chunk, idx ->
                def header = totalChunks > 1 ? "Device Health Alert (${idx+1}/${totalChunks}):\n" : "Device Health Alert:\n"
                def combinedMsg = header + chunk.join("\n")
                tDev.deviceNotification(combinedMsg)
                pauseExecution(500) 
            }
            app.addToHistory("NOTIFIED: Sent ${messages.size()} batched alerts to ${tDev.displayName}.")
        }
    }
    
    def order = ["Purple": 1, "Red": 2, "Yellow": 3, "Blue": 4, "Green": 5]
    results = results.sort { a, b -> 
        def c1 = order[a.status] <=> order[b.status]
        if (c1 != 0) return c1
        return a.name <=> b.name
    }
    
    state.dashboardData = results
    state.lastCheckTime = new Date().format("MM/dd/yyyy h:mm a", location.timeZone)
    
    def critCount = results.count { (it.status == "Red" || it.status == "Purple") && !it.isMuted }
    def warnCount = results.count { it.status == "Yellow" && !it.isMuted }
    
    updateChildHtmlDashboard(results, critCount, warnCount)
    
    state.isScanning = false
    state.scanStartTime = null
    state.tempResults = []
    state.scanQueue = []
    state.pendingAlerts = []
    
    app.addToHistory("SCAN: Staggered scan sequence completed successfully.")
}

def runFirmwareConsistencyCheck(results) {
    def hardwareGroups = results.groupBy { "${it.manufacturer}||${it.model}" }
    
    hardwareGroups.each { signature, devList ->
        if (devList.size() < 3) return 
        
        def fwCounts = devList.countBy { it.firmware }
        def primaryFw = fwCounts.max { it.value }.key
        
        devList.each { dev ->
            if (dev.firmware != primaryFw && dev.firmware != "Unknown") {
                if (dev.status == "Green") dev.status = "Blue" 
                dev.messages.removeAll { it == "Monitoring Active" || it == "Active" }
                if (!dev.messages.any { it.contains("Mismatch") }) {
                    dev.messages << "FW Mismatch (Has ${dev.firmware} vs Standard ${primaryFw})"
                }
                dev.categories = ["Firmware Updates Required"] 
            }
        }
    }
    return results
}

def analyzeSpatialInterference(results) {
    def offlineRoutersByLoc = [:]
    results.each { r ->
        if (r.status == "Red" && r.isMains) {
            def loc = r.customLoc ?: "Unassigned"
            if (!offlineRoutersByLoc[loc]) offlineRoutersByLoc[loc] = []
            offlineRoutersByLoc[loc] << r.name
        }
    }
    results.each { r ->
        if (!r.isMains && r.battType && r.battType != "" && r.battType != "N/A" && r.battType != "Mains / Hardwired") {
            def loc = r.customLoc ?: "Unassigned"
            if (offlineRoutersByLoc[loc] && offlineRoutersByLoc[loc].size() > 0) {
                if (r.status == "Green" || r.status == "Blue") r.status = "Yellow"
                if (!r.messages.any{it.contains("Mesh Risk")}) {
                    r.messages << "⚠️ Mesh Risk: Repeater Offline (${offlineRoutersByLoc[loc][0]})"
                }
            }
        }
    }

    if (!settings.enableInterferenceCheck) return results
    
    def groupedByLoc = results.groupBy { it.customLoc ?: "Unassigned" }
    groupedByLoc.each { loc, devs ->
        if (loc == "Unassigned" || loc == "") return
        def degradedDevs = devs.findAll { it.status == "Purple" || it.messages.any { m -> m.contains("Weak Signal") || m.contains("Critical LQI") || m.contains("Weak Wi-Fi") } }
        
        if (degradedDevs.size() >= 2) {
            def devNames = degradedDevs.collect { it.name }.join(", ")
            app.addToHistory("INTERFERENCE: RF black hole detected in ${loc}. Affected: ${devNames}")
        }
    }
    
    return results
}

def getHubTelemetry() {
    def telemetry = [
        memoryTotal: "Unknown",
        memoryFree: "Unknown",
        dbSize: "Unknown",
        uptime: "Unknown",
        firmware: location.hubs[0]?.firmwareVersionString ?: "Unknown"
    ]
    
    def rawUp = location.hubs[0]?.uptime
    if (rawUp != null && rawUp.toString().isNumber()) {
        long t = rawUp.toLong()
        long d = t / 86400
        long h = (t % 86400) / 3600
        long m = ((t % 86400) % 3600) / 60
        long s = t % 60
        telemetry.uptime = "${d}d ${h}h ${m}m ${s}s"
    }
    
    try {
        httpGet([uri: "http://127.0.0.1:8080/hub/advanced/freeOSMemoryHistory", timeout: 5]) { resp ->
            if (resp.success && resp.data) {
                def lines = resp.data.toString().split("\n")
                if (lines.size() > 1) {
                    def latest = lines.last().split(",")
                    if (latest.size() >= 3) {
                        telemetry.memoryTotal = latest[1].trim()
                        telemetry.memoryFree = latest[2].trim()
                    }
                }
            }
        }
    } catch (e) { log.warn "Could not fetch Hub Memory: ${e}" }

    try {
        httpGet([uri: "http://127.0.0.1:8080/hub/advanced/databaseSize", timeout: 5]) { resp ->
            if (resp.success) telemetry.dbSize = resp.data.toString() + " MB"
        }
    } catch (e) { log.warn "Could not fetch DB Size: ${e}" }

    return telemetry
}

def serveJsonEndpoint() {
    try {
        ensureStateMaps()
        def payload = [
            timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ssZ", location.timeZone),
            telemetry: getHubTelemetry(),
            estateHealth: state.dashboardData ?: []
        ]
        return render(contentType: "application/json", data: JsonOutput.toJson(payload), status: 200)
    } catch (e) {
        log.error "JSON Export Error: ${e}"
        return render(contentType: "application/json", data: '{"error":"Data unavailable"}', status: 500)
    }
}

// Re-written to dynamically adapt to Strings, GStrings, or any other Object
def addToHistory(msg) {
    def msgStr = msg.toString()
    def cleanMsg = msgStr.replaceAll(/<.*?>/, "")
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    log.info "SYSTEM: [${timestamp}] ${cleanMsg}"
    
    if (!state.historyLog) state.historyLog = []
    state.historyLog.add(0, "<span style='color: #888; font-size: 11px;'>[${new Date().format("h:mm a", location.timeZone)}]</span> <b>${cleanMsg}</b>")
    if (state.historyLog.size() > 30) state.historyLog = state.historyLog.take(30)
}

def getRedirectHtml(delayMs, msgText) {
    return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'><script>setTimeout(function(){window.location.href=\"dashboard?access_token=${state.accessToken}\";}, ${delayMs});</script></head><body style=\"background:#0d0d0d;color:#fff;text-align:center;padding-top:100px;font-family:sans-serif;\"><h3>🔄 Standard BMS Syncing...</h3><p style='color:#666;'>${msgText}</p></body></html>"
}

// --------------------------------------------------------------------------------------
// SPA AJAX ENDPOINTS (Eliminates 504 and 403 Cloud Timeouts)
// --------------------------------------------------------------------------------------
def serveDataEndpoint() {
    try {
        ensureStateMaps()
        def nowMs = new Date().time
        def results = state.dashboardData ?: []
        
        if (state.muteExpirations) {
            def keysToRemove = []
            state.muteExpirations.each { k, v -> if (v < nowMs) keysToRemove << k }
            if (keysToRemove) keysToRemove.each { state.muteExpirations.remove(it) }
            
            results.each { dev ->
                dev.isMuted = state.muteExpirations.containsKey(dev.id)
                dev.muteUntil = dev.isMuted ? state.muteExpirations[dev.id] : null
            }
        }

        def customFolderOrderStr = settings.customFolderOrder ?: ""
        def defaultRooms = "Living Room, Kitchen, Master Bedroom, Garage, Front Yard, Backyard, Hallway, Bathroom"
        def roomOptions = (settings.roomList ?: defaultRooms).split(',').collect{it.trim()}.findAll{it != ""}
        def battTypes = ["AA", "AAA", "AAAA", "C", "D", "9V", "CR2032", "CR2025", "CR2450", "CR2477", "CR1632", "CR1220", "CR2", "CR123A", "LR44", "A23", "18650", "14500", "Custom", "Rechargeable Internal"]

        def spareBatts = [:]
        results.each { dev ->
            if (dev.battType && dev.battType != "Rechargeable Internal" && dev.battType != "Mains / Hardwired") {
                def safeType = dev.battType.replaceAll("[^a-zA-Z0-9]+", "_")
                if (!spareBatts[dev.battType]) spareBatts[dev.battType] = settings["spareBatt_${safeType}"] ?: 0
            }
        }

        def payload = [
            token: state.accessToken,
            lastCheck: state.lastCheckTime ?: "Never",
            customFolderOrder: customFolderOrderStr,
            locations: roomOptions,
            batteryTypes: battTypes,
            spares: spareBatts,
            estate: results,
            telemetry: getHubTelemetry()
        ]

        return render(contentType: "application/json", data: JsonOutput.toJson(payload), status: 200)
    } catch (e) {
        log.error "Data API Error: ${e}"
        return render(contentType: "application/json", data: '{"error":"Data unavailable"}', status: 500)
    }
}

def serveDashboardPage() {
    try {
        def css = '''
        body { font-family:-apple-system,BlinkMacSystemFont,sans-serif; padding:20px; background:#0d0d0d; color:#e0e0e0; margin:0; }
        .container { max-width:800px; margin:0 auto; background:#151515; padding:25px; border-radius:12px; box-sizing:border-box; width:100%; }
        .loader { border:4px solid #333; border-top:4px solid #3498db; border-radius:50%; width:40px; height:40px; animation:spin 1s linear infinite; margin:0 auto; }
        @keyframes spin { 0% { transform:rotate(0deg); } 100% { transform:rotate(360deg); } }
        
        .header-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:15px; flex-wrap:wrap; gap:15px; }
        .header-left { display:flex; gap:10px; flex:1; min-width:260px; }
        .header-center { flex:1; display:flex; justify-content:center; min-width:80px; }
        .header-right { flex:1; display:flex; justify-content:flex-end; min-width:120px; }
        
        .top-btn { background:#1f618d; color:#fff; border:none; padding:8px 12px; border-radius:4px; font-size:12px; cursor:pointer; font-weight:bold; transition:background 0.15s; }
        .top-btn:hover { background:#1a5276; }
        .action-row { display:flex; gap:10px; margin-bottom:30px; }
        a.main-btn { background:#1f618d; color:#fff; border:none; padding:14px 20px; border-radius:8px; display:block; text-align:center; text-decoration:none; font-weight:600; flex:1; }
        a.main-btn:hover { background:#1a5276; }
        
        .summary-box { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:25px; }
        .summary-card { flex:1 1 20%; min-width:120px; box-sizing:border-box; background:#1e1e1e; padding:15px; border-radius:8px; text-align:center; border-bottom:3px solid #333; }
        .summary-card b { display:block; font-size:24px; color:#fff; margin-bottom:5px; }
        .summary-card span { font-size:12px; color:#aaa; text-transform:uppercase; }
        
        details { margin-bottom:15px; }
        summary { padding:12px 15px; background:#1c1c1c; border-radius:6px; border-left:4px solid #3498db; cursor:pointer; color:#fff; font-weight:bold; font-size:16px; }
        summary:hover { background:#252525; }
        .cat-count { float:right; font-size:12px; color:#888; margin-top:3px; }
        
        .dev-card { background:#222; padding:15px; border-radius:8px; margin-bottom:12px; border-left:4px solid #333; }
        .clickable-card { cursor:pointer; transition:transform 0.15s ease, background-color 0.15s ease; }
        .clickable-card:hover { transform:translateY(-2px); background-color:#2a2a2a !important; }
        
        .status-Red { border-left-color:#e74c3c; background:linear-gradient(90deg, rgba(231,76,60,.1) 0%, #222 30%); }
        .status-Purple { border-left-color:#9b59b6; background:linear-gradient(90deg, rgba(155,89,182,.1) 0%, #222 30%); }
        .status-Yellow { border-left-color:#f1c40f; background:linear-gradient(90deg, rgba(241,196,15,.1) 0%, #222 30%); }
        .status-Blue { border-left-color:#3498db; background:linear-gradient(90deg, rgba(52,152,219,.1) 0%, #222 30%); }
        .status-Green { border-left-color:#27ae60; }
        
        .dot { height:12px; width:12px; border-radius:50%; display:inline-block; margin-right:12px; flex-shrink:0; }
        .dot-Red { background:#e74c3c; box-shadow:0 0 6px #e74c3c; }
        .dot-Purple { background:#9b59b6; box-shadow:0 0 6px #9b59b6; }
        .dot-Yellow { background:#f1c40f; box-shadow:0 0 6px #f1c40f; }
        .dot-Blue { background:#3498db; box-shadow:0 0 6px #3498db; }
        .dot-Green { background:#27ae60; }
        
        .dflex { display:flex; align-items:center; width:100%; }
        .dev-info { flex-grow:1; min-width:0; }
        .dev-name { font-size:15px; font-weight:bold; color:#fff; margin-bottom:2px; }
        .dev-custom { font-size:11px; color:#888; margin-bottom:5px; }
        .dev-details { font-size:13px; color:#aaa; }
        .dev-subtext { font-size:11px; color:#7f8c8d; margin-top:5px; font-style:italic; }
        
        .card-controls { display:flex; align-items:center; justify-content:flex-end; flex-shrink:0; }
        a.ping-btn { background:#34495e; color:#fff; border:1px solid #2c3e50; padding:6px 12px; font-size:12px; border-radius:4px; font-weight:bold; text-decoration:none; margin-left:10px; flex-shrink:0; }
        a.ping-btn:hover { background:#2c3e50; }
        
        .batt-wrap { margin-top:8px; width:100%; }
        .batt-info { display:flex; justify-content:space-between; font-size:10px; color:#aaa; margin-bottom:3px; }
        .batt-bg { width:100%; background:#333; height:6px; border-radius:3px; overflow:hidden; }
        .batt-fg { height:100%; }
        .bg-grn { background:#27ae60; }
        .bg-ylw { background:#f1c40f; }
        .bg-red { background:#e74c3c; }
        
        .shop-card { flex:1; min-width:160px; box-sizing:border-box; background:#1e1e1e; padding:15px; border-radius:8px; border-left:4px solid #9b59b6; box-shadow:0 4px 6px rgba(0,0,0,.3); }
        .shop-title { color:#fff; font-size:15px; display:block; margin-bottom:8px; }
        .shop-badge { background:#9b59b6; color:#fff; font-size:11px; padding:2px 6px; border-radius:10px; float:right; }
        .shop-list { font-size:11px; color:#888; max-height:80px; overflow-y:auto; line-height:1.4; }
        
        .mute-form { display:inline-flex; align-items:center; margin-left:10px; margin-bottom:0; }
        .mute-input { width:45px; padding:0 4px; font-size:11px; border-radius:4px 0 0 4px; border:1px solid #8e44ad; background:#111; color:#fff; height:26px; box-sizing:border-box; }
        .mute-btn { margin-left:0; border-radius:0 4px 4px 0; background:#8e44ad; border-color:#8e44ad; height:26px; color:#fff; border-style:solid; border-width:1px; font-size:12px; font-weight:bold; cursor:pointer; }
        
        .modal-overlay { display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.75); z-index:1000; align-items:center; justify-content:center; padding:20px; box-sizing:border-box; }
        .modal-content { background:#1a1a1a; color:#e0e0e0; width:100%; max-width:550px; border-radius:12px; padding:22px; box-sizing:border-box; position:relative; box-shadow:0 12px 35px rgba(0,0,0,0.6); border:1px solid #333; max-height:90vh; overflow-y:auto; }
        .modal-header { font-size:18px; font-weight:bold; color:#fff; margin-bottom:15px; border-bottom:1px solid #333; padding-bottom:12px; display:flex; align-items:center; }
        .modal-body table { width:100%; border-collapse:collapse; margin-bottom:20px; }
        .modal-body td { padding:10px 0; border-bottom:1px solid #262626; font-size:13px; line-height:1.4; }
        .modal-body td:first-child { color:#888; font-weight:500; width:38%; vertical-align:middle; }
        .modal-body td:last-child { color:#fff; text-align:right; }
        
        .modal-input { width:100%; padding:6px; border-radius:4px; border:1px solid #444; background:#222; color:#fff; font-size:13px; box-sizing:border-box; }
        .modal-input:focus { outline:none; border-color:#3498db; }
        
        .modal-action-row { display:flex; gap:10px; margin-top:15px; }
        .save-modal-btn { flex:1; padding:12px; background:#27ae60; color:#fff; border:none; border-radius:6px; font-weight:bold; font-size:13px; cursor:pointer; transition:background 0.15s; }
        .save-modal-btn:hover { background:#2ecc71; }
        .save-modal-btn:disabled { background:#7f8c8d; cursor:not-allowed; }
        .close-modal-btn { flex:1; padding:12px; background:#2c3e50; color:#fff; border:none; border-radius:6px; font-weight:bold; font-size:13px; cursor:pointer; text-align:center; transition:background 0.15s; }
        .close-modal-btn:hover { background:#34495e; }

        details.folder-group.dragging { opacity: 0.5; }
        
        /* Ultimate Mobile Responsiveness */
        @media (max-width: 650px) {
            body { padding: 10px; }
            .container { padding: 15px; }
            .header-bar { flex-direction: column; }
            .header-center { order: 1; width: 100%; }
            .header-left { order: 2; width: 100%; justify-content: center; flex-wrap: wrap; }
            .header-right { order: 3; width: 100%; justify-content: center; }
            .summary-card { flex: 1 1 40%; min-width: 40%; }
            .action-row { flex-direction: column; }
            .dflex { flex-wrap: wrap; }
            .card-controls { width: 100%; margin-top: 12px; justify-content: flex-start; }
            .ping-btn { margin-left: 0; margin-right: 10px; }
            .mute-form { margin-left: 0; }
            .modal-content { padding: 15px; }
            .modal-body td { display: block; width: 100%; text-align: left !important; }
            .modal-body td:first-child { font-size: 11px; padding-bottom: 2px; border-bottom: none; color: #3498db; }
            .modal-body td:last-child { padding-top: 2px; padding-bottom: 12px; }
        }
        '''

        def jsScript = '''
        let db = null;
        let groupByMode = 'category';
        const accessToken = 'TOKEN_PLACEHOLDER';

        function loadData() {
            document.getElementById('appContainer').innerHTML = "<div style='text-align:center;padding:50px;color:#888;'><div class='loader'></div><br><br>Syncing Estate Diagnostics...</div>";
            fetch('data?access_token=' + accessToken)
            .then(res => res.json())
            .then(data => {
                db = data;
                renderApp();
            })
            .catch(err => {
                document.getElementById('appContainer').innerHTML = "<div style='text-align:center;padding:50px;color:#e74c3c;'><b>Cloud Connection Error</b><br>Could not retrieve estate data. The hub might be busy processing a scan.<br><br><small>" + err + "</small></div>";
            });
        }

        function silentRefresh() {
            if((!document.getElementById('deviceModalOverlay').style.display || document.getElementById('deviceModalOverlay').style.display === 'none') && 
               (!document.getElementById('batteryLockerModalOverlay').style.display || document.getElementById('batteryLockerModalOverlay').style.display === 'none')){ 
                
                fetch('data?access_token=' + accessToken)
                .then(r => r.json())
                .then(data => {
                    db = data;
                    renderApp();
                });
            } 
        }

        function generateCard(dev) {
            let detailsStr = dev.messages.join(" • ");
            let actStr = dev.lastActive || "Unknown";
            let battStr = dev.battChanged || "";

            let safeName = dev.name.replace(/'/g, "&#39;").replace(/"/g, "&quot;");
            let safeMsgs = detailsStr.replace(/'/g, "&#39;").replace(/"/g, "&quot;");
            let safeLoc = (dev.customLoc || "Unassigned").replace(/'/g, "&#39;").replace(/"/g, "&quot;");
            let safeDesc = (dev.customDesc || "None").replace(/'/g, "&#39;").replace(/"/g, "&quot;");
            let safeBattType = (dev.battType || "N/A").replace(/'/g, "&#39;").replace(/"/g, "&quot;");
            let safeHardwareSig = `${dev.manufacturer} (${dev.model})`;
            let safeDriver = (dev.driverName || "Unknown").replace(/'/g, "&#39;").replace(/"/g, "&quot;");
            let safeOverride = dev.battThreshOverride || "";
            let safeOutdoor = dev.isOutdoor ? "true" : "false";

            let customText = "";
            if (dev.customLoc || dev.customDesc) {
                let parts = [];
                if (dev.customLoc) parts.push("📍 " + dev.customLoc);
                if (dev.customDesc) parts.push("📝 " + dev.customDesc);
                customText = "<div class='dev-custom'>" + parts.join(" &nbsp;|&nbsp; ") + "</div>";
            }

            let battBarHtml = "";
            if (dev.battVal !== null && dev.battVal !== undefined) {
                let barCls = dev.battVal > 50 ? "bg-grn" : (dev.battVal > 20 ? "bg-ylw" : "bg-red");
                let bTypeStr = dev.battType ? " - " + dev.battQty + "x " + dev.battType : "";
                battBarHtml = "<div class='batt-wrap'><div class='batt-info'><span>🔋 " + dev.battVal + "%" + bTypeStr + "</span><span>⏳ " + dev.estDaysLeft + "</span></div><div class='batt-bg'><div class='batt-fg " + barCls + "' style='width:" + dev.battVal + "%;'></div></div></div>";
            }

            let sparklineHtml = "";
            let lastRssiStr = "N/A";
            if (dev.rssiHistory && dev.rssiHistory.length > 1) {
                let pts = [];
                let minR = -100.0, maxR = -30.0, w = 50.0, h = 12.0;
                let xStep = w / (dev.rssiHistory.length - 1);
                dev.rssiHistory.forEach((rVal, idx) => {
                    let x = Math.round((idx * xStep) * 10) / 10.0;
                    let constrainedR = Math.max(minR, Math.min(maxR, parseFloat(rVal)));
                    let y = Math.round((h - (((constrainedR - minR) / (maxR - minR)) * h)) * 10) / 10.0;
                    pts.push(x + "," + y);
                });
                let currRssi = dev.rssiHistory[dev.rssiHistory.length - 1];
                lastRssiStr = currRssi + " dBm";
                let slColor = currRssi > -70 ? "#27ae60" : (currRssi > -85 ? "#f1c40f" : "#e74c3c");
                sparklineHtml = "<div style='margin-top:6px;display:flex;align-items:center;font-size:10px;color:#aaa;'><span>📶 " + currRssi + " dBm</span><svg width='50' height='12' style='margin-left:8px;overflow:visible;'><polyline fill='none' stroke='" + slColor + "' stroke-width='1.5' points='" + pts.join(' ') + "'/></svg></div>";
            } else if (dev.rssiHistory && dev.rssiHistory.length === 1) {
                lastRssiStr = dev.rssiHistory[0] + " dBm";
                sparklineHtml = "<div style='margin-top:6px;display:flex;align-items:center;font-size:10px;color:#aaa;'><span>📶 " + lastRssiStr + "</span></div>";
            }

            let controlsHtml = "<div class='card-controls'>";
            if (dev.canPing) {
                controlsHtml += "<a href='ping?deviceId=" + dev.id + "&access_token=" + accessToken + "' class='ping-btn' onclick='event.stopPropagation();'>📡 Ping</a>";
            }

            if (dev.isMuted) {
                let muteDate = new Date(dev.muteUntil);
                let h = muteDate.getHours();
                let ampm = h >= 12 ? 'PM' : 'AM';
                h = h % 12; h = h ? h : 12;
                let m = muteDate.getMinutes();
                m = m < 10 ? '0'+m : m;
                let mTime = (muteDate.getMonth()+1) + '/' + muteDate.getDate() + ' ' + h + ':' + m + ampm;
                controlsHtml += "<div style='display:inline-block;margin-left:10px;' onclick='event.stopPropagation();'><span style='color:#8e44ad;font-size:11px;margin-right:5px;'>🔕 Muted until " + mTime + "</span><a href='unmute?deviceId=" + dev.id + "&access_token=" + accessToken + "' class='ping-btn' style='background:#8e44ad;border-color:#8e44ad;margin-left:0;'>Unmute</a></div>";
            } else if (dev.status !== "Green" && dev.status !== "Blue") {
                controlsHtml += "<form action='mute' method='GET' class='mute-form' onclick='event.stopPropagation();'><input type='hidden' name='deviceId' value='" + dev.id + "'><input type='hidden' name='access_token' value='" + accessToken + "'><input type='number' name='hours' class='mute-input' placeholder='hrs' required min='1'><button type='submit' class='mute-btn'>🔕 Mute</button></form>";
            }
            controlsHtml += "</div>";

            let battValAttr = dev.battVal !== null && dev.battVal !== undefined ? dev.battVal + "%" : "N/A";

            return `<div class='dev-card clickable-card status-${dev.status}' onclick='openDeviceModal(this)' data-id='${dev.id}' data-name='${safeName}' data-status='${dev.status}' data-msgs='${safeMsgs}' data-lastactive='${actStr}' data-loc='${safeLoc}' data-desc='${safeDesc}' data-batt='${battValAttr}' data-batttype='${safeBattType}' data-battqty='${dev.battQty}' data-daysleft='${dev.estDaysLeft}' data-rssi='${lastRssiStr}' data-hw='${safeHardwareSig}' data-fw='${dev.firmware}' data-driver='${safeDriver}' data-override='${safeOverride}' data-outdoor='${safeOutdoor}'><div class='dflex'><span class='dot dot-${dev.status}'></span><div class='dev-info'><div class='dev-name'>${dev.name}</div>${customText}<div class='dev-details'>${detailsStr}</div><div class='dev-subtext'>Last Active: ${actStr}${battStr}</div>${battBarHtml}${sparklineHtml}</div>${controlsHtml}</div></div>`;
        }

        let isEditMode = false;
        function toggleFolderEdit() {
            isEditMode = !isEditMode;
            document.querySelectorAll('details.folder-group').forEach(el => {
                el.draggable = isEditMode;
                el.style.border = isEditMode ? '2px dashed #3498db' : '';
                el.style.padding = isEditMode ? '5px' : '';
            });
            let btn = document.getElementById('editFolderBtn');
            btn.style.background = isEditMode ? '#e74c3c' : '#1f618d';
            btn.innerText = isEditMode ? '💾 Save Layout' : '✏️ Edit Layout';
            
            if(!isEditMode) {
                saveFolderOrder();
            }
        }

        function saveFolderOrder() {
            if (groupByMode !== 'category') return;
            let folders = [];
            document.querySelectorAll('details.folder-group summary span.folder-title').forEach(el => {
                folders.push(el.innerText);
            });
            fetch(`updateFolders?order=${encodeURIComponent(folders.join(','))}&access_token=${accessToken}`)
            .then(r => console.log('Saved order'));
        }

        function initDragAndDrop() {
            let dragged;
            document.addEventListener('dragstart', function(e) {
                if(!isEditMode || groupByMode !== 'category') return;
                let target = e.target.closest('details.folder-group');
                if(target) {
                    dragged = target;
                    target.classList.add('dragging');
                }
            });
            document.addEventListener('dragend', function(e) {
                let target = e.target.closest('details.folder-group');
                if(target) target.classList.remove('dragging');
            });
            document.addEventListener('dragover', function(e) {
                if(!isEditMode || groupByMode !== 'category') return;
                e.preventDefault();
            });
            document.addEventListener('drop', function(e) {
                if(!isEditMode || groupByMode !== 'category') return;
                e.preventDefault();
                let target = e.target.closest('details.folder-group');
                if (target && target !== dragged) {
                    let container = target.parentNode;
                    let draggedRect = dragged.getBoundingClientRect();
                    let targetRect = target.getBoundingClientRect();
                    if(draggedRect.top > targetRect.top) {
                        container.insertBefore(dragged, target);
                    } else {
                        container.insertBefore(dragged, target.nextSibling);
                    }
                }
            });
        }
        
        function changeGroupBy(mode) {
            groupByMode = mode;
            isEditMode = false;
            renderApp();
        }

        function renderApp() {
            if (!db || !db.estate) return;
            let critCount = 0, warnCount = 0, goodCount = 0;
            let totalCount = db.estate.length;
            let activeIssuesHtml = "";
            let mutedDevsHtml = "";
            let groupHtmlMap = {};

            db.estate.forEach(dev => {
                if (!dev.isMuted) {
                    if (dev.status === 'Red' || dev.status === 'Purple') critCount++;
                    else if (dev.status === 'Yellow') warnCount++;
                    else goodCount++;
                }

                let cardHtml = generateCard(dev);

                if (dev.isMuted) {
                    mutedDevsHtml += cardHtml;
                } else if (dev.status === 'Red' || dev.status === 'Purple' || dev.status === 'Yellow') {
                    activeIssuesHtml += cardHtml;
                } else {
                    let groups = [];
                    if (groupByMode === 'category') {
                        groups = (dev.categories && dev.categories.length > 0) ? dev.categories : ["Unassigned / Other"];
                    } else {
                        groups = [dev.customLoc || "Unassigned / Other"];
                    }
                    groups.forEach(gName => {
                        if (!groupHtmlMap[gName]) groupHtmlMap[gName] = { count: 0, html: "" };
                        groupHtmlMap[gName].html += cardHtml;
                        groupHtmlMap[gName].count++;
                    });
                }
            });

            let html = "";
            html += `<div class='header-bar'>`;
            html += `<div class='header-left'>`;
            html += `<button class='top-btn' onclick='openBatteryLocker()' style='background:#8e44ad;'>🔋 Battery Locker</button>`;
            html += `<select id='groupBySelect' class='top-btn' onchange='changeGroupBy(this.value)' style='background:#2c3e50; border:none; outline:none; color:#fff;'>`;
            html += `<option value='category' ${groupByMode==='category'?'selected':''}>📁 Group by Type</option>`;
            html += `<option value='location' ${groupByMode==='location'?'selected':''}>📍 Group by Room</option>`;
            html += `</select></div>`;
            html += `<div class='header-center'><svg width='70' height='70' viewBox='0 0 100 100' fill='none'><circle cx='50' cy='50' r='48' fill='#151515' stroke='#333' stroke-width='1'/><path d='M 50 20 L 25 30 V 50 C 25 68 35 80 50 85 C 65 80 75 68 75 50 V 30 L 50 20 Z' stroke='#e0e0e0' stroke-width='3' stroke-linejoin='round'/><path d='M 38 52 L 46 60 L 62 42' stroke='#27ae60' stroke-width='4' stroke-linecap='round' stroke-linejoin='round'/></svg></div>`;
            html += `<div class='header-right'><button id='editFolderBtn' class='top-btn' onclick='toggleFolderEdit()' style='display:${groupByMode==='category'?'inline-block':'none'};'>✏️ Edit Layout</button></div>`;
            html += `</div>`;
            
            html += `<h2 style='text-align:center;color:#fff;margin:0 0 5px 0;'>Device Health Dashboard</h2><p style='text-align:center;font-size:13px;color:#888;margin-bottom:25px;'>Last Scan: ${db.lastCheck}</p>`;

            html += `<div class='summary-box'><div class='summary-card' style='border-bottom-color:#e74c3c;'><b>${critCount}</b><span>Critical</span></div><div class='summary-card' style='border-bottom-color:#f1c40f;'><b>${warnCount}</b><span>Warnings</span></div><div class='summary-card' style='border-bottom-color:#27ae60;'><b>${goodCount}</b><span>Healthy</span></div><div class='summary-card' style='border-bottom-color:#3498db;'><b>${totalCount}</b><span>Total Devices</span></div></div>`;

            html += `<div class='action-row'><a href='refresh?access_token=${accessToken}' class='main-btn'>🩺 Force Health Scan</a><a href='ticket?access_token=${accessToken}' target='_blank' class='main-btn' style='background:#8e44ad;'>🖨️ Print Ticket</a></div>`;

            html += `<details><summary style='border-left-color:#95a5a6;'>🖥️ Hub Health Information</summary><div style='padding:15px; background:#1a1a1a; border-radius:8px; margin-top:10px;'><table style='width:100%; color:#ccc; font-size:13px; border-collapse:collapse;'><tr><td style='padding:8px 0; border-bottom:1px solid #333;'>Firmware Version</td><td style='text-align:right; font-weight:bold; color:#fff; border-bottom:1px solid #333;'>${db.telemetry.firmware}</td></tr><tr><td style='padding:8px 0; border-bottom:1px solid #333;'>System Uptime</td><td style='text-align:right; font-weight:bold; color:#fff; border-bottom:1px solid #333;'>${db.telemetry.uptime}</td></tr><tr><td style='padding:8px 0; border-bottom:1px solid #333;'>OS Memory (Free / Total)</td><td style='text-align:right; font-weight:bold; color:#fff; border-bottom:1px solid #333;'>${db.telemetry.memoryFree} / ${db.telemetry.memoryTotal}</td></tr><tr><td style='padding:8px 0;'>Database Size</td><td style='text-align:right; font-weight:bold; color:#fff;'>${db.telemetry.dbSize}</td></tr></table></div></details>`;

            if (activeIssuesHtml) {
                html += `<details open><summary style='border-left-color:#e74c3c;'>⚠️ Active Issues <span class='cat-count'>${critCount + warnCount} Devices</span></summary><div style='padding-top:15px;'>${activeIssuesHtml}</div></details>`;
            }

            if (mutedDevsHtml) {
                html += `<details><summary style='border-left-color:#8e44ad;'>🔕 Muted Devices</summary><div style='padding-top:15px;'>${mutedDevsHtml}</div></details>`;
            }

            let catKeys = Object.keys(groupHtmlMap);
            if (catKeys.length > 0) {
                if (groupByMode === 'category') {
                    let customOrderList = db.customFolderOrder.split(',').map(s => s.trim()).filter(s => s !== "");
                    catKeys.sort((a, b) => {
                        let idxA = customOrderList.indexOf(a);
                        let idxB = customOrderList.indexOf(b);
                        if (idxA !== -1 && idxB !== -1) return idxA - idxB;
                        if (idxA !== -1) return -1;
                        if (idxB !== -1) return 1;
                        return a.localeCompare(b);
                    });
                } else {
                    catKeys.sort((a, b) => {
                        if(a === "Unassigned / Other") return 1;
                        if(b === "Unassigned / Other") return -1;
                        return a.localeCompare(b);
                    });
                }

                html += `<div id='foldersContainer'>`;
                let fIcon = groupByMode === 'category' ? '📁' : '📍';
                catKeys.forEach(catName => {
                    html += `<details class='folder-group'><summary style='border-left-color:#3498db;'>${fIcon} <span class='folder-title'>${catName}</span> <span class='cat-count'>${groupHtmlMap[catName].count} Devices</span></summary><div style='padding-top:15px;'>${groupHtmlMap[catName].html}</div></details>`;
                });
                html += `</div>`;
            } else {
                html += `<div style='text-align:center;color:#888;padding:40px;'>No unmuted devices to display.</div>`;
            }

            let estateBatts = {};
            let replaceNowBatts = {};
            let annualForecast = {};

            db.estate.forEach(dev => {
                if (dev.battType && dev.battType !== "" && dev.battType !== "Rechargeable Internal" && dev.battType !== "Mains / Hardwired") {
                    let bQty = parseInt(dev.battQty) || 1;
                    estateBatts[dev.battType] = (estateBatts[dev.battType] || 0) + bQty;
                    if (dev.messages.some(m => m.includes("Battery Critical") || m.includes("Battery Low") || m.includes("Parasitic"))) {
                        replaceNowBatts[dev.battType] = (replaceNowBatts[dev.battType] || 0) + bQty;
                    }
                    
                    if (dev.ewmaRate && dev.ewmaRate > 0) {
                        let dropsPerYear = dev.ewmaRate * 365.0;
                        let batteriesPerYear = (dropsPerYear / 100.0) * bQty;
                        annualForecast[dev.battType] = (annualForecast[dev.battType] || 0) + batteriesPerYear;
                    }
                }
            });

            let battKeys = Object.keys(estateBatts);
            let battHtml = "";
            if (battKeys.length > 0) {
                battHtml += `<div style='display:flex;flex-wrap:wrap;gap:12px;'>`;
                battKeys.sort().forEach(type => {
                    let totalEstate = estateBatts[type];
                    let replacingNow = replaceNowBatts[type] || 0;
                    let spares = db.spares[type] || 0;
                    let needsBuy = replacingNow > spares;
                    let projectedYr = Math.ceil(annualForecast[type] || 0);

                    let cardStyle = needsBuy ? "border-left:4px solid #e74c3c;" : "border-left:4px solid #27ae60;";
                    let titleColor = needsBuy ? "#e74c3c" : "#27ae60";
                    let shopMsg = needsBuy ? "<div style='color:#e74c3c;font-size:11px;font-weight:bold;margin-top:8px;'>⚠️ Purchase Needed</div>" : "<div style='color:#27ae60;font-size:11px;font-weight:bold;margin-top:8px;'>✓ Stock Sufficient</div>";

                    battHtml += `<div class='shop-card' style='${cardStyle}'><b class='shop-title' style='color:${titleColor}; border-bottom:1px solid #333; padding-bottom:5px; margin-bottom:8px;'>${type}</b><div class='shop-list'><div style='display:flex;justify-content:space-between;margin-bottom:3px;'><span>Estate Total:</span><b>${totalEstate}</b></div><div style='display:flex;justify-content:space-between;margin-bottom:3px;color:#aaa;'><span>Spares on Hand:</span><b>${spares}</b></div><div style='display:flex;justify-content:space-between;margin-bottom:3px;color:#ccc;'><span>Replace Now:</span><b>${replacingNow}</b></div><div style='display:flex;justify-content:space-between;margin-bottom:3px;color:#3498db;border-top:1px dashed #444;padding-top:3px;margin-top:3px;'><span>12-Month Forecast:</span><b>~${projectedYr} / yr</b></div>${shopMsg}</div></div>`;
                });
                battHtml += `</div>`;
            } else {
                battHtml = "<p style='text-align:center;color:#888;'>No batteries tracked in estate.</p>";
            }
            document.getElementById('batteryLockerModalBody').innerHTML = battHtml;

            document.getElementById('appContainer').innerHTML = html;
            initDragAndDrop();
        }

        document.addEventListener("DOMContentLoaded", loadData);
        setInterval(silentRefresh, 60000);

        function checkNewLocation(sel) {
            if (sel.value === '_NEW_') {
                let newVal = prompt('Enter New Location Name:');
                if (newVal) {
                    if (!Array.from(sel.options).some(o => o.value === newVal)) {
                        sel.add(new Option(newVal, newVal), sel.options[sel.options.length - 1]);
                    }
                    sel.value = newVal;
                } else {
                    sel.value = '';
                }
            }
        }

        function checkCustomBattery(sel) {
            if (sel.value === 'Custom') {
                let customVal = prompt('Enter Custom Battery Type:');
                if (customVal) {
                    if (!Array.from(sel.options).some(o => o.value === customVal)) {
                        sel.add(new Option(customVal, customVal));
                    }
                    sel.value = customVal;
                } else {
                    sel.value = '';
                }
            }
        }

        function openDeviceModal(card) {
            document.getElementById('modalDeviceId').value = card.getAttribute('data-id');
            document.getElementById('modalDeviceName').innerText = card.getAttribute('data-name');
            var status = card.getAttribute('data-status');
            document.getElementById('modalStatusDot').className = 'dot dot-' + status;
            document.getElementById('modalNetworkHealth').innerText = card.getAttribute('data-msgs') || 'Nominal';
            
            let locSel = document.getElementById('modalLocationInput');
            let locOpts = db.locations.map(l => `<option value="${l}">${l}</option>`).join('');
            locSel.innerHTML = `<option value="">Unassigned</option>${locOpts}<option value="_NEW_">➕ Add New Location...</option>`;
            
            let cardLoc = card.getAttribute('data-loc') === 'Unassigned' ? '' : card.getAttribute('data-loc');
            if (cardLoc && !Array.from(locSel.options).some(o => o.value === cardLoc)) {
                locSel.add(new Option(cardLoc, cardLoc), locSel.options[locSel.options.length - 1]);
            }
            locSel.value = cardLoc;

            document.getElementById('modalDescriptionInput').value = card.getAttribute('data-desc') === 'None' ? '' : card.getAttribute('data-desc');

            let battSel = document.getElementById('modalBatteryTypeInput');
            let battOpts = db.batteryTypes.map(b => `<option value="${b}">${b}</option>`).join('');
            battSel.innerHTML = `<option value="">N/A / Mains Power</option>${battOpts}`;
            
            let cardBatt = card.getAttribute('data-batttype') === 'N/A' ? '' : card.getAttribute('data-batttype');
            if (cardBatt && !Array.from(battSel.options).some(o => o.value === cardBatt)) {
                battSel.add(new Option(cardBatt, cardBatt));
            }
            battSel.value = cardBatt;

            document.getElementById('modalBatteryQtyInput').value = card.getAttribute('data-battqty') || 1;
            document.getElementById('modalOverrideThreshInput').value = card.getAttribute('data-override') || '';
            document.getElementById('modalOutdoorCheck').checked = card.getAttribute('data-outdoor') === 'true';

            document.getElementById('modalHardwareSig').innerText = card.getAttribute('data-hw') || 'Generic/Unknown';
            document.getElementById('modalFirmwareRev').innerText = card.getAttribute('data-fw') || 'Unknown';
            document.getElementById('modalDriverType').innerText = card.getAttribute('data-driver') || 'Unknown';
            document.getElementById('modalDaysRemaining').innerText = card.getAttribute('data-daysleft') || 'N/A';
            document.getElementById('modalRssi').innerText = card.getAttribute('data-rssi') || 'N/A';
            document.getElementById('modalBatteryHealth').innerText = card.getAttribute('data-batt');
            document.getElementById('modalLastActive').innerText = card.getAttribute('data-lastactive');
            
            document.getElementById('deviceModalOverlay').style.display = 'flex';
        }

        function closeDeviceModal() { document.getElementById('deviceModalOverlay').style.display = 'none'; }
        
        function openBatteryLocker() { document.getElementById('batteryLockerModalOverlay').style.display = 'flex'; }
        function closeBatteryLocker() { document.getElementById('batteryLockerModalOverlay').style.display = 'none'; }
        
        function saveDeviceChanges() {
            let saveBtn = document.querySelector('.save-modal-btn');
            let originalText = saveBtn.innerText;
            saveBtn.innerText = '⏳ Saving...';
            saveBtn.disabled = true;

            let dId = document.getElementById('modalDeviceId').value;
            let loc = document.getElementById('modalLocationInput').value;
            let desc = document.getElementById('modalDescriptionInput').value;
            let battType = document.getElementById('modalBatteryTypeInput').value;
            let battQty = document.getElementById('modalBatteryQtyInput').value;
            let overrideThresh = document.getElementById('modalOverrideThreshInput').value;
            let isOutdoor = document.getElementById('modalOutdoorCheck').checked;
            
            fetch('updateDevice?deviceId=' + dId + '&loc=' + encodeURIComponent(loc) + '&desc=' + encodeURIComponent(desc) + '&battType=' + encodeURIComponent(battType) + '&battQty=' + encodeURIComponent(battQty) + '&overrideThresh=' + encodeURIComponent(overrideThresh) + '&isOutdoor=' + encodeURIComponent(isOutdoor) + '&access_token=' + accessToken)
            .then(response => {
                saveBtn.innerText = originalText;
                saveBtn.disabled = false;
                closeDeviceModal();
                let dev = db.estate.find(d => d.id == dId);
                if(dev) {
                    dev.customLoc = loc || "";
                    dev.customDesc = desc || "";
                    dev.battType = battType || "";
                    dev.battQty = parseInt(battQty) || 1;
                    dev.battThreshOverride = overrideThresh;
                    dev.isOutdoor = isOutdoor;
                }
                renderApp();
            })
            .catch(err => {
                saveBtn.innerText = originalText;
                saveBtn.disabled = false;
                alert("Error saving settings.");
            });
        }

        document.addEventListener('keydown', function(event) { 
            if (event.key === 'Escape') {
                closeDeviceModal();
                closeBatteryLocker();
            }
        });
        '''
        
        jsScript = jsScript.replace('TOKEN_PLACEHOLDER', state.accessToken)

        def modalHtml = '''
        <div class='modal-content' onclick='event.stopPropagation()'>
            <input type='hidden' id='modalDeviceId'>
            <div class='modal-header'>
                <span id='modalStatusDot' class='dot'></span>
                <span id='modalDeviceName'>Device Details</span>
            </div>
            <div class='modal-body'>
                <table>
                    <tr><td>Location</td><td><select id='modalLocationInput' class='modal-input' onchange='checkNewLocation(this)'></select></td></tr>
                    <tr><td>Description</td><td><input type='text' id='modalDescriptionInput' class='modal-input' placeholder='Notes...'></td></tr>
                    <tr><td>Battery Type</td><td><select id='modalBatteryTypeInput' class='modal-input' onchange='checkCustomBattery(this)'></select></td></tr>
                    <tr><td>Battery Quantity</td><td><input type='number' id='modalBatteryQtyInput' class='modal-input' min='1' max='20'></td></tr>
                    <tr><td>Override Critical Batt %</td><td><input type='number' id='modalOverrideThreshInput' class='modal-input' placeholder='Global Default' min='1' max='99'></td></tr>
                    <tr><td>Outdoor Thermal Profile</td><td><label style='display:flex;align-items:center;color:#fff;'><input type='checkbox' id='modalOutdoorCheck' style='margin-right:8px;'> Enable EWMA Dampening</label></td></tr>
                    <tr><td>Driver / Device Type</td><td id='modalDriverType'>-</td></tr>
                    <tr><td>Hardware Type</td><td id='modalHardwareSig'>-</td></tr>
                    <tr><td>Firmware Revision</td><td id='modalFirmwareRev'>-</td></tr>
                    <tr><td>Telemetry Diagnostics</td><td id='modalNetworkHealth'>-</td></tr>
                    <tr><td>Signal Quality (RSSI)</td><td id='modalRssi'>-</td></tr>
                    <tr><td>Battery Level</td><td id='modalBatteryHealth'>-</td></tr>
                    <tr><td>EWMA Forecast</td><td id='modalDaysRemaining'>-</td></tr>
                    <tr><td>Last Mesh Check-In</td><td id='modalLastActive'>-</td></tr>
                </table>
            </div>
            <div class='modal-action-row'>
                <button class='save-modal-btn' onclick='saveDeviceChanges()'>💾 Save Settings</button>
                <button class='close-modal-btn' onclick='closeDeviceModal()'>Cancel</button>
            </div>
        </div>
        '''
        
        def batteryLockerHtml = '''
        <div class='modal-content' onclick='event.stopPropagation()' style='max-width:700px;'>
            <div class='modal-header' style='border-bottom-color:#8e44ad;'>
                <span class='dot' style='background:#8e44ad;box-shadow:0 0 6px #8e44ad;'></span>
                <span>The Battery Locker</span>
            </div>
            <div id='batteryLockerModalBody' class='modal-body' style='max-height:60vh;overflow-y:auto;padding-right:10px;'>
                </div>
            <div class='modal-action-row'>
                <button class='close-modal-btn' onclick='closeBatteryLocker()'>Close Inventory</button>
            </div>
        </div>
        '''

        def html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'><title>Device Health Portal</title><style>${css}</style></head><body><div id='appContainer' class='container'></div><div id='deviceModalOverlay' class='modal-overlay' onclick='closeDeviceModal()'>${modalHtml}</div><div id='batteryLockerModalOverlay' class='modal-overlay' onclick='closeBatteryLocker()'>${batteryLockerHtml}</div><script>${jsScript}</script></body></html>"

        return render(contentType: "text/html", data: html, status: 200)

    } catch (Exception e) {
        log.error "Health Portal Crash: ${e}"
        return render(contentType: "text/html", data: "<h3 style='color:white;'>Portal Error:</h3><p style='color:white;'>${e}</p>", status: 500)
    }
}

def updateDeviceEndpoint() {
    try {
        def dId = params.deviceId
        if (dId) {
            if (params.loc != null) {
                if (params.loc == "") app.removeSetting("loc_${dId}")
                else app.updateSetting("loc_${dId}", [type: "enum", value: params.loc])
            }
            if (params.desc != null) {
                if (params.desc == "") app.removeSetting("desc_${dId}")
                else app.updateSetting("desc_${dId}", [type: "text", value: params.desc])
            }
            if (params.battType != null) {
                def predefinedBatts = ["AA", "AAA", "AAAA", "C", "D", "9V", "CR2032", "CR2025", "CR2450", "CR2477", "CR1632", "CR1220", "CR2", "CR123A", "LR44", "A23", "18650", "14500", "Rechargeable Internal"]
                if (params.battType == "") {
                    app.removeSetting("battType_${dId}")
                    app.removeSetting("customBattType_${dId}")
                } else if (predefinedBatts.contains(params.battType)) {
                    app.updateSetting("battType_${dId}", [type: "enum", value: params.battType])
                    app.removeSetting("customBattType_${dId}")
                } else {
                    app.updateSetting("battType_${dId}", [type: "enum", value: "Custom"])
                    app.updateSetting("customBattType_${dId}", [type: "text", value: params.battType])
                }
            }
            if (params.battQty != null && params.battQty.toString().isNumber()) {
                app.updateSetting("battQty_${dId}", [type: "number", value: params.battQty.toInteger()])
            }
            if (params.overrideThresh != null) {
                if (params.overrideThresh == "") app.removeSetting("overrideBattThresh_${dId}")
                else if (params.overrideThresh.toString().isNumber()) app.updateSetting("overrideBattThresh_${dId}", [type: "number", value: params.overrideThresh.toInteger()])
            }
            if (params.isOutdoor != null) {
                app.updateSetting("outdoorProfile_${dId}", [type: "bool", value: (params.isOutdoor == "true")])
            }

            // Real-time Dashboard Cache Update
            def tempData = state.dashboardData
            if (tempData) {
                def devEntry = tempData.find { it.id == dId }
                if (devEntry) {
                    if (params.loc != null) devEntry.customLoc = params.loc
                    if (params.desc != null) devEntry.customDesc = params.desc
                    if (params.battType != null) devEntry.battType = params.battType
                    if (params.battQty != null && params.battQty.toString().isNumber()) devEntry.battQty = params.battQty.toInteger()
                    if (params.overrideThresh != null) devEntry.battThreshOverride = params.overrideThresh
                    if (params.isOutdoor != null) devEntry.isOutdoor = (params.isOutdoor == "true")
                }
                state.dashboardData = tempData
            }

            app.addToHistory("PORTAL: Hardware settings for device ${dId} updated live via Web Portal.")
        }
        return render(contentType: "application/json", data: '{"success":true}', status: 200)
    } catch(e) { 
        log.error "Portal Device Update Error: ${e}"
        return render(contentType: "application/json", data: '{"error":"'+e+'"}', status: 500) 
    }
}

def updateFoldersEndpoint() {
    try {
        if (params.order != null) {
            app.updateSetting("customFolderOrder", [type: "string", value: params.order])
            app.addToHistory("PORTAL: Dashboard folder priority layout updated live via Web Portal.")
        }
        return render(contentType: "application/json", data: '{"success":true}', status: 200)
    } catch(e) { 
        log.error "Portal Folder Update Error: ${e}"
        return render(contentType: "application/json", data: '{"error":"'+e+'"}', status: 500) 
    }
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
            
            def tempData = state.dashboardData
            if (tempData) {
                tempData.each { if (it.id == dId) { it.isMuted = true; it.muteUntil = muteUntil } }
                state.dashboardData = tempData
            }
            
            app.addToHistory("MUTE: Alerts for device silenced for ${hrs} hours via Portal.")
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
            
            def tempData = state.dashboardData
            if (tempData) {
                tempData.each { if (it.id == dId) { it.isMuted = false; it.muteUntil = null } }
                state.dashboardData = tempData
            }
            
            app.addToHistory("MUTE: Alerts for device unmuted via Portal.")
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
                    app.addToHistory("NETWORK HEAL: Manual ping/refresh sent to ${dev.displayName} via Portal.")
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
