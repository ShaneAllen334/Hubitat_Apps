/**
 * Advanced Meteorologist Report
 *
 * Author: ShaneAllen
 */

definition(
    name: "Advanced Meteorologist Report",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Generates a TV-anchor style meteorologist report combining live local weather station data with a free macro-forecast API. Features dual time-profiles and 7-day tracking.",
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
        
        section("<b>Test Outputs</b>") {
            paragraph "<div style='font-size:13px; color:#555;'>Manually force the generation and broadcast of the script to test your devices.</div>"
            input "btnTestTTS", "button", title: "🔊 Test TTS Broadcast"
            input "btnTestNotify", "button", title: "📱 Test Push Notification"
        }

        section("<b>1. Data Sources</b>") {
            input "localStation", "capability.temperatureMeasurement", title: "Select Personal Weather Station", required: true, multiple: false
            input "pollingInterval", "enum", title: "API Refresh Interval", options: ["15":"Every 15 Mins", "30":"Every 30 Mins", "60":"Every 1 Hour"], defaultValue: "30"
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
            paragraph "<div style='font-size:13px; color:#555;'>When should the report be announced automatically?</div>"
            input "triggerModes", "mode", title: "Trigger on Mode Change To:", multiple: true, required: false
            input "triggerMotion", "capability.motionSensor", title: "Trigger on First Motion (Requires reset switch or daily reset)", required: false, multiple: false
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
    state.hasTriggeredMotionToday = false
    
    // Subscriptions
    if (triggerModes) subscribe(location, "mode", modeChangeHandler)
    if (triggerSwitch) subscribe(triggerSwitch, "switch.on", switchTriggerHandler)
    if (triggerMotion) subscribe(triggerMotion, "motion.active", motionTriggerHandler)
    
    // Daily Reset for Motion Trigger
    schedule("0 0 0 * * ?", resetDailyMotion)
    
    // API Polling Schedule
    def interval = pollingInterval ?: "30"
    if (interval == "15") runEvery15Minutes(fetchApiData)
    else if (interval == "30") runEvery30Minutes(fetchApiData)
    else if (interval == "60") runEvery1Hour(fetchApiData)

    fetchApiData() // Initial pull
}

def appButtonHandler(btn) {
    if (btn == "btnRefresh") {
        fetchApiData()
        generateScript()
        updateChildDevice()
        log.info "Advanced Meteorologist Report: Data manually refreshed."
    }
    else if (btn == "btnTestTTS") {
        fetchApiData()
        def script = generateScript()
        if (ttsSpeakers) ttsSpeakers.speak(script)
        log.info "Test TTS Triggered: ${script}"
        updateChildDevice()
    }
    else if (btn == "btnTestNotify") {
        fetchApiData()
        def script = generateScript()
        if (notifyDevices) notifyDevices.deviceNotification(script)
        log.info "Test Notification Triggered: ${script}"
        updateChildDevice()
    }
}

// ==============================================================================
// TRIGGER HANDLERS
// ==============================================================================

def modeChangeHandler(evt) {
    if ((triggerModes as List)?.contains(evt.value)) {
        log.info "Mode changed to ${evt.value}. Triggering Meteorologist Report."
        executeBroadcast()
    }
}

def switchTriggerHandler(evt) {
    log.info "Virtual Switch triggered. Executing Meteorologist Report."
    executeBroadcast()
    runIn(2, turnOffSwitch) // Auto-reset switch
}

def turnOffSwitch() { if (triggerSwitch) triggerSwitch.off() }

def motionTriggerHandler(evt) {
    if (!state.hasTriggeredMotionToday) {
        state.hasTriggeredMotionToday = true
        log.info "First motion detected. Triggering Meteorologist Report."
        executeBroadcast()
    }
}

def resetDailyMotion() { state.hasTriggeredMotionToday = false }

def executeBroadcast() {
    fetchApiData() 
    def script = generateScript()
    
    if (ttsSpeakers) ttsSpeakers.speak(script)
    if (notifyDevices) notifyDevices.deviceNotification(script)
    
    updateChildDevice()
}

// ==============================================================================
// DATA FETCHING & SCRIPT GENERATION
// ==============================================================================

def fetchApiData() {
    def lat = location.latitude
    def lon = location.longitude
    
    if (!lat || !lon) {
        log.error "Hub latitude/longitude not set. Cannot fetch API data."
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
                
                log.info "Weather API Fetch Successful. 7-Day data stored."
            }
        }
    } catch (e) {
        log.error "Error fetching weather API: ${e.message}"
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
            log.error "Failed to create child device. Ensure the Driver is installed. Error: ${e}"
        }
    }
}

def updateChildDevice() {
    def childNetworkId = "MeteorologistReport_${app.id}"
    def child = getChildDevice(childNetworkId)
    if (child) {
        def high = state.apiDailyHighs ? state.apiDailyHighs[0] : null
        def low = state.apiDailyLows ? state.apiDailyLows[0] : null
        child.updateTile(state.latestScript, high, low, state.apiConditionDesc)
    }
}
