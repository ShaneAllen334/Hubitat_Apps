/**
 * Advanced House Security
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced House Security",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Defensive peace-of-mind engine for family safety. Granular Zooz routing, Curfew Zones, sensor debounce, left-open reminders, and smart arrival announcements.",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    // Failsafe to convert old list-based alerts to the new Map structure upon page load
    if (state.activeAlerts instanceof java.util.List) {
        state.activeAlerts = [:]
    }

    dynamicPage(name: "mainPage", title: "<b>Advanced House Security (Family Protection)</b>", install: true, uninstall: true) {
     
        section("<b>Master Control & Live Dashboard</b>") {
            input "masterEnableSwitch", "capability.switch", title: "<b>Master System Switch</b>", required: false, submitOnChange: true, description: "Turn this OFF to completely silence and bypass the entire security app. Useful for parties or maintenance."
            
            paragraph "<div style='background-color:#d4edda; padding:10px; border-radius:5px; border-left:5px solid #28a745;'>"
            input "armStaySwitch", "capability.switch", title: "<b>🛡️ Arm Perimeter (Stay Mode)</b>", required: false, submitOnChange: true, description: "When ON, the system expects you to be inside but the perimeter to be secure. If any exterior door or window opens, it triggers an Intrusion Alert."
            
            if (armStaySwitch) {
                paragraph "<b>Arm Stay Escalation Rules:</b><br><small>If a door/window is opened while Armed Stay is ON, select exactly what you want the house to do. You can turn on any combination of these:</small>"
                
                input "armStayPush", "bool", title: "📲 Send Critical Push Notifications", defaultValue: true, description: "Sends an immediate red alert to the phones selected in Section 9."
                input "armStayTTS", "bool", title: "🗣️ Announce via Global TTS", defaultValue: true, description: "Uses your Emergency TTS speakers to announce 'Intruder Alert. Arm Stay Perimeter Breach'."
                input "armStaySirens", "bool", title: "🚨 Trigger Global Emergency Sirens", defaultValue: true, description: "Fires the loud emergency Zooz sirens configured in Section 7."
                input "armStayStandardChime", "bool", title: "🚪 Play Standard Door Chime", defaultValue: false, description: "Plays the normal everyday chime you assigned to that specific door in Section 3."
                
                paragraph "<b>Outdoor Motion Behavior During Arm Stay:</b>"
                input "armStayOutMotionEnable", "bool", title: "🏃‍♂️ Force Outdoor Motion Alerts ON", defaultValue: false, submitOnChange: true, description: "If ON, outdoor motion alerts will override normal time schedules and alert whenever Arm Stay is active."
                if (armStayOutMotionEnable) {
                    input "armStayOutLuxSensor", "capability.illuminanceMeasurement", title: "Optional: Restrict by Lux (Brightness)", required: false, submitOnChange: true, description: "Only force the alerts if the lux reading is below the threshold."
                    if (armStayOutLuxSensor) {
                        input "armStayOutLuxThreshold", "number", title: "Lux Threshold (Announce if below this)", defaultValue: 1000, required: true
                    }
                }
            }
            paragraph "</div>"

            paragraph "<hr>"
            input "btnRefresh", "button", title: "🔄 Refresh Data"
           
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> An 'Eye in the Sky' real-time view of your home's entire defensive perimeter, active alerts, and expected engine responses.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            def alertColor = state.currentAlertLevel == "Critical" ? "#d9534f" : (state.currentAlertLevel == "Warning" ? "#f0ad4e" : "#007bff")
            def sysEnabled = !(masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off")
      
            if (!sysEnabled) alertColor = "#dc3545" 
            
            def currentMode = location.mode
            def bypassStatus = (state.bypassEndTime && now() < state.bypassEndTime) ? "<span style='color:#d39e00; font-weight:bold;'>ACTIVE (${Math.round((state.bypassEndTime - now())/60000)} mins left)</span>" : "Inactive"
            def armStayStatus = (armStaySwitch && armStaySwitch.currentValue("switch") == "on") ? "<span style='color:#28a745; font-weight:bold;'>ARMED STAY</span>" : "Disarmed"

            // Global Status Header
            def dashHTML = """
            <div style='background-color:#e9ecef; padding:12px; border-radius:5px; border-left:5px solid ${alertColor}; margin-bottom: 15px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);'>
                <div style='font-size: 15px; margin-bottom: 6px;'><b>System Status:</b> ${statusExplanation}</div>
                <div style='font-size: 14px;'><b>Current Mode:</b> <span style='color:#007bff; font-weight:bold;'>${currentMode}</span> &nbsp;|&nbsp; <b>Perimeter:</b> ${armStayStatus} &nbsp;|&nbsp; <b>Manual Bypass:</b> ${bypassStatus}</div>
            </div>
            """

            // ACTIVE ALERTS TABLE
            if (state.activeAlerts && state.activeAlerts.size() > 0) {
                def alertHtml = """
                <style>
                    .alert-table { width: 100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; margin-bottom: 15px;}
                    .alert-table th { background-color: ${alertColor}; color: white; padding: 10px; text-align: left; }
                    .alert-table td { border-bottom: 1px solid #ddd; padding: 8px 10px; background-color: #fff; }
                    .alert-table tr:hover td { background-color: #f8d7da; }
                    .alert-badge { background:#343a40; color:white; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: bold; }
                </style>
                <div style='border: 1px solid ${alertColor}; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow-x: auto;'>
                <table class="alert-table">
                    <thead>
                        <tr>
                            <th>First Detected</th>
                            <th>Last Detected</th>
                            <th>Source Device</th>
                            <th>Event Details</th>
                            <th>Count</th>
                            <th>Duration</th>
                        </tr>
                    </thead>
                    <tbody>
                """
                state.activeAlerts.each { key, alert ->
                    alertHtml += "<tr>"
                    alertHtml += "<td><span style='font-size:11px; color:#555;'>${alert.time}</span></td>"
                    alertHtml += "<td><span style='font-size:11px; color:#111; font-weight:bold;'>${alert.lastTime}</span></td>"
                    alertHtml += "<td><b>${alert.device}</b></td>"
                    alertHtml += "<td>${alert.msg}</td>"
                    alertHtml += "<td><span class='alert-badge'>${alert.count}</span></td>"
                    alertHtml += "<td><span style='color:#d9534f; font-weight:bold;'>${alert.duration}</span></td>"
                    alertHtml += "</tr>"
                }
                alertHtml += "</tbody></table></div>"
                
                dashHTML += alertHtml
            }

            // EYE IN THE SKY MATRIX
            dashHTML += """
            <style>
                .eye-wrapper { max-height: 550px; overflow-y: auto; border: 1px solid #ccc; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .eye-table { width: 100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; }
                .eye-table th { background-color: #343a40; color: white; padding: 10px; text-align: left; position: sticky; top: 0; z-index: 10; border-bottom: 2px solid #23272b; }
                .eye-table td { border-bottom: 1px solid #ddd; padding: 8px 10px; background-color: #fff; }
                .eye-table tr:hover td { background-color: #f8f9fa; }
                .state-bad { color: #d9534f; font-weight: bold; }
                .state-good { color: #28a745; font-weight: bold; }
                .zone-badge { display: inline-block; background: #e2e3e5; color: #383d41; padding: 3px 6px; border-radius: 4px; font-size: 11px; font-weight: bold; border: 1px solid #d6d8db; }
            </style>
            
            <div class='eye-wrapper'>
            <table class="eye-table">
                <thead>
                    <tr>
                        <th>Sensor Name</th>
                        <th>Assigned Zone</th>
                        <th>Position / State</th>
                        <th>Engine Status</th>
                        <th>Expected Engine Response</th>
                    </tr>
                </thead>
                <tbody>
            """
            
            def processedIds = []
            def buildRows = { devList, zoneName, stateAttr, badState, expectedAction, isZoneActive ->
                if (devList) {
                    devList.each { dev ->
                        if (!processedIds.contains(dev.id)) {
                            def currentState = dev.currentValue(stateAttr)
                            def stateClass = (currentState == badState) ? "state-bad" : "state-good"
                            
                            def finalStatus = ""
                            def isBypassed = (state.bypassEndTime && now() < state.bypassEndTime && bypassDoors?.find { it.id == dev.id })
                            def isArmStay = (armStaySwitch && armStaySwitch.currentValue("switch") == "on" && (zoneName == "Perimeter Door" || zoneName == "Perimeter Window"))

                            if (!sysEnabled) finalStatus = "<span style='color:#dc3545; font-size:11px; font-weight:bold;'>🔴 DISABLED</span>"
                            else if (isBypassed) finalStatus = "<span style='color:#d39e00; font-size:11px; font-weight:bold;'>🟡 BYPASSED</span>"
                            else if (isArmStay) finalStatus = "<span style='color:#28a745; font-size:11px; font-weight:bold;'>🟢 ARMED (STAY)</span>"
                            else if (isZoneActive) finalStatus = "<span style='color:#28a745; font-size:11px; font-weight:bold;'>🟢 ARMED</span>"
                            else finalStatus = "<span style='color:#6c757d; font-size:11px; font-weight:bold;'>⚪ SLEEPING</span>"
                            
                            def renderExpected = isArmStay ? "<span style='color:#d9534f; font-weight:bold;'>🚨 Critical Alarm if Breached</span>" : "<span style='font-size:12px; color:#555;'>${expectedAction}</span>"
                            
                            dashHTML += "<tr>"
                            dashHTML += "<td><b>${dev.displayName}</b></td>"
                            dashHTML += "<td><span class='zone-badge'>${zoneName}</span></td>"
                            dashHTML += "<td class='${stateClass}'>${currentState?.toString()?.toUpperCase() ?: 'UNKNOWN'}</td>"
                            dashHTML += "<td>${finalStatus}</td>"
                            dashHTML += "<td>${renderExpected}</td>"
                            dashHTML += "</tr>"
                            
                            processedIds << dev.id
                        }
                    }
                }
            }
            
            def hrActive = isAdvancedConditionMet(enableHighRiskRules1, highRiskModes1, highRiskStartTimeType1, highRiskStartTime1, highRiskStartOffset1, highRiskEndTimeType1, highRiskEndTime1, highRiskEndOffset1, enableHighRiskRules2, highRiskModes2, highRiskStartTimeType2, highRiskStartTime2, highRiskStartOffset2, highRiskEndTimeType2, highRiskEndTime2, highRiskEndOffset2)
            def curfActive = isAdvancedConditionMet(enableCurfewRules1, curfewModes1, curfewStartTimeType1, curfewStartTime1, curfewStartOffset1, curfewEndTimeType1, curfewEndTime1, curfewEndOffset1, enableCurfewRules2, curfewModes2, curfewStartTimeType2, curfewStartTime2, curfewStartOffset2, curfewEndTimeType2, curfewEndTime2, curfewEndOffset2)
            def doorActive = isAdvancedConditionMet(enableDoorRules1, doorModes1, doorStartTimeType1, doorStartTime1, doorStartOffset1, doorEndTimeType1, doorEndTime1, doorEndOffset1, enableDoorRules2, doorModes2, doorStartTimeType2, doorStartTime2, doorStartOffset2, doorEndTimeType2, doorEndTime2, doorEndOffset2)
            def leftActive = isAdvancedConditionMet(enableLeftOpenRules1, leftOpenModes1, leftOpenStartTimeType1, leftOpenStartTime1, leftOpenStartOffset1, leftOpenEndTimeType1, leftOpenEndTime1, leftOpenEndOffset1, enableLeftOpenRules2, leftOpenModes2, leftOpenStartTimeType2, leftOpenStartTime2, leftOpenStartOffset2, leftOpenEndTimeType2, leftOpenEndTime2, leftOpenEndOffset2)
            def outActive = isAdvancedConditionMet(enableOutMotionRules1, outMotionModes1, outStartTimeType1, outMotionStartTime1, outStartOffset1, outEndTimeType1, outMotionEndTime1, outEndOffset1, enableOutMotionRules2, outMotionModes2, outStartTimeType2, outMotionStartTime2, outStartOffset2, outEndTimeType2, outMotionEndTime2, outEndOffset2, overcastSwitch)
            def inActive = isAdvancedConditionMet(enableInMotionRules1, inMotionModes1, inStartTimeType1, inMotionStartTime1, inStartOffset1, inEndTimeType1, inMotionEndTime1, inEndOffset1, enableInMotionRules2, inMotionModes2, inStartTimeType2, inMotionStartTime2, inStartOffset2, inEndTimeType2, inMotionEndTime2, inEndOffset2)

            buildRows(highRiskSensors, "High-Risk", "contact", "open", "⚠️ Triggers Warning/TTS if rules met", hrActive)
            buildRows(curfewDoors, "Curfew", "contact", "open", "🚨 Triggers Critical Alarm if rules met", curfActive)
            buildRows(perimeterDoors, "Perimeter Door", "contact", "open", "🚪 Custom TTS/Chime + Push", doorActive)
            buildRows(perimeterWindows, "Perimeter Window", "contact", "open", "🪟 Logs Event / Perimeter Breach", true) 
            buildRows(leftOpenSensors, "Left-Open Watch", "contact", "open", "⏱️ Delayed TTS Reminder (${leftOpenDelay ?: 15}m)", leftActive)
            buildRows(outdoorMotion, "Outdoor Motion", "motion", "active", "🔊 Motion TTS / Tracks Outbound", outActive)
            buildRows(indoorMotion, "Indoor Motion", "motion", "active", "🏃 Motion TTS / Tracks Intrusion", inActive)
            buildRows(glassBreakSensors, "Glass Break", "sound.detected", "detected", "💥 Critical Escalation if breached", true) 
            
            def allDetectors = (smokeDetectors ?: []) + (coDetectors ?: [])
            allDetectors = allDetectors.unique { it.id } 
            
            if (allDetectors.size() > 0) {
                allDetectors.each { dev ->
                    if (!processedIds.contains(dev.id)) {
                         def sState = dev.hasAttribute("smoke") ? dev.currentValue("smoke") : "unknown"
                        def cState = dev.hasAttribute("carbonMonoxide") ? dev.currentValue("carbonMonoxide") : "unknown"
                        def batt = dev.hasAttribute("battery") ? dev.currentValue("battery") : "--"
                        
                        def statusColor = (sState == "detected" || cState == "detected") ? "state-bad" : "state-good"
                        def displayStatus = "CLEAR"
                        if (sState == "detected" && cState == "detected") displayStatus = "SMOKE & CO DETECTED"
                        else if (sState == "detected") displayStatus = "SMOKE DETECTED"
                        else if (cState == "detected") displayStatus = "CO DETECTED"
                        else if (sState == "tested" || cState == "tested") displayStatus = "TESTING"
                        
                        def lifeStatus = !sysEnabled ? "<span style='color:#dc3545; font-size:11px; font-weight:bold;'>🔴 DISABLED</span>" : "<span style='color:#28a745; font-size:11px; font-weight:bold;'>🟢 24/7 ARMED</span>"

                        dashHTML += "<tr>"
                        dashHTML += "<td><b>${dev.displayName}</b> <span style='font-size:10px; color:gray;'>(Batt: ${batt}%)</span></td>"
                        dashHTML += "<td><span class='zone-badge' style='background:#f8d7da; color:#721c24; border-color:#f5c6cb;'>Life Safety</span></td>"
                        dashHTML += "<td class='${statusColor}'>${displayStatus}</td>"
                        dashHTML += "<td>${lifeStatus}</td>"
                        dashHTML += "<td><span style='font-size:12px; color:#555;'>🔥 Evac Protocol / Overdrive Volume</span></td>"
                        dashHTML += "</tr>"
                        
                        processedIds << dev.id
                    }
                }
            }

            dashHTML += "</tbody></table></div>"
            paragraph dashHTML
            
            if (state.activeAlerts && state.activeAlerts.size() > 0) {
                input "clearAlertsBtn", "button", title: "🧹 Dismiss & Clear Active Alerts"
            }
        }

        section("<b>📋 Recent Context Engine Events (Last 25)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> A rolling timeline of house activity. Crucial for verifying when kids got home or identifying the source of an alert.</div>"
            if (state.eventHistory) {
                def historyStr = state.eventHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            } else {
                paragraph "<i>No recent events tracked.</i>"
            }
        }

        section("<b>1. 🚫 High-Risk Boundaries (Kids & Hazards)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Dedicated protection for sensitive areas like pool gates, gun safes, or medicine cabinets. Bypasses regular schedules to sound warnings.</div>"
            
            paragraph "<b>Rule Sets Explanation:</b> Use Rule Set 1 for your primary schedule (e.g., Daytime). Use Rule Set 2 if you need a different set of rules (e.g., Nighttime). To run 24/7, simply turn on Rule Set 1 and leave the Modes and Times completely blank."
            input "enableHighRiskRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableHighRiskRules1) {
                input "highRiskModes1", "mode", title: "Active Modes", multiple: true, required: false, description: "Select specific house modes (like 'Away'). Leave blank to run in ALL modes."
                input "highRiskStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskStartTimeType1 in ["Sunrise", "Sunset"]) input "highRiskStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0, description: "Negative numbers start before Sunrise/Sunset. Positive numbers start after."
                else input "highRiskStartTime1", "time", title: "Specific Start Time", required: false, description: "Leave blank to monitor 24/7."
                
                input "highRiskEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskEndTimeType1 in ["Sunrise", "Sunset"]) input "highRiskEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "highRiskEndTime1", "time", title: "Specific End Time", required: false, description: "Leave blank to monitor 24/7."
            }
            
            paragraph "<hr>"
            input "enableHighRiskRules2", "bool", title: "<b>Enable Rule Set 2 (Optional)</b>", defaultValue: false, submitOnChange: true
            if (enableHighRiskRules2) {
                input "highRiskModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "highRiskStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskStartTimeType2 in ["Sunrise", "Sunset"]) input "highRiskStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "highRiskStartTime2", "time", title: "Specific Start Time", required: false
                
                input "highRiskEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.highRiskEndTimeType2 in ["Sunrise", "Sunset"]) input "highRiskEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "highRiskEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "highRiskSensors", "capability.contactSensor", title: "Select High-Risk Contact Sensors", required: false, multiple: true, submitOnChange: true
            if (highRiskSensors) {
                highRiskSensors.each { dev ->
                    paragraph "<div style='background:#e9ecef; padding:5px;'><b style='color:#1a73e8;'>${dev.displayName} Config</b></div>"
                    input "highRiskMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Warning. ${dev.displayName} has been opened."
                    input "highRiskTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "highRiskPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "highRiskZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true, description: "Select the specific siren/chime to play from."
                    if (settings["highRiskZooz_${dev.id}"]) {
                        settings["highRiskZooz_${dev.id}"].each { zDev -> 
                            input "highRiskZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false, description: "Track number to play (e.g., 29)"
                            input "testBtn_highRisk_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>2. 🌙 Toddler / Wander Prevention (Curfew Zones)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Instantly triggers a Critical Alarm (like a panic button) if designated doors are opened during specified hours. Good for preventing kids from wandering outside at night.</div>"
            
            input "enableCurfewRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableCurfewRules1) {
                input "curfewModes1", "mode", title: "Active Modes", multiple: true, required: false, description: "Select specific house modes (like 'Night'). Leave blank to run in ALL modes."
                input "curfewStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewStartTimeType1 in ["Sunrise", "Sunset"]) input "curfewStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewStartTime1", "time", title: "Specific Start Time", required: false, description: "Leave blank for 24/7."
                
                input "curfewEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewEndTimeType1 in ["Sunrise", "Sunset"]) input "curfewEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewEndTime1", "time", title: "Specific End Time", required: false, description: "Leave blank for 24/7."
            }
            
            paragraph "<hr>"
            input "enableCurfewRules2", "bool", title: "<b>Enable Rule Set 2 (Optional)</b>", defaultValue: false, submitOnChange: true
            if (enableCurfewRules2) {
                input "curfewModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "curfewStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewStartTimeType2 in ["Sunrise", "Sunset"]) input "curfewStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewStartTime2", "time", title: "Specific Start Time", required: false
                
                input "curfewEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.curfewEndTimeType2 in ["Sunrise", "Sunset"]) input "curfewEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "curfewEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "curfewDoors", "capability.contactSensor", title: "Select Curfew Doors", required: false, multiple: true, submitOnChange: true
            if (curfewDoors) {
                curfewDoors.each { dev ->
                    paragraph "<div style='background:#e9ecef; padding:5px;'><b style='color:#1a73e8;'>${dev.displayName} Config</b></div>"
                    input "curfewMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Emergency. ${dev.displayName} was opened during curfew."
                    input "curfewTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "curfewPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "curfewZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["curfewZooz_${dev.id}"]) {
                        settings["curfewZooz_${dev.id}"].each { zDev -> 
                            input "curfewZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_curfew_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>3. 🚪 Perimeter Setup (Doors, Windows, Glass)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Manages standard door chimes, window logging, and glass break alarms. This is where your everyday 'door opened' chimes are configured.</div>"
            
            input "enableDoorRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableDoorRules1) {
                input "doorModes1", "mode", title: "Active Modes", multiple: true, required: false, description: "Select the modes you want standard chimes to play. Leave blank to play in ALL modes."
                input "doorStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorStartTimeType1 in ["Sunrise", "Sunset"]) input "doorStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorStartTime1", "time", title: "Specific Start Time", required: false, description: "Leave empty for 24/7."
                
                input "doorEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorEndTimeType1 in ["Sunrise", "Sunset"]) input "doorEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorEndTime1", "time", title: "Specific End Time", required: false, description: "Leave empty for 24/7."
            }
            
            paragraph "<hr>"
            input "enableDoorRules2", "bool", title: "<b>Enable Rule Set 2 (Optional)</b>", defaultValue: false, submitOnChange: true
            if (enableDoorRules2) {
                input "doorModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "doorStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorStartTimeType2 in ["Sunrise", "Sunset"]) input "doorStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorStartTime2", "time", title: "Specific Start Time", required: false
                
                input "doorEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.doorEndTimeType2 in ["Sunrise", "Sunset"]) input "doorEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "doorEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "perimeterDoors", "capability.contactSensor", title: "Select Exterior Doors", required: false, multiple: true, submitOnChange: true
            if (perimeterDoors) {
                perimeterDoors.each { dev ->
                    paragraph "<div style='background:#e9ecef; padding:5px;'><b style='color:#1a73e8;'>${dev.displayName} Config</b></div>"
                    input "doorMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "${dev.displayName} opened.", description: "The text spoken by the TTS speaker or sent in the push notification."
                    input "doorTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "doorPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "doorZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["doorZooz_${dev.id}"]) {
                        settings["doorZooz_${dev.id}"].each { zDev -> 
                            input "doorZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_door_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
            
            paragraph "<hr>"
            paragraph "<b>Mode Change Reminders:</b> Warns you if you arm the house but left a door open."
            input "perimeterCheckModes", "mode", title: "Modes that trigger an 'Open' warning", multiple: true, required: false, description: "Example: If you select 'Night' here, the app will check all doors and windows the moment the house switches to Night mode and warn you if any are open."
            input "perimeterCheckPush", "capability.notification", title: "Who gets this reminder?", multiple: true, required: false
            input "perimeterCheckTTS", "bool", title: "Announce on global TTS?", defaultValue: true
            
            paragraph "<hr>"
            input "perimeterWindows", "capability.contactSensor", title: "Select Perimeter Windows", required: false, multiple: true, description: "Windows don't chime normally, but are tracked for Arm Stay breaches and context logging."
            input "glassBreakSensors", "capability.sensor", title: "Select Glass Break Sensors", required: false, multiple: true, description: "If a glass break sensor trips, followed by indoor motion, it instantly escalates to a Critical alarm."
            input "outboundGracePeriod", "number", title: "Outbound Grace Period (Seconds)", required: false, defaultValue: 30, description: "How long to ignore outdoor motion alerts immediately after an exterior door is opened (so you don't trigger your own cameras when walking outside)."
        }

        section("<b>4. ⏰ Proactive 'Left Open' Reminders</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Sends a delayed alert if a door/window remains open past the specified timer.</div>"
            
            input "enableLeftOpenRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableLeftOpenRules1) {
                input "leftOpenModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "leftOpenStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenStartTimeType1 in ["Sunrise", "Sunset"]) input "leftOpenStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenStartTime1", "time", title: "Specific Start Time", required: false
                
                input "leftOpenEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenEndTimeType1 in ["Sunrise", "Sunset"]) input "leftOpenEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableLeftOpenRules2", "bool", title: "<b>Enable Rule Set 2 (Optional)</b>", defaultValue: false, submitOnChange: true
            if (enableLeftOpenRules2) {
                input "leftOpenModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "leftOpenStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenStartTimeType2 in ["Sunrise", "Sunset"]) input "leftOpenStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenStartTime2", "time", title: "Specific Start Time", required: false
                
                input "leftOpenEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.leftOpenEndTimeType2 in ["Sunrise", "Sunset"]) input "leftOpenEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "leftOpenEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "leftOpenDelay", "number", title: "Minutes to wait before reminding", required: false, defaultValue: 15, description: "If the door is open longer than this, the reminder message will play."
            input "leftOpenSensors", "capability.contactSensor", title: "Select Doors/Windows to Monitor", required: false, multiple: true, submitOnChange: true
            if (leftOpenSensors) {
                leftOpenSensors.each { dev ->
                    paragraph "<div style='background:#e9ecef; padding:5px;'><b style='color:#1a73e8;'>${dev.displayName} Config</b></div>"
                    input "leftOpenMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Reminder, the ${dev.displayName} is left open."
                    input "leftOpenTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "leftOpenPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "leftOpenZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["leftOpenZooz_${dev.id}"]) {
                        settings["leftOpenZooz_${dev.id}"].each { zDev -> 
                            input "leftOpenZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_leftOpen_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>5. 🏃 Interior & Exterior Motion</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Watches for unwanted motion. Includes smart logic to ignore you if you just walked out the front door.</div>"
            
            paragraph "<div style='background:#f8f9fa; border-left:4px solid #1a73e8; padding:8px;'><b>Outdoor Motion Config</b></div>"
            input "overcastSwitch", "capability.switch", title: "Overcast / Weather Override Switch", required: false, description: "Optional virtual switch. If ON, outdoor alerts will trigger regardless of time constraints. Useful for turning on extra driveway warnings during a storm."

            input "enableOutMotionRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableOutMotionRules1) {
                input "outMotionModes1", "mode", title: "Active Modes", multiple: true, required: false, description: "Leave blank for ALL modes."
                input "outStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outStartTimeType1 in ["Sunrise", "Sunset"]) input "outStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionStartTime1", "time", title: "Specific Start Time", required: false, description: "Leave blank for 24/7."

                input "outEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outEndTimeType1 in ["Sunrise", "Sunset"]) input "outEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionEndTime1", "time", title: "Specific End Time", required: false, description: "Leave blank for 24/7."
            }
            
            paragraph "<hr>"
            input "enableOutMotionRules2", "bool", title: "<b>Enable Rule Set 2 (Optional)</b>", defaultValue: false, submitOnChange: true
            if (enableOutMotionRules2) {
                input "outMotionModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "outStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outStartTimeType2 in ["Sunrise", "Sunset"]) input "outStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionStartTime2", "time", title: "Specific Start Time", required: false

                input "outEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.outEndTimeType2 in ["Sunrise", "Sunset"]) input "outEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "outMotionEndTime2", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            paragraph "<b>Resident Outbound Logic:</b> Prevents false alarms when you walk outside."
            input "enableLinkedGracePeriod", "bool", title: "<b>Enable 1-to-1 Door/Motion Linking</b>", defaultValue: false, submitOnChange: true, description: "If ON, you must explicitly link specific doors to specific motion sensors below. If OFF, opening ANY door suppresses ALL outdoor motion globally for the grace period."

            input "outdoorMotion", "capability.motionSensor", title: "Select Outdoor Motion Sensors", required: false, multiple: true, submitOnChange: true
            if (outdoorMotion) {
                outdoorMotion.each { dev ->
                    paragraph "<div style='background:#e9ecef; padding:5px;'><b style='color:#1a73e8;'>${dev.displayName} Config</b></div>"
                    
                    if (settings.enableLinkedGracePeriod) {
                        input "linkedDoors_${dev.id}", "capability.contactSensor", title: "🔗 Linked Doors (Grace Period Trigger)", required: false, multiple: true, description: "Which specific door opening should mute this specific motion sensor?"
                    }
                    
                    input "outMotionMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Motion detected at the ${dev.displayName}."
                    input "outMotionTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "outMotionPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "outMotionZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["outMotionZooz_${dev.id}"]) {
                        settings["outMotionZooz_${dev.id}"].each { zDev -> 
                            input "outMotionZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_outMotion_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
            
            paragraph "<br><div style='background:#f8f9fa; border-left:4px solid #1a73e8; padding:8px;'><b>Indoor Motion Config</b></div>"
            input "enableInMotionRules1", "bool", title: "<b>Enable Rule Set 1</b>", defaultValue: true, submitOnChange: true
            if (enableInMotionRules1) {
                input "inMotionModes1", "mode", title: "Active Modes", multiple: true, required: false
                input "inStartTimeType1", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inStartTimeType1 in ["Sunrise", "Sunset"]) input "inStartOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionStartTime1", "time", title: "Specific Start Time", required: false

                input "inEndTimeType1", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inEndTimeType1 in ["Sunrise", "Sunset"]) input "inEndOffset1", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionEndTime1", "time", title: "Specific End Time", required: false
            }
            
            paragraph "<hr>"
            input "enableInMotionRules2", "bool", title: "<b>Enable Rule Set 2 (Optional)</b>", defaultValue: false, submitOnChange: true
            if (enableInMotionRules2) {
                input "inMotionModes2", "mode", title: "Active Modes", multiple: true, required: false
                input "inStartTimeType2", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inStartTimeType2 in ["Sunrise", "Sunset"]) input "inStartOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionStartTime2", "time", title: "Specific Start Time", required: false

                input "inEndTimeType2", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                if (settings.inEndTimeType2 in ["Sunrise", "Sunset"]) input "inEndOffset2", "number", title: "Offset (minutes)", defaultValue: 0
                else input "inMotionEndTime2", "time", title: "Specific End Time", required: false
            }
            
            input "indoorMotion", "capability.motionSensor", title: "Select Indoor Motion Sensors", required: false, multiple: true, submitOnChange: true
            if (indoorMotion) {
                indoorMotion.each { dev ->
                    paragraph "<div style='background:#e9ecef; padding:5px;'><b style='color:#1a73e8;'>${dev.displayName} Config</b></div>"
                    input "inMotionMsg_${dev.id}", "text", title: "Message Text", required: false, defaultValue: "Motion detected in the ${dev.displayName}."
                    input "inMotionTTS_${dev.id}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "inMotionPush_${dev.id}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "inMotionZooz_${dev.id}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["inMotionZooz_${dev.id}"]) {
                        settings["inMotionZooz_${dev.id}"].each { zDev -> 
                            input "inMotionZoozFile_${dev.id}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_inMotion_${dev.id}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                }
            }
        }

        section("<b>6. 📍 Individual Presence & Safe Transit (Up to 4 People)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Configure granular arrival and departure announcements for individuals (e.g., 'Welcome home, John').</div>"
            
            for (int i = 1; i <= 4; i++) {
                input "enablePerson${i}", "bool", title: "<b>Enable Person / Slot ${i}</b>", defaultValue: false, submitOnChange: true, description: "Turns on the settings for a single person's phone/sensor."
                if (settings["enablePerson${i}"]) {
                    input "personSensor${i}", "capability.presenceSensor", title: "Select Presence Sensor for Person ${i}", required: true
                    
                    paragraph "<div style='background-color:#e9ecef; padding:5px; border-radius:3px;'><b>Arrival Settings (Present)</b></div>"
                    input "arrivalModes${i}", "mode", title: "Active Modes for Arrival Event", multiple: true, required: false
                    input "arrStartTimeType${i}", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["arrStartTimeType${i}"] in ["Sunrise", "Sunset"]) input "arrStartOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "arrStartTime${i}", "time", title: "Specific Start Time", required: false
                    input "arrEndTimeType${i}", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["arrEndTimeType${i}"] in ["Sunrise", "Sunset"]) input "arrEndOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "arrEndTime${i}", "time", title: "Specific End Time", required: false
                    
                    input "arrivalMsg${i}", "text", title: "Custom Arrival Message", required: false, defaultValue: "Welcome home."
                    input "arrivalTTS${i}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "arrivalPush${i}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "arrivalZooz${i}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["arrivalZooz${i}"]) {
                        settings["arrivalZooz${i}"].each { zDev -> 
                            input "arrivalZoozFile${i}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_arrival_${i}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }

                    paragraph "<div style='background-color:#e9ecef; padding:5px; border-radius:3px;'><b>Departure Settings (Not Present)</b></div>"
                    input "departureModes${i}", "mode", title: "Active Modes for Departure Event", multiple: true, required: false
                    input "depStartTimeType${i}", "enum", title: "Start Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["depStartTimeType${i}"] in ["Sunrise", "Sunset"]) input "depStartOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "depStartTime${i}", "time", title: "Specific Start Time", required: false
                    input "depEndTimeType${i}", "enum", title: "End Time Type", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
                    if (settings["depEndTimeType${i}"] in ["Sunrise", "Sunset"]) input "depEndOffset${i}", "number", title: "Offset (minutes)", defaultValue: 0
                    else input "depEndTime${i}", "time", title: "Specific End Time", required: false
                    
                    input "departureMsg${i}", "text", title: "Custom Departure Message", required: false, defaultValue: "Safe travels."
                    input "departureTTS${i}", "capability.speechSynthesis", title: "► Target TTS Devices", multiple: true, required: false
                    input "departurePush${i}", "capability.notification", title: "► Target Push Notifications", multiple: true, required: false
                    input "departureZooz${i}", "capability.chime", title: "► Target Zooz Devices", multiple: true, submitOnChange: true
                    if (settings["departureZooz${i}"]) {
                        settings["departureZooz${i}"].each { zDev -> 
                            input "departureZoozFile${i}_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                            input "testBtn_departure_${i}_${zDev.id}", "button", title: "🔊 Test Speaker"
                        }
                    }
                    paragraph "<hr style='border: 1px solid #1a73e8;'>"
                }
            }
        }

        section("<b>7. 🚨 Life Safety & Dedicated Panic Engine</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Links Smoke/CO detectors and manual Panic buttons to system-wide emergency overdrive (lights, sirens, unlocking doors).</div>"
            
            input "smokeDetectors", "capability.smokeDetector", title: "Select Smoke Detectors", required: false, multiple: true
            input "coDetectors", "capability.carbonMonoxideDetector", title: "Select CO Detectors", required: false, multiple: true
            
            paragraph "<div style='background-color:#f8d7da; padding:10px; border-radius:5px; border-left:5px solid #d9534f;'>"
            paragraph "<b>⚠️ 5-Minute Panic Loop Engine</b><br><small>If any button below is pressed, it triggers an immediate response AND recurring push notifications every 5 minutes until the Reset Switch is flipped.</small>"
            
            for (int i = 1; i <= 4; i++) {
                input "panicButton${i}", "capability.pushableButton", title: "Panic Button ${i} Input", required: false, description: "A physical button or remote."
            }
            paragraph "<hr>"
            input "panicResetSwitch", "capability.switch", title: "<b>Panic Loop Reset Switch</b>", required: false, description: "Create a Virtual Switch in Hubitat. You MUST turn this ON to silence an active Panic loop."
            paragraph "</div>"
            
            paragraph "<b>Emergency Evacuation Automations</b>"
            input "emergencySwitches", "capability.switch", title: "Turn ON Standard Lights/Switches", required: false, multiple: true, description: "Lights up the house to help you see and escape."
            input "emergencyColoredLights", "capability.colorControl", title: "Turn ON & Change Color of RGB Lights", required: false, multiple: true
            if (settings.emergencyColoredLights) {
                input "emergencyLightColor", "enum", title: "Select RGB Emergency Color", options: ["Red", "White", "Blue", "Green", "Yellow"], required: false, defaultValue: "Red"
            }
            input "emergencyLocks", "capability.lock", title: "Unlock Doors", required: false, multiple: true, description: "Ensures deadbolts are open for rapid escape or for firemen to enter."
            
            paragraph "<b>Emergency Audio Override</b>"
            input "emergencyTTSMessage", "text", title: "Custom TTS Evacuation Message", required: false, defaultValue: "Emergency. Evacuation protocol initiated. Please exit the house immediately."
            input "emergencyTTS", "capability.speechSynthesis", title: "► Target Global TTS Devices for Emergencies", required: false, multiple: true
            
            input "emergencyZooz", "capability.chime", title: "► Target Zooz Devices for Critical Sirens", multiple: true, submitOnChange: true, description: "These are your primary, loudest sirens. They are also used for 'Arm Stay' and Intrusion alerts."
            if (emergencyZooz) {
                emergencyZooz.each { zDev -> 
                    input "emergencyZoozFile_${zDev.id}", "number", title: "   ↳ ${zDev.displayName} File #", required: false
                    input "testBtn_emergency_0_${zDev.id}", "button", title: "🔊 Test Speaker"
                }
            }
        }

        section("<b>8. 🔊 Dynamic Audio Routing & Debounce</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Prevents notification spam (debounce) and handles system-wide Quiet Mode muting.</div>"
            
            paragraph "<b>Dynamic TTS Volume Scaling</b>"
            input "dayVolume", "number", title: "Standard Day Volume (0-100)", required: false, defaultValue: 70
            input "quietVolume", "number", title: "Whisper/Quiet Volume (0-100)", required: false, defaultValue: 20, description: "If an announcement plays during Quiet Hours, it will force the speaker down to this volume."
            input "emergencyVolume", "number", title: "Emergency Overdrive Volume (0-100)", required: false, defaultValue: 100, description: "Forces the speaker to absolute maximum volume during a fire or intrusion."
            
            paragraph "<i>When should the system use Whisper/Quiet Volume?</i>"
            input "quietVolumeModes", "mode", title: "Quiet Volume Modes", multiple: true, required: false
            
            input "quietStartTimeType", "enum", title: "Quiet Volume Start", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.quietStartTimeType in ["Sunrise", "Sunset"]) input "quietStartOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "quietVolumeStartTime", "time", title: "Specific Start Time", required: false
            
            input "quietEndTimeType", "enum", title: "Quiet Volume End", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.quietEndTimeType in ["Sunrise", "Sunset"]) input "quietEndOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "quietVolumeEndTime", "time", title: "Specific End Time", required: false

            paragraph "<hr>"
            paragraph "<b>Sensor Debounce (Spam Prevention)</b>"
            input "motionDebounce", "number", title: "Motion Cooldown (Minutes)", required: false, defaultValue: 5, description: "If someone is pacing in a room, this forces the system to wait X minutes before speaking the motion alert again, preventing constant spam."
            input "doorDebounce", "number", title: "Door/Window Cooldown (Seconds)", required: false, defaultValue: 30, description: "Forces a cooldown (in seconds) between door chimes so kids swinging a door open and shut won't flood the speakers."
            
            paragraph "<hr>"
            paragraph "<b>Quiet Hours (Granular Routing Rules)</b>"
            paragraph "<div style='font-size:13px; color:#555;'>Configure exactly what happens when the house is in a Quiet Mode. <i>(Life-Safety, Curfews, and High-Risk Boundaries will still override this and sound alarms).</i></div>"
            input "quietModes", "mode", title: "Select Quiet Modes (e.g., Night, Sleeping)", multiple: true, required: false
            
            paragraph "<i>1. Allowed Event Categories in Quiet Mode:</i>"
            input "qmAllowDoors", "bool", title: "Allow Perimeter Door Alerts", defaultValue: false
            input "qmAllowOutMotion", "bool", title: "Allow Outdoor Motion Alerts", defaultValue: false
            input "qmAllowInMotion", "bool", title: "Allow Indoor Motion Alerts", defaultValue: false
            input "qmAllowLeftOpen", "bool", title: "Allow Left-Open Reminders", defaultValue: false
            input "qmAllowPresence", "bool", title: "Allow Arrival/Departure Announcements", defaultValue: false

            paragraph "<i>2. Allowed Output Methods in Quiet Mode (for the events selected above):</i>"
            input "qmAllowPush", "bool", title: "Send Push Notifications", defaultValue: true, description: "If ON, you get a silent pop-up on your phone during quiet hours."
            input "qmAllowTTS", "bool", title: "Play TTS Announcements", defaultValue: false, description: "If ON, the speakers will talk (using the Whisper/Quiet Volume set above)."
            input "qmAllowZooz", "bool", title: "Play Zooz Chimes", defaultValue: false, description: "If ON, physical door chimes will still ring during quiet hours."
            
            paragraph "<hr>"
            paragraph "<div style='background-color:#fff3cd; padding:10px; border-radius:5px; border-left:5px solid #ffc107;'>"
            input "enableCustomAnnouncements", "bool", title: "<b>MASTER AUDIO TOGGLE: Enable ALL Routine Announcements</b>", defaultValue: true, description: "CRITICAL: This must be turned ON for any standard Door, Motion, or Presence audio to play. Turn off to quickly silence the whole house."
            paragraph "</div>"
        }

        section("<b>9. 📲 Global Alert Notifications</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Sends push notifications for engine-level escalations (e.g., Glass Break + Motion tracking), separate from routine alerts.</div>"
            input "pushCritical", "capability.notification", title: "Who receives Critical Alerts?", multiple: true, required: false, description: "These phones get immediate alerts for Intrusion, Curfew Breaches, and Glass Break regardless of the time of day."
            input "pushWarnings", "capability.notification", title: "Who receives Security Warnings?", multiple: true, required: false, description: "These phones get notifications for standard warnings, but they are limited by the time schedules below."
            input "warningModes", "mode", title: "Modes to ALLOW Warning notifications", multiple: true, required: false, description: "Leave blank for 24/7."
          
            input "warnStartTimeType", "enum", title: "Warning Allowed Start", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.warnStartTimeType in ["Sunrise", "Sunset"]) input "warnStartOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "warningStartTime", "time", title: "Specific Start Time", required: false
            
            input "warnEndTimeType", "enum", title: "Warning Allowed End", options: ["Specific Time", "Sunrise", "Sunset"], defaultValue: "Specific Time", submitOnChange: true
            if (settings.warnEndTimeType in ["Sunrise", "Sunset"]) input "warnEndOffset", "number", title: "Offset (minutes)", defaultValue: 0
            else input "warningEndTime", "time", title: "Specific End Time", required: false
            
            input "enableWatchdog", "bool", title: "Enable Daily Sensor Health Watchdog", defaultValue: true, description: "Checks for low batteries on all configured sensors every day at noon."
            input "pushHealth", "capability.notification", title: "Who receives Maintenance alerts?", multiple: true, required: false
        }
        
        section("<b>10. 🐕 Quality of Life (Temporary Bypasses)</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Use a button push or virtual switch to temporarily mute warnings for specific doors (like letting the dog out at 2 AM) before automatically re-arming.</div>"
            input "bypassButton", "capability.pushableButton", title: "Bypass Button", required: false
            input "bypassButtonNum", "number", title: "Button Number to trigger bypass", required: false, defaultValue: 1
            input "bypassSwitch", "capability.switch", title: "Bypass Switch", required: false
            input "bypassSwitchAutoOff", "bool", title: "Automatically turn Bypass Switch back off?", defaultValue: true
            
            paragraph "<hr>"
            input "bypassDoors", "capability.contactSensor", title: "Which doors/sensors should be ignored?", required: false, multiple: true
            input "bypassDuration", "number", title: "Bypass Duration (Minutes)", required: false, defaultValue: 5, description: "How long the door is ignored before the system automatically rearms it."
            input "bypassTTS", "capability.speechSynthesis", title: "Acknowledge Bypass via TTS?", required: false, multiple: true, description: "Speaks aloud to let you know the bypass was successful."
        }

        section("<b>11. 📡 System Integration & Information Output</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Writes the real-time activity and context events to a Virtual Child Device so other Apps/Dashboards can easily read the system state.</div>"
            input "enableChildDevice", "bool", title: "<b>Create Information Child Device</b>", defaultValue: false, submitOnChange: true
        }

        section("<b>12. 🌙 Night Time Transit Tracker</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Tracks directional movement from up to 3 bedrooms -> hallway -> bathroom to intelligently suppress false indoor alarms at night. Each bedroom arms independently via switch.</div>"
            
            for (int i = 1; i <= 3; i++) {
                paragraph "<b>Bedroom ${i} Setup</b>"
                input "gnSwitch${i}", "capability.switch", title: "Good Night Switch (Trigger ${i})", required: false, submitOnChange: true
                if (settings["gnSwitch${i}"]) {
                    input "originRoom${i}", "capability.motionSensor", title: "↳ Motion Sensor (Bedroom ${i})", required: true
                }
            }
            
            paragraph "<hr><b>Shared Transit Path</b>"
            input "transitZone", "capability.motionSensor", title: "Transit Zone (e.g., Hallway)", required: false
            input "destinationZone", "capability.motionSensor", title: "Destination (e.g., Bathroom)", required: false
            
            input "transitTimeout", "number", title: "Timeout (Seconds) to reset tracking", required: false, defaultValue: 60
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def initialize() {
    if (!state.eventHistory) state.eventHistory = []
    state.activeAlerts = [:] // Formatted as a Map for table deduplication
    
    if (!state.leftOpenAlertSent) state.leftOpenAlertSent = [:]
  
    state.currentAlertLevel = "Normal"
    state.lastDoorOpenTime = 0; state.lastGlassBreakTime = 0
    state.bypassEndTime = 0
    
    // Child Device Creation
    if (enableChildDevice) {
        def childId = "ahs_info_${app.id}"
        def child = getChildDevice(childId)
        if (!child) {
            try {
                addChildDevice("ShaneAllen", "Advanced House Security Information Device", childId, null, [name: "AHS Information", label: "AHS Information Output"])
                logAction("Created Information Child Device.")
            } catch (e) {
                log.error "Could not create child device. Please ensure the custom driver is installed. Error: ${e}"
            }
        }
    }

    subscribe(location, "mode", modeChangeHandler)
    if (masterEnableSwitch) subscribe(masterEnableSwitch, "switch", masterSwitchHandler)
    
    if (perimeterDoors) subscribe(perimeterDoors, "contact", contactHandler)
    if (perimeterWindows) subscribe(perimeterWindows, "contact", contactHandler)
    if (highRiskSensors) subscribe(highRiskSensors, "contact", highRiskHandler)
    if (curfewDoors) subscribe(curfewDoors, "contact", curfewHandler)
    if (leftOpenSensors) subscribe(leftOpenSensors, "contact", leftOpenHandler)
    
    if (outdoorMotion) {
        subscribe(outdoorMotion, "motion.active", outdoorMotionHandler)
        subscribe(outdoorMotion, "motion.inactive", motionInactiveHandler)
    }
    if (indoorMotion) {
        subscribe(indoorMotion, "motion.active", indoorMotionHandler)
        subscribe(indoorMotion, "motion.inactive", motionInactiveHandler)
    }
    
    // Subscribe to individual presence slots
    for (int i = 1; i <= 4; i++) {
        if (settings["enablePerson${i}"] && settings["personSensor${i}"]) {
            subscribe(settings["personSensor${i}"], "presence", individualPresenceHandler)
        }
    }
  
    if (glassBreakSensors) subscribe(glassBreakSensors, "sound.detected", glassBreakHandler) 
    if (smokeDetectors) subscribe(smokeDetectors, "smoke", lifeSafetyHandler)
    if (coDetectors) subscribe(coDetectors, "carbonMonoxide", lifeSafetyHandler)
    
    // Dedicated Panic Engine Subscriptions
    for (int i = 1; i <= 4; i++) {
        if (settings["panicButton${i}"]) subscribe(settings["panicButton${i}"], "pushed", panicHandler)
    }
    if (panicResetSwitch) subscribe(panicResetSwitch, "switch", panicResetHandler)
    
    if (bypassButton) subscribe(bypassButton, "pushed", bypassHandler)
    if (bypassSwitch) subscribe(bypassSwitch, "switch.on", bypassHandler)
    
    // Independent Night Tracker Subscriptions
    for (int i = 1; i <= 3; i++) {
        if (settings["gnSwitch${i}"]) subscribe(settings["gnSwitch${i}"], "switch", goodNightHandler)
        if (settings["originRoom${i}"]) subscribe(settings["originRoom${i}"], "motion.active", nightMotionTracker)
    }
    if (transitZone) subscribe(transitZone, "motion.active", nightMotionTracker)
    if (destinationZone) subscribe(destinationZone, "motion.active", nightMotionTracker)
   
    if (enableWatchdog) schedule("0 0 12 * * ?", dailyHealthCheck)
    
    logAction("Advanced House Security Initialized.")
}

def isSystemEnabled() {
    if (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") return false
    return true
}

// Child Device Output Helper
def updateChildDevice(msg) {
    if (enableChildDevice) {
        def child = getChildDevice("ahs_info_${app.id}")
        if (child) {
            child.sendEvent(name: "variable", value: msg, descriptionText: "AHS Context Update")
        }
    }
}

// Advanced Sunrise/Sunset helper
def resolveTime(type, specificTime, offset) {
    if (type == "Sunrise" || type == "Sunset") {
        def sun = getSunriseAndSunset()
        if (!sun) return null
        def baseTime = (type == "Sunrise") ? sun.sunrise.time : sun.sunset.time
        return new Date(baseTime + ((offset ?: 0) * 60000))
    } else {
        return specificTime ? timeToday(specificTime, location.timeZone) : null
    }
}

// Master condition evaluation for all dual-rule sections
def isAdvancedConditionMet(en1, modes1, stType1, sTime1, sOff1, etType1, eTime1, eOff1, en2, modes2, stType2, sTime2, sOff2, etType2, eTime2, eOff2, overrideSwitch = null) {
    if (overrideSwitch && overrideSwitch.currentValue("switch") == "on") return true
    if (!en1 && !en2) return false

    def match1 = false
    if (en1) {
        def modeOk = !modes1 || (modes1 as List).contains(location.mode)
        def timeOk = true
        def s = resolveTime(stType1, sTime1, sOff1)
        def e = resolveTime(etType1, eTime1, eOff1)
        if (s && e) {
            def n = new Date()
            timeOk = (s < e) ? (n >= s && n <= e) : (n >= s || n <= e)
        }
        match1 = modeOk && timeOk
    }

    def match2 = false
    if (en2) {
        def modeOk = !modes2 || (modes2 as List).contains(location.mode)
        def timeOk = true
        def s = resolveTime(stType2, sTime2, sOff2)
        def e = resolveTime(etType2, eTime2, eOff2)
        if (s && e) {
            def n = new Date()
            timeOk = (s < e) ? (n >= s && n <= e) : (n >= s || n <= e)
        }
        match2 = modeOk && timeOk
    }

    return match1 || match2
}

// --- DYNAMIC VOLUME HELPER ---
def applyTTSVolume(devices, isCritical=false) {
    if (!devices) return
    def targetVol = settings.dayVolume ?: 70
    
    if (isCritical) {
        targetVol = settings.emergencyVolume ?: 100
    } else {
        def isQuietMode = settings.quietVolumeModes && (settings.quietVolumeModes as List).contains(location.mode)
        def isQuietTime = false
        def s = resolveTime(settings.quietStartTimeType, settings.quietVolumeStartTime, settings.quietStartOffset)
        def e = resolveTime(settings.quietEndTimeType, settings.quietVolumeEndTime, settings.quietEndOffset)
        
        if (s && e) {
            def n = new Date()
            isQuietTime = (s < e) ? (n >= s && n <= e) : (n >= s || n <= e)
        }
        if (isQuietMode || isQuietTime) targetVol = settings.quietVolume ?: 20
    }

    [devices].flatten().findAll{it}.each { dev ->
        if (dev.hasCommand("setVolume")) dev.setVolume(targetVol as Integer)
    }
}

// --- MANUAL BYPASS ENGINE ---
def bypassHandler(evt) {
    if (!isSystemEnabled()) return
    if (evt.name == "pushed" && evt.value.toInteger() != (settings.bypassButtonNum ?: 1)) return
    
    def duration = settings.bypassDuration ?: 5
    state.bypassEndTime = now() + (duration * 60000)
    logContextEvent("System Bypass Activated for ${duration} minutes.")
    
    if (settings.bypassTTS) {
        applyTTSVolume(settings.bypassTTS, false)
        settings.bypassTTS.speak("Security bypass activated for ${duration} minutes.")
    }
    
    if (evt.name == "switch" && settings.bypassSwitchAutoOff) {
        runIn(2, "turnOffBypassSwitch")
    }
}

def turnOffBypassSwitch() {
    if (bypassSwitch) bypassSwitch.off()
}

def masterSwitchHandler(evt) {
    if (evt.value == "off") {
        logAction("MASTER SWITCH OFF: Entire security system disabled.")
        state.currentAlertLevel = "Normal"; state.activeAlerts = [:]
    } else logAction("MASTER SWITCH ON: Security system armed and monitoring.")
}

String getHumanReadableStatus() {
    if (!isSystemEnabled()) return "<span style='color:#dc3545;'><b>SYSTEM DISABLED</b></span>"
    if (state.currentAlertLevel == "Normal") return "<span style='color:green;'><b>All Clear.</b></span>"
    
    def levelHtml = state.currentAlertLevel == "Critical" ? "<span style='color:#d9534f;'><b>CRITICAL ALERT</b></span>" : "<span style='color:#f0ad4e;'><b>WARNING</b></span>"
    return "${levelHtml}: Active security events detected. See Active Alerts table below."
}

def appButtonHandler(btn) {
    if (btn == "clearAlertsBtn") { 
        state.activeAlerts = [:]
        state.currentAlertLevel = "Normal"
        logAction("User manually cleared alerts.") 
    } else if (btn.startsWith("testBtn_")) {
        def parts = btn.split("_")
        if (parts.size() >= 4) {
            def prefix = parts[1]
            def devId = parts[2]
            def zDevId = parts[3]
            
            def zDevs
            def fileNum
            
            if (prefix == "emergency") {
                zDevs = settings["emergencyZooz"]
                fileNum = settings["emergencyZoozFile_${zDevId}"]
            } else if (prefix == "arrival" || prefix == "departure") {
                zDevs = settings["${prefix}Zooz${devId}"]
                fileNum = settings["${prefix}ZoozFile${devId}_${zDevId}"]
            } else {
                zDevs = settings["${prefix}Zooz_${devId}"]
                fileNum = settings["${prefix}ZoozFile_${devId}_${zDevId}"]
            }
            
            def zDev = [zDevs].flatten().findAll{it}.find { it.id == zDevId }
            if (zDev && fileNum != null) {
                playZoozSound(zDev, fileNum)
                logAction("TEST BUTTON TRACE: Successfully fired manual command for file ${fileNum} on ${zDev.displayName}")
            } else {
                log.error "TEST BUTTON TRACE FAILED: Could not find target device or file number."
            }
        }
    }
}

// --- TODDLER / WANDER PREVENTION (CURFEW ZONES) ---

def curfewHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (state.bypassEndTime && now() < state.bypassEndTime) {
        if (settings.bypassDoors?.find { it.id == evt.device.id }) {
            logContextEvent("Ignored ${evt.device.displayName} (Bypass Active)")
            return
        }
    }

    if (evt.value == "open") {
        def conditionMet = isAdvancedConditionMet(
            enableCurfewRules1, curfewModes1, curfewStartTimeType1, curfewStartTime1, curfewStartOffset1, curfewEndTimeType1, curfewEndTime1, curfewEndOffset1,
            enableCurfewRules2, curfewModes2, curfewStartTimeType2, curfewStartTime2, curfewStartOffset2, curfewEndTimeType2, curfewEndTime2, curfewEndOffset2
        )
        
        if (conditionMet) {
            logContextEvent("CURFEW BREACH: ${evt.device.displayName} Opened")
            def msg = settings["curfewMsg_${evt.device.id}"] ?: "Emergency. ${evt.device.displayName} was opened during curfew hours."
            def tts = settings["curfewTTS_${evt.device.id}"]
            
            if (tts) { applyTTSVolume(tts, true); tts.speak(msg) }
            playGranularZooz("curfewZooz", evt.device)
            
            triggerAlert("Critical", "Curfew Zone Breached: ${evt.device.displayName} opened during restricted hours!", evt.device.id, evt.device.displayName)
        }
    }
}

// --- PROACTIVE LEFT OPEN REMINDERS ---

def leftOpenHandler(evt) {
    if (!isSystemEnabled()) return
    
    def id = evt.device.id
    if (evt.value == "open") {
        def delay = leftOpenDelay ?: 15
        state.leftOpenAlertSent[id] = false
        runIn(delay * 60, "executeLeftOpenAlert", [data: [devId: id], overwrite: false])
    } else {
        state.leftOpenAlertSent[id] = false
    }
}

def executeLeftOpenAlert(data) {
    def dev = leftOpenSensors?.find { it.id == data.devId }
    if (dev && dev.currentValue("contact") == "open" && !state.leftOpenAlertSent[dev.id]) {
        def conditionMet = isAdvancedConditionMet(
            enableLeftOpenRules1, leftOpenModes1, leftOpenStartTimeType1, leftOpenStartTime1, leftOpenStartOffset1, leftOpenEndTimeType1, leftOpenEndTime1, leftOpenEndOffset1,
            enableLeftOpenRules2, leftOpenModes2, leftOpenStartTimeType2, leftOpenStartTime2, leftOpenStartOffset2, leftOpenEndTimeType2, leftOpenEndTime2, leftOpenEndOffset2
        )
        
        if (conditionMet) {
            def isQuiet = quietModes && (quietModes as List).contains(location.mode)
            if (isQuiet && !settings.qmAllowLeftOpen) return // Blocked by Quiet Mode settings
            
            def msg = settings["leftOpenMsg_${dev.id}"] ?: "Just a reminder, the ${dev.displayName} has been left open."
            logContextEvent("REMINDER: ${dev.displayName} left open too long.")
            def tts = settings["leftOpenTTS_${dev.id}"]
            def push = settings["leftOpenPush_${dev.id}"]
             
            if (tts && (!isQuiet || settings.qmAllowTTS)) { applyTTSVolume(tts, false); tts.speak(msg) }
            if (push && (!isQuiet || settings.qmAllowPush)) push.deviceNotification(msg)
            if (!isQuiet || settings.qmAllowZooz) playGranularZooz("leftOpenZooz", dev)
            
            state.leftOpenAlertSent[dev.id] = true
        }
    }
}

// --- PERIMETER MODE WATCHDOG ---

def modeChangeHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (perimeterCheckModes && (perimeterCheckModes as List).contains(evt.value)) {
        def openSensors = []
        if (perimeterDoors) openSensors += perimeterDoors.findAll { it.currentValue("contact") == "open" }.collect { it.displayName }
        if (perimeterWindows) openSensors += perimeterWindows.findAll { it.currentValue("contact") == "open" }.collect { it.displayName }
        
        if (openSensors.size() > 0) {
            def msg = "Warning. The house is now in ${evt.value} mode, but the following are still open: ${openSensors.join(', ')}."
            logAction("Perimeter check failed on mode change: ${msg}")
            if (perimeterCheckPush) perimeterCheckPush.deviceNotification(msg)
            if (perimeterCheckTTS && emergencyTTS) { applyTTSVolume(emergencyTTS, false); emergencyTTS.speak(msg) }
        }
    }
}

// --- SYSTEM HEALTH WATCHDOG ---

def dailyHealthCheck() {
    if (!isSystemEnabled()) return
    def lowBatteryDevices = []
    def allSensors = (perimeterDoors ?: []) + (perimeterWindows ?: []) + (highRiskSensors ?: []) + (curfewDoors ?: []) + (outdoorMotion ?: []) + (indoorMotion ?: []) + (smokeDetectors ?: []) + (coDetectors ?: []) + (glassBreakSensors ?: [])
    allSensors = allSensors.unique { it.id }
    
    allSensors.each { dev ->
        if (dev.hasAttribute("battery")) {
            def battVal = dev.currentValue("battery")
            if (battVal != null && battVal.toInteger() < 15) lowBatteryDevices.add("${dev.displayName} (${battVal}%)")
        }
    }
    
    if (lowBatteryDevices.size() > 0 && pushHealth) {
        pushHealth.deviceNotification("System Health Alert: Low batteries detected on: ${lowBatteryDevices.join(', ')}")
        logContextEvent("System Health: Low battery warning sent.")
    }
}

// --- GRANULAR ZOOZ + TTS/PUSH AUDIO ROUTING WITH DEBOUNCE ---

def playDeviceCustomAlert(device, type, conditionMet) {
    logInfo("========================================")
    logInfo("X-RAY TRACE: Starting audio evaluation for ${device.displayName} (Category: ${type})")
    
    if (settings.enableCustomAnnouncements == false) {
        logInfo("❌ X-RAY KILL: Master Custom Announcements Switch in Section 8 is turned OFF.")
        return
    }
    
    if (!conditionMet) {
        logInfo("❌ X-RAY KILL: Condition not met. Current house mode/time does not match the active rules set for this door.")
        return
    }
    
    def isQuiet = quietModes && (quietModes as List).contains(location.mode)
    if (isQuiet) logInfo("⚠️ X-RAY NOTICE: The house is currently in a designated 'Quiet Mode'. Checking quiet hour permissions...")
    
    // Evaluate if this specific event category is permitted during Quiet Mode
    if (isQuiet) {
        def allowed = false
        if (type == "door" && settings.qmAllowDoors) allowed = true
        if (type == "outMotion" && settings.qmAllowOutMotion) allowed = true
        if (type == "inMotion" && settings.qmAllowInMotion) allowed = true
        if (!allowed) {
            logInfo("❌ X-RAY KILL: The house is in Quiet Mode and '${type}' events are NOT permitted to alert.")
            return 
        }
    }
    
    def mDebounce = motionDebounce != null ? motionDebounce : 5
    def dDebounce = doorDebounce != null ? doorDebounce : 30 // Now in seconds
    def cooldownMs = (type.contains("Motion")) ? (mDebounce * 60000) : (dDebounce * 1000)
    
    def lastPlayed = state."lastAlert_${device.id}" ?: 0
    if ((now() - lastPlayed) < cooldownMs) {
        logInfo("❌ X-RAY KILL: Debounce timer is active. Command ignored to prevent spam.")
        return 
    }
    
    def played = false
    def prefix = type
    
    def msg = settings["${prefix}Msg_${device.id}"]
    def tts = settings["${prefix}TTS_${device.id}"]
    def push = settings["${prefix}Push_${device.id}"]
    
    // Evaluate if output methods are permitted
    if (msg) {
        if (tts && (!isQuiet || settings.qmAllowTTS)) { applyTTSVolume(tts, false); tts.speak(msg); played = true }
        if (push && (!isQuiet || settings.qmAllowPush)) { push.deviceNotification(msg); played = true }
    }
    
    if (!isQuiet || settings.qmAllowZooz) {
        logInfo("✅ X-RAY PASS: Logic passed all restrictions. Attempting to fire Granular Zooz engine...")
        if (playGranularZooz("${prefix}Zooz", device)) played = true
    } else {
        logInfo("❌ X-RAY KILL: The house is in Quiet Mode. You allowed the event category, but 'Play Zooz Chimes' is disabled in Section 8.")
    }
    
    if (played) {
        state."lastAlert_${device.id}" = now()
        logInfo("✅ X-RAY TRACE COMPLETE: Audio sequence fired successfully.")
    } else {
        logInfo("❌ X-RAY TRACE FAILED: Reached the end of logic but the Zooz engine failed to play.")
    }
    logInfo("========================================")
}

def playGranularZooz(settingPrefix, sourceDevice) {
    def played = false
    def zDevs = settings["${settingPrefix}_${sourceDevice.id}"]
    
    if (!zDevs) {
        logInfo("❌ ZOOZ ENGINE KILL: No physical target speaker was configured in the app for ${sourceDevice.displayName}.")
        return false
    }

    [zDevs].flatten().findAll{it}.eachWithIndex { zDev, index ->
        if (index > 0) pauseExecution(1000)
        def fileNum = settings["${settingPrefix}File_${sourceDevice.id}_${zDev.id}"]
        if (fileNum != null) { 
            logInfo("🔊 ZOOZ ENGINE: Sending command to play file [${fileNum}] on [${zDev.displayName}]")
            if (playZoozSound(zDev, fileNum)) played = true
        } else {
            logInfo("❌ ZOOZ ENGINE KILL: Found the speaker (${zDev.displayName}) but no File Number was assigned in the app settings.")
        }
    }
    return played
}

// --- ROBUST ZOOZ PLAYBACK FIX ---
def playZoozSound(zDev, soundNum) {
    if (!zDev || soundNum == null) return false
    
    def played = false
    def isNumeric = soundNum.toString().isNumber()
    def trackNum = isNumeric ? soundNum.toString().toInteger() : null

    try {
        // Instead of using Hubitat's strict hasCommand(), we grab the raw list of supported commands
        def cmds = zDev.supportedCommands?.collect { it.name } ?: []

        if (cmds.contains("playSound") && trackNum != null) {
            zDev.playSound(trackNum)
            played = true
        } else if (cmds.contains("playTrack")) {
            zDev.playTrack(soundNum.toString())
            played = true
        } else if (cmds.contains("chime") && trackNum != null) {
            zDev.chime(trackNum)
            played = true
        } else {
            // Bruteforce fallback for custom drivers that hide their capabilities
            try {
                if (trackNum != null) { zDev.playSound(trackNum); played = true }
            } catch (e1) {
                try {
                    if (trackNum != null) { zDev.chime(trackNum); played = true }
                } catch (e2) {
                    log.error "ZOOZ ENGINE ERROR: ${zDev.displayName} does not support standard audio commands (playSound, playTrack, or chime)."
                }
            }
        }
    } catch (e) {
        log.error "Failed to play sound on Zooz/Aeotec device ${zDev.displayName}: ${e}"
    }
    return played
}

// --- SENSOR HANDLERS ---

def individualPresenceHandler(evt) {
    if (!isSystemEnabled()) return

    def devId = evt.device.id
    def isArrival = (evt.value == "present")
    def slot = null

    for (int i = 1; i <= 4; i++) {
        if (settings["enablePerson${i}"] && settings["personSensor${i}"]?.id == devId) {
             slot = i
            break
        }
    }

    if (!slot) return

    logContextEvent("${evt.device.displayName} ${isArrival ? 'arrived' : 'departed'}.")

    def modeSet = isArrival ? settings["arrivalModes${slot}"] : settings["departureModes${slot}"]
    def timeTypeStart = isArrival ? settings["arrStartTimeType${slot}"] : settings["depStartTimeType${slot}"]
    def timeStart = isArrival ? settings["arrStartTime${slot}"] : settings["depStartTime${slot}"]
    def timeOffStart = isArrival ? settings["arrStartOffset${slot}"] : settings["depStartOffset${slot}"]
    def timeTypeEnd = isArrival ? settings["arrEndTimeType${slot}"] : settings["depEndTimeType${slot}"]
    def timeEnd = isArrival ? settings["arrEndTime${slot}"] : settings["depEndTime${slot}"]
    def timeOffEnd = isArrival ? settings["arrEndOffset${slot}"] : settings["depEndOffset${slot}"]

    def conditionMet = isAdvancedConditionMet(
        true, modeSet,
        timeTypeStart, timeStart, timeOffStart,
        timeTypeEnd, timeEnd, timeOffEnd,
        false, null, null, null, null, null, null, null
    )

    if (conditionMet) {
        def isQuiet = quietModes && (quietModes as List).contains(location.mode)
        if (isQuiet && !settings.qmAllowPresence) return // Blocked by Quiet Mode settings
        
        def msg = isArrival ? settings["arrivalMsg${slot}"] : settings["departureMsg${slot}"]
        def tts = isArrival ? settings["arrivalTTS${slot}"] : settings["departureTTS${slot}"]
        def push = isArrival ? settings["arrivalPush${slot}"] : settings["departurePush${slot}"]
        def zooz = isArrival ? settings["arrivalZooz${slot}"] : settings["departureZooz${slot}"]

        if (msg) {
            if (tts && (!isQuiet || settings.qmAllowTTS)) {
                applyTTSVolume(tts, false)
                tts.speak(msg)
            }
            if (push && (!isQuiet || settings.qmAllowPush)) push.deviceNotification(msg)
        }

        if (!isQuiet || settings.qmAllowZooz) {
            [zooz].flatten().findAll{it}.eachWithIndex { zDev, index ->
                if (index > 0) pauseExecution(1000)
                def fileNum = isArrival ? settings["arrivalZoozFile${slot}_${zDev.id}"] : settings["departureZoozFile${slot}_${zDev.id}"]
                if (fileNum) playZoozSound(zDev, fileNum)
            }
        }
    }
}

def highRiskHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (state.bypassEndTime && now() < state.bypassEndTime) {
        if (settings.bypassDoors?.find { it.id == evt.device.id }) {
            logContextEvent("Ignored ${evt.device.displayName} (Bypass Active)")
            return
        }
    }

    if (evt.value == "open") {
        logContextEvent("HIGH RISK: ${evt.device.displayName} Opened")
        
        def conditionMet = isAdvancedConditionMet(
            enableHighRiskRules1, highRiskModes1, highRiskStartTimeType1, highRiskStartTime1, highRiskStartOffset1, highRiskEndTimeType1, highRiskEndTime1, highRiskEndOffset1,
            enableHighRiskRules2, highRiskModes2, highRiskStartTimeType2, highRiskStartTime2, highRiskStartOffset2, highRiskEndTimeType2, highRiskEndTime2, highRiskEndOffset2
        )
        
        if (conditionMet) {
            def msg = settings["highRiskMsg_${evt.device.id}"] ?: "Warning. ${evt.device.displayName} has been opened."
            def tts = settings["highRiskTTS_${evt.device.id}"]
            
            if (tts) { applyTTSVolume(tts, true); tts.speak(msg) }
            playGranularZooz("highRiskZooz", evt.device)
 
            triggerAlert("Warning", "High-Risk Boundary Breached: ${evt.device.displayName}", evt.device.id, evt.device.displayName)
        }
    }
}

// --- DEDICATED PANIC LOOP ENGINE ---

def panicHandler(evt) {
    if (!isSystemEnabled()) return
    def devName = evt.device.displayName
    logContextEvent("PANIC INITIATED via ${devName}")
    
    // Set the state logic so the engine knows a panic loop is actively running
    state.panicActive = true
    state.panicSource = devName
    
    executeEmergencyProtocol("PANIC BUTTON ACTIVATED: ${devName}!", evt.device.id, devName)
    panicLoop() // Start the recurring loop
}

def panicLoop() {
    if (state.panicActive) {
        def msg = "🚨 ONGOING PANIC EMERGENCY 🚨 Triggered by ${state.panicSource}. Please send help. Turn on the Panic Reset Switch in Hubitat to stop these alerts."
        if (pushCritical) pushCritical.deviceNotification(msg)
        
        // Recursively call this method every 300 seconds (5 minutes) until the reset switch is flipped
        runIn(300, "panicLoop")
    }
}

def panicResetHandler(evt) {
    if (evt.value == "on") {
        state.panicActive = false
        logContextEvent("Panic Alarm Loop Reset by User.")
        if (pushCritical) pushCritical.deviceNotification("Panic Alarm has been deactivated and reset.")
        unschedule("panicLoop") // Stop the 5-minute timer
        
        // Turn the physical switch back off automatically so it's ready for next time
        runIn(2, "turnOffPanicResetSwitch")
    }
}

def turnOffPanicResetSwitch() {
    if (panicResetSwitch) panicResetSwitch.off()
}

def lifeSafetyHandler(evt) {
    if (!isSystemEnabled()) return
    if (evt.value == "detected") {
        logContextEvent("LIFE SAFETY EMERGENCY: ${evt.name.toUpperCase()} at ${evt.device.displayName}")
        executeEmergencyProtocol("Life Safety Alarm: ${evt.name.toUpperCase()} detected by ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
    }
}

def executeEmergencyProtocol(alertReason, deviceId = "system", deviceName = "System Engine") {
    if (emergencySwitches) emergencySwitches.on()
    if (emergencyColoredLights) setEmergencyColor(emergencyColoredLights, emergencyLightColor ?: "Red")
    if (emergencyLocks) emergencyLocks.unlock()
    
    if (emergencyTTS) applyTTSVolume(emergencyTTS, true)
    
    def msg = emergencyTTSMessage ?: "Emergency. Evacuation protocol initiated. Please exit the house immediately."
    if (emergencyTTS) emergencyTTS.speak(msg)
    
    [emergencyZooz].flatten().findAll{it}.eachWithIndex { zDev, index ->
        if (index > 0) pauseExecution(1000)
        def fileNum = settings["emergencyZoozFile_${zDev.id}"]
        if (fileNum) playZoozSound(zDev, fileNum)
    }
    
    // Always trigger push for Fire/Evacuation
    triggerAlert("Critical", alertReason, deviceId, deviceName, true)
}

def executeIntrusionProtocol(alertReason, deviceId = "system", deviceName = "System Engine") {
    // Optionally turn on lights to scare off the intruder
    if (emergencySwitches) emergencySwitches.on()
    if (emergencyColoredLights) setEmergencyColor(emergencyColoredLights, emergencyLightColor ?: "Red")
    
    // NOTE: We specifically DO NOT unlock the emergencyLocks here.
    
    if (settings.armStayTTS != false && emergencyTTS) {
        applyTTSVolume(emergencyTTS, true)
        emergencyTTS.speak("Intruder Alert. ${alertReason}")
    }
    
    if (settings.armStaySirens != false) {
        // Uses the global emergency sirens defined in Section 7
        [emergencyZooz].flatten().findAll{it}.eachWithIndex { zDev, index ->
            if (index > 0) pauseExecution(1000)
            def fileNum = settings["emergencyZoozFile_${zDev.id}"]
            if (fileNum) playZoozSound(zDev, fileNum)
        }
    }
    
    // triggerAlert will check the sendPush flag passed as the 5th parameter
    triggerAlert("Critical", alertReason, deviceId, deviceName, settings.armStayPush != false)
}

def setEmergencyColor(devices, colorName) {
    def hueColor = 0; def saturation = 100
    switch(colorName) { case "White": hueColor=0; saturation=0; break; case "Red": hueColor=0; break; case "Yellow": hueColor=16; break; case "Green": hueColor=33; break; case "Blue": hueColor=66; break; }
    devices.setColor([hue: hueColor, saturation: saturation, level: 100])
}

def contactHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (state.bypassEndTime && now() < state.bypassEndTime) {
        if (settings.bypassDoors?.find { it.id == evt.device.id }) {
            logContextEvent("Ignored ${evt.device.displayName} (Bypass Active)")
            return
        }
    }

    if (evt.value == "open") {
        state.lastDoorOpenTime = now()
        state."lastDoorOpenTime_${evt.device.id}" = now() // Track specific door timestamp
        
        def isWindow = perimeterWindows?.find { it.id == evt.device.id } != null
        def isDoor = perimeterDoors?.find { it.id == evt.device.id } != null
        def isArmStayActive = (armStaySwitch && armStaySwitch.currentValue("switch") == "on")
  
        def isCurfewDoor = curfewDoors?.find { it.id == evt.device.id } != null
        def curfewMet = isCurfewDoor && isAdvancedConditionMet(
             enableCurfewRules1, curfewModes1, curfewStartTimeType1, curfewStartTime1, curfewStartOffset1, curfewEndTimeType1, curfewEndTime1, curfewEndOffset1,
             enableCurfewRules2, curfewModes2, curfewStartTimeType2, curfewStartTime2, curfewStartOffset2, curfewEndTimeType2, curfewEndTime2, curfewEndOffset2
        )
        if (curfewMet) return
        
        logContextEvent("${evt.device.displayName} Opened")
        
        // --- ARM STAY LOGIC OVERRIDE ---
        if (isArmStayActive && (isDoor || isWindow)) {
             logContextEvent("ARM STAY VIOLATION: ${evt.device.displayName} Opened!")
             
             // This function now reads your exact toggle choices and fires what you told it to fire
             executeIntrusionProtocol("Arm Stay Perimeter Breach: ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
             
             // If you toggled "Play Standard Door Chime", we send the command to that specific door's speaker
             if (settings.armStayStandardChime && isDoor) {
                 playDeviceCustomAlert(evt.device, "door", true)
             }
             return // Stop processing standard door logic since an Arm Stay breach was handled
        }
        
        if (isDoor) {
            def conditionMet = isAdvancedConditionMet(
                enableDoorRules1, doorModes1, doorStartTimeType1, doorStartTime1, doorStartOffset1, doorEndTimeType1, doorEndTime1, doorEndOffset1,
                enableDoorRules2, doorModes2, doorStartTimeType2, doorStartTime2, doorStartOffset2, doorEndTimeType2, doorEndTime2, doorEndOffset2
            )
            playDeviceCustomAlert(evt.device, "door", conditionMet)
        }
        
         if (state.currentAlertLevel == "Warning" && state.lastGlassBreakTime > (now() - 60000)) triggerAlert("Critical", "Glass break followed by perimeter breach at ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
         else if (isWindow) triggerAlert("Warning", "${evt.device.displayName} was opened.", evt.device.id, evt.device.displayName)
    } else {
        logContextEvent("${evt.device.displayName} Closed")
    }
}

// MOTION INACTIVE TRACKER FOR DURATION
def motionInactiveHandler(evt) {
    def start = state."motionStart_${evt.device.id}"
    if (start) {
        def durationSec = Math.round((now() - start) / 1000)
        logContextEvent("${evt.device.displayName} motion ended (Duration: ${durationSec}s)")
        updateAlertDuration(evt.device.id, durationSec)
        state.remove("motionStart_${evt.device.id}")
    }
}

def outdoorMotionHandler(evt) {
    if (!isSystemEnabled()) return
    state."motionStart_${evt.device.id}" = now()
    
    def conditionMet = isAdvancedConditionMet(
        enableOutMotionRules1, outMotionModes1, outStartTimeType1, outMotionStartTime1, outStartOffset1, outEndTimeType1, outMotionEndTime1, outEndOffset1,
        enableOutMotionRules2, outMotionModes2, outStartTimeType2, outMotionStartTime2, outStartOffset2, outEndTimeType2, outMotionEndTime2, outEndOffset2,
        overcastSwitch
    )
    
    // --- ARM STAY OUTDOOR MOTION OVERRIDE ---
    def isArmStayActive = (armStaySwitch && armStaySwitch.currentValue("switch") == "on")
    if (isArmStayActive && settings.armStayOutMotionEnable) {
        def forceAlert = true
        if (settings.armStayOutLuxSensor) {
            def currentLux = settings.armStayOutLuxSensor.currentValue("illuminance") ?: 0
            def threshold = settings.armStayOutLuxThreshold != null ? settings.armStayOutLuxThreshold : 1000
            if (currentLux >= threshold) {
                forceAlert = false // It's too bright outside to force the override
            }
        }
        if (forceAlert) {
            conditionMet = true
            logContextEvent("Arm Stay Override: Outdoor Motion forced active.")
        }
    }
    
    if (conditionMet) {
        def graceMs = (outboundGracePeriod ?: 30) * 1000
        def residentOutbound = false
        
        // Check linked doors if the feature is enabled
        if (settings.enableLinkedGracePeriod && settings["linkedDoors_${evt.device.id}"]) {
            def linkedDoors = settings["linkedDoors_${evt.device.id}"]
            [linkedDoors].flatten().findAll{it}.each { lDoor ->
                def doorOpenTime = state."lastDoorOpenTime_${lDoor.id}" ?: 0
                def isCurrentlyOpen = lDoor.currentValue("contact") == "open"
                
                // Outbound if the door is currently open OR recently closed within the grace period
                if (isCurrentlyOpen || ((now() - doorOpenTime) <= graceMs && doorOpenTime != 0)) {
                    residentOutbound = true
                }
            }
        } else {
            // Fall back to original global logic
            def globalDoorTime = state.lastDoorOpenTime ?: 0
            def anyDoorOpen = settings.perimeterDoors?.any { it.currentValue("contact") == "open" }
            
            if (anyDoorOpen || ((now() - globalDoorTime) <= graceMs && globalDoorTime != 0)) {
                residentOutbound = true
            }
        }

        if (residentOutbound) {
            logContextEvent("Ignored ${evt.device.displayName} (Resident outbound logic)")
        } else {
            logContextEvent("Unknown motion at ${evt.device.displayName}")
            
            // ANNOUNCEMENT TRIGGER MOVED HERE: 
            // It will only execute if someone DID NOT just walk out the door.
            playDeviceCustomAlert(evt.device, "outMotion", true)
            
            def reason = settings.enableLinkedGracePeriod ? "linked door opening" : "door opening"
            triggerAlert("Warning", "Motion detected at ${evt.device.displayName} without ${reason}.", evt.device.id, evt.device.displayName)
        }
    }
}

def indoorMotionHandler(evt) {
    if (!isSystemEnabled()) return
    state."motionStart_${evt.device.id}" = now()
    
    // Suppress interior security motion alarms if the person is dynamically tracked through the Night Transit Engine
    if (state.transitPath && state.transitPath != "unknown") {
        def isTracked = false
        for (int i = 1; i <= 3; i++) {
            if (settings["originRoom${i}"]?.id == evt.device.id && settings["gnSwitch${i}"]?.currentValue("switch") == "on") isTracked = true
        }
        if (transitZone?.id == evt.device.id || destinationZone?.id == evt.device.id) isTracked = true
        
        if (isTracked) {
            logContextEvent("Ignored ${evt.device.displayName} (Resident night transit active).")
            return
        }
    }
    
    def conditionMet = isAdvancedConditionMet(
        enableInMotionRules1, inMotionModes1, inStartTimeType1, inMotionStartTime1, inStartOffset1, inEndTimeType1, inMotionEndTime1, inEndOffset1,
        enableInMotionRules2, inMotionModes2, inStartTimeType2, inMotionStartTime2, inStartOffset2, inEndTimeType2, inMotionEndTime2, inEndOffset2
    )
   
    if (conditionMet) {
        logContextEvent("Indoor motion at ${evt.device.displayName}")
        playDeviceCustomAlert(evt.device, "inMotion", true)
        
        if (state.currentAlertLevel == "Warning" || state.lastGlassBreakTime > (now() - 120000)) {
            triggerAlert("Critical", "Intruder tracked to ${evt.device.displayName}!", evt.device.id, evt.device.displayName)
            playGranularZooz("inMotionZooz", evt.device) // Force play to bypass debounce/quiet modes on a real warning
        }
    }
}

def glassBreakHandler(evt) {
    if (!isSystemEnabled()) return
    state.lastGlassBreakTime = now()
    logContextEvent("GLASS BREAK detected at ${evt.device.displayName}")
    triggerAlert("Warning", "Possible glass break detected at ${evt.device.displayName}.", evt.device.id, evt.device.displayName)
}

def triggerAlert(level, message, deviceId = "system", deviceName = "System Engine", sendPush = true) {
    if (state.currentAlertLevel == "Critical" && level == "Warning") return 
   
    state.currentAlertLevel = level
    
    // Core structural change from List to Map for deduplication
    def alertKey = "${deviceId}_${level}"
    def alerts = state.activeAlerts ?: [:]
    
    if (alerts[alertKey]) {
        alerts[alertKey].count = (alerts[alertKey].count ?: 1) + 1
        alerts[alertKey].lastTime = new Date().format("MM/dd hh:mm:ss a", location.timeZone)
    } else {
        alerts[alertKey] = [
            time: new Date().format("MM/dd hh:mm:ss a", location.timeZone),
            lastTime: new Date().format("MM/dd hh:mm:ss a", location.timeZone),
            device: deviceName,
            msg: message,
            level: level,
            count: 1,
            duration: "Ongoing"
        ]
    }
    state.activeAlerts = alerts
    
    logAction("ALERT TRIGGERED [${level}]: ${message}")
    
    if (level == "Critical") {
        if (sendPush && pushCritical) pushCritical.deviceNotification("Security CRITICAL: ${message}")
    } else if (level == "Warning") {
        def n = new Date()
        def s = resolveTime(settings.warnStartTimeType, settings.warningStartTime, settings.warnStartOffset)
        def e = resolveTime(settings.warnEndTimeType, settings.warningEndTime, settings.warnEndOffset)
        
        def timeAllowed = (!s || !e) || (s < e ? (n >= s && n <= e) : (n >= s || n <= e))
        if (sendPush && pushWarnings && (!warningModes || (warningModes as List).contains(location.mode)) && timeAllowed) pushWarnings.deviceNotification("Security Warning: ${message}")
    }
    
    // Write Alert to Child Device
    updateChildDevice("ALERT [${level}]: ${message}")
}

def updateAlertDuration(deviceId, durationSec) {
    def alerts = state.activeAlerts ?: [:]
    alerts.each { key, alert ->
        if (key.startsWith("${deviceId}_") && alert.duration == "Ongoing") {
            def mins = Math.floor(durationSec / 60).toInteger()
            def secs = durationSec % 60
            alert.duration = mins > 0 ? "${mins}m ${secs}s" : "${secs}s"
        }
    }
    state.activeAlerts = alerts
}

def logContextEvent(msg) {
    def h = state.eventHistory ?: [];
    h.add(0, "[${new Date().format("MM/dd hh:mm:ss a", location.timeZone)}] ${msg}")
    if (h.size() > 25) h = h[0..24];
    state.eventHistory = h
    
    // Write Event to Child Device
    updateChildDevice(msg)
}

def logAction(msg) { log.info "${app.label}: ${msg}" }
def logInfo(msg) { log.info "${app.label}: ${msg}" }

// --- NIGHT TRACKING STATE MACHINE ---

def goodNightHandler(evt) {
    if (evt.value == "on") {
        logContextEvent("${evt.device.displayName} turned ON. Local transit tracking armed.")
        state.transitPath = "origin" 
        state.transitLastTime = now()
    } else {
        logContextEvent("${evt.device.displayName} turned OFF.")
    }
}

def nightMotionTracker(evt) {
    def devId = evt.device.id
    def currentLoc = ""

    // 1. Identify if motion was in a valid, armed bedroom
    for (int i = 1; i <= 3; i++) {
        if (settings["originRoom${i}"]?.id == devId) {
            if (settings["gnSwitch${i}"]?.currentValue("switch") == "on") {
                currentLoc = "origin"
                break
            } else {
                return // Motion in a bedroom where the switch is OFF. Ignore tracking.
            }
        }
    }

    // 2. Identify if motion was in transit/destination
    if (!currentLoc) {
        if (devId == transitZone?.id) currentLoc = "transit"
        else if (devId == destinationZone?.id) currentLoc = "destination"
    }

    if (!currentLoc) return

    def lastLoc = state.transitPath ?: "unknown"
    def timeoutMs = (settings.transitTimeout ?: 60) * 1000
    def timeSinceLast = now() - (state.transitLastTime ?: now())

    if (timeSinceLast > timeoutMs) {
        lastLoc = "unknown" 
    }

    // 3. Evaluate Direction
    if (currentLoc == "origin") {
        logContextEvent("Night Tracker: Movement detected in Armed Bedroom.")
        state.transitPath = "origin"
        
    } else if (lastLoc == "origin" && currentLoc == "transit") {
        logContextEvent("Night Tracker: Resident entered Hallway from Bedroom.")
        state.transitPath = "transit"
        
    } else if (lastLoc == "transit" && currentLoc == "destination") {
        logContextEvent("Night Tracker: Resident entered Bathroom.")
        state.transitPath = "destination"
        
    } else if (lastLoc == "destination" && currentLoc == "transit") {
        logContextEvent("Night Tracker: Resident entered Hallway from Bathroom.")
        state.transitPath = "returning"
        
    } else if (lastLoc == "returning" && currentLoc == "origin") {
        logContextEvent("Night Tracker: Resident returned to Armed Bedroom.")
        state.transitPath = "origin"
    }

    // Stamp the clock for the next sensor trip
    state.transitLastTime = now()
}
