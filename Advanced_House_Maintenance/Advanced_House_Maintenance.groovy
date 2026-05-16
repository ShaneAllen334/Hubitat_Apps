/**
 * Advanced House Maintenance
 *
 * Author: ShaneAllen
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
        
        section("<b>Web Dashboard Links</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Family Dashboard:</b> Share these links with household members. They open a clean, dark-mode webpage showing only overdue items that they can check off securely from their phone.</div>"
            
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

        section("<b>Live Maintenance Dashboard</b>") {
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
        
        section("<b>90-Day Consumables Forecast</b>") {
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

        section("<b>Dynamic BMS Triggers & Suspension</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>Occupancy-Aware Timers:</b> Select modes where the house is empty. The app will automatically pause wear-and-tear timers for appliances while you are gone.</div>"
            input "pauseModes", "mode", title: "Vacation / Away Modes (Pauses Timers)", multiple: true, required: false
            
            paragraph "<div style='font-size:13px; color:#555; margin-top:10px;'><b>Environmental Sensors:</b> If heavy water is detected, critical drainage tasks (Sump Pump, Gutters, Window Wells) are immediately flagged as Due Today.</div>"
            input "rainSensors", "capability.waterSensor", title: "Heavy Rain / Sump Pit Sensors", multiple: true, required: false
        }

        section("<b>App Control & Global Settings</b>") {
            input "appEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch (Optional)", required: false, multiple: false
            
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

        section("<b>Task Configuration Directory</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Click on a category below to expand and select which maintenance tasks you want to track.</div>"
        }

        def categories = getTaskList().groupBy { it.category }
        categories.each { catName, tasks ->
            section("<b>⚙️ ${catName} Maintenance</b>", hideable: true, hidden: true) {
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
        
        section("<b>Recent Action History</b>") {
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
    def overdueTasks = []
    
    getTaskList().each { t ->
        if (settings["enable_${t.id}"]) {
            def tData = state.taskData[t.id]
            if (tData) {
                def isSnoozed = (tData.snoozedUntil != null && tData.snoozedUntil > now())
                def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                def remainingDays = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()
                
                // Fetch tasks that are overdue or due today
                if (remainingDays <= 0 && !isSnoozed) {
                    overdueTasks << [id: t.id, name: t.name, desc: t.description, category: t.category, overdue: Math.abs(remainingDays)]
                }
            }
        }
    }

    overdueTasks.sort { it.overdue }.reverse()

    def html = """<!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <meta name="apple-mobile-web-app-capable" content="yes">
        <meta name="theme-color" content="#121212">
        <title>House Maintenance</title>
        <style>
            :root { --bg: #121212; --card-bg: #1e1e1e; --text-main: #e0e0e0; --text-muted: #888; --accent: #dc3545; --success: #28a745; --success-hover: #218838; }
            body { background-color: var(--bg); color: var(--text-main); font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; margin: 0; padding: 20px 15px; -webkit-font-smoothing: antialiased; }
            .container { max-width: 600px; margin: 0 auto; }
            .header { text-align: center; margin-bottom: 35px; }
            h1 { font-size: 1.8em; font-weight: 500; margin: 0 0 5px 0; color: #ffffff; letter-spacing: 0.5px; }
            .subtitle { color: var(--text-muted); font-size: 0.95em; }
            
            .card { background-color: var(--card-bg); border-radius: 12px; padding: 20px; margin-bottom: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.3); border-left: 5px solid var(--accent); display: flex; flex-direction: column; transition: all 0.3s ease; position: relative; overflow: hidden; }
            .card.completed { opacity: 0; transform: scale(0.95); pointer-events: none; }
            
            .card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
            .task-name { font-size: 1.25em; font-weight: 600; color: #ffffff; line-height: 1.3; margin-right: 15px;}
            .category-badge { background: #333; color: #aaa; font-size: 0.7em; padding: 3px 8px; border-radius: 12px; text-transform: uppercase; letter-spacing: 0.5px; white-space: nowrap; }
            
            .task-desc { font-size: 0.95em; color: #a0a0a0; margin-bottom: 15px; line-height: 1.5; }
            
            .card-footer { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #333; padding-top: 15px; }
            .task-meta { font-size: 0.85em; font-weight: 600; }
            .meta-overdue { color: var(--accent); background: rgba(220, 53, 69, 0.1); padding: 5px 10px; border-radius: 6px; }
            .meta-today { color: #fd7e14; background: rgba(253, 126, 20, 0.1); padding: 5px 10px; border-radius: 6px; }
            
            .btn { background-color: var(--success); color: white; border: none; padding: 10px 20px; border-radius: 8px; cursor: pointer; font-size: 0.95em; font-weight: 600; transition: background-color 0.2s, transform 0.1s; display: flex; align-items: center; justify-content: center; outline: none; }
            .btn:hover { background-color: var(--success-hover); }
            .btn:active { transform: scale(0.96); }
            .btn svg { margin-right: 6px; width: 16px; height: 16px; fill: currentColor; }
            
            .empty-state { text-align: center; padding: 60px 20px; background-color: var(--card-bg); border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.2); }
            .empty-icon { font-size: 4em; margin-bottom: 15px; line-height: 1; }
            .empty-title { font-size: 1.3em; font-weight: 600; color: #ffffff; margin-bottom: 8px; }
            .empty-desc { color: var(--text-muted); font-size: 0.95em; }
        </style>
        <script>
            function markComplete(taskId, btnElement) {
                // Visual feedback instantly
                let originalText = btnElement.innerHTML;
                btnElement.innerHTML = 'Saving...';
                btnElement.style.backgroundColor = '#6c757d';
                btnElement.disabled = true;

                fetch('dashboard/complete/' + taskId + '?access_token=${state.accessToken}')
                .then(response => {
                    if(response.ok) {
                        let card = document.getElementById('card-' + taskId);
                        card.classList.add('completed');
                        setTimeout(() => {
                            card.style.display = 'none';
                            checkEmptyState();
                        }, 300);
                    } else {
                        btnElement.innerHTML = 'Error';
                        btnElement.style.backgroundColor = '#dc3545';
                        setTimeout(() => {
                            btnElement.innerHTML = originalText;
                            btnElement.style.backgroundColor = '';
                            btnElement.disabled = false;
                        }, 2000);
                    }
                })
                .catch(err => {
                    alert("Network error occurred.");
                    btnElement.innerHTML = originalText;
                    btnElement.style.backgroundColor = '';
                    btnElement.disabled = false;
                });
            }

            function checkEmptyState() {
                let visibleCards = document.querySelectorAll('.card:not([style*="display: none"])').length;
                if(visibleCards === 0) {
                    location.reload(); // Reloads to show the empty state rendered by server
                }
            }
        </script>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h1>Maintenance Required</h1>
                <div class="subtitle">Secure Family Dashboard</div>
            </div>
    """

    if (overdueTasks.size() > 0) {
        overdueTasks.each { t ->
            def metaClass = t.overdue > 0 ? "meta-overdue" : "meta-today"
            def metaText = t.overdue > 0 ? "⚠️ Overdue by ${t.overdue} Days" : "⏰ Due Today"
            
            html += """
            <div class="card" id="card-${t.id}">
                <div class="card-header">
                    <div class="task-name">${t.name}</div>
                    <div class="category-badge">${t.category}</div>
                </div>
                <div class="task-desc">${t.desc}</div>
                <div class="card-footer">
                    <div class="task-meta ${metaClass}">${metaText}</div>
                    <button class="btn" onclick="markComplete('${t.id}', this)">
                        <svg viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
                        Complete
                    </button>
                </div>
            </div>
            """
        }
    } else {
        html += """
            <div class="empty-state">
                <div class="empty-icon">🎉</div>
                <div class="empty-title">All Caught Up!</div>
                <div class="empty-desc">There are no overdue maintenance tasks requiring attention right now. Great job!</div>
            </div>
        """
    }

    html += """
        </div>
    </body>
    </html>
    """
    
    render contentType: "text/html", data: html, status: 200
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

def initialize() {
    if (!state.actionHistory) state.actionHistory = []
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
    if (btn == "btnRefresh") {
        updateChildDevice()
    } 
    else if (btn == "btnCreateDevice") {
        createIntegrationDevice()
    }
    else if (btn == "resetHistory") {
        state.actionHistory = []
    }
    else if (btn.startsWith("btnDashComplete_")) {
        completeTask(btn.replace("btnDashComplete_", ""))
    }
    else if (btn.startsWith("btnSnooze_")) {
        snoozeTask(btn.replace("btnSnooze_", ""))
    }
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
    
    // --- NEW: BUTLER INTEGRATION DATA ---
    def overdueTasks = []
    def maxOverdueDays = 0
    // ------------------------------------

    getTaskList().each { t ->
        if (settings["enable_${t.id}"]) {
            def tData = state.taskData[t.id]
            if (tData) {
                def isSnoozed = (tData.snoozedUntil != null && tData.snoozedUntil > now())
                def interval = (settings["interval_${t.id}"] ?: t.defaultDays).toInteger()
                def dueDateMs = tData.lastCompleted + ((long)interval * 86400000L)
                def remainingDays = Math.ceil((dueDateMs - now()) / 86400000.0).toInteger()
                enabledTasks << [name: t.name, days: remainingDays, snoozed: isSnoozed]
                
                // --- NEW: GATHER OVERDUE METRICS ---
                if (remainingDays < 0 && !isSnoozed) {
                    overdueTasks << t.name
                    if (Math.abs(remainingDays) > maxOverdueDays) maxOverdueDays = Math.abs(remainingDays)
                }
                // -----------------------------------
            }
        }
    }
    
    // --- NEW: PUBLISH TO DEVICE FOR BUTLER TO READ ---
    // Added a ternary operator to output "None" instead of "" when the array is empty.
    // Also explicitly setting isStateChange to true to force UI/Device refresh.
    def overdueStr = overdueTasks.size() > 0 ? overdueTasks.join(", ") : "None"
    child.sendEvent(name: "overdueTasks", value: overdueStr, isStateChange: true)
    child.sendEvent(name: "maxOverdueDays", value: maxOverdueDays, isStateChange: true)
    // -------------------------------------------------
    
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
                if (isSnoozed) return // Skip notifying for snoozed items
                
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

def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29] 
    state.actionHistory = h 
}

def logInfo(msg) { if(txtEnable) log.info "${app.label}: ${msg}" }

// ==============================================================================
// MAINTENANCE TASK DATABASE
// ==============================================================================
def getTaskList() {
    return [
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
        [id: "e13", category: "Exterior", name: "Inspect Exterior Flashing", defaultDays: 365, description: "Check caulk around dryer vents and outdoor pipes.", consumable: true, estCost: 8.00]
    ]
}
