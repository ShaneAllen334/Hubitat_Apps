/**
 * Advanced House Maintenance
 *
 * Author: ShaneAllen
 *
 * Version 1.5
 */

definition(
    name: "Advanced House Maintenance",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Commercial-grade tracking for essential household maintenance items. Includes predictive triggers, budget forecasting, custom intervals, and automated countdowns.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    oauth: true
)

preferences {
    page(name: "mainPage")
}

mappings {
    path("/dashboard") { action: [GET: "serveDashboard"] }
    path("/dashboard/complete/:id") { action: [GET: "webCompleteTask"] }
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced House Maintenance</b>", install: true, uninstall: true) {
        
        section("<b>Web Dashboard Links</b>", hideable: true, hidden: false) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Family Dashboard:</b> Share these links with household members. They open a clean, dark-mode webpage showing tracked items that they can check off securely from their phone. Items are sorted into category folders.</div>"
            
            if (!state.accessToken) {
                try {
                    createAccessToken()
                } catch (e) {
                    paragraph "<span style='color:red; font-weight:bold;'>⚠️ OAuth is not enabled!</span><br>Please go to your Hubitat/SmartThings IDE, open this App's code, click the 'OAuth' button, enable it, and update. Then return to this page."
                }
            }

            if (state.accessToken) {
                def localUri = "${getFullLocalApiServerUrl()}/dashboard?access_token=${state.accessToken}"
                def cloudUri = "${getApiServerUrl()}/${hubUID}/apps/${app.id}/dashboard?access_token=${state.accessToken}"
                
                paragraph "<b>🏠 Local Network Link (Fastest):</b><br><a href='${localUri}' target='_blank' style='font-size:12px; word-break: break-all;'>${localUri}</a>"
                paragraph "<b>☁️ Cloud Link (Use Anywhere):</b><br><a href='${cloudUri}' target='_blank' style='font-size:12px; word-break: break-all;'>${cloudUri}</a>"
            }
        }

        section("<b>Live Maintenance Dashboard</b>", hideable: true, hidden: true) {
            input "btnRefresh", "button", title: "🔄 Refresh Data"
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time overview of your enabled maintenance tasks broken down by category. You can complete or snooze tasks to the upcoming weekend.</div>"
            
            def statusExplanation = getHumanReadableStatus()
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff; margin-bottom: 20px;'><b>System Status:</b> ${statusExplanation}</div>"

            def tempData = state.taskData ?: [:]
            def stateChanged = false
            def enabledTasks = []
            def totalActive = 0
            
            getTaskList().each { t ->
                if (settings["enable_${t.id}"]) {
                    totalActive++
                    if (!tempData[t.id]) {
                        tempData[t.id] = [lastCompleted: now(), snoozedUntil: 0]
                        stateChanged = true
                    }
                    
                    def tData = tempData[t.id]
                    def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                    def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                    def remainingDays = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()
                    def isSnoozed = (tData.snoozedUntil != null && tData.snoozedUntil > now())
                    
                    enabledTasks << [
                        id: t.id, name: t.name, category: t.category, 
                        days: remainingDays, last: tData.lastCompleted, 
                        interval: interval, snoozed: isSnoozed
                    ]
                }
            }
            
            if (stateChanged) state.taskData = tempData
            
            if (totalActive > 0) {
                def warningThreshold = upcomingDays != null ? upcomingDays : 7
                def groupedTasks = enabledTasks.groupBy { it.category }
                
                groupedTasks.each { catName, tasks ->
                    tasks.sort { it.days } 
                    
                    paragraph "<h3 style='margin-top: 15px; margin-bottom: 5px; color: #333;'>${catName}</h3>"
                    
                    def dashHTML = """
                    <style>
                        .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 15px; }
                        .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                        .dash-table th { background-color: #343a40; color: white; }
                        .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 40%; }
                    </style>
                    <table class="dash-table">
                        <thead><tr><th>Maintenance Task</th><th>Last Completed</th><th>Interval</th><th>Current Status</th></tr></thead>
                        <tbody>
                    """
                    
                    tasks.each { t ->
                        def statusHtml = ""
                        if (t.snoozed) {
                            statusHtml = "<span style='color:purple; font-weight:bold;'>Snoozed to Weekend</span>"
                        } else if (t.days < 0) {
                            statusHtml = "<span style='color:red; font-weight:bold;'>OVERDUE (${Math.abs(t.days)} days ago)</span>"
                        } else if (t.days == 0) {
                            statusHtml = "<span style='color:red; font-weight:bold;'>DUE TODAY</span>"
                        } else if (t.days <= warningThreshold) {
                            statusHtml = "<span style='color:orange; font-weight:bold;'>Due Soon (${t.days} days)</span>"
                        } else {
                            statusHtml = "<span style='color:green;'>Good (${t.days} days left)</span>"
                        }
                        
                        def lastStr = new Date(t.last).format("MM/dd/yyyy", location.timeZone)
                        dashHTML += "<tr><td class='dash-hl'>${t.name}</td><td>${lastStr}</td><td>Every ${t.interval} Days</td><td>${statusHtml}</td></tr>"
                    }
                    dashHTML += "</tbody></table>"
                    paragraph dashHTML
                    
                    paragraph "<div style='font-size:12px; color:#666; margin-bottom:5px;'><i>Quick Actions for ${catName}:</i></div>"
                    tasks.each { t ->
                        if (t.days <= warningThreshold && !t.snoozed) {
                            input "btnDashComplete_${t.id}", "button", title: "✅ Complete: ${t.name}", width: 6
                            input "btnSnooze_${t.id}", "button", title: "💤 Snooze to Saturday", width: 6
                        } else {
                            input "btnDashComplete_${t.id}", "button", title: "✅ Complete: ${t.name}", width: 12
                        }
                    }
                    paragraph "<div style='clear: both;'></div><hr style='margin-top:20px; margin-bottom:10px; border-top: 1px dashed #ccc;'>"
                }
            } else {
                paragraph "<i>Please enable tasks in the configuration sections below to populate the dashboard.</i>"
            }
        }
        
        section("<b>90-Day Consumables Forecast</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><i>This view aggregates tasks requiring physical parts over the next 90 days. This allows you to group hardware store purchases to maximize credit card rewards for end-of-year holiday planning.</i></div>"
            
            def forecastTasks = []
            def totalCost = 0.0
            def tempData = state.taskData ?: [:]
            
            getTaskList().each { t ->
                if (settings["enable_${t.id}"] && t.consumable && tempData[t.id]) {
                    def tData = tempData[t.id]
                    def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                    def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                    def remainingDays = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()
                    
                    if (remainingDays <= 90) {
                        def cost = t.estCost ?: 0.0
                        totalCost += cost
                        forecastTasks << [name: t.name, days: remainingDays, cost: cost]
                    }
                }
            }
            
            if (forecastTasks.size() > 0) {
                forecastTasks.sort { it.days }
                def fHtml = "<table style='width:100%; border-collapse:collapse; font-size:14px; margin-bottom:15px;'>"
                fHtml += "<tr style='background-color:#17a2b8; color:white;'><th style='padding:6px;'>Upcoming Consumable</th><th style='padding:6px; text-align:center;'>Due In</th><th style='padding:6px; text-align:right;'>Est. Cost</th></tr>"
                forecastTasks.each { ft ->
                    def costStr = String.format("\$%.2f", ft.cost)
                    def dayStr = ft.days <= 0 ? "Now" : "${ft.days} Days"
                    fHtml += "<tr><td style='padding:6px; border-bottom:1px solid #ccc;'>${ft.name}</td><td style='padding:6px; border-bottom:1px solid #ccc; text-align:center;'>${dayStr}</td><td style='padding:6px; border-bottom:1px solid #ccc; text-align:right;'>${costStr}</td></tr>"
                }
                def totalStr = String.format("\$%.2f", totalCost)
                fHtml += "<tr><td colspan='2' style='padding:6px; text-align:right; font-weight:bold;'>90-Day Projected Spend:</td><td style='padding:6px; text-align:right; font-weight:bold; color:green;'>${totalStr}</td></tr>"
                fHtml += "</table>"
                paragraph fHtml
            } else {
                paragraph "<span style='color:green; font-weight:bold;'>No consumable purchases projected for the next 90 days.</span>"
            }
        }

        section("<b>Dynamic BMS Triggers & Suspension</b>", hideable: true, hidden: true) {
            paragraph "<div style='font-size:13px; color:#555;'><b>Occupancy-Aware Timers:</b> Select modes where the house is empty. The app will automatically pause wear-and-tear timers for appliances while you are gone.</div>"
            input "pauseModes", "mode", title: "Vacation / Away Modes (Pauses Timers)", multiple: true, required: false
            
            paragraph "<div style='font-size:13px; color:#555; margin-top:10px;'><b>Environmental Sensors:</b> If heavy water is detected, critical drainage tasks (Sump Pump, Gutters, Window Wells) are immediately flagged as Due Today.</div>"
            input "rainSensors", "capability.waterSensor", title: "Heavy Rain / Sump Pit Sensors", multiple: true, required: false
        }

        section("<b>App Control & Global Settings</b>", hideable: true, hidden: true) {
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
            paragraph "<div style='font-size:13px; color:#555; margin-top:10px;'><b>Task Staggering (Anti-Overload):</b> If you just set up the app and enabled many tasks, press this button. It will randomly scatter the due dates across the year so you don't get hit with 30 tasks on the exact same day.</div>"
            input "btnStaggerTasks", "button", title: "🗓️ Stagger Task Due Dates"

            paragraph "<div style='font-size:13px; color:#555; margin-top:10px;'><b>Voice Butler Integration:</b> Press the button below to generate the device that securely passes overdue task counts to the Voice Butler.</div>"
            input "btnCreateDevice", "button", title: "➕ Create 'Advanced House Maintenance Device'"
            
            if (getChildDevice("AHM_${app.id}")) {
                paragraph "<span style='color:green; font-weight:bold;'>✅ Integration Device Created: 'Advanced House Maintenance Device'</span>"
            }
            
            paragraph "<hr>"
            input "notifyDevices", "capability.notification", title: "Select Notification Devices", required: false, multiple: true
            input "notifyModes", "mode", title: "Allowed Modes for Notifications", multiple: true, required: false
            input "checkTime", "time", title: "Daily Evaluation Time", defaultValue: "09:00", required: true
            input "upcomingDays", "number", title: "Days prior to trigger 'Upcoming' notification", defaultValue: 7, required: true
            input "remindOverdue", "bool", title: "Send Daily Reminders for Overdue tasks", defaultValue: true, required: true
        }

        section("<b>Task Configuration Directory</b>", hideable: true, hidden: false) {
            paragraph "<div style='font-size:13px; color:#555;'>Click on a category below to expand and select which maintenance tasks you want to track.</div>"
        }

        def categories = getTaskList().groupBy { it.category }
        categories.each { catName, tasks ->
            section("<b>⚙️ ${catName}</b>", hideable: true, hidden: true) {
                tasks.each { t ->
                    input "enable_${t.id}", "bool", title: "<b>Track: ${t.name}</b>", submitOnChange: true
                    if (settings["enable_${t.id}"]) {
                        def costNotice = t.consumable ? "<br><span style='color:green;'><i>* Tracks against 90-Day Budget (Est. \$${t.estCost})</i></span>" : ""
                        paragraph "<div style='font-size:12px; color:#444; background-color:#f8f9fa; padding:8px; border-radius:4px; border-left:4px solid #17a2b8; margin-bottom: 5px;'><b>Procedure:</b> ${t.description}${costNotice}</div>"
                        input "interval_${t.id}", "number", title: "Interval (Days) [Default: ${t.defaultDays}]", defaultValue: t.defaultDays
                        paragraph "<hr>"
                    }
                }
            }
        }
        
        section("<b>Recent Action History</b>", hideable: true, hidden: true) {
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "resetHistory", "button", title: "Clear Action History"
        }
    }
}

// ==============================================================================
// WEB DASHBOARD ENDPOINTS
// ==============================================================================

def serveDashboard() {
    ensureStateMaps()

    def allPortalTasks = []
    def totalActive = 0
    def warningThreshold = upcomingDays != null ? upcomingDays : 7

    getTaskList().each { t ->
        if (settings["enable_${t.id}"]) {
            totalActive++
            def tData = state.taskData[t.id]
            if (tData) {
                def isSnoozed = (tData.snoozedUntil != null && tData.snoozedUntil > now())
                def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                def remainingDays = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()

                allPortalTasks << [
                    id: t.id, name: t.name, desc: t.description, 
                    category: t.category, days: remainingDays, 
                    interval: interval, last: tData.lastCompleted, 
                    snoozed: isSnoozed
                ]
            }
        }
    }

    def overdueCount = allPortalTasks.count { it.days < 0 && !it.snoozed }
    def upcomingCount = allPortalTasks.count { it.days >= 0 && it.days <= warningThreshold && !it.snoozed }
    def snoozedCount = allPortalTasks.count { it.snoozed }

    def css = "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:20px;background:#0d0d0d;color:#e0e0e0}.container{max-width:800px;margin:0 auto;background:#151515;padding:25px;border-radius:12px;box-sizing:border-box}a.main-btn{background:#1f618d;color:#fff;border:none;padding:14px 20px;border-radius:8px;display:block;text-align:center;text-decoration:none;font-weight:600;margin-bottom:30px}a.main-btn:hover{background:#1a5276}.summary-box{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:25px}.summary-card{flex:1;min-width:100px;box-sizing:border-box;background:#1e1e1e;padding:15px;border-radius:8px;text-align:center;border-bottom:3px solid #333}.summary-card b{display:block;font-size:24px;color:#fff;margin-bottom:5px}.summary-card span{font-size:12px;color:#aaa;text-transform:uppercase}details{margin-bottom:15px;background:#1c1c1c;border-radius:6px;}summary{padding:12px 15px;border-radius:6px;border-left:4px solid #3498db;cursor:pointer;color:#fff;font-weight:bold;font-size:16px}summary:hover{background:#252525}.cat-count{float:right;font-size:12px;color:#888;margin-top:3px}.folder-content{padding:15px; background:#181818; border-bottom-left-radius:6px; border-bottom-right-radius:6px;}.dev-card{background:#222;padding:15px;border-radius:8px;margin-bottom:12px;border-left:4px solid #333;transition:all 0.3s ease}.status-Red{border-left-color:#e74c3c;background:linear-gradient(90deg,rgba(231,76,60,.1) 0%,#222 30%)}.status-Purple{border-left-color:#9b59b6;background:linear-gradient(90deg,rgba(155,89,182,.1) 0%,#222 30%)}.status-Yellow{border-left-color:#f1c40f;background:linear-gradient(90deg,rgba(241,196,15,.1) 0%,#222 30%)}.status-Green{border-left-color:#27ae60}.dot{height:12px;width:12px;border-radius:50%;display:inline-block;margin-right:12px;flex-shrink:0}.dot-Red{background:#e74c3c;box-shadow:0 0 6px #e74c3c}.dot-Purple{background:#9b59b6;box-shadow:0 0 6px #9b59b6}.dot-Yellow{background:#f1c40f;box-shadow:0 0 6px #f1c40f}.dot-Green{background:#27ae60}.dev-info{flex-grow:1;min-width:0}.dev-name{font-size:15px;font-weight:bold;color:#fff;margin-bottom:2px}.dev-custom{font-size:11px;color:#888;margin-bottom:5px}.dev-details{font-size:13px;color:#aaa;line-height:1.4;margin-bottom:6px}.dev-subtext{font-size:11px;color:#7f8c8d;margin-top:5px;font-style:italic}.action-btn{background:#27ae60;color:#fff;border:1px solid #219653;padding:8px 14px;font-size:12px;border-radius:4px;font-weight:bold;cursor:pointer;white-space:nowrap;margin-left:10px;flex-shrink:0;transition:all 0.2s}.action-btn:hover{background:#219653}.dflex{display:flex;align-items:center;width:100%}.completed-state{opacity:0;transform:scale(0.95);pointer-events:none;transition:all 0.3s ease}"

    StringBuilder html = new StringBuilder()
    html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Maintenance Portal</title><meta name='viewport' content='width=device-width, initial-scale=1'>")

    html.append("""
    <script>
        function markComplete(taskId, btnElement) {
            let card = document.getElementById('card-' + taskId);
            let originalText = btnElement.innerHTML;
            btnElement.innerHTML = 'Saving...';
            btnElement.style.background = '#666';
            btnElement.disabled = true;

            fetch('dashboard/complete/' + taskId + '?access_token=${state.accessToken}')
            .then(r => {
                if(r.ok) {
                    card.classList.add('completed-state');
                    setTimeout(() => { card.style.display = 'none'; }, 300);
                } else {
                    btnElement.innerHTML = 'Error';
                    btnElement.style.background = '#e74c3c';
                    setTimeout(() => { btnElement.innerHTML = originalText; btnElement.style.background = ''; btnElement.disabled = false; }, 2000);
                }
            }).catch(e => {
                btnElement.innerHTML = 'Error';
                btnElement.style.background = '#e74c3c';
                setTimeout(() => { btnElement.innerHTML = originalText; btnElement.style.background = ''; btnElement.disabled = false; }, 2000);
            });
        }
    </script>
    """)

    html.append("<style>${css}</style></head><body><div class='container'>")

    html.append("<div style='text-align:center;margin-bottom:15px;display:flex;justify-content:center;'><svg width='70' height='70' viewBox='0 0 100 100' fill='none'><circle cx='50' cy='50' r='48' fill='#151515' stroke='#333' stroke-width='1'/><path d='M 50 20 L 25 30 V 50 C 25 68 35 80 50 85 C 65 80 75 68 75 50 V 30 L 50 20 Z' stroke='#e0e0e0' stroke-width='3' stroke-linejoin='round'/><path d='M 38 52 L 46 60 L 62 42' stroke='#27ae60' stroke-width='4' stroke-linecap='round' stroke-linejoin='round'/></svg></div><h2 style='text-align:center;color:#fff;margin:0 0 5px 0;'>Estate Maintenance Dashboard</h2><p style='text-align:center;font-size:13px;color:#888;margin-bottom:25px;'>Last Sync: ${new Date().format("MM/dd/yyyy h:mm a", location.timeZone)}</p>")

    html.append("<div class='summary-box'><div class='summary-card' style='border-bottom-color:#e74c3c;'><b>${overdueCount}</b><span>Overdue</span></div><div class='summary-card' style='border-bottom-color:#f1c40f;'><b>${upcomingCount}</b><span>Upcoming</span></div><div class='summary-card' style='border-bottom-color:#9b59b6;'><b>${snoozedCount}</b><span>Snoozed</span></div><div class='summary-card' style='border-bottom-color:#3498db;'><b>${totalActive}</b><span>Tracked Tasks</span></div></div>")

    def buildTaskCard = { t, statusColor ->
        def metaText = ""
        if (t.days < 0) metaText = "⚠️ Overdue by ${Math.abs(t.days)} Days"
        else if (t.days == 0) metaText = "⏰ Due Today"
        else if (statusColor == "Purple") metaText = "💤 Snoozed"
        else metaText = "⏳ Due in ${t.days} Days"

        def lastStr = new Date(t.last).format("MM/dd/yyyy", location.timeZone)
        def subtext = "Last Completed: ${lastStr} (Every ${t.interval} Days)"

        return "<div class='dev-card status-${statusColor}' id='card-${t.id}'><div class='dflex'><span class='dot dot-${statusColor}'></span><div class='dev-info'><div class='dev-name'>${t.name}</div><div class='dev-details'>${t.desc}</div><div class='dev-subtext'>${metaText} • ${subtext}</div></div><button class='action-btn' onclick=\"markComplete('${t.id}', this)\">✅ Complete</button></div></div>"
    }

    if (allPortalTasks.size() > 0) {
        def categorizedTasks = allPortalTasks.groupBy { it.category }
        
        categorizedTasks.each { catName, tasks ->
            def hasOverdue = tasks.any { it.days < 0 && !it.snoozed }
            def folderColor = hasOverdue ? "#e74c3c" : "#3498db"
            def icon = hasOverdue ? "⚠️" : "📁"
            def openTag = hasOverdue ? "open" : ""
            
            html.append("<details ${openTag}><summary style='border-left-color:${folderColor};'>${icon} ${catName} <span class='cat-count'>${tasks.size()} Tasks</span></summary><div class='folder-content'>")
            
            tasks.sort { it.days }.each { t ->
                def statusColor = "Green"
                if (t.snoozed) statusColor = "Purple"
                else if (t.days < 0) statusColor = "Red"
                else if (t.days <= warningThreshold) statusColor = "Yellow"
                
                html.append(buildTaskCard(t, statusColor))
            }
            html.append("</div></details>")
        }
    } else {
        html.append("<div style='text-align:center;padding:20px;background:#1e1e1e;border-radius:8px;color:#27ae60;font-weight:bold;margin-bottom:15px;border-left:4px solid #27ae60;'>No tasks are currently enabled.</div>")
    }

    html.append("</div></body></html>")
    
    return render(contentType: "text/html", data: html.toString(), status: 200)
}

def webCompleteTask() {
    def taskId = params.id
    if (taskId) {
        completeTask(taskId)
        render contentType: "application/json", data: '{"status":"success"}', status: 200
    } else {
        render contentType: "application/json", data: '{"status":"error", "message":"No ID provided"}', status: 400
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def installed() { logInfo("Installed"); initialize() }
def updated() { logInfo("Updated"); unsubscribe(); unschedule(); initialize() }

def ensureStateMaps() {
    if (state.actionHistory == null) state.actionHistory = []
    if (state.taskData == null) state.taskData = [:]
}

def initialize() {
    ensureStateMaps()
    def tempData = state.taskData ?: [:]
    def stateChanged = false
    
    getTaskList().each { t ->
        if (settings["enable_${t.id}"] && !tempData[t.id]) {
            tempData[t.id] = [lastCompleted: now(), snoozedUntil: 0]
            stateChanged = true
            logAction("Tracking started for: ${t.name}")
        }
    }
    
    if (stateChanged) state.taskData = tempData
    
    if (appEnableSwitch) subscribe(appEnableSwitch, "switch", enableSwitchHandler)
    if (rainSensors) subscribe(rainSensors, "water.wet", rainTriggerHandler)
    subscribe(location, "systemStart", hubRestartHandler)
    
    scheduleDailyCheck()
    updateChildDevice()
    logAction("App Initialized. BMS Engine Ready.")
}

def scheduleDailyCheck() {
    unschedule(dailyMaintenanceCheck)
    if (checkTime) schedule(checkTime, dailyMaintenanceCheck)
    else schedule("0 0 9 * * ?", dailyMaintenanceCheck)
}

def hubRestartHandler(evt) { scheduleDailyCheck() }

def enableSwitchHandler(evt) { 
    if (evt.value == "off") logAction("App Disabled via Master Switch.")
    else logAction("App Enabled via Master Switch.")
}

def rainTriggerHandler(evt) {
    logAction("⚠️ BMS TRIGGER: High moisture detected. Expediting drainage and pump tasks.")
    def tempData = state.taskData ?: [:]
    def tasksToTrigger = ["p8", "e1", "e9"] // Sump pump, gutters, window wells
    def updated = false
    
    tasksToTrigger.each { tid ->
        if (settings["enable_${tid}"] && tempData[tid]) {
            def task = getTaskList().find { it.id == tid }
            if (task) {
                def intervalMs = (settings["interval_${tid}"] ?: task.defaultDays).toInteger() * 86400000L
                tempData[tid].lastCompleted = now() - intervalMs // Force due today
                tempData[tid].snoozedUntil = 0 // Strip snooze
                updated = true
            }
        }
    }
    
    if (updated) {
        state.taskData = tempData
        if (notifyDevices) notifyDevices.deviceNotification("BMS Alert: Heavy water detected. Sump Pump and drainage tasks flagged as DUE TODAY.")
        updateChildDevice()
    }
}

def appButtonHandler(btn) {
    ensureStateMaps()
    if (btn == "btnRefresh") {
        updateChildDevice()
    } 
    else if (btn == "btnCreateDevice") {
        createIntegrationDevice()
    }
    else if (btn == "resetHistory") {
        state.actionHistory = []
    }
    else if (btn == "btnStaggerTasks") {
        staggerTasks()
    }
    else if (btn.startsWith("btnDashComplete_")) {
        completeTask(btn.replace("btnDashComplete_", ""))
    }
    else if (btn.startsWith("btnSnooze_")) {
        snoozeTask(btn.replace("btnSnooze_", ""))
    }
}

def staggerTasks() {
    def tempData = state.taskData ?: [:]
    int count = 0
    Random rand = new Random()
    
    getTaskList().each { t ->
        if (settings["enable_${t.id}"] && tempData[t.id]) {
            def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
            if (interval > 1) {
                def randomDays = rand.nextInt(interval)
                tempData[t.id].lastCompleted = now() - ((long)randomDays * 86400000L)
                tempData[t.id].snoozedUntil = 0
                count++
            }
        }
    }
    
    state.taskData = tempData
    logAction("🗓️ Staggered due dates for ${count} tasks to prevent overload.")
    updateChildDevice()
}

def createIntegrationDevice() {
    def childId = "AHM_${app.id}"
    if (!getChildDevice(childId)) {
        try {
            addChildDevice("ShaneAllen", "Maintenance Dashboard Tile", childId, null, [name: "Advanced House Maintenance Device"])
            logAction("Created Integration Device: 'Advanced House Maintenance Device'")
            runIn(2, "updateChildDevice")
        } catch (e) {
            log.error "Failed to create child device. Ensure 'Maintenance Dashboard Tile' driver is installed. Error: ${e}"
        }
    } else {
        logInfo("Integration device already exists.")
    }
}

def snoozeTask(taskId) {
    def taskList = getTaskList()
    def task = taskList.find { it.id == taskId }
    
    if (task) {
        Calendar cal = Calendar.getInstance(location.timeZone)
        int today = cal.get(Calendar.DAY_OF_WEEK)
        int addDays = Calendar.SATURDAY - today
        if (addDays <= 0) addDays += 7
        
        def snoozeTime = now() + (addDays * 86400000L)
        def tempData = state.taskData ?: [:]
        
        if (tempData[taskId]) {
            tempData[taskId].snoozedUntil = snoozeTime
            state.taskData = tempData
            logAction("💤 Task '${task.name}' snoozed until Saturday.")
            updateChildDevice()
        }
    }
}

def completeTask(taskId) {
    def taskList = getTaskList()
    def task = taskList.find { it.id == taskId }
    
    if (task) {
        def tempData = state.taskData ?: [:]
        tempData[taskId] = [lastCompleted: now(), snoozedUntil: 0]
        state.taskData = tempData 
        
        def interval = (settings["interval_${taskId}"] ?: task.defaultDays).toInteger()
        logAction("✅ Completed: '${task.name}'. Next due in ${interval} days.")
        updateChildDevice()
    }
}

def updateChildDevice() {
    def childId = "AHM_${app.id}"
    def child = getChildDevice(childId)
    if (!child) return
    
    def enabledTasks = []
    def overdueTasks = []
    def maxOverdueDays = 0

    getTaskList().each { t ->
        if (settings["enable_${t.id}"]) {
            def tData = state.taskData[t.id]
            if (tData) {
                def isSnoozed = (tData.snoozedUntil != null && tData.snoozedUntil > now())
                def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                def remainingDays = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()
                enabledTasks << [name: t.name, days: remainingDays, snoozed: isSnoozed]
                
                if (remainingDays < 0 && !isSnoozed) {
                    overdueTasks << t.name
                    if (Math.abs(remainingDays) > maxOverdueDays) maxOverdueDays = Math.abs(remainingDays)
                }
            }
        }
    }
    
    def overdueStr = overdueTasks.size() > 0 ? overdueTasks.join(", ") : "None"
    child.sendEvent(name: "overdueTasks", value: overdueStr, isStateChange: true)
    child.sendEvent(name: "maxOverdueDays", value: maxOverdueDays, isStateChange: true)
    
    def html = ""
    if (enabledTasks.size() > 0) {
        enabledTasks.sort { it.days }
        def top5 = enabledTasks.take(5)
        
        html += "<table style='width:100%; border-collapse:collapse; font-size:14px; font-family:sans-serif;'>"
        html += "<tr style='background-color:#343a40; color:white;'><th style='padding:6px; text-align:left;'>Top 5 Tasks</th><th style='padding:6px; text-align:center;'>Due</th></tr>"
        
        top5.each { t ->
            def color = "green"
            def dayStr = "${t.days} Days"
            def threshold = upcomingDays != null ? upcomingDays : 7
            
            if (t.snoozed) {
                color = "purple"
                dayStr = "SNOOZED"
            } else if (t.days < 0) {
                color = "red"
                dayStr = "OVERDUE"
            } else if (t.days == 0) {
                color = "darkorange"
                dayStr = "TODAY"
            } else if (t.days <= threshold) {
                color = "orange"
            }
            
            html += "<tr><td style='padding:6px; border-bottom:1px solid #ccc;'>${t.name}</td><td style='padding:6px; border-bottom:1px solid #ccc; text-align:center; color:${color}; font-weight:bold;'>${dayStr}</td></tr>"
        }
        html += "</table>"
    } else {
        html = "<div style='padding:15px; text-align:center; font-family:sans-serif; font-size:16px; color:green; font-weight:bold;'>No items due</div>"
    }
    
    child.sendEvent(name: "htmlTile", value: html, isStateChange: true)
}

def dailyMaintenanceCheck() {
    if (pauseModes && (pauseModes as List).contains(location.mode)) {
        logInfo("App is in Vacation/Pause Mode. Shifting appliance wear-and-tear timers forward by 1 day.")
        def tempData = state.taskData ?: [:]
        tempData.each { k, v ->
            v.lastCompleted = v.lastCompleted + 86400000L
        }
        state.taskData = tempData
        updateChildDevice()
        return 
    }

    updateChildDevice()
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return
    if (notifyModes && !(notifyModes as List).contains(location.mode)) return
    
    def threshold = upcomingDays != null ? upcomingDays : 7
    def allowOverdueReminders = remindOverdue != null ? remindOverdue : true
    def alertsGenerated = []
    
    getTaskList().each { t ->
        if (settings["enable_${t.id}"]) {
            def tData = state.taskData[t.id]
            if (tData) {
                def isSnoozed = (tData.snoozedUntil != null && tData.snoozedUntil > now())
                if (isSnoozed) return 
                
                def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                def remainingDays = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()
                
                if (remainingDays == threshold) alertsGenerated << "UPCOMING: '${t.name}' is due in ${remainingDays} days."
                else if (remainingDays == 0) alertsGenerated << "DUE TODAY: Time to complete '${t.name}'!"
                else if (remainingDays < 0 && allowOverdueReminders) alertsGenerated << "OVERDUE: '${t.name}' is overdue by ${Math.abs(remainingDays)} days."
            }
        }
    }
    
    if (alertsGenerated) {
        def combinedMessage = "House Maintenance Alerts:\n" + alertsGenerated.join("\n")
        logAction("Notifications Fired: \n" + alertsGenerated.join(" | "))
        if (notifyDevices) notifyDevices.deviceNotification(combinedMessage)
    }
}

String getHumanReadableStatus() {
    if (appEnableSwitch && appEnableSwitch.currentValue("switch") == "off") return "<span style='color:red;'>Disabled via Master Switch. Notifications paused.</span>"
    if (pauseModes && (pauseModes as List).contains(location.mode)) return "<span style='color:purple;'><b>Vacation Mode Active.</b> Daily wear-and-tear timers are paused.</span>"
    
    def overdueCount = 0
    def warningCount = 0
    def dueTodayCount = 0
    def snoozeCount = 0
    def threshold = upcomingDays != null ? upcomingDays : 7
    
    getTaskList().each { t ->
        if (settings["enable_${t.id}"]) {
            def tData = state.taskData[t.id]
            if (tData) {
                if (tData.snoozedUntil != null && tData.snoozedUntil > now()) {
                    snoozeCount++
                } else {
                    def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                    def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                    def days = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()
                    
                    if (days < 0) overdueCount++
                    else if (days == 0) dueTodayCount++
                    else if (days <= threshold) warningCount++
                }
            }
        }
    }
    
    if (overdueCount > 0) return "<span style='color:red;'><b>Requires Attention:</b></span> You have ${overdueCount} task(s) currently overdue."
    if (dueTodayCount > 0) return "<span style='color:orange;'><b>Due Today:</b></span> You have ${dueTodayCount} task(s) that need to be completed today."
    if (warningCount > 0) return "<span style='color:orange;'><b>Upcoming Maintenance:</b></span> You have ${warningCount} task(s) approaching their due date."
    if (snoozeCount > 0) return "<span style='color:purple;'><b>Snoozed:</b></span> You have ${snoozeCount} task(s) deferred to the weekend."
    
    return "<span style='color:green;'><b>All Good:</b></span> All tracked maintenance tasks are up to date."
}

def logAction(String msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29] 
    state.actionHistory = h 
}
def logAction(Object msg) { logAction(msg.toString()) }
def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }

// ==============================================================================
// MAINTENANCE TASK DATABASE
// ==============================================================================
def getTaskList() {
    return [
        // Pool & Spa (New)
        [id: "pool1", category: "Pool & Spa", name: "Clean Pool / Spa Filter", defaultDays: 30, description: "Backwash sand/DE filters or hose off cartridge filters to maintain pump flow.", consumable: true, estCost: 5.00],
        [id: "pool2", category: "Pool & Spa", name: "Inspect Pool Pump & O-Rings", defaultDays: 90, description: "Check pump basket for cracks, empty debris, and lubricate the lid O-ring with Teflon gel.", consumable: true, estCost: 10.00],
        [id: "pool3", category: "Pool & Spa", name: "Deep Clean Salt Cell", defaultDays: 180, description: "Soak the saltwater generator cell in a mild acid solution to remove calcium buildup.", consumable: true, estCost: 15.00],
        [id: "pool4", category: "Pool & Spa", name: "Winterize / Open Pool", defaultDays: 365, description: "Blow out plumbing lines, add winterizing chemicals, and securely attach the safety cover."],

        // Outdoor Living & BBQ (New)
        [id: "bbq1", category: "Outdoor Living & BBQ", name: "Deep Clean Grill & Grease Trap", defaultDays: 90, description: "Scrub grates, clean burner tubes, and empty the grease catch pan to prevent flare-ups and grease fires."],
        [id: "bbq2", category: "Outdoor Living & BBQ", name: "Inspect Propane Hoses & Fittings", defaultDays: 180, description: "Use soapy water to check the gas regulator and hoses for bubbling/leaks."],
        [id: "bbq3", category: "Outdoor Living & BBQ", name: "Clean & Seal Fire Pit", defaultDays: 365, description: "Clear out old ash to prevent rust, and reseal masonry or metal surfaces."],

        // Photography & Tech Gear (New)
        [id: "camgear1", category: "Photography & Tech Gear", name: "Clean Camera Sensors & Lenses", defaultDays: 180, description: "Use a dedicated sensor swab kit on your mirrorless camera and wipe lenses with optics solution.", consumable: true, estCost: 20.00],
        [id: "camgear2", category: "Photography & Tech Gear", name: "Audit & Format SD / Memory Cards", defaultDays: 90, description: "Backup all media to your PC/NAS, then do a deep format on your camera SD cards to prevent corruption."],
        [id: "camgear3", category: "Photography & Tech Gear", name: "Charge & Cycle Backup Batteries", defaultDays: 90, description: "Top off spare camera and electronics batteries to maintain lithium cell health."],

        // Security Cameras
        [id: "cam1", category: "Security Cameras", name: "Clean Camera Lenses & Housings", defaultDays: 90, description: "Wipe lenses with a microfiber cloth. Clear cobwebs, dust, and check for condensation inside housings."],
        [id: "cam2", category: "Security Cameras", name: "Inspect Mounts & Cables", defaultDays: 180, description: "Check that mounting hardware is tight and verify outdoor cables for weather or pest damage."],
        [id: "cam3", category: "Security Cameras", name: "Verify Storage & Firmware", defaultDays: 90, description: "Check NVR/DVR or cloud storage to ensure footage is saving. Apply firmware updates."],
        [id: "cam4", category: "Security Cameras", name: "Test Night Vision & Motion Zones", defaultDays: 180, description: "Verify IR LEDs turn on in the dark and adjust motion detection sensitivity to reduce false alarms."],

        // Weather Station
        [id: "ws1", category: "Weather Station", name: "Clean Rain Gauge Tipping Bucket", defaultDays: 90, description: "Remove debris, leaves, and insect nests from the collector bucket so water flows freely."],
        [id: "ws2", category: "Weather Station", name: "Clean Solar Panel & Radiation Shield", defaultDays: 180, description: "Wipe the solar panel with a damp cloth and clear dust from the temperature radiation shield."],
        [id: "ws3", category: "Weather Station", name: "Inspect Anemometer & Wind Vane", defaultDays: 180, description: "Ensure the wind cups and vane spin freely without binding. Lubricate or clear debris if necessary."],
        [id: "ws4", category: "Weather Station", name: "Check Batteries & Calibration", defaultDays: 365, description: "Replace backup batteries in the sensor suite and verify temperature/humidity against a known good thermometer.", consumable: true, estCost: 10.00],

        // Water Systems 
        [id: "w1", category: "Water Systems", name: "Replace Whole House Water Filter", defaultDays: 90, description: "Swap out sediment/carbon filter cartridges.", consumable: true, estCost: 35.00],
        [id: "w2", category: "Water Systems", name: "Sanitize RO System & Swap Filters", defaultDays: 365, description: "Replace reverse osmosis pre-filters and sanitize housing.", consumable: true, estCost: 50.00],
        [id: "w3", category: "Water Systems", name: "Add Salt to Water Softener", defaultDays: 30, description: "Check brine tank levels and top off with salt pellets.", consumable: true, estCost: 15.00],
        [id: "w4", category: "Water Systems", name: "Test Well Water Quality", defaultDays: 365, description: "Test for coliform bacteria, nitrates, and pH levels.", consumable: true, estCost: 30.00],

        // Power & Electrical 
        [id: "pe1", category: "Power & Electrical", name: "Test Portable/Standby Generator", defaultDays: 30, description: "Run generator under load for 15-20 minutes."],
        [id: "pe2", category: "Power & Electrical", name: "Change Generator Oil & Filter", defaultDays: 365, description: "Perform annual maintenance on emergency generator.", consumable: true, estCost: 20.00],
        [id: "pe3", category: "Power & Electrical", name: "Clean Solar Panels", defaultDays: 180, description: "Wash dust and pollen off solar array for peak efficiency."],
        [id: "pe4", category: "Power & Electrical", name: "Visually Inspect Main Breaker Panel", defaultDays: 365, description: "Check for scorch marks, buzzing sounds, or tripped breakers."],
        [id: "pe5", category: "Power & Electrical", name: "Test UPS / Battery Backups", defaultDays: 180, description: "Unplug UPS units from wall to ensure battery holds load for PC and Network gear."],

        // Smart Home & Tech 
        [id: "sh1", category: "Smart Home", name: "Backup Hubitat Hub", defaultDays: 30, description: "Download a local backup of your home automation hub configuration."],
        [id: "sh2", category: "Smart Home", name: "Replace Sensor Batteries", defaultDays: 365, description: "Swap batteries in motion, temperature, and contact sensors.", consumable: true, estCost: 20.00],
        [id: "sh3", category: "Smart Home", name: "Clean Robot Vacuum Sensors", defaultDays: 30, description: "Wipe cliff sensors and clean main brush roller."],
        [id: "sh4", category: "Smart Home", name: "Test Smart Water Leak Detectors", defaultDays: 90, description: "Place a damp paper towel on leak sensors to ensure they trigger hub automations and sirens."],

        // Ride-On Mower
        [id: "m1", category: "Ride-On Mower", name: "Change Oil & Filter", defaultDays: 365, description: "Drain oil, replace filter, and refill. (Or every 50-100 hours of use).", consumable: true, estCost: 25.00],
        [id: "m2", category: "Ride-On Mower", name: "Sharpen or Replace Blades", defaultDays: 180, description: "Remove deck blades and sharpen with a grinder or replace entirely.", consumable: true, estCost: 45.00],
        [id: "m3", category: "Ride-On Mower", name: "Replace Air Filter & Spark Plug", defaultDays: 365, description: "Swap out the engine air filter and install a fresh gapped spark plug.", consumable: true, estCost: 15.00],
        [id: "m4", category: "Ride-On Mower", name: "Grease Spindles & Fittings", defaultDays: 90, description: "Use a grease gun on mower deck spindles and axle zerks.", consumable: true, estCost: 5.00],
        [id: "m5", category: "Ride-On Mower", name: "Check Tire Pressure", defaultDays: 30, description: "Verify front and rear tires are at manufacturer recommended PSI for an even cut."],
        [id: "m6", category: "Ride-On Mower", name: "Winterize Mower", defaultDays: 365, description: "Add fuel stabilizer, run engine to distribute, and place battery on a trickle charger."],
        [id: "m7", category: "Ride-On Mower", name: "Clean Mower Deck", defaultDays: 30, description: "Scrape caked grass from the underside of the deck to prevent rust."],

        // Vehicle Maintenance
        [id: "v1", category: "Vehicle Maintenance", name: "Check Tire Pressure & Tread", defaultDays: 30, description: "Verify PSI on all tires (including spare) and check tread depth with a gauge."],
        [id: "v2", category: "Vehicle Maintenance", name: "Oil & Filter Change", defaultDays: 180, description: "Replace engine oil and filter based on mileage or time.", consumable: true, estCost: 45.00],
        [id: "v3", category: "Vehicle Maintenance", name: "Replace Wiper Blades", defaultDays: 180, description: "Swap out front and rear wiper blades.", consumable: true, estCost: 35.00],
        [id: "v4", category: "Vehicle Maintenance", name: "Rotate Tires", defaultDays: 180, description: "Rotate tires to ensure even wear across the treads."],
        [id: "v5", category: "Vehicle Maintenance", name: "Replace Cabin Air Filter", defaultDays: 365, description: "Swap the internal cabin AC filter behind the glovebox.", consumable: true, estCost: 20.00],
        [id: "v6", category: "Vehicle Maintenance", name: "Replace Engine Air Filter", defaultDays: 365, description: "Check and replace the main engine air intake filter.", consumable: true, estCost: 25.00],
        [id: "v7", category: "Vehicle Maintenance", name: "Check Brake Pads & Fluid", defaultDays: 180, description: "Visually inspect pad thickness and verify brake fluid is at the max line."],
        [id: "v8", category: "Vehicle Maintenance", name: "Wash, Wax & Protectant", defaultDays: 90, description: "Apply a ceramic or carnauba wax to protect clear coat.", consumable: true, estCost: 10.00],

        // Financial & Administration
        [id: "f1", category: "Financial & Admin", name: "Review Home Insurance Rates", defaultDays: 365, description: "Call broker to check for better homeowner policy rates or multi-line discounts."],
        [id: "f2", category: "Financial & Admin", name: "Review Auto Insurance Rates", defaultDays: 365, description: "Shop around auto policies to ensure you have the best premium."],
        [id: "f3", category: "Financial & Admin", name: "Audit Internet & Utility Bills", defaultDays: 180, description: "Review telecom and internet bills to negotiate promotional rates or remove hidden fees."],
        [id: "f4", category: "Financial & Admin", name: "Check Annual Credit Report", defaultDays: 365, description: "Pull free annual credit reports to check for anomalies."],
        [id: "f5", category: "Financial & Admin", name: "File Property Tax Exemptions", defaultDays: 365, description: "Verify Homestead Exemption or other tax benefits are actively filed."],
        [id: "f6", category: "Financial & Admin", name: "Digital & PC Backups", defaultDays: 30, description: "Ensure automated cloud or external hard drive backups are functioning."],

        // Garden & Outdoors
        [id: "g1", category: "Garden & Outdoors", name: "Clean & Inspect Greenhouse", defaultDays: 180, description: "Wash down greenhouse panels for maximum light transmission and inspect structural anchors."],
        [id: "g2", category: "Garden & Outdoors", name: "Top Dress Raised Garden Beds", defaultDays: 365, description: "Add fresh compost and organic amendments to garden beds before planting season.", consumable: true, estCost: 40.00],
        [id: "g3", category: "Garden & Outdoors", name: "Clean & Oil Garden Tools", defaultDays: 90, description: "Brush dirt off shovels/shears and apply boiled linseed oil to wooden handles."],
        [id: "g4", category: "Garden & Outdoors", name: "Inspect Irrigation & Hoses", defaultDays: 180, description: "Check soaker hoses and drip lines for leaks or clogs."],

        // Home Gym
        [id: "gym1", category: "Home Gym", name: "Sanitize Mats & Equipment", defaultDays: 30, description: "Wipe down all benches, cardio machines, and rubber flooring with antibacterial spray."],
        [id: "gym2", category: "Home Gym", name: "Inspect Cable Machines & Hardware", defaultDays: 90, description: "Check cables for fraying, tighten loose bolts on racks, and lubricate guide rods with silicone."],

        // Safety & Security
        [id: "s1", category: "Safety & Security", name: "Test Smoke Alarms", defaultDays: 30, description: "Press and hold the test button on each unit."],
        [id: "s2", category: "Safety & Security", name: "Replace Smoke Alarm Batteries", defaultDays: 180, description: "Replace the 9V or AA batteries.", consumable: true, estCost: 15.00],
        [id: "s3", category: "Safety & Security", name: "Test CO Detectors", defaultDays: 30, description: "Press the test button."],
        [id: "s4", category: "Safety & Security", name: "Replace CO Detector Batteries", defaultDays: 180, description: "Swap out batteries.", consumable: true, estCost: 10.00],
        [id: "s5", category: "Safety & Security", name: "Check Fire Extinguishers", defaultDays: 180, description: "Check pressure gauge and shake powder."],
        [id: "s6", category: "Safety & Security", name: "Clean Dryer Vent", defaultDays: 365, description: "Brush out built-up lint in wall duct."],
        [id: "s7", category: "Safety & Security", name: "Inspect Extension Cords", defaultDays: 180, description: "Inspect for fraying or damage."],
        [id: "s8", category: "Safety & Security", name: "Test Radon Levels", defaultDays: 365, description: "Set up a short-term radon test kit.", consumable: true, estCost: 20.00],
        [id: "s9", category: "Safety & Security", name: "Test Sump Pump Battery Backup", defaultDays: 180, description: "Unplug main pump and manually lift float switch."],
        [id: "s10", category: "Safety & Security", name: "Test AFCI/GFCI Breakers", defaultDays: 30, description: "Press 'Test' button on electrical panel breakers."],
        [id: "s11", category: "Safety & Security", name: "Lubricate Garage Door Mechanics", defaultDays: 180, description: "Apply silicone spray to rollers and hinges.", consumable: true, estCost: 8.00],
        [id: "s12", category: "Safety & Security", name: "Inspect Fire Escape Ladders", defaultDays: 365, description: "Ensure emergency ladders are easily accessible and deploy without catching."],
        [id: "s13", category: "Safety & Security", name: "Clean Air Purifier Filters", defaultDays: 90, description: "Vacuum or replace HEPA filters in standalone air purifiers.", consumable: true, estCost: 35.00],
        [id: "s14", category: "Safety & Security", name: "Audit First Aid Kit & Emergency Supplies", defaultDays: 180, description: "Throw out expired medications. Verify emergency water, food rations, and flashlight batteries are intact."],
        [id: "s15", category: "Safety & Security", name: "Test Security System Sirens", defaultDays: 90, description: "Put monitoring in test mode and deliberately trip an alarm to ensure sirens and central station dispatch are working."],
        
        // HVAC
        [id: "h1", category: "HVAC", name: "Change HVAC Filter", defaultDays: 90, description: "Replace standard air handler filter.", consumable: true, estCost: 25.00],
        [id: "h2", category: "HVAC", name: "Clean AC Condenser Coils", defaultDays: 365, description: "Wash debris out of outdoor unit fins."],
        [id: "h3", category: "HVAC", name: "Clear AC Condensate Drain", defaultDays: 180, description: "Pour white vinegar down the indoor AC drain.", consumable: true, estCost: 4.00],
        [id: "h4", category: "HVAC", name: "Inspect Ductwork", defaultDays: 365, description: "Check for tears or leaks in attic/crawlspace."],
        [id: "h5", category: "HVAC", name: "Winterize AC Unit", defaultDays: 365, description: "Clear debris and protect top from ice."],
        [id: "h6", category: "HVAC", name: "Test Heating System", defaultDays: 365, description: "Verify warm air blows before winter hits."],
        [id: "h7", category: "HVAC", name: "Bleed Radiators", defaultDays: 365, description: "Release trapped air using radiator key."],
        [id: "h8", category: "HVAC", name: "Inspect Chimney & Flue", defaultDays: 365, description: "Check for creosote or loose bricks."],
        [id: "h9", category: "HVAC", name: "Clean Bathroom Exhaust Fans", defaultDays: 180, description: "Vacuum dust out of motor housing."],
        [id: "h10", category: "HVAC", name: "Service Whole-House Humidifier", defaultDays: 365, description: "Replace humidifier pad.", consumable: true, estCost: 18.00],
        [id: "h11", category: "HVAC", name: "Clean Baseboard Heater Fins", defaultDays: 365, description: "Vacuum aluminum fins for efficiency."],
        [id: "h12", category: "HVAC", name: "Clear ERV/HRV Filters", defaultDays: 90, description: "Wash or replace internal pre-filters.", consumable: true, estCost: 30.00],
        
        // Plumbing
        [id: "p1", category: "Plumbing", name: "Flush Water Heater", defaultDays: 365, description: "Drain bottom valve until water runs clear."],
        [id: "p2", category: "Plumbing", name: "Test Water Heater PRV", defaultDays: 365, description: "Lift lever on pressure relief valve."],
        [id: "p3", category: "Plumbing", name: "Clean Garbage Disposal", defaultDays: 30, description: "Run ice cubes and lemon through disposal."],
        [id: "p4", category: "Plumbing", name: "Check Sinks & Toilets for Leaks", defaultDays: 180, description: "Look for stains under cabinets. Dye test toilet tank."],
        [id: "p5", category: "Plumbing", name: "Clean Faucet Aerators", defaultDays: 180, description: "Soak screens in white vinegar."],
        [id: "p6", category: "Plumbing", name: "Inspect Washing Machine Hoses", defaultDays: 365, description: "Check for bulging or cracks."],
        [id: "p7", category: "Plumbing", name: "Snake Slow Drains", defaultDays: 180, description: "Pull hair out of shower/sink drains."],
        [id: "p8", category: "Plumbing", name: "Inspect Sump Pump", defaultDays: 180, description: "Pour water into pit to ensure float switch triggers."],
        [id: "p9", category: "Plumbing", name: "Septic System Inspection", defaultDays: 1095, description: "Measure sludge level in septic tank."],
        [id: "p10", category: "Plumbing", name: "Exercise Shutoff Valves", defaultDays: 180, description: "Turn main and under-sink valves off and on to prevent seizing."],
        [id: "p11", category: "Plumbing", name: "Fill Unused P-Traps", defaultDays: 30, description: "Run water in guest bathrooms to prevent sewer gas."],
        [id: "p12", category: "Plumbing", name: "Descale Tankless Water Heater", defaultDays: 365, description: "Flush heat exchanger with descaling solution.", consumable: true, estCost: 25.00],
        [id: "p13", category: "Plumbing", name: "Check Sump Pump Discharge", defaultDays: 365, description: "Ensure exterior pipe is clear and draining away from foundation."],
        [id: "p14", category: "Plumbing", name: "Flush Garage & Basement Floor Drains", defaultDays: 180, description: "Pour a bucket of water down floor drains to keep the trap sealed against sewer gases."],
        [id: "p15", category: "Plumbing", name: "Inspect Toilet Supply Lines & Flappers", defaultDays: 180, description: "Check braided supply lines for rust/bulges to prevent catastrophic leaks. Verify flappers seal fully.", consumable: true, estCost: 15.00],
        [id: "p16", category: "Plumbing", name: "Check Water Heater Anode Rod", defaultDays: 365, description: "Inspect sacrificial anode rod to prevent the tank from rusting out and failing.", consumable: true, estCost: 25.00],
        [id: "p17", category: "Plumbing", name: "Check Home Water Pressure", defaultDays: 365, description: "Attach a pressure gauge to an exterior hose bib. Readings over 80 PSI mean the house PRV has failed and pipes are at risk of bursting.", consumable: true, estCost: 10.00],
        [id: "p18", category: "Plumbing", name: "Test Backflow Preventer", defaultDays: 365, description: "Ensure the backflow preventer on your irrigation/city water line is functioning to stop contaminated water from entering the home."],
        
        // Appliances
        [id: "a1", category: "Appliances", name: "Clean Range Hood Filter", defaultDays: 90, description: "Degrease metal filters above stove."],
        [id: "a2", category: "Appliances", name: "Deep Clean Oven", defaultDays: 180, description: "Run self-cleaning cycle or scrub interior."],
        [id: "a3", category: "Appliances", name: "Clean Refrigerator Coils", defaultDays: 180, description: "Vacuum coils to save compressor."],
        [id: "a4", category: "Appliances", name: "Replace Fridge Water Filter", defaultDays: 180, description: "Install new fridge filter and flush.", consumable: true, estCost: 45.00],
        [id: "a5", category: "Appliances", name: "Run Dishwasher Self-Clean", defaultDays: 30, description: "Run heavy hot-water cycle with vinegar."],
        [id: "a6", category: "Appliances", name: "Clean Washing Machine Tub", defaultDays: 30, description: "Run hot cycle with washer cleaning tablet.", consumable: true, estCost: 6.00],
        [id: "a7", category: "Appliances", name: "Clean Microwave Vents", defaultDays: 180, description: "Replace or clean top charcoal filter.", consumable: true, estCost: 12.00],
        [id: "a8", category: "Appliances", name: "Descale Coffee Maker", defaultDays: 90, description: "Brew white vinegar/water mix to clear scale."],
        [id: "a9", category: "Appliances", name: "Clean Dishwasher Filter", defaultDays: 30, description: "Wash plastic filter at bottom of dishwasher."],
        [id: "a10", category: "Appliances", name: "Inspect Oven Door Gasket", defaultDays: 365, description: "Check braided seal for heat loss."],
        [id: "a11", category: "Appliances", name: "Vacuum Internal Dryer Cabinet", defaultDays: 365, description: "Vacuum lint bypassing trap around heating element."],
        [id: "a12", category: "Appliances", name: "Descale & Sanitize Ice Maker", defaultDays: 180, description: "Clean ice bin and run descaling solution through the machine.", consumable: true, estCost: 10.00],
        [id: "a13", category: "Appliances", name: "Clean Refrigerator Drip Pan", defaultDays: 180, description: "Empty and wash the condensate pan underneath the fridge to prevent odors."],
        [id: "a14", category: "Appliances", name: "Vacuum Behind Large Appliances", defaultDays: 365, description: "Pull out stove and fridge to vacuum dust bunnies and stray crumbs."],
        [id: "a15", category: "Appliances", name: "Clean Range Hood Duct", defaultDays: 365, description: "Inspect and clean grease buildup inside the main exhaust duct leading outside to prevent chimney fires."],
        
        // Interior
        [id: "i1", category: "Interior", name: "Clean Baseboards", defaultDays: 90, description: "Wipe down with damp cloth."],
        [id: "i2", category: "Interior", name: "Deep Clean Carpets", defaultDays: 365, description: "Steam clean to remove allergens.", consumable: true, estCost: 40.00],
        [id: "i3", category: "Interior", name: "Wash Windows (Inside)", defaultDays: 180, description: "Clean all interior glass."],
        [id: "i4", category: "Interior", name: "Dust Ceiling Fans", defaultDays: 90, description: "Wipe fan blades clean."],
        [id: "i5", category: "Interior", name: "Clean Window Treatments", defaultDays: 180, description: "Vacuum or wash curtains and blinds."],
        [id: "i6", category: "Interior", name: "Test GFCI Outlets", defaultDays: 30, description: "Test and reset wet-area outlets."],
        [id: "i7", category: "Interior", name: "Lubricate Door Hinges", defaultDays: 365, description: "Apply lithium grease to squeaky hinges."],
        [id: "i8", category: "Interior", name: "Inspect Grout & Caulking", defaultDays: 180, description: "Check tub/shower caulk lines for peeling.", consumable: true, estCost: 10.00],
        [id: "i9", category: "Interior", name: "Test Garage Door Auto-Reverse", defaultDays: 30, description: "Test crush sensor with paper towel roll."],
        [id: "i10", category: "Interior", name: "Check Weatherstripping", defaultDays: 180, description: "Inspect foam seals around exterior doors.", consumable: true, estCost: 15.00],
        [id: "i11", category: "Interior", name: "Inspect Attic for Leaks & Pests", defaultDays: 180, description: "Look for daylight or water stains."],
        [id: "i12", category: "Interior", name: "Tighten Hardware", defaultDays: 365, description: "Tighten door knobs, hinges, and stair handrails."],
        [id: "i13", category: "Interior", name: "Clean & Sanitize Trash Cans", defaultDays: 90, description: "Hose out and scrub the interior of kitchen and bathroom trash bins with bleach or disinfectant."],
        [id: "i14", category: "Interior", name: "Lubricate Lock Cylinders", defaultDays: 365, description: "Puff graphite powder into exterior door locks for smooth operation.", consumable: true, estCost: 5.00],
        [id: "i15", category: "Interior", name: "Sanitize High-Touch Surfaces", defaultDays: 30, description: "Wipe down all door knobs, light switches, and cabinet pulls with disinfectant."],
        
        // Exterior
        [id: "e1", category: "Exterior", name: "Clean Gutters", defaultDays: 180, description: "Scoop leaves and flush downspouts."],
        [id: "e2", category: "Exterior", name: "Inspect Roof for Damage", defaultDays: 365, description: "Check for missing shingles or sagging."],
        [id: "e3", category: "Exterior", name: "Inspect Exterior Siding", defaultDays: 365, description: "Check for peeling paint or cracked vinyl."],
        [id: "e4", category: "Exterior", name: "Power Wash Deck / Patio", defaultDays: 365, description: "Blast away mildew and dirt."],
        [id: "e5", category: "Exterior", name: "Reseal Deck or Fence", defaultDays: 730, description: "Apply fresh coat of sealer.", consumable: true, estCost: 80.00],
        [id: "e6", category: "Exterior", name: "Inspect Foundation", defaultDays: 365, description: "Look for large horizontal cracks."],
        [id: "e7", category: "Exterior", name: "Trim Trees Near House", defaultDays: 365, description: "Cut branches touching roof."],
        [id: "e8", category: "Exterior", name: "Winterize Outdoor Faucets", defaultDays: 365, description: "Disconnect hoses and drain spigots."],
        [id: "e9", category: "Exterior", name: "Clean Window Wells", defaultDays: 180, description: "Remove leaves so water can drain."],
        [id: "e10", category: "Exterior", name: "Seal Driveway Cracks", defaultDays: 365, description: "Fill cracks with asphalt/masonry sealant.", consumable: true, estCost: 20.00],
        [id: "e11", category: "Exterior", name: "Clear Foundation Vents", defaultDays: 180, description: "Ensure crawlspace vents are unblocked."],
        [id: "e12", category: "Exterior", name: "Clean Window & Brick Weep Holes", defaultDays: 365, description: "Clear small weep holes at bottom of brick/windows."],
        [id: "e13", category: "Exterior", name: "Inspect Exterior Flashing", defaultDays: 365, description: "Check caulk around dryer vents and outdoor pipes.", consumable: true, estCost: 8.00],
        [id: "e14", category: "Exterior", name: "Inspect & Clean Outdoor Lighting", defaultDays: 180, description: "Wipe down fixtures, remove cobwebs, and verify all exterior security bulbs are functioning."],
        [id: "e15", category: "Exterior", name: "Clear Driveway Culvert", defaultDays: 180, description: "Remove debris blocking the drainage pipe under the driveway."],
        [id: "e16", category: "Exterior", name: "Inspect & Clean Septic Effluent Filter", defaultDays: 180, description: "Pull and hose off the septic tank effluent filter to prevent backups."],
        [id: "e17", category: "Exterior", name: "Professional Termite/Pest Inspection", defaultDays: 365, description: "Schedule a professional to walk the perimeter and crawlspace looking for structural pest damage."],
        [id: "e18", category: "Exterior", name: "Winterize / Blow Out Irrigation System", defaultDays: 365, description: "Blow compressed air through sprinkler lines to prevent bursting during winter freezes."],
        [id: "e19", category: "Exterior", name: "Spray Perimeter Pest Control", defaultDays: 90, description: "Spray exterior foundation, doors, and windows with insecticide to keep bugs out of the house.", consumable: true, estCost: 35.00],
        [id: "e20", category: "Exterior", name: "Check Attic Ventilation & Soffits", defaultDays: 365, description: "Verify attic insulation has not collapsed over the soffit vents, ensuring proper airflow to prevent mold and ice dams."]
    ]
}
