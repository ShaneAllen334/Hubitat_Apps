/**
 * Advanced School Pickup and Drop Off
 */
definition(
    name: "Advanced School Pickup and Drop Off",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Advanced bus indicator with weather warnings, safe arrival, departure tracking, full-day sick mode, mode restrictions, smart iCal automation, Master Kill Switch, and Smart Learning.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Advanced School Pickup & Drop Off", install: true, uninstall: true) {
        
        section("Live Tracker & Averages") {
            def amCountdown = getCountdownText(settings.amStage3, false)
            def pmCountdown = getCountdownText(settings.pmStage3, true)
            
            // Safe Integer Math to prevent BigDecimal Modulo crashes
            long totalAm = (state.amTotalDoorTime ?: 0) as Long
            long countAm = (state.amDoorCount ?: 0) as Long
            def amAvg = "N/A"
            if (countAm > 0) {
                long avgAmSecs = totalAm.intdiv(countAm)
                amAvg = "${avgAmSecs.intdiv(60)}m ${avgAmSecs % 60}s"
            }

            long totalPm = (state.pmTotalDoorTime ?: 0) as Long
            long countPm = (state.pmDoorCount ?: 0) as Long
            def pmAvg = "N/A"
            if (countPm > 0) {
                long avgPmSecs = totalPm.intdiv(countPm)
                pmAvg = "${avgPmSecs.intdiv(60)}m ${avgPmSecs % 60}s"
            }

            // Smart Learning Progress Tracking
            int reqDays = settings.minLearningDays != null ? settings.minLearningDays as int : 10
            int amPts = state.amLearnedTimes?.size() ?: 0
            int pmPts = state.pmLearnedTimes?.size() ?: 0

            if (settings.enableSmartLearning) {
                if (amPts >= reqDays) {
                    amAvg += "<br><span style='color: #0066cc; font-size: 11px;'><b>Auto-Shift: ${getSmartOffsetStr(true)}</b> (${amPts}/${reqDays} met)</span>"
                } else {
                    amAvg += "<br><span style='color: #888888; font-size: 11px;'><i>Learning: ${amPts}/${reqDays} days</i></span>"
                }
                
                if (pmPts >= reqDays) {
                    pmAvg += "<br><span style='color: #0066cc; font-size: 11px;'><b>Auto-Shift: ${getSmartOffsetStr(false)}</b> (${pmPts}/${reqDays} met)</span>"
                } else {
                    pmAvg += "<br><span style='color: #888888; font-size: 11px;'><i>Learning: ${pmPts}/${reqDays} days</i></span>"
                }
            }
            
            def dashText = "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc;'>"
            dashText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Event Status</th><th style='padding: 8px;'>Stage 3 Countdown</th><th style='padding: 8px;'>Avg Door Time</th></tr>"
            dashText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Morning Pickup</b></td><td style='padding: 8px; color:#aa0000;'><b>${amCountdown}</b></td><td style='padding: 8px;'>${amAvg}</td></tr>"
            dashText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Afternoon Drop-off</b></td><td style='padding: 8px; color:#aa0000;'><b>${pmCountdown}</b></td><td style='padding: 8px;'>${pmAvg}</td></tr>"
            dashText += "</table>"
            
            def sysStatus = "ACTIVE"
            def statusColor = "green"
        
            if (!isSystemEnabled()) { sysStatus = "PAUSED (Master Switch OFF)"; statusColor = "red" }
            else if (!isSchoolDay()) { sysStatus = "NO SCHOOL TODAY"; statusColor = "#888" }
            else if (isSickDay()) { sysStatus = "SICK DAY (Skipping Routines)"; statusColor = "#ff8c00" }
            else if (state.earlyArrivalDetected) { sysStatus = "EARLY ARRIVAL DETECTED (PM Skipped)"; statusColor = "#1e90ff" }

            dashText += "<div style='margin-top: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            dashText += "<b>System State:</b> <span style='color: ${statusColor}; font-weight: bold;'>${sysStatus}</span></div>"

            // --- UI 3-Day Forecast Engine ---
            if (state.calendarForecastHtml) {
                dashText += state.calendarForecastHtml
            }

            // --- UI Logging Engine ---
            def historyText = "<div style='margin-top: 10px; padding: 10px; background: #fff; border-radius: 4px; font-size: 12px; border: 1px solid #ccc; max-height: 180px; overflow-y: auto;'>"
            historyText += "<h4 style='margin-top: 0px; margin-bottom: 5px; color: #333;'>System Log & Recorded History</h4>"
            if (state.actionHistory && state.actionHistory.size() > 0) {
                historyText += "<ul style='margin: 0; padding-left: 20px; color: #555;'>"
                state.actionHistory.each { entry -> historyText += "<li style='margin-bottom: 4px;'>${entry}</li>" }
                historyText += "</ul>"
            } else {
                historyText += "<i style='color: #888;'>No recent activity logged. Waiting for events...</i>"
            }
            historyText += "</div>"
            dashText += historyText
            // -----------------------

            paragraph dashText
            
            input "forceSyncBtn", "button", title: "Force Sync Calendar Now"
            input "resetStatsBtn", "button", title: "Reset Average Statistics"
            input "clearLogBtn", "button", title: "Clear System Log"
        }

        section("1. Master Control & Conditions", hideable: true, hidden: true) {
            paragraph "MASTER SWITCH: If this switch is selected and turned OFF, the entire application is completely disabled."
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false

            input "schoolDaySwitch", "capability.switch", title: "School Day Virtual Switch", required: true
            input "enableCalendar", "bool", title: "Auto-update switch via calendar?", defaultValue: true
            
            input "iCalUrl", "text", title: "iCal Feed URL (https://...)", defaultValue: "https://www.elmoreco.com/sndreq/generateCalendarICS.php?calendar_id=145095", required: false
            input "holidayKeywords", "text", title: "Keywords indicating NO school", defaultValue: "spring break, teacher workday, juneteenth, no school, no students, holiday, closed, staff development, professional development, weather, e-learning, elearning, thanksgiving, christmas, winter break, fall break, presidents day, mlk", required: false

            input "allowedModes", "mode", title: "Allowed Location Modes", multiple: true, required: false
            input "sickDaySwitch", "capability.switch", title: "Sick Day Virtual Switch", required: false
            input "busLight", "capability.colorControl", title: "Select Hue Light", required: true
        }

        section("2. Weather & Umbrella Warning", hideable: true, hidden: true) {
            input "weatherDevice", "capability.sensor", title: "Weather Sensor (e.g., OpenWeatherMap)", required: false
            input "rainCondition", "text", title: "Weather condition indicating rain", defaultValue: "Rain"
            input "rainSwitch", "capability.switch", title: "Rain Virtual Switch", required: false
            input "sprinklingSwitch", "capability.switch", title: "Sprinkling Virtual Switch", required: false
        }

        section("3. Notifications & Audio", hideable: true, hidden: true) {
            input "notifyPhones", "capability.notification", title: "Send Push Alerts to Phones", multiple: true, required: false
            
            paragraph "<b>Custom Notification Messages</b>"
            input "amImminentMsg", "text", title: "AM Imminent Message", defaultValue: "The school bus is imminent! Time to head to the door.", required: true
            input "pmArrivedMsg", "text", title: "PM Arrived Message", defaultValue: "The school bus has arrived at the afternoon stop.", required: true
            input "amMissedMsg", "text", title: "AM Missed Bus Message", defaultValue: "WARNING: The bus arrived, but the door/motion conditions were not met!", required: true
            input "pmMissedMsg", "text", title: "PM Missed Bus Message", defaultValue: "CRITICAL: The school bus dropped off, but the front door hasn't opened!", required: true
            input "notifyOnSave", "bool", title: "Send Push Notification when safe arrival/departure is saved? (Includes recorded time)", defaultValue: true
            
            input "ttsDevice", "capability.speechSynthesis", title: "Global Audio Announcement Device (Echo/Sonos)", required: false
            input "audioAllowedModes", "mode", title: "Modes for Audio Announcements", multiple: true, required: false
            input "audioMotionTimeout", "number", title: "Audio Motion Timeout (Minutes)", defaultValue: 5, description: "Time to wait after motion stops before muting a room's announcements (prevents muting if someone is sitting still watching TV)."
            
            paragraph "<b>1-to-1 Room Mapping for Zooz Speakers</b><br>Pair a speaker with a motion sensor. If a motion sensor is selected, the paired speaker will ONLY play if there was recent motion in that specific room."
            
            input "alwaysOnRoom", "enum", title: "Select ONE room to ALWAYS announce (Ignores motion)", options: ["1": "Room 1", "2": "Room 2", "3": "Room 3", "4": "Room 4", "5": "Room 5", "6": "Room 6", "7": "Room 7"], required: false
            
            input "room1Speaker", "capability.actuator", title: "Room 1 Speaker", required: false
            input "room1Motion", "capability.motionSensor", title: "Room 1 Motion Sensor", required: false
            
            input "room2Speaker", "capability.actuator", title: "Room 2 Speaker", required: false
            input "room2Motion", "capability.motionSensor", title: "Room 2 Motion Sensor", required: false
            
            input "room3Speaker", "capability.actuator", title: "Room 3 Speaker", required: false
            input "room3Motion", "capability.motionSensor", title: "Room 3 Motion Sensor", required: false
            
            input "room4Speaker", "capability.actuator", title: "Room 4 Speaker", required: false
            input "room4Motion", "capability.motionSensor", title: "Room 4 Motion Sensor", required: false
            
            input "room5Speaker", "capability.actuator", title: "Room 5 Speaker", required: false
            input "room5Motion", "capability.motionSensor", title: "Room 5 Motion Sensor", required: false
            
            input "room6Speaker", "capability.actuator", title: "Room 6 Speaker", required: false
            input "room6Motion", "capability.motionSensor", title: "Room 6 Motion Sensor", required: false
            
            input "room7Speaker", "capability.actuator", title: "Room 7 Speaker", required: false
            input "room7Motion", "capability.motionSensor", title: "Room 7 Motion Sensor", required: false
        }

        section("4. Safe Arrival & Departure (Sensors)", hideable: true, hidden: true) {
            input "doorSensor", "capability.contactSensor", title: "Front Door Sensor", required: false
            input "motionSensor", "capability.motionSensor", title: "Outside Drop Location Motion Sensor (Optional)", required: false
            input "luxThreshold", "number", title: "Max Lux Threshold for Motion", defaultValue: 1000, description: "If the motion sensor reports lux higher than this, it will be ignored and the app will rely solely on the door sensor."
            input "ignorePmMotion", "bool", title: "Ignore motion requirement in the afternoon?", defaultValue: false, description: "Enable this if your sensor only reports motion in the dark."
            input "departureTimeout", "number", title: "Minutes after AM Stage 3 before 'Missed Bus' alert", defaultValue: 5
            input "arrivalTimeout", "number", title: "Minutes to wait for door/motion before afternoon alert", defaultValue: 15
        }

        section("5. Early Dismissal & Lock Overrides", hideable: true, hidden: true) {
            input "earlyDismissalSwitch", "capability.switch", title: "Early Dismissal Virtual Switch", required: false
            input "earlyOffset", "number", title: "Minutes to shift afternoon schedule earlier", defaultValue: 120
            
            paragraph "<b>Smart Lock Early Arrival Detection</b><br>If a designated code is entered after 12:00 PM, the system will assume an early release and automatically cancel the afternoon bus routines."
            input "frontDoorLock", "capability.lock", title: "Select Door Lock", required: false, submitOnChange: true
            
            if (frontDoorLock) {
                def lockCodes = getLockCodesMap(frontDoorLock)
                if (lockCodes) {
                    input "kidLockCode", "enum", title: "Select the Lock Code to trigger Early Arrival", options: lockCodes, required: false
                } else {
                    paragraph "<i>No lock codes found or the lock does not support code reporting.</i>"
                }
            }
        }

        section("6. Dynamic Lighting Effects", hideable: true, hidden: true) {
            input "enableBlinking", "bool", title: "Enable Proximity Blinking", defaultValue: false, description: "If enabled, the selected Hue light will blink, increasing in speed as the next stage time approaches."
        }

        section("7. System Testing", hideable: true, hidden: true) {
            paragraph "Use these buttons to force a simulated rapid run of the routines. Stages will cycle every 30 seconds."
            input "testAmBtn", "button", title: "Test Morning Pickup Routine"
            input "testPmBtn", "button", title: "Test Afternoon Drop-Off Routine"
            input "stopTestBtn", "button", title: "Stop Active Test & Turn Off Light"
        }
        
        section("8. Integration & External Overrides", hideable: true, hidden: true) {
            paragraph "<b>Freeze other applications during sequence</b><br>If you are using shared lights for your bus indicator (such as a porch light managed by a motion lighting app), those other apps might try to turn the light off while the bus notification is active.<br><br>Select a Virtual Switch here. This app will turn it ON during an active bus sequence. You can use that switch in your other apps to 'freeze' or 'disable' them until the sequence completes."
            input "overrideSwitch", "capability.switch", title: "State Override Switch (Freezes external apps)", required: false
        }

        section("9. Smart Learning (Auto-Adjust Times)", hideable: true, hidden: true) {
            input "enableSmartLearning", "bool", title: "Enable Smart Time Learning", defaultValue: false, description: "Tracks actual departure/arrival times and automatically shifts your stage schedules to dial in accuracy. Automatically resets every August."
            input "minLearningDays", "number", title: "Required school days before adjusting", defaultValue: 10
            
            if (enableSmartLearning) {
                def amPts = state.amLearnedTimes?.size() ?: 0
                def pmPts = state.pmLearnedTimes?.size() ?: 0
                def reqDays = settings.minLearningDays != null ? settings.minLearningDays as int : 10
                
                def amShift = amPts >= reqDays ? getSmartOffsetStr(true) : "Learning Phase"
                def pmShift = pmPts >= reqDays ? getSmartOffsetStr(false) : "Learning Phase"
                
                paragraph "<b>Learning Status:</b><br>AM Data Points: ${amPts}/${reqDays} (Current Shift: ${amShift})<br>PM Data Points: ${pmPts}/${reqDays} (Current Shift: ${pmShift})"
                input "resetLearningBtn", "button", title: "Reset Learned Data Now"
            }
        }

        section("Morning Pickup Schedule", hideable: true, hidden: true) {
            input "amStage1", "time", title: "Time for Stage 1 (Get Ready)", required: false
            input "amColor1", "enum", title: "Stage 1 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Green"
            input "amSound1", "number", title: "Stage 1 Sound File # (Optional)", required: false
            input "testAmSound1Btn", "button", title: "Test AM Stage 1 Sound"
            
            input "amStage2", "time", title: "Time for Stage 2 (Almost Here)", required: false
            input "amColor2", "enum", title: "Stage 2 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Yellow"
            input "amSound2", "number", title: "Stage 2 Sound File # (Optional)", required: false
            input "testAmSound2Btn", "button", title: "Test AM Stage 2 Sound"
            
            input "amStage3", "time", title: "Time for Stage 3 (Bus Imminent)", required: false
            input "amColor3", "enum", title: "Stage 3 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Red"
            input "amSound3", "number", title: "Stage 3 Sound File # (Optional)", required: false
            input "testAmSound3Btn", "button", title: "Test AM Stage 3 Sound"
            
            input "amClear", "time", title: "Time to turn light OFF", required: false
        }

        section("Afternoon Drop-Off Schedule", hideable: true, hidden: true) {
            input "pmStage1", "time", title: "Time for Stage 1 (Bus left school)", required: false
            input "pmColor1", "enum", title: "Stage 1 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Green"
            input "pmSound1", "number", title: "Stage 1 Sound File # (Optional)", required: false
            input "testPmSound1Btn", "button", title: "Test PM Stage 1 Sound"
            
            input "pmStage2", "time", title: "Time for Stage 2 (Approaching neighborhood)", required: false
            input "pmColor2", "enum", title: "Stage 2 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Yellow"
            input "pmSound2", "number", title: "Stage 2 Sound File # (Optional)", required: false
            input "testPmSound2Btn", "button", title: "Test PM Stage 2 Sound"
            
            input "pmStage3", "time", title: "Time for Stage 3 (At the stop)", required: false
            input "pmColor3", "enum", title: "Stage 3 Color", options: ["Red", "Green", "Blue", "Yellow", "Orange", "Purple", "Pink", "White"], defaultValue: "Red"
            input "pmSound3", "number", title: "Stage 3 Sound File # (Optional)", required: false
            input "testPmSound3Btn", "button", title: "Test PM Stage 3 Sound"
            
            input "pmClear", "time", title: "Time to turn light OFF", required: false
        }
    }
}

def installed() {
    log.debug "Installed Advanced School Pickup and Drop Off"
    initialize()
}

def updated() {
    log.debug "Updated Advanced School Pickup and Drop Off"
    unsubscribe()
    unschedule()
    
    initialize()
}

def initialize() {
    state.amTotalDoorTime = state.amTotalDoorTime ?: 0
    state.amDoorCount = state.amDoorCount ?: 0
    state.pmTotalDoorTime = state.pmTotalDoorTime ?: 0
    state.pmDoorCount = state.pmDoorCount ?: 0
    
    // Smart Learning state variables
    state.amLearnedTimes = state.amLearnedTimes ?: []
    state.pmLearnedTimes = state.pmLearnedTimes ?: []
    if (!state.lastResetYear) state.lastResetYear = new Date().format("yyyy").toInteger()
    
    state.isBlinking = false
    state.isTesting = false
    state.lightActiveByApp = state.lightActiveByApp ?: false 
    state.actionHistory = state.actionHistory ?: []
    state.calendarForecastHtml = state.calendarForecastHtml ?: ""
    state.earlyArrivalDetected = state.earlyArrivalDetected ?: false

    logAction("System Initialized/Updated.")

    if (settings.doorSensor) {
        subscribe(settings.doorSensor, "contact.open", doorOpenedHandler)
    }
    
    if (settings.motionSensor) {
        subscribe(settings.motionSensor, "motion.active", motionActiveHandler)
        subscribe(settings.motionSensor, "illuminance", luxHandler) // Dynamically dismiss motion if sun rises mid-routine
    }

    if (settings.frontDoorLock) {
        subscribe(settings.frontDoorLock, "lock.unlocked", lockHandler)
    }

    // Subscribe to specific room motion sensors
    if (settings.room1Motion) subscribe(settings.room1Motion, "motion.active", room1MotionHandler)
    if (settings.room2Motion) subscribe(settings.room2Motion, "motion.active", room2MotionHandler)
    if (settings.room3Motion) subscribe(settings.room3Motion, "motion.active", room3MotionHandler)
    if (settings.room4Motion) subscribe(settings.room4Motion, "motion.active", room4MotionHandler)
    if (settings.room5Motion) subscribe(settings.room5Motion, "motion.active", room5MotionHandler)
    if (settings.room6Motion) subscribe(settings.room6Motion, "motion.active", room6MotionHandler)
    if (settings.room7Motion) subscribe(settings.room7Motion, "motion.active", room7MotionHandler)

    if (settings.enableCalendar) {
        runEvery30Minutes("checkSchoolCalendar")
        runIn(5, "checkSchoolCalendar")
    }

    // New unified daily scheduling engine
    schedule("0 1 0 ? * *", "scheduleDailyEvents")
    scheduleDailyEvents() 
}

// --- Logging & Helper Methods ---

def logAction(String msg) {
    def timeString = new Date().format("MM/dd h:mm a", location.timeZone)
    def entry = "<b>${timeString}</b>: ${msg}"
    def list = state.actionHistory ?: []
    list.add(0, entry)
    if (list.size() > 10) list = list[0..9] 
    state.actionHistory = list
    log.info "UI LOG: ${msg}"
}

def isSystemEnabled() {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") == "off") return false
    return true
}

def isSchoolDay() {
    return settings.schoolDaySwitch?.currentValue("switch") == "on"
}

def isSickDay() {
    return settings.sickDaySwitch?.currentValue("switch") == "on"
}

def isAllowedMode() {
    if (!settings.allowedModes) return true
    return settings.allowedModes.contains(location.mode)
}

def isAudioAllowedMode() {
    if (!settings.audioAllowedModes) return true
    return settings.audioAllowedModes.contains(location.mode)
}

def isItRaining() {
    if (settings.rainSwitch && settings.rainSwitch.currentValue("switch") == "on") return true
    if (settings.sprinklingSwitch && settings.sprinklingSwitch.currentValue("switch") == "on") return true
    String currentCondition = settings.weatherDevice?.currentValue("weather") ?: ""
    return currentCondition.contains(settings.rainCondition ?: "Rain")
}

def isMotionUsable() {
    if (!settings.motionSensor) return false
    try {
        def luxValue = settings.motionSensor.currentValue("illuminance")
        if (luxValue != null) {
            int maxLux = settings.luxThreshold != null ? settings.luxThreshold as int : 1000
            if ((luxValue as int) > maxLux) {
                return false // Lux is too high, ignore motion sensor completely
            }
        }
    } catch (e) {
        // Ignore error if sensor doesn't support illuminance
    }
    return true
}

// --- Lock Integration Methods ---

def getLockCodesMap(lockDevice) {
    def codes = [:]
    try {
        def lockCodesJson = lockDevice.currentValue("lockCodes")
        if (lockCodesJson) {
            def parsedCodes = new groovy.json.JsonSlurper().parseText(lockCodesJson)
            parsedCodes.each { slot, data ->
                codes[slot] = "${data.name} (Slot ${slot})"
            }
        }
    } catch (e) {
        log.error "Error parsing lock codes: ${e}"
    }
    return codes
}

def lockHandler(evt) {
    if (!isSystemEnabled()) return
    
    if (evt.value == "unlocked") {
        if (!settings.kidLockCode) return
        
        String targetSlot = settings.kidLockCode.toString()
        String dataStr = evt.data?.toString() ?: ""
        boolean codeMatched = false
        
        // Regex aggressively hunts for "codeId" followed by an optional quote, space, colon or equals sign, and extracts the digits
        def match = dataStr =~ /codeId['"]?\s*[:=]\s*['"]?(\d+)['"]?/
        if (match) {
            if (match[0][1] == targetSlot) {
                codeMatched = true
            }
        }
        
        if (codeMatched) {
            checkEarlyArrival()
        } else {
            // Logs to your main Hubitat log so you can inspect what the lock payload actually looks like if it fails again
            log.debug "Door unlocked, but payload (${dataStr}) did not match slot ${targetSlot}"
        }
    }
}

def checkEarlyArrival() {
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.setTime(new Date())
    int hour = cal.get(Calendar.HOUR_OF_DAY)
    
    // Trigger if unlocked anytime 12:00 PM or later
    if (hour >= 12) {
        logAction("Early arrival detected via Lock Code! Disabling PM routines for the rest of today.")
        state.earlyArrivalDetected = true
        state.hasArrived = true
        state.waitingForArrival = false
        
        // Unschedule any pending PM stages just in case we are actively in the middle of a routine
        unschedule("pmSetStage1")
        unschedule("pmSetStage2")
        unschedule("pmSetStage3")
        turnLightOff()
    }
}

// --- Audio & 1-to-1 Motion Helper Engine ---

def room1MotionHandler(evt) { state.lastMotionRoom1 = new Date().time }
def room2MotionHandler(evt) { state.lastMotionRoom2 = new Date().time }
def room3MotionHandler(evt) { state.lastMotionRoom3 = new Date().time }
def room4MotionHandler(evt) { state.lastMotionRoom4 = new Date().time }
def room5MotionHandler(evt) { state.lastMotionRoom5 = new Date().time }
def room6MotionHandler(evt) { state.lastMotionRoom6 = new Date().time }
def room7MotionHandler(evt) { state.lastMotionRoom7 = new Date().time }

def isRoomMotionActive(int roomNum) {
    if (settings.alwaysOnRoom && settings.alwaysOnRoom.toString() == roomNum.toString()) {
        return true
    }

    def sensor = settings."room${roomNum}Motion"
    if (!sensor) return true 
    
    if (sensor.currentValue("motion") == "active") {
        state."lastMotionRoom${roomNum}" = new Date().time
        return true
    }
    
    def lastTime = state."lastMotionRoom${roomNum}"
    if (lastTime) {
        long timeoutMillis = (settings.audioMotionTimeout ?: 5) * 60 * 1000
        if ((new Date().time - lastTime) <= timeoutMillis) {
            return true
        }
    }
    
    return false
}

def isAnyAudioMotionActive() {
    boolean hasAnySensor = false
    for(int i = 1; i <= 7; i++) {
        if (settings."room${i}Motion") {
            hasAnySensor = true
            if (isRoomMotionActive(i)) return true
        }
    }
    return !hasAnySensor 
}

def playSirenSound(soundNum, force = false) {
    if (soundNum == null) return
    if (!force && !isAudioAllowedMode()) return
    
    def isNumeric = soundNum.toString().isNumber()
    def trackNum = isNumeric ? soundNum.toString().toInteger() : null

    int playCount = 0
    boolean playedAny = false

    for (int i = 1; i <= 7; i++) {
        def siren = settings."room${i}Speaker"
        if (siren) {
            if (force || isRoomMotionActive(i)) {
                playedAny = true
                if (playCount > 0) pauseExecution(1000) 
                try {
                    if (siren.hasCommand("playSound") && trackNum != null) {
                        siren.playSound(trackNum)
                    } else if (siren.hasCommand("playTrack")) {
                        siren.playTrack(soundNum.toString())
                    } else if (siren.hasCommand("chime") && trackNum != null) {
                        siren.chime(trackNum)
                    } else {
                        log.error "${siren.displayName} does not support standard audio/siren commands."
                    }
                    playCount++
                } catch (e) {
                    log.error "${siren.displayName} failed to play sound: ${e.message ?: e}"
                }
            } else {
                logAction("Skipping Room ${i} Zooz Speaker: No recent motion.")
            }
        }
    }
    
    if (playedAny) logAction("Triggered sound file ${soundNum} on active Zooz Speakers.")
}

// --- Dynamic Unified Scheduling Engine ---

def scheduleDailyEvents() {
    // Reset flags at the start of a new day
    state.earlyArrivalDetected = false

    // Annual Smart Learning Reset Logic
    Calendar cal = Calendar.getInstance(location.timeZone)
    int currentYear = cal.get(Calendar.YEAR)
    int currentMonth = cal.get(Calendar.MONTH) // 0-indexed, 7 is August
    
    if (currentMonth == 7 && state.lastResetYear != currentYear) {
        logAction("Annual Smart Learning Reset triggered (August). Clearing historical averages.")
        state.amLearnedTimes = []
        state.pmLearnedTimes = []
        state.lastResetYear = currentYear
    }

    // Schedule AM
    scheduleTodayEvent(settings.amStage1, "amSetStage1", false)
    scheduleTodayEvent(settings.amStage2, "amSetStage2", false)
    scheduleTodayEvent(settings.amStage3, "amSetStage3", false)
    scheduleTodayEvent(settings.amClear, "amTurnLightOff", false)

    // Schedule PM
    scheduleTodayEvent(settings.pmStage1, "pmSetStage1", true)
    scheduleTodayEvent(settings.pmStage2, "pmSetStage2", true)
    scheduleTodayEvent(settings.pmStage3, "pmSetStage3", true)
    scheduleTodayEvent(settings.pmClear, "pmTurnLightOff", true)
}

def scheduleTodayEvent(timeInput, handlerMethod, isPm = false) {
    if (!timeInput) return
    Date scheduledTime = timeToday(timeInput, location.timeZone)
    
    if (isPm && settings.earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = settings.earlyOffset ?: 120
        use(groovy.time.TimeCategory) { scheduledTime = scheduledTime - offset.minutes }
    }
    
    // Apply Smart Learning Adjustment
    int smartOffset = getSmartOffsetMinutes(!isPm) // Passing true for AM, false for PM
    if (smartOffset != 0) {
        use(groovy.time.TimeCategory) { scheduledTime = scheduledTime + smartOffset.minutes }
    }
    
    if (scheduledTime.after(new Date())) {
        runOnce(scheduledTime, handlerMethod, [overwrite: true])
    }
}

def amTurnLightOff() { turnLightOff() }
def pmTurnLightOff() { turnLightOff() }

// ------------------------------------

def getEpochTime(timeStr, isPm = false) {
    if (!timeStr) return new Date().time + 3600000 
    
    Date target = timeToday(timeStr, location.timeZone)
    
    if (isPm && settings.earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = settings.earlyOffset ?: 120
        use(groovy.time.TimeCategory) { target = target - offset.minutes }
    }
    
    // Apply Smart Learning Adjustment
    int smartOffset = getSmartOffsetMinutes(!isPm)
    if (smartOffset != 0) {
        use(groovy.time.TimeCategory) { target = target + smartOffset.minutes }
    }
    
    return target.time
}

def getCountdownText(timeStr, isPm = false) {
    if (!timeStr) return "Not Configured"
    def now = new Date()
    def target = timeToday(timeStr, location.timeZone)
    
    if (isPm && settings.earlyDismissalSwitch?.currentValue("switch") == "on") {
        int offset = settings.earlyOffset ?: 120
        use(groovy.time.TimeCategory) { target = target - offset.minutes }
    }
    
    // Apply Smart Learning Adjustment
    int smartOffset = getSmartOffsetMinutes(!isPm)
    if (smartOffset != 0) {
        use(groovy.time.TimeCategory) { target = target + smartOffset.minutes }
    }
    
    if (target.before(now)) return "Completed Today"
    
    long diff = target.time - now.time
    long hours = diff.intdiv(3600000)
    long mins = (diff % 3600000).intdiv(60000)
    
    if (hours > 0) return "${hours}h ${mins}m"
    return "${mins} Minutes"
}

// --- Smart Learning Core Methods ---

def recordSmartTime(boolean isAm) {
    Calendar cal = Calendar.getInstance(location.timeZone)
    int minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    
    def list = isAm ? (state.amLearnedTimes ?: []) : (state.pmLearnedTimes ?: [])
    list << minOfDay
    if (list.size() > 14) list = list.drop(list.size() - 14) // Keep trailing 14 days
    
    if (isAm) state.amLearnedTimes = list else state.pmLearnedTimes = list
    logAction("Smart Learning: Recorded ${isAm ? 'Morning' : 'Afternoon'} data point at min ${minOfDay}")
}

def getSmartOffsetMinutes(boolean isAm) {
    if (!settings.enableSmartLearning) return 0
    def list = isAm ? state.amLearnedTimes : state.pmLearnedTimes
    int minDays = settings.minLearningDays != null ? settings.minLearningDays as int : 10
    
    if (!list || list.size() < minDays) return 0
    
    // Calculate average minute of the day actual event happened
    int sum = 0
    list.each { sum += it }
    int avgMinOfDay = sum.intdiv(list.size())
    
    // Target user time for Stage 3
    def targetInput = isAm ? settings.amStage3 : settings.pmStage3
    if (!targetInput) return 0
    
    Date targetDate = timeToday(targetInput, location.timeZone)
    Calendar tCal = Calendar.getInstance(location.timeZone)
    tCal.setTime(targetDate)
    int targetMinOfDay = tCal.get(Calendar.HOUR_OF_DAY) * 60 + tCal.get(Calendar.MINUTE)
    
    // Return difference in minutes (e.g. Arrives at 7:35 vs input 7:30 = +5 offset)
    return avgMinOfDay - targetMinOfDay
}

def getSmartOffsetStr(boolean isAm) {
    int offset = getSmartOffsetMinutes(isAm)
    if (offset == 0) return "None (Using Default)"
    if (offset > 0) return "+${offset} mins"
    return "${offset} mins"
}

// ------------------------------------

def getHueColor(colorName) {
    switch(colorName) {
        case "Red": return [hue: 100, saturation: 100]
        case "Green": return [hue: 39, saturation: 100]
        case "Blue": return [hue: 65, saturation: 100]
        case "Yellow": return [hue: 16, saturation: 100]
        case "Orange": return [hue: 10, saturation: 100]
        case "Purple": return [hue: 75, saturation: 100]
        case "Pink": return [hue: 90, saturation: 100]
        case "White": return [hue: 0, saturation: 0]
        default: return [hue: 39, saturation: 100]
    }
}

def appButtonHandler(btn) {
    if (btn == "forceSyncBtn") {
        logAction("Manual Calendar Sync Triggered.")
        doCalendarSync(true)
    } else if (btn == "resetStatsBtn") {
        logAction("Reset Average Door Statistics")
        state.amTotalDoorTime = 0
        state.amDoorCount = 0
        state.pmTotalDoorTime = 0
        state.pmDoorCount = 0
    } else if (btn == "clearLogBtn") {
        state.actionHistory = []
    } else if (btn == "testAmBtn") {
        logAction("Initiating Morning TEST")
        startAmTest()
    } else if (btn == "testPmBtn") {
        logAction("Initiating Afternoon TEST")
        startPmTest()
    } else if (btn == "stopTestBtn") {
        logAction("Stopped Active TEST")
        turnLightOff()
    } else if (btn == "resetLearningBtn") {
        logAction("Manual Reset of Smart Learning Data.")
        state.amLearnedTimes = []
        state.pmLearnedTimes = []
    } else if (btn == "testAmSound1Btn") {
        if (settings.amSound1 != null) { logAction("Testing AM Stage 1 Sound..."); playSirenSound(settings.amSound1, true) }
        else { logAction("Please enter a sound number for AM Stage 1 first (click outside the box to register it).") }
    } else if (btn == "testAmSound2Btn") {
        if (settings.amSound2 != null) { logAction("Testing AM Stage 2 Sound..."); playSirenSound(settings.amSound2, true) }
        else { logAction("Please enter a sound number for AM Stage 2 first (click outside the box to register it).") }
    } else if (btn == "testAmSound3Btn") {
        if (settings.amSound3 != null) { logAction("Testing AM Stage 3 Sound..."); playSirenSound(settings.amSound3, true) }
        else { logAction("Please enter a sound number for AM Stage 3 first (click outside the box to register it).") }
    } else if (btn == "testPmSound1Btn") {
        if (settings.pmSound1 != null) { logAction("Testing PM Stage 1 Sound..."); playSirenSound(settings.pmSound1, true) }
        else { logAction("Please enter a sound number for PM Stage 1 first (click outside the box to register it).") }
    } else if (btn == "testPmSound2Btn") {
        if (settings.pmSound2 != null) { logAction("Testing PM Stage 2 Sound..."); playSirenSound(settings.pmSound2, true) }
        else { logAction("Please enter a sound number for PM Stage 2 first (click outside the box to register it).") }
    } else if (btn == "testPmSound3Btn") {
        if (settings.pmSound3 != null) { logAction("Testing PM Stage 3 Sound..."); playSirenSound(settings.pmSound3, true) }
        else { logAction("Please enter a sound number for PM Stage 3 first (click outside the box to register it).") }
    }
}

// --- STATE CAPTURE ENGINE ---
def captureLightState(dev) {
    if (!state.savedLightState) state.savedLightState = [:]
    
    state.savedLightState = [
        switch: dev.currentValue("switch"),
        hue: dev.currentValue("hue"),
        saturation: dev.currentValue("saturation"),
        level: dev.currentValue("level"),
        colorTemperature: dev.currentValue("colorTemperature")
    ]
    log.info "Captured previous state for ${dev.displayName}: ${state.savedLightState}"
}

def restoreLightState(dev) {
    if (!state.savedLightState) {
        dev.off()
        return
    }
    
    def saved = state.savedLightState
    if (saved.switch == "on") {
        if (saved.colorTemperature) {
            dev.setColorTemperature(saved.colorTemperature, saved.level)
        } else if (saved.hue != null && saved.saturation != null) {
            dev.setColor([hue: saved.hue, saturation: saved.saturation, level: saved.level])
        } else {
            dev.on()
            if (saved.level) dev.setLevel(saved.level)
        }
        log.info "Restored ${dev.displayName} to ON state."
    } else {
        dev.off()
        log.info "Restored ${dev.displayName} to OFF state."
    }
    
    state.savedLightState = [:] 
}

// --- Dynamic Sequence & Blink Engine ---

def startLightingSequence(colorName, isRainOverride, nextTargetEpoch, soundNum = null) {
    if (settings.overrideSwitch && settings.overrideSwitch.currentValue("switch") != "on") {
        settings.overrideSwitch.on()
        if (settings.busLight) captureLightState(settings.busLight)
    }

    state.lightActiveByApp = true 

    if (soundNum != null) {
        playSirenSound(soundNum)
    }

    if (isRainOverride) {
        logAction("Precipitation detected: Showing Blue for 10 seconds before starting timer.")
        def blueMap = getHueColor("Blue")
        if (settings.busLight) settings.busLight.setColor([hue: blueMap.hue, saturation: blueMap.saturation, level: 80])
        runIn(10, "applyNormalStageColor", [data: [color: colorName, target: nextTargetEpoch], overwrite: true])
    } else {
        applyNormalStageColor([color: colorName, target: nextTargetEpoch])
    }
}

def applyNormalStageColor(data) {
    String colorName = data.color
    long nextTargetEpoch = data.target
    
    def colorMap = getHueColor(colorName)
    state.currentStageColor = colorMap
    
    if (settings.busLight) settings.busLight.setColor([hue: colorMap.hue, saturation: colorMap.saturation, level: 80])

    if (settings.enableBlinking) {
        state.isBlinking = true
        state.blinkState = true
        state.targetTime = nextTargetEpoch
        
        // 2. TRACK START TIME FOR FAILSAFE
        state.blinkStartTime = new Date().time 
        
        runIn(5, "blinkLoop", [overwrite: true])
    }
}

def blinkLoop() {
    if (!state.isBlinking) return
   
    // 3. HARD FAILSAFE: Kill automatically if running for > 45 minutes
    long now = new Date().time
    long started = state.blinkStartTime ?: now
    if ((now - started) > 2700000) { // 2,700,000 ms = 45 minutes
        log.warn "FAILSAFE TRIGGERED: Blinking exceeded 45 minutes. Forcing OFF."
        turnLightOff()
        return
    }

    if (!state.isTesting) {
        if (!isSystemEnabled() || isSickDay() || !isSchoolDay() || !isAllowedMode()) {
            state.isBlinking = false
            return
        }
    }

    long target = state.targetTime ?: now
    long diffSeconds = (target - now) / 1000
    if (diffSeconds < 0) diffSeconds = 0

    int interval = 15 
    if (diffSeconds <= 60) interval = 2 
    else if (diffSeconds <= 180) interval = 4 
    else if (diffSeconds <= 300) interval = 8 
    else if (diffSeconds <= 600) interval = 15 
    else interval = 20 

    state.blinkState = !state.blinkState

    if (state.blinkState) {
        if (settings.busLight) settings.busLight.setColor([hue: state.currentStageColor.hue, saturation: state.currentStageColor.saturation, level: 80])
    } else {
        if (settings.busLight) settings.busLight.off()
    }

    runIn(interval, "blinkLoop", [overwrite: true])
}

// --- Testing Routines ---

def startAmTest() {
    if (!isSystemEnabled()) return
    state.isTesting = true
    state.waitingForDeparture = true 
    state.hasDeparted = false
    state.amDoorTriggered = false
    state.amMotionTriggered = false
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor1 ?: "Green", isItRaining(), nextTarget, settings.amSound1)
    runIn(30, "testAmStage2", [overwrite: true])
}

def testAmStage2() {
    if (!state.isTesting) return
    if (state.hasDeparted) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor2 ?: "Yellow", isItRaining(), nextTarget, settings.amSound2)
    runIn(30, "testAmStage3", [overwrite: true])
}

def testAmStage3() {
    if (!state.isTesting) return
    if (state.hasDeparted) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.amColor3 ?: "Red", isItRaining(), nextTarget, settings.amSound3)
    String msg = "TEST: ${settings.amImminentMsg ?: 'The school bus is imminent! Time to head to the door.'}"
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
    if (settings.ttsDevice && isAudioAllowedMode()) {
        if (isAnyAudioMotionActive()) {
            settings.ttsDevice.speak(msg)
        } else {
            logAction("Skipping TTS announcement: No recent motion in any mapped rooms.")
        }
    }
    runIn(30, "turnLightOff", [overwrite: true])
}

def startPmTest() {
    if (!isSystemEnabled()) return
    state.isTesting = true
    state.waitingForArrival = true
    state.hasArrived = false
    state.pmDoorTriggered = false
    state.pmMotionTriggered = false
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor1 ?: "Green", isItRaining(), nextTarget, settings.pmSound1)
    runIn(30, "testPmStage2", [overwrite: true])
}

def testPmStage2() {
    if (!state.isTesting) return
    if (state.hasArrived) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor2 ?: "Yellow", isItRaining(), nextTarget, settings.pmSound2)
    runIn(30, "testPmStage3", [overwrite: true])
}

def testPmStage3() {
    if (!state.isTesting) return
    if (state.hasArrived) return
    long nextTarget = new Date().time + 30000 
    startLightingSequence(settings.pmColor3 ?: "Red", isItRaining(), nextTarget, settings.pmSound3)
    String msg = "TEST: ${settings.pmArrivedMsg ?: 'The school bus has arrived at the afternoon stop.'}"
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
    if (settings.ttsDevice && isAudioAllowedMode()) {
        if (isAnyAudioMotionActive()) {
            settings.ttsDevice.speak(msg)
        } else {
            logAction("Skipping TTS announcement: No recent motion in any mapped rooms.")
        }
    }
    runIn(30, "turnLightOff", [overwrite: true])
}

// --- Morning Routines ---

def amSetStage1() {
    state.earlyArrivalDetected = false // Clear flag at start of day

    if (!isSystemEnabled()) { logAction("AM Stage 1 Aborted: Master Switch is OFF"); return }
    if (!isSchoolDay()) { logAction("AM Stage 1 Aborted: Today is not a School Day"); return }
    if (isSickDay()) { logAction("AM Stage 1 Aborted: Sick Day is active"); return }
    if (!isAllowedMode()) { logAction("AM Stage 1 Aborted: Location Mode '${location.mode}' is not allowed"); return }

    logAction("AM Stage 1 Started Successfully.")
    state.waitingForDeparture = true
    state.hasDeparted = false
    state.amDoorTriggered = false
    state.amMotionTriggered = false
    state.amTrackTime = null

    long nextTarget = getEpochTime(settings.amStage2, false)
    startLightingSequence(settings.amColor1 ?: "Green", isItRaining(), nextTarget, settings.amSound1)
}

def amSetStage2() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasDeparted) { logAction("AM Stage 2 Skipped: Student already departed."); return }
    
    logAction("AM Stage 2 Started.")
    long nextTarget = getEpochTime(settings.amStage3, false)
    startLightingSequence(settings.amColor2 ?: "Yellow", isItRaining(), nextTarget, settings.amSound2)
}

def amSetStage3() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasDeparted) { logAction("AM Stage 3 Skipped: Student already departed."); return }
    
    logAction("AM Stage 3 Started. Awaiting conditions.")
    state.amTrackTime = new Date().time
    
    if (settings.doorSensor || settings.motionSensor) {
        int delaySeconds = (settings.departureTimeout ?: 5) * 60
        runIn(delaySeconds, "checkMissedBus")
    }

    long nextTarget = getEpochTime(settings.amClear, false)
    startLightingSequence(settings.amColor3 ?: "Red", isItRaining(), nextTarget, settings.amSound3)

    String msg = settings.amImminentMsg ?: "The school bus is imminent! Time to head to the door."
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
    if (settings.ttsDevice && isAudioAllowedMode()) {
        if (isAnyAudioMotionActive()) {
            settings.ttsDevice.speak(msg)
        } else {
            logAction("Skipping TTS announcement: No recent motion in any mapped rooms.")
        }
    }
}

// --- Afternoon Routines ---

def pmSetStage1() {
    if (!isSystemEnabled()) { logAction("PM Stage 1 Aborted: Master Switch is OFF"); return }
    if (!isSchoolDay()) { logAction("PM Stage 1 Aborted: Not a School Day"); return }
    if (isSickDay()) { logAction("PM Stage 1 Aborted: Sick Day is active"); return }
    if (!isAllowedMode()) { logAction("PM Stage 1 Aborted: Location Mode '${location.mode}' is not allowed"); return }
    if (state.earlyArrivalDetected) { logAction("PM Stage 1 Aborted: Early arrival detected via Smart Lock."); return }

    logAction("PM Stage 1 Started Successfully.")
    
    state.waitingForArrival = true
    state.hasArrived = false
    state.pmDoorTriggered = false
    state.pmMotionTriggered = false
    state.pmTrackTime = null

    long nextTarget = getEpochTime(settings.pmStage2, true)
    startLightingSequence(settings.pmColor1 ?: "Green", isItRaining(), nextTarget, settings.pmSound1)
}

def pmSetStage2() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasArrived || state.earlyArrivalDetected) { logAction("PM Stage 2 Skipped: Student already arrived."); return }
    
    logAction("PM Stage 2 Started.")
    long nextTarget = getEpochTime(settings.pmStage3, true)
    startLightingSequence(settings.pmColor2 ?: "Yellow", isItRaining(), nextTarget, settings.pmSound2)
}

def pmSetStage3() {
    if (!isSystemEnabled() || !isSchoolDay() || isSickDay() || !isAllowedMode()) return
    if (state.hasArrived || state.earlyArrivalDetected) { logAction("PM Stage 3 Skipped: Student already arrived."); return }
    
    logAction("PM Stage 3 Started. Awaiting conditions.")
    state.pmTrackTime = new Date().time
    
    if (settings.doorSensor || settings.motionSensor) {
        int delaySeconds = (settings.arrivalTimeout ?: 15) * 60
        runIn(delaySeconds, "checkSafeArrival")
    }

    long nextTarget = getEpochTime(settings.pmClear, true)
    startLightingSequence(settings.pmColor3 ?: "Red", isItRaining(), nextTarget, settings.pmSound3)

    String msg = settings.pmArrivedMsg ?: "The school bus has arrived at the afternoon stop."
    if (settings.notifyPhones) settings.notifyPhones.deviceNotification(msg)
    if (settings.ttsDevice && isAudioAllowedMode()) {
        if (isAnyAudioMotionActive()) {
            settings.ttsDevice.speak(msg)
        } else {
            logAction("Skipping TTS announcement: No recent motion in any mapped rooms.")
        }
    }
}

// --- Door and Security Routines ---

def doorOpenedHandler(evt) {
    if (!isSystemEnabled()) return
    boolean needAmMotion = isMotionUsable()
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived && !state.pmDoorTriggered) {
        state.pmDoorTriggered = true
        if (needPmMotion && !state.pmMotionTriggered) {
            logAction("Front door opened. Waiting for outside motion (Afternoon)...")
        } else if (!needPmMotion && settings.motionSensor) {
            logAction("Front door opened. Motion requirement skipped (Lux too high).")
        }
        checkArrivalDepartureConditions()
    }
    
    if (state.waitingForDeparture && !state.hasDeparted && !state.amDoorTriggered) {
        state.amDoorTriggered = true
        if (needAmMotion && !state.amMotionTriggered) {
            logAction("Front door opened. Waiting for outside motion (Morning)...")
        } else if (!needAmMotion && settings.motionSensor) {
            logAction("Front door opened. Motion requirement skipped (Lux too high).")
        }
        checkArrivalDepartureConditions()
    }
}

def motionActiveHandler(evt) {
    if (!isSystemEnabled()) return
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived && !state.pmMotionTriggered) {
        state.pmMotionTriggered = true
        if (needPmMotion && !state.pmDoorTriggered) {
            logAction("Outside motion detected. Waiting for front door (Afternoon)...")
        }
        checkArrivalDepartureConditions()
    }

    if (state.waitingForDeparture && !state.hasDeparted && !state.amMotionTriggered) {
        state.amMotionTriggered = true
        if (!state.amDoorTriggered) {
            logAction("Outside motion detected. Waiting for front door (Morning)...")
        }
        checkArrivalDepartureConditions()
    }
}

def luxHandler(evt) {
    if (!isSystemEnabled()) return
    checkArrivalDepartureConditions()
}

def checkArrivalDepartureConditions() {
    boolean needAmMotion = isMotionUsable()
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived) {
        boolean pmCondition = needPmMotion ? (state.pmDoorTriggered && state.pmMotionTriggered) : state.pmDoorTriggered
        if (pmCondition) {
            long diffSecs = state.pmTrackTime ? ((new Date().time - state.pmTrackTime) / 1000) : 0
            String durStr = "${diffSecs.intdiv(60)}m ${diffSecs % 60}s"
            logAction("Arrival conditions met! Safe drop-off recorded. (Time to door: ${durStr})")
            
            state.waitingForArrival = false
            state.hasArrived = true
            state.isBlinking = false 
            
            if (settings.enableSmartLearning) {
                recordSmartTime(false) // false for PM
            }
            
            if (state.pmTrackTime && !state.isTesting) {
                state.pmTotalDoorTime = (state.pmTotalDoorTime ?: 0) + diffSecs
                state.pmDoorCount = (state.pmDoorCount ?: 0) + 1
                state.pmTrackTime = null
            }
            
            if (settings.notifyOnSave && settings.notifyPhones) {
                settings.notifyPhones.deviceNotification("Safe drop-off logged! Recorded door time: ${durStr}.")
            }

            if (isAllowedMode() || state.isTesting) {
                if (settings.busLight) settings.busLight.setColor([hue: 39, saturation: 100, level: 100]) 
            }
        
            runIn(10, "turnLightOff")
            unschedule("checkSafeArrival")
            
            // Force schedules to immediately recalculate with the new saved averages
            scheduleDailyEvents()
        }
    }

    if (state.waitingForDeparture && !state.hasDeparted) {
        boolean amCondition = needAmMotion ? (state.amDoorTriggered && state.amMotionTriggered) : state.amDoorTriggered
        if (amCondition) {
            long diffSecs = state.amTrackTime ? ((new Date().time - state.amTrackTime) / 1000) : 0
            String durStr = "${diffSecs.intdiv(60)}m ${diffSecs % 60}s"
            logAction("Departure conditions met! Safe pickup recorded. (Time to door: ${durStr})")
            
            state.waitingForDeparture = false
            state.hasDeparted = true
            state.isBlinking = false 
            
            if (settings.enableSmartLearning) {
                recordSmartTime(true) // true for AM
            }
            
            if (state.amTrackTime && !state.isTesting) {
                state.amTotalDoorTime = (state.amTotalDoorTime ?: 0) + diffSecs
                state.amDoorCount = (state.amDoorCount ?: 0) + 1
                state.amTrackTime = null
            }

            if (settings.notifyOnSave && settings.notifyPhones) {
                settings.notifyPhones.deviceNotification("Safe pickup logged! Recorded door time: ${durStr}.")
            }

            if (isAllowedMode() || state.isTesting) {
                if (settings.busLight) settings.busLight.setColor([hue: 39, saturation: 100, level: 100]) 
            }
            runIn(5, "turnLightOff")
            unschedule("checkMissedBus")
            
            // Force schedules to immediately recalculate with the new saved averages
            scheduleDailyEvents()
        }
    }
}

def checkMissedBus() {
    if (!isSystemEnabled()) return
    boolean needAmMotion = isMotionUsable()

    if (state.waitingForDeparture && !state.hasDeparted) {
        logAction("WARNING: Missed bus alert triggered!")
        String defaultMsg = needAmMotion ? "WARNING: The bus arrived, but the door/motion conditions were not met!" : "WARNING: The bus arrived, but the front door never opened!"
        String alertMsg = settings.amMissedMsg ?: defaultMsg
        if (settings.notifyPhones) settings.notifyPhones.deviceNotification(alertMsg)
        state.waitingForDeparture = false
        state.amTrackTime = null
    }
}

def checkSafeArrival() {
    if (!isSystemEnabled()) return
    boolean needPmMotion = (isMotionUsable() && !settings.ignorePmMotion)

    if (state.waitingForArrival && !state.hasArrived) {
        logAction("CRITICAL: Missed drop-off alert triggered!")
        String defaultMsg = needPmMotion ? "CRITICAL: The bus dropped off, but the door/motion conditions were not met!" : "CRITICAL: The school bus dropped off, but the front door hasn't opened!"
        String alertMsg = settings.pmMissedMsg ?: defaultMsg
        if (settings.notifyPhones) settings.notifyPhones.deviceNotification(alertMsg)
        state.waitingForArrival = false
        state.pmTrackTime = null
    }
}

def turnLightOff() {
    if (!state.lightActiveByApp && !state.isTesting) {
        return 
    }

    state.lightActiveByApp = false 
    state.isBlinking = false
    state.isTesting = false
    state.waitingForArrival = false
    state.waitingForDeparture = false
    
    // 1. EXPLICITLY KILL THE LOOP
    unschedule("blinkLoop")
    
    // RESTORE STATE & UNFREEZE Motion App
    if (settings.busLight) restoreLightState(settings.busLight)
    if (settings.overrideSwitch) settings.overrideSwitch.off()
    
    unschedule("testAmStage2")
    unschedule("testAmStage3")
    unschedule("testPmStage2")
    unschedule("testPmStage3")
}

// --- Smart Calendar Routines ---

def checkSchoolCalendar() {
    doCalendarSync(false)
}

def doCalendarSync(boolean isForced) {
    if (!isSystemEnabled()) {
        if (isForced) logAction("Sync Aborted: Master Switch is OFF")
        return
    }
    
    if (!settings.enableCalendar && !isForced) return

    boolean onlineSuccess = false

    if (settings.iCalUrl) {
        String fetchUrl = settings.iCalUrl.replace("webcal://", "https://")
        def params = [
            uri: fetchUrl,
            headers: ["User-Agent": "Hubitat/2.0"],
            timeout: 15
        ]

        try {
            httpGet(params) { response ->
                if (response.status == 200) {
                    onlineSuccess = true
                    String icsData = ""
                    
                    if (response.data == null) {
                        log.warn "iCal fetch returned empty data payload."
                    } else if (response.data instanceof String) {
                        icsData = response.data
                    } else {
                        try {
                            icsData = response.data.text
                        } catch (e) {
                            icsData = response.data.toString()
                        }
                    }
                    
                    if (!icsData || !icsData.contains("BEGIN:VCALENDAR")) {
                        state.calendarForecastHtml = "<div style='padding: 10px; font-size: 12px; color: #aa0000;'><b>Parse Error:</b> Downloaded data is not a valid calendar. It may be blocked by the server.</div>"
                        log.warn "iCal fetch succeeded, but payload missing calendar headers. Payload preview: ${icsData?.take(50)}"
                        applyDayResult(determineIfSchoolDayOffline(), "Offline Local Math", isForced)
                        return
                    }

                    boolean isSchoolDay = processCalendarData(icsData) 
                    applyDayResult(isSchoolDay, "Online iCal Feed", isForced)
                }
            }
        } catch (e) {
            log.warn "Failed to fetch iCal feed. Error: ${e.message}"
        }
    }

    if (!onlineSuccess) {
        state.calendarForecastHtml = "<div style='padding: 10px; font-size: 12px; color: #aa0000;'><b>Forecast Unavailable:</b> Feed offline. Using fallback.</div>"
        boolean isSchoolDay = determineIfSchoolDayOffline()
        applyDayResult(isSchoolDay, "Offline Local Math", isForced)
    }
}

def processCalendarData(String icsData) {
    Calendar localCalendar = Calendar.getInstance(location.timeZone)
    def targetDates = []
    def forecastMap = [:] 
    
    for (int i = 0; i < 3; i++) {
        String dateKey = new Date(localCalendar.timeInMillis).format("yyyyMMdd", location.timeZone)
        String dateLabel = new Date(localCalendar.timeInMillis).format("EEE, MMM d", location.timeZone)
        
        int dayOfWeek = localCalendar.get(Calendar.DAY_OF_WEEK)
        boolean isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
        
        targetDates << dateKey
        forecastMap[dateKey] = [
            label: dateLabel, 
            events: [], 
            status: isWeekend ? "Weekend" : "School Day", 
            color: isWeekend ? "#888" : "green",
            isSchoolDay: !isWeekend
        ]
        localCalendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    String rawKeywords = settings.holidayKeywords ?: "spring break, teacher workday, no school"
    List<String> keywords = rawKeywords.split(',').collect { it.trim().toLowerCase() }.findAll { it.length() > 0 }

    def lines = icsData.split(/\r?\n/)
    boolean inEvent = false
    String eventStart = ""
    String eventEnd = ""
    String eventSummary = ""

    for (String line : lines) {
        String upperLine = line.trim().toUpperCase()
        
        if (upperLine.startsWith("BEGIN:VEVENT")) {
            inEvent = true
            eventStart = ""; eventEnd = ""; eventSummary = ""
        } else if (upperLine.startsWith("END:VEVENT")) {
            if (inEvent && eventStart && eventSummary) {
                if (!eventEnd) eventEnd = eventStart 
                for (String tDate : targetDates) {
                    if (tDate >= eventStart && tDate <= eventEnd) {
                        if (!forecastMap[tDate].events.contains(eventSummary)) {
                            forecastMap[tDate].events.add(eventSummary)
                        }
                        String lowerSummary = eventSummary.toLowerCase()
                        for (String keyword : keywords) {
                            if (lowerSummary.contains(keyword)) {
                                forecastMap[tDate].status = "No School (${keyword})"
                                forecastMap[tDate].color = "#aa0000"
                                forecastMap[tDate].isSchoolDay = false
                                break
                            }
                        }
                    }
                }
            }
            inEvent = false
        } else if (inEvent) {
            if (upperLine.startsWith("DTSTART")) {
                int idx = line.indexOf(':')
                if (idx > -1) {
                    String val = line.substring(idx+1).replaceAll("[^0-9]", "")
                    if (val.length() >= 8) eventStart = val.substring(0, 8)
                }
            } else if (upperLine.startsWith("DTEND")) {
                int idx = line.indexOf(':')
                if (idx > -1) {
                    String val = line.substring(idx+1).replaceAll("[^0-9]", "")
                    if (val.length() >= 8) eventEnd = val.substring(0, 8)
                }
            } else if (upperLine.startsWith("SUMMARY")) {
                int idx = line.indexOf(':')
                if (idx > -1) {
                    eventSummary = line.substring(idx+1).trim()
                }
            }
        }
    }

    String html = "<div style='margin-top: 10px; padding: 10px; background: #f9f9f9; border-radius: 4px; font-size: 12px; border: 1px solid #ccc;'>"
    html += "<h4 style='margin-top: 0px; margin-bottom: 5px;'>3-Day Calendar Forecast</h4><table style='width:100%; border-collapse: collapse;'>"
    forecastMap.each { k, v ->
        String evs = v.events.size() > 0 ? v.events.join(", ") : "<i>No events</i>"
        html += "<tr style='border-bottom: 1px solid #eee;'><td style='padding: 6px 0; width: 25%;'><b>${v.label}</b></td>"
        html += "<td style='padding: 6px 0; width: 45%; color: #555;'>${evs}</td>"
        html += "<td style='padding: 6px 0; width: 30%; color: ${v.color}; text-align: right;'><b>${v.status}</b></td></tr>"
    }
    html += "</table></div>"
    state.calendarForecastHtml = html
    return forecastMap[targetDates[0]].isSchoolDay
}

def applyDayResult(boolean isSchoolDay, String source, boolean isForced) {
    boolean currentSwitch = settings.schoolDaySwitch?.currentValue("switch") == "on"
    if (isSchoolDay) {
        settings.schoolDaySwitch?.on()
        if (!currentSwitch || isForced) logAction("Calendar Sync: Marked as School Day [${source}]")
    } else {
        settings.schoolDaySwitch?.off()
        if (currentSwitch || isForced) logAction("Calendar Sync: Marked as Off-Day [${source}]")
    }
}

def determineIfSchoolDayOffline() {
    Calendar cal = Calendar.getInstance(location.timeZone)
    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false 
    return true
}
