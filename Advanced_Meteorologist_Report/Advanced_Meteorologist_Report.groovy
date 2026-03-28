/**
 * Advanced Meteorologist Report
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Meteorologist Report",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Generates a TV-anchor style meteorologist report combining live local weather station data with a free macro-forecast API. Features dual time-profiles, 7-day tracking, and granular time/mode triggers.",
    category: "Weather",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Meteorologist Report</b>", install: true, uninstall: true) {
        
        section("<b>Live Weather & 7-Day Forecast Dashboard</b>") {
            input "btnRefresh", "button", title: "🔄 Refresh API & Local Data"
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Displays your local weather station data alongside the 7-day macro-forecast pulled automatically from the free Open-Meteo API.</div>"
            
            def reportStatus = state.lastReportTime ? "Last script generated at ${state.lastReportTime}" : "Waiting for initial data..."
            paragraph "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff;'><b>System Status:</b> ${reportStatus}</div>"

            if (localStation) {
                // Local Data
                def lTemp = localStation.currentValue("temperature") ?: "--"
                def lHum = localStation.currentValue("humidity") ?: "--"
                def lWind = localStation.currentValue("windSpeed") ?: "--"
                
                // API Current Data
                def apiTemp = state.apiCurrentTemp ?: "--"
                def apiCondition = state.apiConditionDesc ?: "Unknown"

                def dashHTML = """
                <style>
                    .dash-table { width: 100%; border-collapse: collapse; font-size: 14px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                    .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                    .dash-table th { background-color: #343a40; color: white; }
                    .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 28%; }
                    .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: center !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                    .dash-val { text-align: left !important; padding-left: 15px !important; }
                    .day-box { font-size: 12px; line-height: 1.4; }
                </style>
                <table class="dash-table">
                    <thead><tr><th>Metric</th><th>Local Micro-Climate (Your Station)</th><th>Macro Forecast (API)</th></tr></thead>
                    <tbody>
                        <tr><td class="dash-hl">Current Temp</td><td><b>${lTemp}°</b></td><td>${apiTemp}°</td></tr>
                        <tr><td class="dash-hl">Humidity / Wind</td><td><b>${lHum}% / ${lWind} mph</b></td><td>--</td></tr>
                        <tr><td class="dash-hl">Conditions</td><td colspan="2" class="dash-val"><b>${apiCondition}</b></td></tr>
                    </tbody>
                </table>
                """
                paragraph dashHTML
                
                // 7-Day Forecast Table
                if (state.apiDailyDates && state.apiDailyDates.size() > 0) {
                    def forecastHTML = "<table class='dash-table'><thead><tr><td colspan='7' class='dash-subhead'>7-Day Outlook</td></tr><tr>"
                    
                    // Headers (Days)
                    state.apiDailyDates.each { dStr -> forecastHTML += "<th>${getDayOfWeek(dStr)}</th>" }
                    forecastHTML += "</tr></thead><tbody><tr>"
                    
                    // Data (High/Low/Cond)
                    state.apiDailyDates.eachWithIndex { dStr, i ->
                        def h = state.apiDailyHighs[i]
                        def l = state.apiDailyLows[i]
                        def c = state.apiDailyConditions[i]?.capitalize()
                        forecastHTML += "<td class='day-box'><span style='color:#d9534f; font-weight:bold;'>H: ${h}°</span><br><span style='color:#007bff; font-weight:bold;'>L: ${l}°</span><br><i>${c}</i></td>"
                    }
                    forecastHTML += "</tr></tbody></table>"
                    paragraph forecastHTML
                }

                if (state.latestScript) {
                    paragraph "<b>📝 Latest TV-Anchor Script Generated:</b>"
                    paragraph "<div style='font-size: 14px; font-style: italic; background-color: #fdfd96; padding: 10px; border-radius: 5px; border: 1px solid #e1e182;'>\"${state.latestScript}\"</div>"
                }

            } else {
                paragraph "<i>Please select your personal weather station below to populate the dashboard.</i>"
            }
        }

        section("<b>Recent Action History</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a rolling log of API fetches, trigger events, and script generation.</div>"
            input "txtEnable", "bool", title: "Enable Description Text Logging", defaultValue: true
            if (state.actionHistory) {
                def historyStr = state.actionHistory.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${historyStr}</span>"
            }
            input "btnClearHistory", "button", title: "Clear History"
        }
        
        section("<b>Test Outputs & Device Management</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Manually force broadcasts, or recreate your dashboard device if it was accidentally deleted.</div>"
            input "btnTestTTS", "button", title: "🔊 Test TTS Broadcast"
            input "btnTestNotify", "button", title: "📱 Test Push Notification"
            input "btnCreateDevice", "button", title: "⚙️ Create & Sync Child Device"
        }

        section("<b>1. Data Sources</b>") {
            input "localStation", "capability.temperatureMeasurement", title: "Select Personal Weather Station", required: true, multiple: false
            input "pollingInterval", "enum", title: "Background Sync Interval (Updates Device)", options: ["15":"Every 15 Mins", "30":"Every 30 Mins", "60":"Every 1 Hour"], defaultValue: "30"
        }

        section("<b>2. Morning Report Profile</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Configures what the anchor talks about during the morning hours. Usually focused on the day ahead.</div>"
            input "morningStartTime", "time", title: "Morning Window Start", required: false
            input "morningEndTime", "time", title: "Morning Window End", required: false
            input "m_includeGreeting", "bool", title: "Include Greeting (Good Morning)", defaultValue: true
            input "m_includeMicro", "bool", title: "Include Local Station Data", defaultValue: true
            input "m_includeDaily", "bool", title: "Include Today's Highs/Lows/Conditions", defaultValue: true
            input "m_includeWeekly", "bool", title: "Include Upcoming Week Teaser", defaultValue: false
        }
        
        section("<b>3. Evening/Night Report Profile</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Configures what the anchor talks about during the evening. Usually focused on overnight lows and tomorrow.</div>"
            input "eveningStartTime", "time", title: "Evening Window Start", required: false
            input "eveningEndTime", "time", title: "Evening Window End", required: false
            input "e_includeGreeting", "bool", title: "Include Greeting (Good Evening)", defaultValue: true
            input "e_includeMicro", "bool", title: "Include Local Station Data", defaultValue: true
            input "e_includeDaily", "bool", title: "Include Tonight's Low & Tomorrow's Forecast", defaultValue: true
            input "e_includeWeekly", "bool", title: "Include Upcoming Week Teaser", defaultValue: true
        }

        section("<b>4. Broadcast Triggers</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>When should the report be announced automatically? Choose between scheduled times, mode changes, or a manual switch. All are optional.</div>"
            
            paragraph "<b>🕒 Scheduled Time Triggers</b>"
            paragraph "<i>Broadcast the report at specific times of the day.</i>"
            input "timeTrigger1", "time", title: "Time Trigger 1", required: false
            input "timeTrigger2", "time", title: "Time Trigger 2", required: false
            input "timeTrigger3", "time", title: "Time Trigger 3", required: false

            paragraph "<hr>"
            paragraph "<b>🏠 Mode-Based Triggers</b>"
            paragraph "<i>Broadcast the report when your home changes modes (e.g., waking up, arriving home). You can restrict these with allowable time windows.</i>"
            
            input "triggerMode1", "mode", title: "Mode Change To:", required: false, multiple: false
            input "t1StartTime", "time", title: "Allowable Start Time", required: false
            input "t1EndTime", "time", title: "Allowable End Time", required: false
            
            input "triggerMode2", "mode", title: "Mode Change To:", required: false, multiple: false
            input "t2StartTime", "time", title: "Allowable Start Time", required: false
            input "t2EndTime", "time", title: "Allowable End Time", required: false
            
            input "triggerMode3", "mode", title: "Mode Change To:", required: false, multiple: false
            input "t3StartTime", "time", title: "Allowable Start Time", required: false
            input "t3EndTime", "time", title: "Allowable End Time", required: false
            
            paragraph "<hr>"
            paragraph "<b>🔘 Manual Switch Trigger</b>"
            input "triggerSwitch", "capability.switch", title: "Trigger via Virtual Switch (Turns off automatically)", required: false, multiple: false
        }

        section("<b>5. Outputs (Speakers & Notifications)</b>") {
            input "ttsSpeakers", "capability.speechSynthesis", title: "Select TTS Speakers", required: false, multiple: true
            input "notifyDevices", "capability.notification", title: "Select Notification Devices", required: false, multiple: true
        }
    }
}

def installed() { initialize() }
def updated() { unsubscribe(); unschedule(); initialize() }

def initialize() {
    createChildDevice()
    if (!state.actionHistory) state.actionHistory = []
    
    // Subscriptions
    subscribe(location, "mode", modeChangeHandler)
    if (triggerSwitch) subscribe(triggerSwitch, "switch.on", switchTriggerHandler)
    
    // Scheduled Time Triggers
    if (timeTrigger1) schedule(timeTrigger1, timeTrigger1Handler)
    if (timeTrigger2) schedule(timeTrigger2, timeTrigger2Handler)
    if (timeTrigger3) schedule(timeTrigger3, timeTrigger3Handler)

    // Background Polling & Device Sync Schedule
    def interval = pollingInterval ?: "30"
    if (interval == "15") runEvery15Minutes(routineSync)
    else if (interval == "30") runEvery30Minutes(routineSync)
    else if (interval == "60") runEvery1Hour(routineSync)

    routineSync() // Initial pull and sync on setup
    logAction("App Initialized. Advanced Meteorologist Report Ready.")
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        routineSync()
        logAction("Data manually refreshed via dashboard.")
    }
    else if (btn == "btnClearHistory") {
        state.actionHistory = []
        log.info "Advanced Meteorologist Report: Action history cleared."
    }
    else if (btn == "btnTestTTS") {
        routineSync()
        if (ttsSpeakers) ttsSpeakers.speak(state.latestScript)
        logAction("Test TTS Triggered: ${state.latestScript}")
    }
    else if (btn == "btnTestNotify") {
        routineSync()
        if (notifyDevices) notifyDevices.deviceNotification(state.latestScript)
        logAction("Test Notification Triggered: ${state.latestScript}")
    }
    else if (btn == "btnCreateDevice") {
        createChildDevice()
        routineSync()
        logAction("Child device manually created and synced.")
    }
}

// ==============================================================================
// BACKGROUND ROUTINE
// ==============================================================================

def routineSync() {
    fetchApiData()
    generateScript()
    updateChildDevice()
    logAction("Routine Background Sync: Data fetched and device updated.")
}

// ==============================================================================
// TRIGGER HANDLERS
// ==============================================================================

def timeTrigger1Handler() {
    logAction("Scheduled Time Trigger 1 activated. Broadcasting Meteorologist Report.")
    executeBroadcast()
}

def timeTrigger2Handler() {
    logAction("Scheduled Time Trigger 2 activated. Broadcasting Meteorologist Report.")
    executeBroadcast()
}

def timeTrigger3Handler() {
    logAction("Scheduled Time Trigger 3 activated. Broadcasting Meteorologist Report.")
    executeBroadcast()
}

def modeChangeHandler(evt) {
    def newMode = evt.value
    def nowTime = new Date()
    
    def shouldTrigger = false
    def triggeredBy = ""
    
    if (triggerMode1 && newMode == triggerMode1) {
        if (!t1StartTime || !t1EndTime || timeOfDayIsBetween(t1StartTime, t1EndTime, nowTime, location.timeZone)) {
            shouldTrigger = true
            triggeredBy = "Trigger 1 (${newMode})"
        } else {
            logAction("Mode changed to ${newMode} (Trigger 1), but outside allowable time frame. Ignored.")
        }
    }
    else if (triggerMode2 && newMode == triggerMode2) {
        if (!t2StartTime || !t2EndTime || timeOfDayIsBetween(t2StartTime, t2EndTime, nowTime, location.timeZone)) {
            shouldTrigger = true
            triggeredBy = "Trigger 2 (${newMode})"
        } else {
            logAction("Mode changed to ${newMode} (Trigger 2), but outside allowable time frame. Ignored.")
        }
    }
    else if (triggerMode3 && newMode == triggerMode3) {
        if (!t3StartTime || !t3EndTime || timeOfDayIsBetween(t3StartTime, t3EndTime, nowTime, location.timeZone)) {
            shouldTrigger = true
            triggeredBy = "Trigger 3 (${newMode})"
        } else {
            logAction("Mode changed to ${newMode} (Trigger 3), but outside allowable time frame. Ignored.")
        }
    }
    
    if (shouldTrigger) {
        logAction("Valid mode change detected. Broadcasting Meteorologist Report via ${triggeredBy}.")
        executeBroadcast()
    }
}

def switchTriggerHandler(evt) {
    logAction("Virtual Switch triggered. Executing Meteorologist Report.")
    executeBroadcast()
    runIn(2, turnOffSwitch) // Auto-reset switch
}

def turnOffSwitch() { if (triggerSwitch) triggerSwitch.off() }

def executeBroadcast() {
    routineSync() // Ensure we have the absolute freshest data before speaking
    if (ttsSpeakers) ttsSpeakers.speak(state.latestScript)
    if (notifyDevices) notifyDevices.deviceNotification(state.latestScript)
}

// ==============================================================================
// DATA FETCHING & SCRIPT GENERATION
// ==============================================================================

def fetchApiData() {
    def lat = location.latitude
    def lon = location.longitude
    
    if (!lat || !lon) {
        logAction("ERROR: Hub latitude/longitude not set. Cannot fetch API data.")
        return
    }

    def url = "https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch&timezone=auto"
    
    try {
        httpGet([uri: url, timeout: 10]) { resp ->
            if (resp.success) {
                def data = resp.data
                // Current Data
                state.apiCurrentTemp = data.current?.temperature_2m?.toBigDecimal()?.setScale(0, BigDecimal.ROUND_HALF_UP)
                state.apiConditionDesc = getWeatherDescription(data.current?.weather_code ?: 0)
                
                // 7-Day Arrays
                state.apiDailyDates = data.daily?.time ?: []
                state.apiDailyHighs = data.daily?.temperature_2m_max?.collect { it.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP) } ?: []
                state.apiDailyLows = data.daily?.temperature_2m_min?.collect { it.toBigDecimal().setScale(0, BigDecimal.ROUND_HALF_UP) } ?: []
                state.apiDailyConditions = data.daily?.weather_code?.collect { getWeatherDescription(it) } ?: []
            }
        }
    } catch (e) {
        logAction("ERROR fetching weather API: ${e.message}")
    }
}

def generateScript() {
    def script = ""
    def nowTime = new Date()
    
    // Determine active profile
    def isMorning = morningStartTime && morningEndTime && timeOfDayIsBetween(morningStartTime, morningEndTime, nowTime, location.timeZone)
    def isEvening = eveningStartTime && eveningEndTime && timeOfDayIsBetween(eveningStartTime, eveningEndTime, nowTime, location.timeZone)
    
    def useGreeting = true; def useMicro = true; def useDaily = true; def useWeekly = false
    def activeProfileName = "Standard"
    
    if (isMorning) {
        useGreeting = m_includeGreeting; useMicro = m_includeMicro; useDaily = m_includeDaily; useWeekly = m_includeWeekly
        activeProfileName = "Morning"
    } else if (isEvening) {
        useGreeting = e_includeGreeting; useMicro = e_includeMicro; useDaily = e_includeDaily; useWeekly = e_includeWeekly
        activeProfileName = "Evening"
    }
    
    // 1. Greeting
    if (useGreeting) {
        def hour = nowTime.format("HH", location.timeZone).toInteger()
        def greeting = "Good evening"
        if (hour >= 4 && hour < 12) greeting = "Good morning"
        else if (hour >= 12 && hour < 17) greeting = "Good afternoon"
        script += "${greeting}! "
    }
    
    // 2. Micro-Climate (User Station)
    if (useMicro && localStation) {
        def lTemp = localStation.currentValue("temperature") ?: "unknown"
        def lHum = localStation.currentValue("humidity") ?: "unknown"
        script += "Right now at your house, it is currently ${lTemp} degrees with a humidity of ${lHum} percent. "
    }
    
    // 3. Macro Forecast (API)
    if (useDaily && state.apiDailyDates && state.apiDailyDates.size() > 1) {
        def cond = state.apiDailyConditions[0] ?: "varying conditions"
        def tHigh = state.apiDailyHighs[0] ?: "unknown"
        def tLow = state.apiDailyLows[0] ?: "unknown"
        def tomorrowHigh = state.apiDailyHighs[1] ?: "unknown"
        def tomorrowCond = state.apiDailyConditions[1] ?: "varying conditions"

        if (activeProfileName == "Evening") {
            script += "Overnight, expect ${cond} as temperatures drop to a low of ${tLow}. Looking ahead to tomorrow, we will see ${tomorrowCond} with a high of ${tomorrowHigh}. "
        } else {
            script += "Looking at the broader forecast, expect ${cond} today. We will reach a high of ${tHigh}, and drop to a low of ${tLow} tonight. "
        }
    }
    
    // 4. Weekly Teaser
    if (useWeekly && state.apiDailyDates && state.apiDailyDates.size() >= 5) {
        def day4Name = getDayOfWeek(state.apiDailyDates[3])
        def day4High = state.apiDailyHighs[3]
        def day4Cond = state.apiDailyConditions[3]
        script += "Keep an eye out as we move through the week, ${day4Name} is looking to be ${day4Cond} with temperatures around ${day4High} degrees."
    }

    state.latestScript = script
    state.lastReportTime = nowTime.format("MM/dd hh:mm a", location.timeZone)
    return script
}

// Translates WMO Weather codes from Open-Meteo to human phrases
def getWeatherDescription(code) {
    def map = [
        0: "clear skies", 1: "mostly clear skies", 2: "partly cloudy skies", 3: "overcast conditions",
        45: "foggy conditions", 48: "depositing rime fog", 51: "light drizzle", 53: "moderate drizzle", 
        55: "dense drizzle", 61: "slight rain", 63: "moderate rain", 65: "heavy rain",
        71: "slight snow fall", 73: "moderate snow fall", 75: "heavy snow fall",
        77: "snow grains", 80: "light rain showers", 81: "moderate rain showers", 82: "violent rain showers",
        95: "thunderstorms", 96: "thunderstorms with slight hail", 99: "thunderstorms with heavy hail"
    ]
    return map[code as Integer] ?: "variable conditions"
}

// Converts YYYY-MM-DD to standard Day name (e.g., "Mon", "Tue")
def getDayOfWeek(dateString) {
    try {
        def date = Date.parse("yyyy-MM-dd", dateString)
        return date.format("EEE")
    } catch (e) {
        return "Unknown"
    }
}

// Custom Logging function to populate Action History array
def logAction(msg) { 
    if(txtEnable) log.info "${app.label}: ${msg}"
    def h = state.actionHistory ?: []
    h.add(0, "[${new Date().format("MM/dd hh:mm a", location.timeZone)}] ${msg}")
    if(h.size() > 30) h = h[0..29]
    state.actionHistory = h 
}

// ==============================================================================
// CHILD DEVICE MANAGEMENT
// ==============================================================================

def createChildDevice() {
    def childNetworkId = "MeteorologistReport_${app.id}"
    def child = getChildDevice(childNetworkId)
    if (!child) {
        log.info "Creating Meteorologist Report Child Device..."
        try {
            addChildDevice("ShaneAllen", "Advanced Meteorologist Report Device", childNetworkId, [name: "Advanced Meteorologist Report", isComponent: true])
        } catch (e) {
            logAction("ERROR: Failed to create child device. Ensure the Driver is installed. Error: ${e}")
        }
    }
}

def updateChildDevice() {
    def childNetworkId = "MeteorologistReport_${app.id}"
    def child = getChildDevice(childNetworkId)
    if (child) {
        child.updateTile(
            state.latestScript, 
            state.apiCurrentTemp, 
            state.apiConditionDesc, 
            state.apiDailyDates, 
            state.apiDailyHighs, 
            state.apiDailyLows, 
            state.apiDailyConditions
        )
    }
}
