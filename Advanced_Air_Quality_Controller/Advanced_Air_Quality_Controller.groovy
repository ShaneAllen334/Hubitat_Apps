/**
 * Advanced Air Quality Controller
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Air Quality Controller",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced AQI control for up to 12 rooms, integrating Ecowitt sensors, ROI tracking, Smart Filters, Post-Scrubbing, Hourly Cycles, and Alerting.",
    category: "Health & Wellness",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Air Quality Controller</b>", install: true, uninstall: true) {
        
        // Dashboard remains open by default for quick viewing
        section("<b>Live System Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time view of your home's air quality, active purifiers, and the current logic state of the AQI engine.</div>"
            
            def statusExplanation = getHumanReadableStatus()
          
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${statusExplanation}</div>"

            def masterState = appEnableSwitch ? appEnableSwitch.currentValue("switch")?.toUpperCase() : "ON (NO SWITCH)"
            def purifierState = isWholeHouse && mainPurifier ? mainPurifier.currentValue("switch")?.toUpperCase() : "N/A"
            
            def outAQIStr = getAQI(outdoorAQI) != null ? getAQI(outdoorAQI) : "--"
            def inAQIStr = isWholeHouse && indoorAQI ? (getAQI(indoorAQI) != null ? getAQI(indoorAQI) : "--") : "N/A (Multi-Zone)"
            
            def pollenStr = state.localPollen != null ? state.localPollen : "Waiting for data..."
            def currentLocMode = location.mode ?: "Unknown"

            def effectivenessStr = "Requires Indoor and Outdoor Sensors"
            def outVal = getAQI(outdoorAQI)
       
            if (isWholeHouse && indoorAQI && outdoorAQI) {
                def inVal = getAQI(indoorAQI)
                if (inVal != null && outVal != null) {
                    if (inVal < outVal) effectivenessStr = "<span style='color:green;'><b>Working Effectively</b> (Indoor is ${outVal - inVal} points cleaner)</span>"
                    else if (inVal == outVal) effectivenessStr = "<span style='color:orange;'><b>Neutral</b> (Indoor equals Outdoor)</span>"
                    else effectivenessStr = "<span style='color:red;'><b>Poor</b> (Indoor is ${inVal - outVal} points worse)</span>"
                }
            }
            
            // Generate exact switch states for Main Configuration
            def mTriggerNames = "None Setup"
            if (isWholeHouse && mainTriggerSwitch) {
                mTriggerNames = mainTriggerSwitch.collect { "${it.displayName}: <b>${it.currentValue('switch')?.toUpperCase()}</b>" }.join("<br>")
                if (state.mainScrubEnd && now() < state.mainScrubEnd) mTriggerNames += "<br><span style='color:blue;'><i>(Post-Scrubbing Active)</i></span>"
            }
            
            def mPreventName = "None Setup"
            if (isWholeHouse && mainPreventSwitch) {
                mPreventName = "${mainPreventSwitch.displayName}: <b>${mainPreventSwitch.currentValue('switch')?.toUpperCase()}</b>"
            }

            def dashHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 28%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
            </style>
            <table class="dash-table">
                <thead><tr><th>Metric</th><th colspan="3">Current Value</th></tr></thead>
                <tbody>
                    <tr><td class="dash-hl">Master App Switch</td><td colspan="3" class="dash-val"><b>${masterState}</b></td></tr>
                    <tr><td class="dash-hl">Main Purifier State</td><td colspan="3" class="dash-val" style="color:${purifierState == 'ON' ? 'green' : 'black'};"><b>${purifierState}</b></td></tr>
                    <tr><td class="dash-hl">Current Reason</td><td colspan="3" class="dash-val"><i>${state.currentReason ?: "System Idle"}</i></td></tr>
                    
                    <tr><td colspan="4" class="dash-subhead">Air Quality Data</td></tr>
                    <tr><td class="dash-hl">Outdoor AQI</td><td colspan="3" class="dash-val">${outAQIStr}</td></tr>
                    <tr><td class="dash-hl">Indoor AQI</td><td colspan="3" class="dash-val">${inAQIStr}</td></tr>
                    <tr><td class="dash-hl">System Effectiveness</td><td colspan="3" class="dash-val">${effectivenessStr}</td></tr>
                    
                    ${isWholeHouse ? """
                    <tr><td colspan="4" class="dash-subhead">Main Logic Switches</td></tr>
                    <tr><td class="dash-hl">Trigger Switches</td><td colspan="3" class="dash-val">${mTriggerNames}</td></tr>
                    <tr><td class="dash-hl">Prevent Switch</td><td colspan="3" class="dash-val">${mPreventName}</td></tr>
                    """ : ""}
                    
                    <tr><td colspan="4" class="dash-subhead">External Variables</td></tr>
                    <tr><td class="dash-hl">Local Pollen Count</td><td colspan="3" class="dash-val">${pollenStr}</td></tr>
                    <tr><td class="dash-hl">Location Mode</td><td colspan="3" class="dash-val">${currentLocMode}</td></tr>
                </tbody>
            </table>
            """
            paragraph dashHTML
        }

        section("<b>Performance, ROI & Filter Tracking</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Estimates daily run costs, verifies Air Changes per Hour (ACH), and tracks filter degradation (with a 1.5x wear penalty during severe AQI events).</div>"
            
            def perfHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Purifier Zone</th><th>Filter Life</th><th>ACH</th><th>Run Time (Today)</th><th>Est. Cost</th></tr></thead><tbody>"
            def totalDayCost = 0.0
            def today = new Date().format("yyyy-MM-dd", location.timeZone)

            if (isWholeHouse && mainPurifier) {
                def cadr = mainPurifierCADR ?: 450.0
                def sqft = houseSqFt ?: 500.0
                def ceiling = houseCeiling ?: 8.0
                def vol = sqft * ceiling
                def ach = vol > 0 ? String.format("%.1f", (cadr * 60.0) / vol) : "0.0"
                
                def histMins = state.runHistory?.get(today)?.get(mainPurifier.id) ?: 0.0
                def totalMins = histMins + liveRunMins(mainPurifier.id)
                def cost = (totalMins / 60.0) * ((mainPurifierWatts ?: 65.0) / 1000.0) * (costPerKwh ?: 0.15)
                totalDayCost += cost
                
                def fMins = (state.filterWearMins?.get(mainPurifier.id) ?: 0.0) + totalMins
                def fMax = (mainFilterHours ?: 4380) * 60.0
                def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
                def fStr = fPct < 10 ? "<span style='color:red;'><b>${String.format("%.1f", fPct)}%</b></span>" : "${String.format("%.1f", fPct)}%"
                
                perfHTML += "<tr><td><b>Main House</b></td><td>${fStr}</td><td>${ach}</td><td>${String.format("%.1f", totalMins / 60.0)}h</td><td>&#36;${String.format("%.3f", cost)}</td></tr>"
            } else if (!isWholeHouse) {
                def hasActiveZones = false
                for (int i = 1; i <= 12; i++) {
                    def purif = settings["z${i}Purifier"]
                    if (settings["enableZ${i}"] && purif) {
                        hasActiveZones = true
                        def cadr = settings["z${i}CADR"] ?: 100.0
                        def sqft = settings["z${i}SqFt"] ?: 150.0
                        def ceiling = settings["z${i}Ceiling"] ?: 8.0
                        def vol = sqft * ceiling
                        def ach = vol > 0 ? String.format("%.1f", (cadr * 60.0) / vol) : "0.0"
                        
                        def histMins = state.runHistory?.get(today)?.get(purif.id) ?: 0.0
                        def totalMins = histMins + liveRunMins(purif.id)
                        def cost = (totalMins / 60.0) * ((settings["z${i}Watts"] ?: 20.0) / 1000.0) * (costPerKwh ?: 0.15)
                        totalDayCost += cost
                        
                        def fMins = (state.filterWearMins?.get(purif.id) ?: 0.0) + totalMins
                        def fMax = (settings["z${i}FilterHours"] ?: 4380) * 60.0
                        def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
                        def fStr = fPct < 10 ? "<span style='color:red;'><b>${String.format("%.1f", fPct)}%</b></span>" : "${String.format("%.1f", fPct)}%"
                        
                        def zName = settings["z${i}Name"] ?: "Zone ${i}"
                        perfHTML += "<tr><td>${zName}</td><td>${fStr}</td><td>${ach}</td><td>${String.format("%.1f", totalMins / 60.0)}h</td><td>&#36;${String.format("%.3f", cost)}</td></tr>"
                    }
                }
                if (!hasActiveZones) perfHTML += "<tr><td colspan='5'><i>No Zone Purifiers Configured</i></td></tr>"
            }
            
            perfHTML += "<tr><td colspan='4' style='text-align:right;'><b>Total Daily Energy Cost:</b></td><td><b style='color:green;'>&#36;${String.format("%.3f", totalDayCost)}</b></td></tr>"
            perfHTML += "</tbody></table>"
            
            paragraph perfHTML
            input "resetMainFilter", "button", title: "Reset Main Filter to 100%"
            input "resetHistory", "button", title: "Clear Tracking History"
        }

        section("<b>Zone Breakdown</b>", hideable: true, hidden: true) {
            def zoneHTML = "<table class='dash-table' style='margin-top:0px;'><thead><tr><th>Zone Name</th><th>AQI</th><th>Trigger Switches</th><th>Prevent Switch</th><th>Zone Purifier</th></tr></thead><tbody>"
            def hasZones = false
            
            for (int i = 1; i <= 12; i++) {
                if (settings["enableZ${i}"] && settings["z${i}AQI"]) {
                    hasZones = true
                    def zName = settings["z${i}Name"] ?: "Zone ${i}"
                    def zAQI = getAQI(settings["z${i}AQI"]) ?: "--"
                    
                    def zTriggerNames = "None Setup"
                    if (!isWholeHouse && settings["z${i}TriggerSwitch"]) {
                        zTriggerNames = settings["z${i}TriggerSwitch"].collect { "${it.displayName}: <b>${it.currentValue('switch')?.toUpperCase()}</b>" }.join("<br>")
                        def isScrubbing = state.scrubEndTimes?.get("z${i}") && now() < state.scrubEndTimes.get("z${i}")
                        if (isScrubbing) zTriggerNames += "<br><span style='color:blue;'><i>(Post-Scrubbing Active)</i></span>"
                    }

                    def zPreventName = "None Setup"
                    if (settings["z${i}PreventSwitch"]) {
                        def pState = settings["z${i}PreventSwitch"].currentValue("switch")
                        zPreventName = "${settings["z${i}PreventSwitch"].displayName}: <b>${pState?.toUpperCase()}</b>"
                        
                        if (pState == "on") {
                            def overrideThresh = settings["z${i}OverrideLevel"] ?: 100
                            if (zAQI != "--" && zAQI.toInteger() >= overrideThresh) {
                                zPreventName += "<br><span style='color:orange;'><i>(Bypassed: AQI > ${overrideThresh})</i></span>"
                            } else {
                                zPreventName += "<br><span style='color:red;'><i>(Blocking Purifier)</i></span>"
                            }
                        }
                    }
                    
                    def zPurifierState = isWholeHouse ? "Handled by Main" : (settings["z${i}Purifier"] ? settings["z${i}Purifier"].currentValue("switch")?.toUpperCase() : "No Device")
                    
                    zoneHTML += "<tr><td><b>${zName}</b></td><td>${zAQI}</td><td>${zTriggerNames}</td><td>${zPreventName}</td><td>${zPurifierState}</td></tr>"
                }
            }
            zoneHTML += "</tbody></table>"
            if (hasZones) paragraph zoneHTML else paragraph "<i>No zones configured. Running on Main Sensor only.</i>"
        }

        section("<b>Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
        }

        section("<b>App Control & Main Hardware</b>", hideable: true, hidden: true) {
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            input "notifyDevice", "capability.notification", title: "Notification Device for General App Alerts", required: false, multiple: true
            input "costPerKwh", "decimal", title: "Utility Rate (&#36; per kWh)", required: false, defaultValue: 0.15
            
            paragraph "<hr>"
            input "isWholeHouse", "bool", title: "<b>Use Single Full House Air Purifier?</b>", defaultValue: true, submitOnChange: true
            
            if (isWholeHouse) {
                input "indoorAQI", "capability.sensor", title: "Select Main Indoor AQI Sensor", required: false
                input "mainPurifier", "capability.switch", title: "Select Main House Air Purifier", required: false, multiple: false
                
                input "mainTriggerSwitch", "capability.switch", title: "Trigger Switches (e.g., Vacuum - Forces ON)", required: false, multiple: true
                input "mainScrubTime", "number", title: "Post-Cleaning Scrub Time (Minutes to run after trigger turns off)", required: false, defaultValue: 30
                
                input "mainPreventSwitch", "capability.switch", title: "Prevent Switch (e.g., TV - Stops purifier)", required: false, multiple: false
                if (mainPreventSwitch) input "mainOverrideLevel", "number", title: "Emergency Override AQI", required: false, defaultValue: 100
            
                paragraph "<b>Air Mega ProX Default Specs</b>"
                input "mainPurifierCADR", "number", title: "Purifier CADR (CFM)", required: false, defaultValue: 450
                input "mainPurifierWatts", "number", title: "Purifier Max Wattage", required: false, defaultValue: 65
                input "houseSqFt", "number", title: "Total Treated House Square Footage", required: false, defaultValue: 500
                input "houseCeiling", "decimal", title: "Ceiling Height (Feet)", required: false, defaultValue: 8.0
                input "mainFilterHours", "number", title: "Filter Lifespan (Hours)", required: false, defaultValue: 4380
            }
            input "targetAQI", "number", title: "Target AQI (Turn ON if any room goes above this)", required: false, defaultValue: 50
        }

        section("<b>Audio Announcements & Global Alerts</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'>Announce globally when the air quality indoors or outdoors reaches a harmful level.</div>"
            
            input "alertAllowedModes", "mode", title: "Allowed Modes for Alerts (Leave blank for all)", multiple: true, required: false

            input "alertThresholdIndoor", "number", title: "Indoor AQI Alert Threshold", defaultValue: 100, required: false
            input "alertThresholdOutdoor", "number", title: "Outdoor AQI Alert Threshold", defaultValue: 150, required: false
            input "sendPushBadAQI", "bool", title: "Send Push Notification on Bad AQI?", defaultValue: false

            paragraph "<b>Return Home Reminders</b>"
            input "enableReturnReminders", "bool", title: "Send a reminder alert if returning home and AQI is still harmful?", defaultValue: true, submitOnChange: true
            if (enableReturnReminders) {
                input "awayNightModes", "mode", title: "Select your 'Away/Night' Modes", multiple: true, required: false
                input "homeModes", "mode", title: "Select your 'Return/Home' Modes", multiple: true, required: false
            }

            paragraph "<b>Smart Speakers (TTS)</b>"
            input "ttsSpeakers", "capability.speechSynthesis", title: "Smart Speakers", multiple: true, required: false
            input "ttsBadIndoorAQIText", "text", title: "Indoor Bad AQI Announcement Text", defaultValue: "Warning, the indoor air quality has reached poor levels."
            input "ttsBadOutdoorAQIText", "text", title: "Outdoor Bad AQI Announcement Text", defaultValue: "Warning, the outdoor air quality has reached poor levels."
            
            paragraph "<b>Zooz Siren & Chime</b>"
            input "zoozChimes", "capability.chime", title: "Zooz Chime Devices", multiple: true, required: false, submitOnChange: true
            if (zoozChimes) {
                input "zoozSoundBadIndoorAQI", "number", title: "Sound File #: Bad Indoor AQI Warning", required: false
                input "zoozSoundBadOutdoorAQI", "number", title: "Sound File #: Bad Outdoor AQI Warning", required: false
                input "testZoozIndoorBtn", "button", title: "Test Indoor AQI Sound (Bypass Motion)"
                input "testZoozOutdoorBtn", "button", title: "Test Outdoor AQI Sound (Bypass Motion)"
            }
        }

        section("<b>Global Audio Room Mapping</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>1-to-1 Motion Filtering:</b> Map your speakers to motion sensors here. When the system attempts to play audio on a speaker or chime, it will automatically intercept the command and check if that specific device's room has recent motion. (Devices not mapped here will play unconditionally).</div>"
            
            input "audioMotionTimeout", "number", title: "Audio Motion Timeout (Minutes)", defaultValue: 5, description: "Time to wait after motion stops before muting announcements."
            input "alwaysOnRoom", "enum", title: "Select ONE room to ALWAYS announce (Ignores motion)", options: ["1": "Room 1", "2": "Room 2", "3": "Room 3", "4": "Room 4", "5": "Room 5", "6": "Room 6", "7": "Room 7"], required: false
            
            input "room1Speaker", "capability.actuator", title: "Room 1 Speaker/Chime(s)", required: false, multiple: true
            input "room1Motion", "capability.motionSensor", title: "Room 1 Motion Sensor(s)", required: false, multiple: true
            
            input "room2Speaker", "capability.actuator", title: "Room 2 Speaker/Chime(s)", required: false, multiple: true
            input "room2Motion", "capability.motionSensor", title: "Room 2 Motion Sensor(s)", required: false, multiple: true
            
            input "room3Speaker", "capability.actuator", title: "Room 3 Speaker/Chime(s)", required: false, multiple: true
            input "room3Motion", "capability.motionSensor", title: "Room 3 Motion Sensor(s)", required: false, multiple: true
            
            input "room4Speaker", "capability.actuator", title: "Room 4 Speaker/Chime(s)", required: false, multiple: true
            input "room4Motion", "capability.motionSensor", title: "Room 4 Motion Sensor(s)", required: false, multiple: true
            
            input "room5Speaker", "capability.actuator", title: "Room 5 Speaker/Chime(s)", required: false, multiple: true
            input "room5Motion", "capability.motionSensor", title: "Room 5 Motion Sensor(s)", required: false, multiple: true
            
            input "room6Speaker", "capability.actuator", title: "Room 6 Speaker/Chime(s)", required: false, multiple: true
            input "room6Motion", "capability.motionSensor", title: "Room 6 Motion Sensor(s)", required: false, multiple: true
            
            input "room7Speaker", "capability.actuator", title: "Room 7 Speaker/Chime(s)", required: false, multiple: true
            input "room7Motion", "capability.motionSensor", title: "Room 7 Motion Sensor(s)", required: false, multiple: true
        }

        section("<b>Operating Modes & Alerts</b>", hideable: true, hidden: true) {
            input "allowedModes", "mode", title: "Allowed Modes (Leave blank to run 24/7)", multiple: true, required: false
            input "quietModes", "mode", title: "Quiet Modes (Prevents purifiers from turning on UNLESS emergency override is met)", multiple: true, required: false
            input "quietOverrideAQI", "number", title: "Emergency AQI Threshold during Quiet Modes", required: false, defaultValue: 150
            input "emergencyAlertDelay", "number", title: "Minutes sustained in Emergency AQI before sending push alert", required: false, defaultValue: 60
            
            paragraph "<b>Routine Cycling</b>"
            input "enableHourlyCycle", "bool", title: "Enable Hourly Cycle (Turns ON for 1 hour, OFF for 1 hour when idle & AQI is good)", required: false, defaultValue: false
            
            paragraph "<b>Health & Sick Mode</b>"
            input "sickModeSwitch", "capability.switch", title: "Sick Mode Virtual Switches (Forces 24/7 Operation)", multiple: true, required: false
            input "sickAwayModes", "mode", title: "Away Modes (Pauses Sick Mode automatically when vacant)", multiple: true, required: false
        }

        section("<b>External Monitoring & Pollen</b>", hideable: true, hidden: true) {
            input "outdoorAQI", "capability.sensor", title: "Outdoor AQI Sensor (Supports Ecowitt)", required: false
            input "enablePollen", "bool", title: "Enable Local Pollen.com Tracking", defaultValue: false, submitOnChange: true
            if (enablePollen) {
                input "zipCode", "text", title: "Zip Code for Pollen Data", required: false
                input "pollenThresholdDrop", "number", title: "Drop Target AQI by this much if Pollen is High (> 7.0)", required: false, defaultValue: 10
                
                paragraph "<b>Continuous Pollen Mode</b>"
                input "continuousPollen", "bool", title: "Run Purifiers 24/7 if Pollen is High?", defaultValue: false, submitOnChange: true
                if (continuousPollen) {
                    input "continuousPollenThreshold", "decimal", title: "Pollen Threshold to Trigger 24/7 Run", defaultValue: 7.0
                }
                
                input "testPollen", "button", title: "Fetch Pollen Data Now"
            }
        }

        section("<b>Zone Enable / Disable</b>", hideable: true, hidden: true) {
            for (int i = 1; i <= 12; i++) {
                input "enableZ${i}", "bool", title: "Enable Zone ${i}", submitOnChange: true
            }
        }
        
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"]) {
                def zName = settings["z${i}Name"] ?: "Zone ${i}"
                section("<b>${zName} Configuration</b>", hideable: true, hidden: true) {
                    input "z${i}Name", "text", title: "Zone Name", required: false, defaultValue: "Zone ${i}"
                    input "z${i}AQI", "capability.sensor", title: "AQI Sensor", required: true
            
                    if (!isWholeHouse) {
                        input "z${i}Purifier", "capability.switch", title: "Zone Air Purifier Switch", required: false
                        input "z${i}TriggerSwitch", "capability.switch", title: "Trigger Switches (e.g., Vacuum)", required: false, multiple: true
                        input "z${i}ScrubTime", "number", title: "Post-Cleaning Scrub Time (Mins)", required: false, defaultValue: 30
                        
                        paragraph "<b>Zone Purifier Specs</b>"
                        input "z${i}CADR", "number", title: "Purifier CADR (CFM)", required: false, defaultValue: 100
                        input "z${i}Watts", "number", title: "Purifier Max Wattage", required: false, defaultValue: 20
                        input "z${i}SqFt", "number", title: "Room Square Footage", required: false, defaultValue: 150
                        input "z${i}Ceiling", "decimal", title: "Ceiling Height (Feet)", required: false, defaultValue: 8.0
                        input "z${i}FilterHours", "number", title: "Filter Lifespan (Hours)", required: false, defaultValue: 4380
                        input "resetZ${i}Filter", "button", title: "Reset Zone ${i} Filter"
                    }
                    
                    input "z${i}PreventSwitch", "capability.switch", title: "Prevent Switch (e.g., TV - Stops purifier)", required: false
                    if (settings["z${i}PreventSwitch"]) {
                        input "z${i}OverrideLevel", "number", title: "Emergency Override AQI", required: false, defaultValue: 100
                    }
                }
            }
        }
        
        section("<b>Disclaimer</b>") {
            paragraph "<div style='font-size:12px; color:#888;'><b>Legal Disclaimer:</b> ShaneAllen is not responsible for any damage or liability with the use of this application.</div>"
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def systemEventHandler(evt) {
    // Bundles rapid-fire events from multiple sensor attributes into a single execution
    runIn(2, "evaluateSystem", [overwrite: true])
}

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
    if (!state.purifierStarts) state.purifierStarts = [:]
    if (!state.runHistory) state.runHistory = [:]
    if (!state.filterWearMins) state.filterWearMins = [:]
    if (!state.filterAlertSent) state.filterAlertSent = [:]
    if (!state.scrubEndTimes) state.scrubEndTimes = [:]
    
    if (!state.emergencyStartTimes) state.emergencyStartTimes = [:]
    if (!state.emergencyAlertSent) state.emergencyAlertSent = [:]
    
    // Reset global alerting locks on initialize
    if (state.outdoorInAlarm == null) state.outdoorInAlarm = false
    if (state.indoorInAlarm == null) state.indoorInAlarm = false
    state.currentMode = location.mode
    
    state.currentReason = "System Initialized"
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", "systemEventHandler")
    if (sickModeSwitch) subscribe(sickModeSwitch, "switch", "systemEventHandler")
    subscribe(location, "mode", modeChangeHandler)
    
    if (outdoorAQI) {
        subscribe(outdoorAQI, "airQualityIndex", "systemEventHandler")
        subscribe(outdoorAQI, "aqi", "systemEventHandler")
        subscribe(outdoorAQI, "pm25", "systemEventHandler")
    }
    
    if (isWholeHouse) {
        if (indoorAQI) {
            subscribe(indoorAQI, "airQualityIndex", "systemEventHandler")
            subscribe(indoorAQI, "aqi", "systemEventHandler")
            subscribe(indoorAQI, "pm25", "systemEventHandler")
        }
        if (mainTriggerSwitch) subscribe(mainTriggerSwitch, "switch", "systemEventHandler")
        if (mainPreventSwitch) subscribe(mainPreventSwitch, "switch", "systemEventHandler")
        if (mainPurifier) subscribe(mainPurifier, "switch", "systemEventHandler") // Enforce Sick Mode
    }
    
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"]) {
            if (settings["z${i}AQI"]) {
                subscribe(settings["z${i}AQI"], "airQualityIndex", "systemEventHandler")
                subscribe(settings["z${i}AQI"], "aqi", "systemEventHandler")
                subscribe(settings["z${i}AQI"], "pm25", "systemEventHandler")
            }
            if (settings["z${i}PreventSwitch"]) subscribe(settings["z${i}PreventSwitch"], "switch", "systemEventHandler")
            if (!isWholeHouse && settings["z${i}TriggerSwitch"]) subscribe(settings["z${i}TriggerSwitch"], "switch", "systemEventHandler")
            if (!isWholeHouse && settings["z${i}Purifier"]) subscribe(settings["z${i}Purifier"], "switch", "systemEventHandler") // Enforce Sick Mode
        }
    }
    
    // Subscribe to Audio Room Motion
    if (settings.room1Motion) subscribe(settings.room1Motion, "motion.active", "room1MotionHandler")
    if (settings.room2Motion) subscribe(settings.room2Motion, "motion.active", "room2MotionHandler")
    if (settings.room3Motion) subscribe(settings.room3Motion, "motion.active", "room3MotionHandler")
    if (settings.room4Motion) subscribe(settings.room4Motion, "motion.active", "room4MotionHandler")
    if (settings.room5Motion) subscribe(settings.room5Motion, "motion.active", "room5MotionHandler")
    if (settings.room6Motion) subscribe(settings.room6Motion, "motion.active", "room6MotionHandler")
    if (settings.room7Motion) subscribe(settings.room7Motion, "motion.active", "room7MotionHandler")
    
    if (enablePollen && zipCode) {
        schedule("0 0 8 * * ?", fetchPollenData) 
        fetchPollenData()
    }
    
    if (enableHourlyCycle) {
        // Evaluate precisely at the top of every hour to flip the cycle seamlessly
        schedule("0 0 * ? * *", evaluateSystem)
    }
    
    logAction("App Initialized. Logic Engine Ready.")
    evaluateSystem()
}

// ------------------------------------------------------------------------------
// AUDIO & 1-TO-1 MOTION HELPER ENGINE
// ------------------------------------------------------------------------------

def room1MotionHandler(evt) { state.lastMotionRoom1 = now() }
def room2MotionHandler(evt) { state.lastMotionRoom2 = now() }
def room3MotionHandler(evt) { state.lastMotionRoom3 = now() }
def room4MotionHandler(evt) { state.lastMotionRoom4 = now() }
def room5MotionHandler(evt) { state.lastMotionRoom5 = now() }
def room6MotionHandler(evt) { state.lastMotionRoom6 = now() }
def room7MotionHandler(evt) { state.lastMotionRoom7 = now() }

def isSpeakerMotionActive(speaker) {
    boolean isMapped = false
    boolean hasMotion = false
    
    for (int i = 1; i <= 7; i++) {
        def mappedSpeaker = settings["room${i}Speaker"]
        if (mappedSpeaker) {
            def mappedList = mappedSpeaker instanceof List ? mappedSpeaker : [mappedSpeaker]
            if (mappedList.any { it.id == speaker.id }) {
                isMapped = true
                
                // 1. Check Always On Room
                if (settings.alwaysOnRoom && settings.alwaysOnRoom.toString() == i.toString()) {
                    hasMotion = true
                }
                
                // 2. Evaluate Standard Motion
                if (!hasMotion) {
                    def motion = settings["room${i}Motion"]
                    if (!motion) {
                        hasMotion = true // Mapped, but no sensor to restrict it
                    } else {
                        def mList = motion instanceof List ? motion : [motion]
                        if (mList.any { it?.currentValue("motion") == "active" }) {
                            state."lastMotionRoom${i}" = now()
                            hasMotion = true
                        } else {
                            def lastTime = state."lastMotionRoom${i}"
                            if (lastTime) {
                                long timeoutMillis = (settings.audioMotionTimeout ?: 5) * 60 * 1000
                                if ((now() - lastTime) <= timeoutMillis) {
                                    hasMotion = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (!isMapped) return true // Unmapped speakers play unconditionally
    return hasMotion
}

def playTTS(speakers, msg) {
    if (!speakers || !msg) return
    def devList = speakers instanceof List ? speakers : [speakers]
    devList.each { dev ->
        if (isSpeakerMotionActive(dev)) {
            try { 
                dev.speak(msg) 
                logInfo("Played TTS on ${dev.displayName}: ${msg}")
            } catch (e) { log.error "Failed to play TTS: ${e}" }
        } else {
            logInfo("Skipping TTS on ${dev.displayName}: No recent motion.")
        }
    }
}

def playZoozChime(soundNum, bypassMotion = false) {
    if (!settings.zoozChimes || soundNum == null) return
    
    def isNumeric = soundNum.toString().isNumber()
    def trackNum = isNumeric ? soundNum.toString().toInteger() : null

    int playCount = 0
    settings.zoozChimes.each { chime ->
        if (bypassMotion || isSpeakerMotionActive(chime)) {
            if (playCount > 0) pauseExecution(1000)
            try {
                if (chime.hasCommand("playSound") && trackNum != null) {
                    chime.playSound(trackNum)
                } else if (chime.hasCommand("playTrack")) {
                    chime.playTrack(soundNum.toString())
                } else if (chime.hasCommand("chime") && trackNum != null) {
                    chime.chime(trackNum)
                } else {
                    log.error "${chime.displayName} does not support standard audio commands."
                }
                playCount++
            } catch (e) {
                log.error "${chime.displayName} failed to play sound: ${e.message ?: e}"
            }
        } else {
            logInfo("Skipping Zooz Chime on ${chime.displayName}: No recent motion.")
        }
    }
}

def triggerBadAQIAlert(reasonMsg, ttsText, zoozSound) {
    if (alertAllowedModes && !(alertAllowedModes as List).contains(location.mode)) {
        logInfo("Skipping alert '${reasonMsg}': Restricted by Mode settings.")
        return
    }

    if (sendPushBadAQI) {
        sendAlert(reasonMsg)
    } else {
        logAction("AUDIO ALERT TRIGGERED: ${reasonMsg}")
    }

    if (ttsSpeakers && ttsText) {
        playTTS(ttsSpeakers, ttsText)
    }
    if (zoozChimes && zoozSound != null) {
        playZoozChime(zoozSound)
    }
}

// ------------------------------------------------------------------------------
// APPLICATION LOGIC
// ------------------------------------------------------------------------------

def appButtonHandler(btn) {
    if (btn == "testPollen") {
        logAction("Manual Pollen Fetch requested.")
        fetchPollenData()
        evaluateSystem()
    } else if (btn == "resetHistory") {
        state.runHistory = [:]
        state.purifierStarts = [:]
        logAction("Performance Tracking history cleared.")
    } else if (btn == "resetMainFilter") {
        if (mainPurifier) state.filterWearMins?.remove(mainPurifier.id)
        state.filterAlertSent?.remove(mainPurifier?.id)
        logAction("Main Purifier Filter reset to 100%.")
    } else if (btn.startsWith("resetZ")) {
        def zNum = btn.replace("resetZ", "").replace("Filter", "")
        def dev = settings["z${zNum}Purifier"]
        if (dev) {
            state.filterWearMins?.remove(dev.id)
            state.filterAlertSent?.remove(dev.id)
            logAction("Zone ${zNum} Filter reset to 100%.")
        }
    } else if (btn == "testZoozIndoorBtn") {
        logAction("Testing Zooz Indoor AQI Notification (Bypassing Motion)")
        playZoozChime(zoozSoundBadIndoorAQI, true)
    } else if (btn == "testZoozOutdoorBtn") {
        logAction("Testing Zooz Outdoor AQI Notification (Bypassing Motion)")
        playZoozChime(zoozSoundBadOutdoorAQI, true)
    }
}

def getAQI(device) {
    if (!device) return null
    def aqi = device.currentValue("airQualityIndex")
    if (aqi == null) aqi = device.currentValue("aqi")
    if (aqi == null) aqi = device.currentValue("pm25")
    return aqi != null ? aqi.toInteger() : null
}

String getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "The application is disabled via the Master Switch."
    if (allowedModes && !(allowedModes as List).contains(location.mode)) return "<span style='color:orange;'><b>App Disabled by Mode</b></span>"
    
    def isSickModeActive = sickModeSwitch ? sickModeSwitch.any { it.currentValue("switch") == "on" } : false
    def isSickAway = sickAwayModes ? (sickAwayModes as List).contains(location.mode) : false

    if (isSickModeActive && isSickAway) return "<span style='color:orange;'><b>Sick Mode Paused:</b></span> System is in Away Mode."
    if (isSickModeActive && !isSickAway) return "<span style='color:red;'><b>Sick Mode Active:</b></span> Purifiers are locked ON for Health Priority."

    def isQuiet = quietModes ? (quietModes as List).contains(location.mode) : false
    def pollenVal = (state.localPollen != null && state.localPollen != "Error/Unavailable") ? state.localPollen.toBigDecimal() : 0.0
    def isPollenContinuous = (enablePollen && continuousPollen && pollenVal >= (continuousPollenThreshold ?: 7.0))
    
    if (isQuiet) return "<span style='color:purple;'><b>Quiet Mode Active:</b></span> Purifiers are locked OFF unless emergency thresholds are met."
    if (isPollenContinuous) return "<span style='color:blue;'><b>Continuous Mode Active:</b></span> Running 24/7 due to High Local Pollen (${pollenVal})."
    if (enableHourlyCycle) return "Monitoring actively. Enforcing Target AQI and running routine Hourly Air Cycles."
    return "Monitoring actively. System is enforcing Target AQI requirements."
}

def modeChangeHandler(evt) { 
    def oldMode = state.currentMode ?: location.mode
    def newMode = evt.value
    state.currentMode = newMode
    
    if (enableReturnReminders && awayNightModes && homeModes) {
        def awayList = awayNightModes instanceof List ? awayNightModes : [awayNightModes]
        def homeList = homeModes instanceof List ? homeModes : [homeModes]
        
        if (awayList.contains(oldMode) && homeList.contains(newMode)) {
            if (state.outdoorInAlarm || state.indoorInAlarm) {
                logAction("User returned home during an active AQI alarm. Scheduling 15-minute reminder.")
                runIn(15 * 60, "returnHomeReminder")
            }
        }
    }
    evaluateSystem() 
}

def returnHomeReminder() {
    if (state.outdoorInAlarm) {
        def currentOutAQI = getAQI(outdoorAQI) ?: 0
        triggerBadAQIAlert("Welcome back. Reminder: Outdoor AQI is still harmful (${currentOutAQI}).", ttsBadOutdoorAQIText, zoozSoundBadOutdoorAQI)
    }
    if (state.indoorInAlarm) {
        def mainIndoorAQIVal = isWholeHouse ? (getAQI(indoorAQI) ?: 0) : 0
        def highestZoneAQI = 0
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && settings["z${i}AQI"]) {
                def aqiVal = getAQI(settings["z${i}AQI"])
                if (aqiVal != null && aqiVal > highestZoneAQI) highestZoneAQI = aqiVal
            }
        }
        def maxIndoorAQI = isWholeHouse ? Math.max(mainIndoorAQIVal, highestZoneAQI) : highestZoneAQI
        triggerBadAQIAlert("Welcome back. Reminder: Indoor AQI is still harmful (${maxIndoorAQI}).", ttsBadIndoorAQIText, zoozSoundBadIndoorAQI)
    }
}

def sendAlert(msg) {
    if (notifyDevice) notifyDevice.deviceNotification("AQI Alert: ${msg}")
    logAction("ALERT SENT: ${msg}")
}

def fetchPollenData() {
    if (!zipCode) return
    try {
        def params = [uri: "https://www.pollen.com/api/forecast/current/pollen/${zipCode}", headers: ["Referer": "https://www.pollen.com", "Accept": "application/json"]]
        httpGet(params) { resp ->
            if (resp.status == 200 && resp.data) {
                def pData = resp.data.Location.periods.find { it.Type == "Today" } ?: resp.data.Location.periods[1]
                if (pData && pData.Index != null) {
                    state.localPollen = pData.Index.toBigDecimal().setScale(1, BigDecimal.ROUND_HALF_UP)
                    logAction("Pollen data updated for ${zipCode}: ${state.localPollen}")
                
                    if (state.localPollen >= 7.0 && !state.pollenAlertSent) {
                        sendAlert("Outdoor Pollen is High (${state.localPollen}). Adapting system logic automatically.")
                        state.pollenAlertSent = true
                    } else if (state.localPollen < 7.0) state.pollenAlertSent = false
                }
            }
        }
    } catch (e) { logInfo("Pollen fetch failed. Error: ${e}"); state.localPollen = "Error/Unavailable" }
}

def liveRunMins(deviceId) { 
    return (state.purifierStarts && state.purifierStarts[deviceId]) ? (now() - state.purifierStarts[deviceId]) / 60000.0 : 0.0 
}

def saveRunTime(deviceId, runMins, currentAQI = 50) {
    def today = new Date().format("yyyy-MM-dd", location.timeZone)
    if (!state.runHistory) state.runHistory = [:]
    if (!state.runHistory[today]) state.runHistory[today] = [:]
    
    state.runHistory[today][deviceId] = (state.runHistory[today][deviceId] ?: 0.0) + runMins
    
    // Apply Wear Multiplier if AQI is poor (> 100)
    def wearMultiplier = currentAQI > 100 ? 1.5 : 1.0
    if (!state.filterWearMins) state.filterWearMins = [:]
    state.filterWearMins[deviceId] = (state.filterWearMins[deviceId] ?: 0.0) + (runMins * wearMultiplier)
    
    def keys = state.runHistory.keySet().sort().reverse()
    if (keys.size() > 7) state.runHistory = state.runHistory.subMap(keys[0..6])
}

def controlPurifier(device, command, currentAQI = 50) {
    if (!device) return
    def currentState = device.currentValue("switch")
    
    if (command == "on" && currentState != "on") {
        device.on()
        if (!state.purifierStarts) state.purifierStarts = [:]
        state.purifierStarts[device.id] = now()
    } else if (command == "off" && currentState != "off") {
        device.off()
        if (state.purifierStarts && state.purifierStarts[device.id]) {
            def runMins = (now() - state.purifierStarts[device.id]) / 60000.0
            saveRunTime(device.id, runMins, currentAQI)
            state.purifierStarts.remove(device.id)
            checkFilterHealth(device.id)
        }
    }
}

def checkFilterHealth(deviceId) {
    def fMins = state.filterWearMins?.get(deviceId) ?: 0.0
    def fMax = 4380 * 60.0 // Default 6 months
    
    if (isWholeHouse && mainPurifier && mainPurifier.id == deviceId) fMax = (mainFilterHours ?: 4380) * 60.0
    else {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Purifier"]?.id == deviceId) {
                fMax = (settings["z${i}FilterHours"] ?: 4380) * 60.0
                break
            }
        }
    }
    
    def fPct = Math.max(0.0, 100.0 - ((fMins / fMax) * 100.0))
    if (fPct < 5.0 && !state.filterAlertSent?.get(deviceId)) {
        sendAlert("Filter for device requires replacement (Below 5% Life Remaining).")
        if (!state.filterAlertSent) state.filterAlertSent = [:]
        state.filterAlertSent[deviceId] = true
    }
}

// --- MAIN LOGIC ENGINE ---
def evaluateSystem(evt = null) {
    // 1. HARD OVERRIDES (Master Switch & Modes)
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") {
        turnOffAll()
        state.currentReason = "OFF: Master Switch is Disabled."
        return
    }
    if (allowedModes && !(allowedModes as List).contains(location.mode)) {
        turnOffAll()
        state.currentReason = "OFF: Current Location Mode is not allowed."
        return
    }

    // 2. BASELINE CALCULATIONS
    def isSickModeActive = sickModeSwitch ? sickModeSwitch.any { it.currentValue("switch") == "on" } : false
    def isSickAway = sickAwayModes ? (sickAwayModes as List).contains(location.mode) : false
    def forceSickMode = isSickModeActive && !isSickAway

    def isQuiet = quietModes ? (quietModes as List).contains(location.mode) : false
    def baseTarget = targetAQI ?: 50
    def pollenVal = (state.localPollen != null && state.localPollen != "Error/Unavailable") ? state.localPollen.toBigDecimal() : 0.0
    
    def isPollenContinuous = false
    if (enablePollen && pollenVal >= 7.0) {
        baseTarget -= (pollenThresholdDrop ?: 10)
    }
    if (enablePollen && continuousPollen && pollenVal >= (continuousPollenThreshold ?: 7.0)) {
        isPollenContinuous = true
    }
    
    // Cycle logic: True if current hour is even (e.g. 14:00/2PM is ON, 15:00/3PM is OFF)
    def isCycleHour = (enableHourlyCycle && new Date().format("H", location.timeZone).toInteger() % 2 == 0)

    def anyZoneNeedsPurification = false
    def highestZoneAQI = 0
    def activeZoneReasons = [] 
    def hasConfiguredZones = false
    
    if (!state.scrubEndTimes) state.scrubEndTimes = [:]
    if (!state.emergencyStartTimes) state.emergencyStartTimes = [:]
    if (!state.emergencyAlertSent) state.emergencyAlertSent = [:]

    // 3. ZONE EVALUATIONS
    for (int i = 1; i <= 12; i++) {
        if (settings["enableZ${i}"] && settings["z${i}AQI"]) {
            hasConfiguredZones = true
            def aqiVal = getAQI(settings["z${i}AQI"])
            if (aqiVal == null) continue
            if (aqiVal > highestZoneAQI) highestZoneAQI = aqiVal
            
            def zNeedsIt = false
            def zName = settings["z${i}Name"] ?: "Zone ${i}"
            def emergencyQ = settings["z${i}OverrideLevel"] ?: 100
            
            // Core Need Check
            def isForcedOn = false
            def isSickModeForced = forceSickMode

            if (isSickModeForced) {
                zNeedsIt = true
                isForcedOn = true
            } else if (isQuiet) { 
                if (aqiVal >= (quietOverrideAQI ?: 150)) zNeedsIt = true 
            } else if (aqiVal > baseTarget || isPollenContinuous) zNeedsIt = true

            // Trigger & Scrub Check
            def isScrubbing = false
            if (!isWholeHouse && settings["z${i}TriggerSwitch"]) {
                if (settings["z${i}TriggerSwitch"].any { it.currentValue("switch") == "on" }) {
                    zNeedsIt = true; isForcedOn = true
                    state.scrubEndTimes["z${i}"] = now() + ((settings["z${i}ScrubTime"] ?: 30) * 60000)
                } else if (state.scrubEndTimes?.get("z${i}") && now() < state.scrubEndTimes.get("z${i}")) {
                    zNeedsIt = true; isScrubbing = true
                    runIn(((state.scrubEndTimes.get("z${i}") - now()) / 1000).toInteger(), "evaluateSystem")
                }
            }

            // Hourly Cycle check
            def zCycleActive = false
            if (!zNeedsIt && isCycleHour && !isQuiet) {
                def pBlocked = false
                if (settings["z${i}PreventSwitch"] && settings["z${i}PreventSwitch"].currentValue("switch") == "on") pBlocked = true
                if (!pBlocked) {
                    zNeedsIt = true
                    zCycleActive = true
                }
            }

            // Prevent Switch Check
            if (zNeedsIt && !isForcedOn && !isScrubbing && settings["z${i}PreventSwitch"]) {
                if (settings["z${i}PreventSwitch"].currentValue("switch") == "on") {
                    if (aqiVal < emergencyQ) zNeedsIt = false // Blocked
                }
            }
            
            // Alerting Logic (Device Notification level)
            if (zNeedsIt && aqiVal >= emergencyQ) {
                if (!state.emergencyStartTimes?.get("z${i}")) state.emergencyStartTimes["z${i}"] = now()
                def elapsedMins = (now() - state.emergencyStartTimes.get("z${i}")) / 60000.0
                if (elapsedMins >= (emergencyAlertDelay ?: 60) && !state.emergencyAlertSent?.get("z${i}")) {
                    sendAlert("⚠️ High AQI: ${zName} has been at ${aqiVal} for over ${emergencyAlertDelay} minutes.")
                    state.emergencyAlertSent["z${i}"] = true
                }
            } else {
                state.emergencyStartTimes?.remove("z${i}")
                state.emergencyAlertSent?.remove("z${i}")
            }

            // Local Execution (if not Whole House)
            if (!isWholeHouse && settings["z${i}Purifier"]) {
                def pState = settings["z${i}Purifier"].currentValue("switch")
                if (zNeedsIt && pState != "on") {
                    controlPurifier(settings["z${i}Purifier"], "on", aqiVal)
                    logAction("BMS Command -> Turned ON ${zName} Purifier (AQI: ${aqiVal}${isScrubbing ? ' | Scrubbing' : ''}${isSickModeForced ? ' | Sick Mode' : ''})")
                } else if (!zNeedsIt && pState != "off") {
                    controlPurifier(settings["z${i}Purifier"], "off", aqiVal)
                    logAction("BMS Command -> Turned OFF ${zName} Purifier (AQI: ${aqiVal})")
                }
            }

            if (zNeedsIt) {
                anyZoneNeedsPurification = true
                if (isSickModeForced) activeZoneReasons << "${zName} (Sick Mode)"
                else if (isScrubbing) activeZoneReasons << "${zName} (Scrubbing)"
                else if (isForcedOn) activeZoneReasons << "${zName} (Trigger Switch)"
                else if (zCycleActive) activeZoneReasons << "${zName} (Hourly Cycle)"
                else activeZoneReasons << "${zName} (AQI: ${aqiVal})"
            }
        }
    }

    // 4. MAIN WHOLE HOUSE EVALUATION
    def mainIndoorAQIVal = isWholeHouse ? (getAQI(indoorAQI) ?: 0) : 0
    
    if (isWholeHouse && mainPurifier) {
        def isMainForcedOn = false
        def isMainScrubbing = false
        def mainHouseNeedsIt = false
        
        // Main Trigger Check
        if (mainTriggerSwitch) {
            if (mainTriggerSwitch.any { it.currentValue("switch") == "on" }) {
                isMainForcedOn = true
                state.mainScrubEnd = now() + ((mainScrubTime ?: 30) * 60000)
            } else if (state.mainScrubEnd && now() < state.mainScrubEnd) {
                isMainForcedOn = true; isMainScrubbing = true
                runIn(((state.mainScrubEnd - now()) / 1000).toInteger(), "evaluateSystem")
            }
        }
        
        // Core Main House Need Check
        if (forceSickMode) {
            mainHouseNeedsIt = true
            isMainForcedOn = true // Bypasses prevent switch
        } else if (isQuiet) { 
            if (mainIndoorAQIVal >= (mainOverrideLevel ?: 100)) mainHouseNeedsIt = true 
        } else if (mainIndoorAQIVal > baseTarget || isPollenContinuous) {
            mainHouseNeedsIt = true
        }
        
        // Final Need Calculation
        def mainNeedsIt = anyZoneNeedsPurification || isMainForcedOn || mainHouseNeedsIt
        
        // Hourly Cycle apply check for Main
        def mainCycleActive = false
        if (!mainNeedsIt && isCycleHour && !isQuiet) {
            mainNeedsIt = true
            mainCycleActive = true
        }
        
        // Prevent Switch Check
        if (mainNeedsIt && !isMainForcedOn && !isMainScrubbing && mainPreventSwitch) {
            if (mainPreventSwitch.currentValue("switch") == "on") {
                def mOverride = mainOverrideLevel ?: 100
                if (mainIndoorAQIVal < mOverride && highestZoneAQI < mOverride) {
                    mainNeedsIt = false // Blocked
                    mainCycleActive = false // Override hourly cycle
                }
            }
        }

        // Generate Specific Reason String
        def currentActionReason = "Idle"
        if (mainNeedsIt) {
            if (forceSickMode) currentActionReason = "ON: Health Priority (Sick Mode Active)"
            else if (isMainScrubbing) currentActionReason = "ON: Main Post-Cleaning Scrubbing Active"
            else if (isMainForcedOn) currentActionReason = "ON: Forced by Main Trigger Switch"
            else if (isQuiet) currentActionReason = "ON: Emergency AQI Override during Quiet Mode"
            else if (isPollenContinuous) currentActionReason = "ON: Continuous Run for High Pollen (${pollenVal})"
            else if (mainHouseNeedsIt) currentActionReason = "ON: Main Indoor AQI (${mainIndoorAQIVal}) > Target (${baseTarget})"
            else if (mainCycleActive) currentActionReason = "ON: Routine Hourly Air Cycle"
            else if (anyZoneNeedsPurification) currentActionReason = "ON: Triggered by " + activeZoneReasons.join(", ")
        } else {
            if (mainPreventSwitch && mainPreventSwitch.currentValue("switch") == "on") {
                currentActionReason = "OFF: Blocked by Prevent Switch (AQI: ${mainIndoorAQIVal})"
            } else if (isQuiet) {
                currentActionReason = "OFF: Quiet Mode Active (AQI: ${mainIndoorAQIVal} < Emergency Level)"
            } else {
                def maxCurrent = Math.max(mainIndoorAQIVal, highestZoneAQI)
                currentActionReason = hasConfiguredZones ? "OFF: All zones and Main satisfied (Max AQI: ${maxCurrent} < Target ${baseTarget})" : "OFF: Indoor AQI Satisfied (${mainIndoorAQIVal} < Target ${baseTarget})"
            }
        }

        // Execute Hardware Command
        def mState = mainPurifier.currentValue("switch")
        if (mainNeedsIt && mState != "on") {
            controlPurifier(mainPurifier, "on", Math.max(mainIndoorAQIVal, highestZoneAQI))
            logAction("BMS Command -> Main Purifier ON. Reason: ${currentActionReason}")
            state.currentReason = currentActionReason
        } else if (!mainNeedsIt && mState != "off") {
            controlPurifier(mainPurifier, "off", Math.max(mainIndoorAQIVal, highestZoneAQI))
            logAction("BMS Command -> Main Purifier OFF. Reason: ${currentActionReason}")
            state.currentReason = currentActionReason
        } else {
            state.currentReason = currentActionReason 
        }
    } else if (!isWholeHouse) {
        // Detailed Multi-Zone Output
        state.currentReason = forceSickMode ? "ON: Health Priority (Sick Mode Active)" : (anyZoneNeedsPurification ? "MIXED: Active Zones -> " + activeZoneReasons.join(" | ") : "OFF: All zones satisfied (AQI < Target).")
    }
    
    // 5. GLOBAL ALERTING & NOTIFICATIONS (Audio/Push)
    def currentOutAQI = getAQI(outdoorAQI) ?: 0
    def maxIndoorAQI = isWholeHouse ? Math.max(mainIndoorAQIVal, highestZoneAQI) : highestZoneAQI
    
    def outThreshold = alertThresholdOutdoor ?: 150
    def inThreshold = alertThresholdIndoor ?: 100
    
    // Evaluate Outdoor Global Alerts
    if (alertThresholdOutdoor != null && currentOutAQI >= outThreshold) {
        if (!state.outdoorInAlarm) {
            state.outdoorInAlarm = true // Lock immediately
            triggerBadAQIAlert("Outdoor AQI is harmful (${currentOutAQI}).", ttsBadOutdoorAQIText, zoozSoundBadOutdoorAQI)
        }
    } else if (alertThresholdOutdoor != null && currentOutAQI < (outThreshold - 10)) {
        if (state.outdoorInAlarm) {
            logInfo("Outdoor AQI dropped below threshold. Clearing alarm state.")
            state.outdoorInAlarm = false
        }
    }
    
    // Evaluate Indoor Global Alerts
    if (alertThresholdIndoor != null && maxIndoorAQI >= inThreshold) {
        if (!state.indoorInAlarm) {
            state.indoorInAlarm = true // Lock immediately
            triggerBadAQIAlert("Indoor AQI is harmful (${maxIndoorAQI}).", ttsBadIndoorAQIText, zoozSoundBadIndoorAQI)
        }
    } else if (alertThresholdIndoor != null && maxIndoorAQI < (inThreshold - 10)) {
        if (state.indoorInAlarm) {
            logInfo("Indoor AQI dropped below threshold. Clearing alarm state.")
            state.indoorInAlarm = false
        }
    }
}

def turnOffAll() {
    if (isWholeHouse && mainPurifier) controlPurifier(mainPurifier, "off")
    else {
        for (int i = 1; i <= 12; i++) {
            if (settings["enableZ${i}"] && settings["z${i}Purifier"]) controlPurifier(settings["z${i}Purifier"], "off")
        }
    }
}

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }
