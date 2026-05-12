/**
 * Advanced Voice Butler
 *
 * Author: ShaneAllen
 *
 * Version 1.10
 */
definition(
    name: "Advanced Voice Butler",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Estate Manager TTS orchestrator with AI Habit Tracking, Secrecy Engine, and Organic Intercepts.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomPage")
}

mappings {
    path("/google/email") { action: [POST: "handleGoogleEmail"] }
    path("/google/calendar") { action: [POST: "handleGoogleCalendar"] }
    path("/notes") { action: [GET: "serveNotesPage"] }
    path("/notes/add") { action: [POST: "addNoteEndpoint"] }
    path("/notes/clear") { action: [POST: "clearNotesEndpoint"] }
    path("/directory/announce") { action: [POST: "announceDirectoryEndpoint"] }
    path("/agenda/update") { action: [POST: "updateAgendaEndpoint"] }
    path("/wifi/announce") { action: [POST: "announceWifiEndpoint"] }
    path("/pa/announce") { action: [POST: "instantPAEndpoint"] }
    path("/presence/depart") { action: [POST: "manualDepartEndpoint"] }
    path("/reply/quick") { action: [POST: "quickReplyEndpoint"] } // <-- UPDATED PATH
}

def getRoutingOptions() {
    return [
        "Global Indoor Speaker Only",
        "Outdoor Speaker Only",
        "Outdoor + Global Indoor",
        "Dedicated Feature Speaker",
        "Follow-Me (Active Rooms Only)",
        "Follow-Me + Outdoor",
        "Follow-Me + Fallback (Global ONLY if no motion)",
        "Follow-Me + Fallback + Outdoor",
        "Follow-Me + Global Simultaneous",
        "Follow-Me + Global Simultaneous + Outdoor"
    ]
}

def mainPage() {
    
    if (!state.accessToken) {
        try {
            createAccessToken()
        } catch (Exception e) {
            log.error "OAuth is not enabled! Please click 'OAuth' at the top of the app code and enable it."
        }
    }
    
    dynamicPage(name: "mainPage", title: "Voice Butler Configuration", install: true, uninstall: true) {
        
        // --- HABIT DATABASE SCRUB ---
        // Removes old "Group" entries (e.g. Shane and Christy) to keep the roster clean
        if (state.learnedHabits) {
            def keysToRemove = []
            state.learnedHabits.each { k, v -> if (k.contains(" and ") || k.contains("&") || k.contains(",")) keysToRemove << k }
            keysToRemove.each { state.learnedHabits.remove(it) }
        }
        // ----------------------------

        def prevStyle = "margin-top: 15px; padding: 10px; background-color: #e9ecef; border-left: 4px solid #0b3b60; border-radius: 4px; font-size: 13px; line-height: 1.4;"
        
        def lockUsers = []
        if (frontDoorLock) {
            try {
                def lockCodesStr = frontDoorLock.currentValue("lockCodes")
                if (lockCodesStr) {
                    def parsed = new groovy.json.JsonSlurper().parseText(lockCodesStr)
                    if (parsed instanceof Map) {
                        lockUsers = parsed.collect { it.value.name ?: it.value }.findAll { it != null }.sort()
                    }
                }
            } catch (Exception e) {}
        }

        section("Live System Dashboard", hideable: false, hidden: false) {
            paragraph "<i>Welcome to the Voice Butler command center. Below is a real-time read-only view of your perimeter status, active voice zones, and today's arrival/departure log.</i>"
            
            // --- UPDATED WEBHOOK ENDPOINT DISPLAY ---
            if (state.accessToken) {
                def cloudUrl = getFullApiServerUrl()
                def localUrl = getFullLocalApiServerUrl()
                paragraph "<div style='padding:10px; background-color:#ffeeba; border:1px solid #ffeeba; color:#856404; border-radius:4px;'><b>Your Webhook Base URL (Cloud):</b><br>${cloudUrl}/google?access_token=${state.accessToken}<br><br><b>Your Webhook Base URL (Local):</b><br>${localUrl}/google?access_token=${state.accessToken}</div>"
            }
            // ----------------------------------------
            
            input "btnRefresh", "button", title: "🔄 Refresh Data Dashboard"
            input "btnQuickSave", "button", title: "💾 Quick Save / Refresh Page"
            input "btnForceSync", "button", title: "🔄 Force Sync Calendar & News Data", description: "Instantly poll all external API feeds to update the dashboard below."
            
            def dndModesList = [settings.dndModes].flatten().findAll { it != null }
            def isDndMode = dndModesList.contains(location.mode)
            def isDndSwitch = dndSwitch?.currentValue("switch") == "on"
            def isPartySwitch = settings.partyModeSwitch?.currentValue("switch") == "on"
            
            def isMasterOff = masterSwitch?.currentValue("switch") == "off"
            def isGuestMode = guestModeSwitch?.currentValue("switch") == "on"
            
            def systemState = ""
            if (isMasterOff) {
                systemState = "<span style='color: #c0392b; font-weight: bold;'>MUTED (Master Switch OFF)</span>"
            } else if (isGuestMode) {
                systemState = "<span style='color: #f39c12; font-weight: bold;'>SILENT (Guest Mode ON)</span>"
            } else {
                systemState = "<span style='color: #27ae60; font-weight: bold;'>RUNNING AND ACTIVE</span>"
            }

            def dndState = ""
            if (isPartySwitch && settings.enablePartyMode) {
                dndState = "<span style='color: #8e44ad; font-weight: bold;'>HOSTING (Party Mode Active)</span>"
            } else if (isDndSwitch || isDndMode) {
                dndState = "<span style='color: #c0392b; font-weight: bold;'>ACTIVE (Do Not Disturb)</span>"
            } else {
                dndState = "<span style='color: #27ae60; font-weight: bold;'>STANDBY (Accepting Visitors)</span>"
            }
            
            def inetStatus = (!settings.enableInternetCheck || state.internetActive != false) ? "<span style='color: #27ae60; font-weight: bold;'>ONLINE</span>" : "<span style='color: #c0392b; font-weight: bold;'>OFFLINE (TTS Suppressed)</span>"
            def queueStatus = state.ttsQueue?.size() > 0 ? "<span style='color: #d35400; font-weight: bold;'>${state.ttsQueue.size()} Messages Queued</span>" : "<span style='color: #27ae60;'>Idle</span>"
            
            def statusText = "<div style='margin-bottom: 10px; padding: 10px; background: #e9e9e9; border-radius: 4px; font-size: 13px; border: 1px solid #ccc;'>"
            statusText += "<b>System State:</b> ${systemState}<br>"
            statusText += "<b>Perimeter Status:</b> ${dndState}<br>"
            statusText += "<b>Internet Connection:</b> ${inetStatus}<br>"
            statusText += "<b>TTS Queue Engine:</b> ${queueStatus}</div>"
            
            // --- External Sync Data ---
            statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>External Integrations Sync</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Service</th><th style='padding: 8px;'>Status / Next Item</th><th style='padding: 8px;'>Last Checked / Time</th></tr>"
            
            def nowMs = new Date().time
            if (state.nextEventEpoch && nowMs > state.nextEventEpoch) { state.nextEventName = null; state.nextEventTimeStr = null }
            def nextCalText = (state.nextEventName && state.nextEventTimeStr) ? "<b>${state.nextEventName}</b>" : "<span style='color: #7f8c8d;'>Waiting for Sync... / No Events</span>"
            if (!settings.enableCalendar) nextCalText = "<span style='color: #7f8c8d;'>Disabled</span>"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Calendar Engine</b></td><td style='padding: 8px;'>${nextCalText}</td><td style='padding: 8px;'>${state.nextEventTimeStr ?: '--'}</td></tr>"

            // --- NEW: SCHEDULED BUTLER NOTES ROW ---
            def nextNoteText = "<span style='color: #7f8c8d;'>No Scheduled Notes</span>"
            def nextNoteTimeStr = "--"
            if (state.butlerNotes) {
                def upcomingNotes = state.butlerNotes.findAll { it.when == "Time" && it.timeEpoch && it.timeEpoch > nowMs }.sort { it.timeEpoch }
                if (upcomingNotes.size() > 0) {
                    def nextNote = upcomingNotes[0]
                    def senderPrefix = nextNote.sender != "Someone" ? "${nextNote.sender} to " : "To "
                    nextNoteText = "<b>${senderPrefix}${nextNote.target}:</b> <i>\"${nextNote.text.take(20)}${nextNote.text.length() > 20 ? '...' : ''}\"</i>"
                    nextNoteTimeStr = new Date(nextNote.timeEpoch).format("MMM d 'at' h:mm a", location.timeZone)
                }
            }
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Scheduled Notes</b></td><td style='padding: 8px;'>${nextNoteText}</td><td style='padding: 8px; color: #0b3b60; font-weight: bold;'>${nextNoteTimeStr}</td></tr>"
            // ---------------------------------------

            def mealNewsText = state.mealNewsHeadline ?: "<span style='color: #7f8c8d;'>Waiting for Sync...</span>"
            def mealNewsTime = state.mealNewsSyncTime ?: "--"
            if (!settings.enableMealTime || !settings.mealTimeNewsWeather) mealNewsText = "<span style='color: #7f8c8d;'>Disabled</span>"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Meal Time News</b></td><td style='padding: 8px;'>${mealNewsText}</td><td style='padding: 8px;'>${mealNewsTime}</td></tr>"

            def breakNewsText = state.lastBreakingHeadline ?: "<span style='color: #7f8c8d;'>Waiting for Sync...</span>"
            def breakNewsTime = state.breakingNewsSyncTime ?: "--"
            if (!settings.enableBreakingNews) breakNewsText = "<span style='color: #7f8c8d;'>Disabled</span>"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Organic Breaking News</b></td><td style='padding: 8px;'>${breakNewsText}</td><td style='padding: 8px;'>${breakNewsTime}</td></tr>"
            
            def numRoomsConfig = settings.numRooms ? settings.numRooms as Integer : 0
            if (numRoomsConfig > 0) {
                for (int i = 1; i <= numRoomsConfig; i++) {
                    if (settings["roomNewsEnable_${i}"]) {
                        def rName = settings["roomName_${i}"] ?: "Room ${i}"
                        def roomNewsText = state."roomNewsHeadline_${i}" ?: "<span style='color: #7f8c8d;'>Waiting for Sync...</span>"
                        def roomNewsTime = state."roomNewsSyncTime_${i}" ?: "--"
                        statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Morning News (${rName})</b></td><td style='padding: 8px;'>${roomNewsText}</td><td style='padding: 8px;'>${roomNewsTime}</td></tr>"
                    }
                }
            }
            statusText += "</table>"
            
            // --- AI Habit Tracker Dashboard ---
            statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>🧠 AI Habit & Anomaly Engine</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>User</th><th style='padding: 8px;'>Learned Departure Time</th><th style='padding: 8px;'>Current Status</th></tr>"
            
            if (state.learnedHabits && state.learnedHabits.size() > 0) {
                state.learnedHabits.each { uName, habitData ->
                    def timeStr = habitData.avgDepartureMins ? formatMinsToTime(habitData.avgDepartureMins) : "Learning..."
                    
                    // FIX: Use bulletproof logic for the AI Tracker
                    def arrived = state.hasArrivedToday != null && (state.hasArrivedToday[uName] == true || state.hasArrivedToday[uName] == "true")
                    def departed = state.hasDepartedToday != null && (state.hasDepartedToday[uName] == true || state.hasDepartedToday[uName] == "true")
                    
                    def statStr = ""
                    if (departed || !arrived) statStr = "<span style='color: #27ae60;'>Departed</span>"
                    else if (state.anomalyAlertedToday[uName]) statStr = "<span style='color: #c0392b; font-weight: bold;'>Anomaly (Running Late)</span>"
                    else statStr = "<span style='color: #7f8c8d;'>Home / Waiting</span>"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${applyAlias(uName)}</b></td><td style='padding: 8px;'>${timeStr}</td><td style='padding: 8px;'>${statStr}</td></tr>"
                }
            } else {
                statusText += "<tr><td colspan='3' style='padding: 8px; color: #7f8c8d;'>Gathering initial habit data...</td></tr>"
            }
            statusText += "</table>"
            
            // --- Incident Log ---
            statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>Butler Incident Log</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Event Type</th><th style='padding: 8px;'>Active Count</th><th style='padding: 8px;'>Last (Morning Report)</th></tr>"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Porch Motion (Night)</b></td><td style='padding: 8px;'>${state.nightMotionCount ?: 0}</td><td style='padding: 8px;'>${state.lastNightMotionCount ?: 0}</td></tr>"
            statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>Doorbell Rings (Away)</b></td><td style='padding: 8px;'>${state.awayDoorbellCount ?: 0}</td><td style='padding: 8px;'>${state.lastAwayDoorbellCount ?: 0}</td></tr>"
            statusText += "</table>"
            
            // --- Presence ---
            statusText += "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>Live House Roster</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>User</th><th style='padding: 8px;'>Status</th><th style='padding: 8px;'>Context</th></tr>"
            
            def allNames = []
            if (arrivalMode == "Automatic (Reads lock memory)") {
                settings.trackedLockCodes?.each { codeName ->
                    if (codeName.toLowerCase() == "admin code" && settings.adminUserAlias) { allNames << settings.adminUserAlias } else { allNames << codeName }
                }
            } else if (numLockUsers && numLockUsers > 0) {
                for (int i = 1; i <= (numLockUsers as Integer); i++) {
                    if (settings["lockUserName_${i}"]) allNames << settings["lockUserName_${i}"]
                }
            }
            allNames += (state.hasArrivedToday ?: [:]).keySet().findAll { it != "global" }
            allNames = allNames.unique().sort()
            
            allNames.each { uName ->
                // FIX: Use bulletproof logic for the Live House Roster
                def arrived = state.hasArrivedToday != null && (state.hasArrivedToday[uName] == true || state.hasArrivedToday[uName] == "true")
                def departed = state.hasDepartedToday != null && (state.hasDepartedToday[uName] == true || state.hasDepartedToday[uName] == "true")
                def isHome = arrived && !departed
                
                def arrStatus = isHome ? "<span style='color: #27ae60; font-weight: bold;'>Arrived</span>" : "<span style='color: #7f8c8d;'>Waiting...</span>"
                
                def contextText = ""
                if (isHome) {
                    contextText = "Present"
                } else if (departed) {
                    contextText = "Departed Today"
                } else {
                    contextText = state.resetReasons?."${uName}" ?: state.globalResetReason ?: "Awaiting First Entry"
                }
                
                statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${applyAlias(uName)}</b></td><td style='padding: 8px;'>${arrStatus}</td><td style='padding: 8px; color: #555;'><i>${contextText}</i></td></tr>"
            }
            statusText += "</table>"

            paragraph statusText
        }

        section("System Event History", hideable: false, hidden: false) {
            paragraph "<i>A running log of the last 30 voice announcements and status changes.</i>"
            if (state.historyLog && state.historyLog.size() > 0) {
                def histHtml = "<div style='max-height: 250px; overflow-y: auto; background-color: #f4f4f4; border: 1px solid #ccc; padding: 10px; font-family: monospace; font-size: 12px; line-height: 1.4;'>"
                state.historyLog.each { logEntry -> histHtml += "<div style='margin-bottom: 6px; border-bottom: 1px dashed #ddd; padding-bottom: 6px;'>${logEntry}</div>" }
                histHtml += "</div>"
                paragraph histHtml
            }
        }
        
        // --- NEW: BUTLER NOTES PORTAL ---
        section("📝 Butler Notes Portal", hideable: true, hidden: true) {
            paragraph "<i>Access this secure webpage from your phone or computer to leave targeted notes for specific members of the household.</i>"
            if (state.accessToken) {
                def cloudUrl = getFullApiServerUrl()
                def localUrl = getFullLocalApiServerUrl()
                paragraph "<div style='padding:10px; background-color:#d1ecf1; border:1px solid #bee5eb; color:#0c5460; border-radius:4px;'><b>Cloud Notes Portal URL (Use anywhere):</b><br><a href='${cloudUrl}/notes?access_token=${state.accessToken}' target='_blank' style='color:#0c5460; font-weight:bold; word-wrap:break-word;'>${cloudUrl}/notes?access_token=${state.accessToken}</a><br><br><b>Local Notes Portal URL (Use at home):</b><br><a href='${localUrl}/notes?access_token=${state.accessToken}' target='_blank' style='color:#0c5460; font-weight:bold; word-wrap:break-word;'>${localUrl}/notes?access_token=${state.accessToken}</a></div>"
            }
            
            input "announceNotesArrival", "bool", title: "Announce pending notes on Arrival?", defaultValue: true
            input "announceNotesMorning", "bool", title: "Announce pending notes during Morning Briefing?", defaultValue: true
            
            if (state.butlerNotes && state.butlerNotes.size() > 0) {
                paragraph "<b>Currently Pending Notes:</b><br>"
                state.butlerNotes.each { note ->
                    def timeTxt = note.when == "Time" ? " (At specific time)" : " (On ${note.when})"
                    paragraph "• <b>For ${note.target}</b>${timeTxt}: ${note.text}"
                }
                input "btnClearNotes", "button", title: "🗑️ Clear All Notes"
            } else {
                paragraph "<i>No notes currently pending.</i>"
            }
        }
        // --------------------------------
        
        // --- NEW: QUICK REPLIES ---
        section("Portal Quick Replies (Outdoor Intercom)", hideable: true, hidden: true) {
            paragraph "<i>Create up to 6 pre-set messages that you can trigger instantly from the web portal. These will announce on the Outdoor Speaker and automatically cancel any pending unanswered doorbell follow-ups.</i>"
            input "numQuickReplies", "number", title: "Number of Quick Replies (0-6)", defaultValue: 0, range: "0..6", submitOnChange: true
            
            if (numQuickReplies > 0) {
                for (int i = 1; i <= (numQuickReplies as Integer); i++) {
                    paragraph "<b>Quick Reply Button ${i}</b>"
                    input "quickReplyName_${i}", "text", title: "Button Label (e.g., Leave Package)", required: false
                    input "quickReplyText_${i}", "text", title: "Spoken Message", required: false, defaultValue: "Please leave the package at the front door. Thank you."
                }
            }
        }
        // --------------------------
        
        section("1. Global System Control & Audio Hardware", hideable: true, hidden: true) {
            input "dashboardStatusDevice", "capability.actuator", title: "App Status Tile Device", required: false
            
            paragraph "<b>Primary Triggers</b>"
            input "frontDoorbell", "capability.pushableButton", title: "Front Doorbell Button", required: false
            // FIX: Added 'multiple: true' so you can select an unlimited number of porch/door sensors
            input "frontDoorMotion", "capability.motionSensor", title: "Front Porch/Door Motion Sensor(s)", multiple: true, required: false
            
            paragraph "<hr>"
            input "masterSwitch", "capability.switch", title: "Master Enable/Pause Switch", required: false
            input "enableInbox", "bool", title: "Enable 'Silver Platter' Message Inbox (Hold missed alerts)?", defaultValue: false, submitOnChange: true
            input "enableChime", "bool", title: "Enable Pre-Speech 'Throat Clear' Chime?", defaultValue: false, submitOnChange: true
            if (enableChime) {
                input "chimeUrl", "text", title: "Chime Audio File URL (MP3/WAV)", description: "e.g., http://127.0.0.1:8080/local/chime.mp3", required: true
            }
            input "guestModeSwitch", "capability.switch", title: "Guest Mode Mute Switch", required: false
            input "notificationDevice", "capability.notification", title: "Silent Mode Notification Devices", multiple: true, required: false
            input "ttsTTL", "number", title: "Message Expiration / Time-To-Live (Minutes)", defaultValue: 5, required: false
            
            // --- THE PHANTOM QUIET HOURS ---
            paragraph "<hr>"
            paragraph "<b>Quiet Hours Muting</b>"
            input "quietHoursStart", "time", title: "Quiet Hours Start Time", required: false
            input "quietHoursEnd", "time", title: "Quiet Hours End Time", required: false
            input "quietVolume", "number", title: "Quiet Hours Max Volume (0-100)", required: false
            // --------------------------------------
            
            paragraph "<hr>"
            input "globalIndoorSpeaker", "capability.speechSynthesis", title: "Global Indoor Speaker(s)", multiple: true, required: false
            input "globalVolume", "number", title: "Global Speaker Volume (0-100)", required: false
            input "globalTVSwitch", "capability.actuator", title: "Global Entertainment / TV Device", required: false
            input "mediaPauseList", "capability.actuator", title: "Other Media Players to Pause/Mute", multiple: true, required: false
            input "butlerName", "text", title: "Your Butler's Name", required: false, defaultValue: "Alfred"
            input "btnTestGlobal", "button", title: "▶️ Test Global Indoor Speaker"
            
            paragraph "<hr>"
            input "outdoorSpeaker", "capability.speechSynthesis", title: "Outdoor/Porch Speaker", required: false
            input "outdoorVolume", "number", title: "Default Outdoor Volume (0-100)", required: false
            input "btnTestOutdoor", "button", title: "▶️ Test Outdoor Speaker"
            
            paragraph "<hr>"
            input "wakeupPadDelay", "number", title: "Speaker Amp Warm-up Delay (Seconds)", defaultValue: 0
            input "enableInternetCheck", "bool", title: "Enable Internet Connection Safety?", defaultValue: true
        }
        
        // --- FIX 3A: GLOBAL LIVING ROOM REPORT INPUTS ---
        section("Global Foyer / Living Room Morning Report", hideable: true, hidden: true) {
            paragraph "<i>Used to deliver Global Incident Reports if you walk into the main living space in the morning before triggering a local room routine.</i>"
            input "butlerLrMotion", "capability.motionSensor", title: "Living Room Motion Sensor", required: false
            input "butlerLrSpeaker", "capability.speechSynthesis", title: "Living Room Speaker", required: false
            input "butlerLrVolume", "number", title: "Announcement Volume (0-100)", required: false
        }
        // ------------------------------------------------

        section("Arrival Greetings & Smart Locks", hideable: true, hidden: true) {
            input "frontDoorLock", "capability.lock", title: "Front Door Smart Lock", required: false, submitOnChange: true
            input "arrivalVolume", "number", title: "Welcome Home Announcement Volume (0-100)", required: false
            input "arrivalFoyerSpeaker", "capability.speechSynthesis", title: "Foyer / Entryway Speaker (Plays Full Greeting)", required: false

            input "arrivalIndoorSpeaker", "bool", title: "Play Third-Party Arrival Notice to Rest of House?", defaultValue: false, submitOnChange: true
            if (arrivalIndoorSpeaker) {
                input "indoorArrivalMessage", "text", title: "Indoor Notice Message", defaultValue: "%name% has arrived home.", required: false
                input "indoorArrivalVolume", "number", title: "Indoor Notice Volume (0-100)", required: false
                input "arrivalNoticeRoutingMode", "enum", title: "Notice Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only", submitOnChange: true
            }
            
            // --- NEW: GUEST / VIP USERS ---
            input "guestUsers", "enum", title: "Guest Users (Omit from Missing/Curfew Warnings)", options: lockUsers, multiple: true, required: false
            input "guestCustomUsers", "text", title: "Custom Guest Names (Comma separated)", required: false
            // ------------------------------
            
            paragraph "<hr>"
            input "enableExtendedAbsence", "bool", title: "Enable Extended Absence (Vacation) Greetings?", defaultValue: false, submitOnChange: true
            if (enableExtendedAbsence) {
                input "extendedAbsenceHours", "number", title: "Absence Threshold (Hours)", defaultValue: 48, required: false
                for (int m = 1; m <= 5; m++) { input "extAbsenceMessage_${m}", "text", title: "Extended Message ${m}", required: false, defaultValue: getDefaultMessages("ExtendedAbsence")[m-1] }
            }
            
            input "enableDurationAware", "bool", title: "Enable Duration-Aware Arrival Context (Quick trip vs Long day)?", defaultValue: false
            
            paragraph "<hr>"

            input "btnTestArrival", "button", title: "▶️ Test Welcome Home Audio"
            input "disableGlobalAnnouncements", "bool", title: "Ignore Keys & Manual Unlocks?", defaultValue: false
            input "ignoredCodes", "enum", title: "Silent / Ghost Codes", options: lockUsers, multiple: true, required: false
            input "ignoredCustomCodes", "text", title: "Custom Silent Codes", required: false
            input "arrivalMode", "enum", title: "Arrival Detection Mode", options: ["Automatic (Reads lock memory)", "Manual (Assign names to slots)"], defaultValue: "Automatic (Reads lock memory)", submitOnChange: true
            
            if (arrivalMode == "Automatic (Reads lock memory)") {
                input "trackedLockCodes", "enum", title: "Select Codes to Track", options: lockUsers, multiple: true, required: false, submitOnChange: true
                input "adminUserAlias", "text", title: "Admin Code Alias", required: false
                for (int m = 1; m <= 10; m++) { input "autoGreeting_${m}", "text", title: "Dynamic Welcome Message ${m}", required: false, defaultValue: getDefaultMessages("Arrival")[m-1] }
            } else {
                for (int d = 1; d <= 10; d++) { input "defaultArrivalMessage_${d}", "text", title: "Default Arrival Message ${d}", required: false, defaultValue: getDefaultMessages("Arrival")[d-1] }
                input "numLockUsers", "number", title: "Number of Manual User Slots (1-5)", required: true, defaultValue: 1, range: "1..5", submitOnChange: true
                if (numLockUsers > 0) {
                    for (int i = 1; i <= (numLockUsers as Integer); i++) {
                        input "lockUserName_${i}", lockUsers.size() > 0 ? "enum" : "text", title: "User ${i} Lock Code Name", options: lockUsers, required: false
                        for (int m = 1; m <= 10; m++) { input "lockGreeting_${i}_${m}", "text", title: "User ${i} Welcome Message ${m}", required: false, defaultValue: getDefaultMessages("Arrival")[m-1] }
                    }
                }
            }
            
            // --- NEW: DEDICATED PRESENCE SENSOR LINKING ENGINE ---
            paragraph "<hr>"
            paragraph "<b>Presence Sensor & Lock Linking</b><br><i>Link your presence sensors to your lock code names. If a sensor arrives but the door isn't unlocked within 10 minutes, the Butler will automatically mark them home. Works with both Automatic and Manual modes!</i>"
            input "numPresenceMappings", "number", title: "Number of Presence Sensor Links (0-10)", defaultValue: 0, submitOnChange: true
            if (numPresenceMappings > 0) {
                for (int i = 1; i <= numPresenceMappings; i++) {
                    input "presenceUserName_${i}", lockUsers.size() > 0 ? "enum" : "text", title: "User ${i} Name (Matches Lock Code)", options: lockUsers, required: false
                    input "fallbackPresence_${i}", "capability.presenceSensor", title: "User ${i} Presence Sensor", required: false
                }
            }

            def arrPrevBase = arrivalMode == "Automatic (Reads lock memory)" ? (settings["autoGreeting_1"] ?: "Welcome home %name%.") : (settings["defaultArrivalMessage_1"] ?: "Welcome home.")
            def arrPrev = applyDynamicVars(arrPrevBase.replace("%name%", "Guest"))
            if (settings.enableHouseRoster) arrPrev += " You are the first to arrive. The house is empty."
            if (settings.enableMailCheck) arrPrev += " Pardon the reminder, but the mail was delivered earlier today and still needs to be retrieved."
            def inPrevStr = settings.arrivalIndoorSpeaker ? "<br><br><b>Rest of House Notice (Routing: ${settings.arrivalNoticeRoutingMode ?: 'Global Indoor Speaker Only'}):</b><br><i>" + applyDynamicVars((settings.indoorArrivalMessage ?: "%name% has arrived home.").replace("%name%", "Guest")) + "</i>" : ""
            paragraph "<div style='${prevStyle}'><b>Live Arrival Preview:</b><br><i>${arrPrev}</i>${inPrevStr}</div>"
        }

        section("Health & Wellness Concierge", hideable: true, hidden: true) {
            paragraph "<i>Track medical and dental appointments for household members. The Butler can remind them during the morning briefing or dynamically during a user-defined daytime window.</i>"
            
            input "enableHealthMorning", "bool", title: "Announce in Morning Briefing (Per Room)?", defaultValue: true
            input "enableHealthWindow", "bool", title: "Announce during Daytime Window (Global)?", defaultValue: false, submitOnChange: true

            if (enableHealthWindow) {
                input "healthWindowStart", "time", title: "Window Start Time", required: true
                input "healthWindowEnd", "time", title: "Window End Time", required: true
                input "healthRoutingMode", "enum", title: "Window Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only"
                input "healthVolume", "number", title: "Announcement Volume (0-100)", required: false
                input "btnTestHealth", "button", title: "▶️ Test Health Reminder"
            }

            paragraph "<hr>"
            input "numHealthProfiles", "number", title: "Number of Health Profiles (0-5)", defaultValue: 0, range: "0..5", submitOnChange: true

            if (numHealthProfiles > 0) {
                for (int i = 1; i <= (numHealthProfiles as Integer); i++) {
                    paragraph "<b>Health Profile ${i}</b>"
                    input "healthUser_${i}", "text", title: "User Name (Matches Lock/Presence)", required: false
                    input "lastDental_${i}", "text", title: "Last Dental Cleaning (YYYY-MM-DD)", description: "Triggers after 6 months", required: false
                    input "lastMedical_${i}", "text", title: "Last Annual Physical (YYYY-MM-DD)", description: "Triggers after 1 year", required: false
                    input "lastVision_${i}", "text", title: "Last Vision/Eye Exam (YYYY-MM-DD)", description: "Triggers after 1 year", required: false
                }
            }
        }
        
        section("Calendar & Appointment Reminders", hideable: true, hidden: true) {
            paragraph "<i><b>Note:</b> For Google Calendar, use the 'Secret address in iCal format' (.ics link) found in your calendar settings.</i>"
            input "enableCalendar", "bool", title: "Enable Calendar Reminders?", defaultValue: false, submitOnChange: true
            if (enableCalendar) {
                input "calendarType", "enum", title: "Calendar Source Type", options: ["Built-In Device (Advanced Calendar App)", "iCal / Google Calendar URL"], defaultValue: "Built-In Device (Advanced Calendar App)", submitOnChange: true
                if (calendarType == "Built-In Device (Advanced Calendar App)") {
                    input "calendarDevice", "capability.sensor", title: "Calendar Device", required: true
                    input "calEventTitleAttr", "text", title: "Event Title Attribute", defaultValue: "eventTitle", required: true
                    input "calEventTimeAttr", "text", title: "Event Time Attribute (Epoch)", defaultValue: "eventEpoch", required: true
                } else {
                    input "calendarUrl", "text", title: "iCal or Google Calendar URL (.ics)", required: true
                    input "calSyncMethod", "enum", title: "Sync Method", options: ["Google Apps Script Webhook (Instant)", "Standard .ics Polling (Delayed)"], defaultValue: "Google Apps Script Webhook (Instant)", submitOnChange: true
                    
                    if (calSyncMethod == "Standard .ics Polling (Delayed)") {
                        input "calPollInterval", "enum", title: "Polling Interval", options: ["15 Minutes", "30 Minutes", "1 Hour", "3 Hours"], defaultValue: "1 Hour", submitOnChange: true
                    } else {
                        paragraph "<div style='padding:8px; background-color:#d4edda; border:1px solid #c3e6cb; color:#155724; border-radius:4px;'><i><b>Webhook Mode Active:</b> The app is listening for instant pushes from your Google Script. Background polling is disabled.</i></div>"
                    }
                }
                
                // --- MOVED: Travel & Mapping Intelligence is now outside the local-only condition ---
                paragraph "<hr>"
                paragraph "<b>🌍 Travel & Mapping Intelligence</b>"
                input "googleMapsApiKey", "text", title: "Google Maps API Key", description: "Enter your key from Google Cloud Console", required: false
                input "homeAddress", "text", title: "Home Address", description: "e.g., 123 Main St, Your City, State", required: false
                input "leaveNowBuffer", "number", title: "Leave Now Buffer (Minutes)", defaultValue: 5, description: "Warning threshold before you MUST leave."
                
                // --- THIS IS THE MISSING BUTTON AND FAILSAFE ---
                input "apiCallLimit", "number", title: "Monthly API Call Limit", defaultValue: 500, description: "Failsafe limit to prevent billing charges."
                input "btnTestGoogleApi", "button", title: "▶️ Test Google API Connection"
                input "enableTravelPush", "bool", title: "Send Event & Gas Addresses to Phones via Push?", description: "Requires 'Silent Mode Notification Devices' to be configured in Section 1.", defaultValue: false, submitOnChange: true
                
                def apiCount = state.apiCallCount ?: 0
                def apiLimit = settings.apiCallLimit ?: 500
                paragraph "<div style='${prevStyle}'><b>API Usage This Month:</b> ${apiCount} / ${apiLimit} Calls</div>"
                // -----------------------------------------------

                paragraph "<hr>"
                
                input "calAlertModes", "mode", title: "Allowed Modes for Alerts", multiple: true, required: false
                input "calAlertIntervals", "enum", title: "Warning Intervals", options: ["3 Hours", "2 Hours", "1 Hour", "30 Minutes", "15 Minutes"], multiple: true, required: false
                input "calRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Follow-Me + Fallback (Global ONLY if no motion)", submitOnChange: true
                input "calVolume", "number", title: "Announcement Volume (0-100)", required: false

                for (int d = 1; d <= 4; d++) { input "calMessage_${d}", "text", title: "Reminder Message ${d}", required: false, defaultValue: getDefaultMessages("Calendar")[d-1] }
                input "btnTestCalendar", "button", title: "▶️ Test Calendar Alert Audio"
                
                def calPrev = applyDynamicVars((settings["calMessage_1"] ?: "%interruption%, but you have %event% starting in %time%.").replace("%event%", "a meeting").replace("%time%", "1 Hour"))
                paragraph "<div style='${prevStyle}'><b>Live Calendar Preview:</b><br><i>${calPrev}</i></div>"
            }
        }

        section("Important Email Alerts (Google Webhook)", hideable: true, hidden: true) {
            paragraph "<i><b>Note:</b> Requires the Google Apps Script bridge to be configured and running.</i>"
            input "enableEmailAlerts", "bool", title: "Enable Incoming Email Alerts?", defaultValue: false, submitOnChange: true
            
            if (enableEmailAlerts) {
                input "enableDeliveryTracking", "bool", title: "Announce Package Deliveries?", defaultValue: true
                input "enableOomaVoicemail", "bool", title: "Announce Ooma Voicemails?", defaultValue: true
                
                input "emailAlertModes", "mode", title: "Allowed Modes for Alerts", multiple: true, required: false
                input "emailRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only", submitOnChange: true
                input "emailVolume", "number", title: "Announcement Volume (0-100)", required: false
                input "emailPrefix", "text", title: "Announcement Prefix", defaultValue: "%interruption%, you have just received an important email from", required: false
                
                def emailPrev = applyDynamicVars((settings.emailPrefix ?: "%interruption%, you have just received an important email from") + " John Doe. The subject is: Project Update.")
                def pkgPrev = applyDynamicVars("%interruption%, I am seeing a delivery notification from Amazon. The subject is: Your package is out for delivery. You have a package arriving today.")
                def oomaPrev = applyDynamicVars("%interruption%, you have a new Ooma voicemail from John Doe, received at 2:00 PM. The message says: Please call me back when you get this.")
                
                def previewHtml = "<div style='${prevStyle}'><b>Live Email Preview:</b><br><i>${emailPrev}</i>"
                if (settings.enableDeliveryTracking) previewHtml += "<br><br><b>Live Package Preview:</b><br><i>${pkgPrev}</i>"
                if (settings.enableOomaVoicemail) previewHtml += "<br><br><b>Live Ooma Preview:</b><br><i>${oomaPrev}</i>"
                previewHtml += "</div>"
                
                paragraph previewHtml
            }
        }
        
        section("Organic Breaking News Intercept", hideable: true, hidden: true) {
            input "enableBreakingNews", "bool", title: "Enable Organic News Intercept?", defaultValue: false, submitOnChange: true
            if (enableBreakingNews) {
                input "breakingNewsFeed", "text", title: "Breaking News RSS URL", defaultValue: "https://feeds.npr.org/1001/rss.xml"
                input "breakingNewsInterval", "enum", title: "Base Check Interval (AI will randomize +/- 30%)", options: ["15 Minutes", "30 Minutes", "1 Hour", "3 Hours"], defaultValue: "1 Hour", submitOnChange: true
                input "breakingNewsModes", "mode", title: "Allowed Modes for Breaking News", multiple: true, required: false
                input "breakingNewsRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Follow-Me + Fallback (Global ONLY if no motion)", submitOnChange: true
                input "breakingNewsVolume", "number", title: "Announcement Volume (0-100)", required: false
                
                for (int d = 1; d <= 4; d++) { input "breakingNewsPrefix_${d}", "text", title: "Prefix ${d}", required: false, defaultValue: getDefaultMessages("BreakingNews")[d-1] }
                input "btnTestBreakingNews", "button", title: "▶️ Test Breaking News Audio"
                
                def bnPrev = applyDynamicVars(settings["breakingNewsPrefix_1"] ?: "%interruption%, but a major news event has just occurred.") + " [Live Breaking Headline]."
                paragraph "<div style='${prevStyle}'><b>Live Breaking News Preview:</b><br><i>${bnPrev}</i></div>"
            }
        }
        
        section("Office Interceptor (Science & Tech News)", hideable: true, hidden: true) {
            input "enableOffice", "bool", title: "Enable Office Interceptor?", defaultValue: false, submitOnChange: true
            if (enableOffice) {
                paragraph "<i>Assign a virtual switch. When turned ON, the butler will fetch the latest tech news and deliver a briefing to the assigned speaker.</i>"
                input "officeSwitch", "capability.switch", title: "Office Trigger Switch", required: true
                input "officeSpeaker", "capability.speechSynthesis", title: "Office Speaker", required: true
                input "officeVolume", "number", title: "Speaker Volume (0-100)", required: false
                input "officeModes", "mode", title: "Allowed Modes", multiple: true, required: false
                input "officeFeed", "enum", title: "News Source", options: ["TechCrunch": "TechCrunch", "Engadget": "Engadget", "Wired": "Wired", "Ars Technica": "Ars Technica", "Custom": "Custom URL"], defaultValue: "TechCrunch", submitOnChange: true
                input "officeDebounceHours", "number", title: "Minimum Hours Between Briefings (Cooldown)", defaultValue: 4, required: false
                if (officeFeed == "Custom") {
                    input "officeCustomUrl", "text", title: "Custom RSS URL", required: true
                }
            }
        }

        section("Household Maintenance & Task Alerts", hideable: true, hidden: true) {
            input "middayMaintenanceDevice", "capability.sensor", title: "House Maintenance Dashboard Device", required: false, submitOnChange: true
            
            if (middayMaintenanceDevice) {
                input "taskDashboardUrl", "text", title: "Dashboard Shortcut URL (Adds a link to the Web Portal)", description: "e.g., https://cloud.hubitat.com/api/...", required: false
                
                paragraph "<hr>"
                input "enableMiddayMaintenance", "bool", title: "Enable Random Midday Reminder (11am-3pm)?", defaultValue: false, submitOnChange: true
                if (enableMiddayMaintenance) {
                    paragraph "<i>The Butler will secretly pick a random time between 11:00 AM and 3:00 PM every day to politely remind you of any overdue tasks. It will remain silent if the house is empty or if guests are present.</i>"
                    input "middayMaintenanceModes", "mode", title: "Allowed Modes for Reminder", multiple: true, required: false
                    input "middayRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only", submitOnChange: true
                    input "middayVolume", "number", title: "Announcement Volume (0-100)", required: false
                    input "btnTestMidday", "button", title: "▶️ Test Midday Maintenance Reminder"
                }

                paragraph "<hr>"
                input "enableRealTimeTasks", "bool", title: "Enable Real-Time 'Newly Due' Task Alerts?", defaultValue: false, submitOnChange: true
                if (enableRealTimeTasks) {
                    paragraph "<i>The Butler will instantly announce the moment a task switches to 'due'.</i>"
                    input "realTimeTaskRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only", submitOnChange: true
                    input "realTimeTaskVolume", "number", title: "Announcement Volume (0-100)", required: false
                }
            }
        }
        
       section("Quick Exit (Away Mode Farewell)", hideable: true, hidden: true) {
            paragraph "<i>If the house enters Away Mode, the next door opened within 5 minutes will trigger a farewell message and a gas price scout.</i>"
            input "enableQuickExit", "bool", title: "Enable Quick Exit Farewell?", defaultValue: false, submitOnChange: true
            if (enableQuickExit) {
                input "quickExitDoors", "capability.contactSensor", title: "Exit Doors", multiple: true, required: true
                input "quickExitModes", "mode", title: "Away Modes to Watch For", multiple: true, required: true
                input "quickExitRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Outdoor Speaker Only", submitOnChange: true
                input "quickExitVolume", "number", title: "Farewell Volume (0-100)", required: false
                input "btnTestQuickExit", "button", title: "▶️ Test Quick Exit Audio"
            }
        }
        
        section("Contextual Departures & Habit Tracking", hideable: true, hidden: true) {
            input "frontDoorContact", "capability.contactSensor", title: "Front Door Contact Sensor", required: false
            input "numDepartureUsers", "number", title: "Number of Departure Profiles (0-5)", required: true, defaultValue: 0, range: "0..5", submitOnChange: true
            
            input "enableCoatCheck", "bool", title: "Enable Departure 'Coat Check' (Weather Warnings)?", defaultValue: false, submitOnChange: true
            if (enableCoatCheck) {
                input "depWeatherDevice", "capability.temperatureMeasurement", title: "Weather Device for Coat Check", required: true
            }
            
            if (numDepartureUsers > 0) {
                for (int i = 1; i <= (numDepartureUsers as Integer); i++) {
                    paragraph "<b>Departure Profile ${i}</b>"
                    input "depUserName_${i}", "text", title: "User Name (replaces %name%)", required: false
                    input "depType_${i}", "enum", title: "Profile Type", options: ["Work", "School", "General"], defaultValue: "Work", submitOnChange: true
                    input "depSwitch_${i}", "capability.switch", title: "Context Switch (e.g. Work Day)", required: false
                    input "depSickSwitch_${i}", "capability.switch", title: "Sick Day Override Switch", required: false
                    input "depModes_${i}", "mode", title: "Allowed House Modes", multiple: true, required: false
                    input "depTimeStart_${i}", "time", title: "Departure Window Start", required: false
                    input "depTimeEnd_${i}", "time", title: "Departure Window End", required: false
                    input "depDelay_${i}", "number", title: "Greeting Delay (Seconds)", defaultValue: 5, required: false
                    input "depRoutingMode_${i}", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Outdoor Speaker Only", submitOnChange: true
                    input "depVolume_${i}", "number", title: "Departure Volume (0-100)", required: false
                    input "btnTestDeparture_${i}", "button", title: "▶️ Test Departure Profile ${i} Audio"
                    
                    def selMsgs = getDefaultMessages(settings["depType_${i}"] ?: "Work")
                    for (int m = 1; m <= 10; m++) { input "depMessage_${i}_${m}", "text", title: "Message ${m}", required: false, defaultValue: selMsgs[m-1] }
                }
                
                def depType = settings["depType_1"] ?: "Work"
                def depPrevBase = settings["depMessage_1_1"] ?: getDefaultMessages(depType)[0]
                def depPrev = applyDynamicVars(depPrevBase.replace("%name%", settings["depUserName_1"] ?: "Guest"))
                paragraph "<div style='${prevStyle}'><b>Live Departure Preview (Routing: ${settings["depRoutingMode_1"] ?: 'Outdoor Speaker Only'}):</b><br><i>${depPrev}</i></div>"
            }
        }

        section("Master Perimeter & Estate Security", hideable: true, hidden: true) {
            paragraph "<i>Designate all critical access points across the property you wish the Butler to guard. This includes primary house doors, secondary gates, and livestock coops. The Butler will automatically verify these are secured during the Good Night routine.</i>"
            input "estateDoors", "capability.contactSensor", title: "All Estate Doors, Gates & Coops", multiple: true, required: false
            input "estateLocks", "capability.lock", title: "All Estate Smart Locks", multiple: true, required: false
            
            paragraph "<hr>"
            paragraph "<b>Advanced Weather Logic</b>"
            input "stormSwitch", "capability.switch", title: "Severe Weather / Storm Override Switch", required: false
        }
        
        section("Guest Wi-Fi Details", hideable: true, hidden: true) {
            input "enableWifiPortal", "bool", title: "Enable Wi-Fi Info in Portal?", defaultValue: false, submitOnChange: true
            if (enableWifiPortal) {
                paragraph "<i>Add your Guest Wi-Fi details here to display a quick-announce button on the web portal.</i>"
                input "wifiSSID", "text", title: "Wi-Fi Network Name (SSID)", required: false
                input "wifiPassword", "text", title: "Wi-Fi Password", required: false
                input "wifiRoutingMode", "enum", title: "Announcement Routing", options: getRoutingOptions(), defaultValue: "Follow-Me + Fallback (Global ONLY if no motion)"
                input "wifiVolume", "number", title: "Announcement Volume (0-100)", required: false
            }
        }
        
        section("Estate Directory & Emergency Contacts", hideable: true, hidden: true) {
            input "enableDirectory", "bool", title: "Enable Estate Directory?", defaultValue: false, submitOnChange: true
            if (enableDirectory) {
                paragraph "<i>Create a digital Rolodex. Pair a virtual switch to a contact. When turned ON via a dashboard or voice assistant, the Butler will announce the details and push them to your phones.</i>"
                input "directoryRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Follow-Me + Fallback (Global ONLY if no motion)"
                input "directoryVolume", "number", title: "Announcement Volume (0-100)", required: false
                input "numContacts", "number", title: "Number of Contacts (1-10)", defaultValue: 1, range: "1..10", submitOnChange: true

                if (numContacts > 0) {
                    for (int i = 1; i <= (numContacts as Integer); i++) {
                        paragraph "<b>Contact Profile ${i}</b>"
                        input "contactName_${i}", "text", title: "Service Name (e.g., Plumber, Pediatrician)", required: false
                        input "contactInfo_${i}", "text", title: "Contact Info / Phone Number", required: false
                        input "contactSwitch_${i}", "capability.switch", title: "Trigger Switch", required: false
                    }
                }
            }
        }
        
        section("Local Voice Zones (Rooms)", hideable: true, hidden: true) {
            input "numRooms", "number", title: "Number of Local Voice Zones (1-5)", required: true, defaultValue: 1, range: "1..5", submitOnChange: true
        }

        if (numRooms > 0) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                section("${settings["roomName_${i}"] ?: "Room Zone ${i}"}", hideable: true, hidden: true) { 
                    href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "Configure ${settings["roomName_${i}"] ?: "Room ${i}"}") 
                }
            }
        }

       section("Indoor Doorbell / Intercom Routing", hideable: true, hidden: true) {
            input "enableIndoorRouting", "bool", title: "Enable Targeted Indoor Routing?", defaultValue: false, submitOnChange: true
            if (enableIndoorRouting) {
                // FIX: Added the missing Follow-Me Occupancy Timeout config
                input "followMeTimeout", "number", title: "Follow-Me Occupancy Window (Minutes)", defaultValue: 5, description: "Keep room active for X minutes after last motion."
                
                input "indoorDoorbellMsg", "text", title: "Announcement Message", defaultValue: "This is %butler%. %interruption%, but there is a visitor at the front door."
                input "indoorDoorbellRoutingMode", "enum", title: "Indoor Routing Mode", options: getRoutingOptions(), defaultValue: "Follow-Me + Fallback (Global ONLY if no motion)", submitOnChange: true
                input "indoorRouteMuteDND", "bool", title: "Mute During Do Not Disturb?", defaultValue: true
                input "indoorRouteRestrictedModes", "mode", title: "Restricted Modes (Do Not Announce)", multiple: true, required: false
                input "btnTestIndoorRouting", "button", title: "▶️ Test Indoor Routing"
                input "numRoutingRooms", "number", title: "Number of Routing Zones (1-7)", defaultValue: 0, range: "0..7", submitOnChange: true

                if (numRoutingRooms > 0) {
                    for (int i = 1; i <= (numRoutingRooms as Integer); i++) {
                        // FIX: Added the missing Last Active indicator
                        def lastAct = state.lastZoneMotion ? state.lastZoneMotion["${i}"] : 0
                        def sinceStr = lastAct ? "${Math.round((new Date().time - lastAct)/60000)}m ago" : "Never"
                        paragraph "<b>Routing Zone ${i}</b> (Last Active: ${sinceStr})"
                        
                        input "routeRoomName_${i}", "text", title: "Zone Name", required: false, defaultValue: "Zone ${i}"
                        input "routeMotion_${i}", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
                        input "routeSpeaker_${i}", "capability.speechSynthesis", title: "Target Speaker", required: false
                        input "routeVolume_${i}", "number", title: "Announcement Volume (0-100)", required: false
                        // FIX: Updated to 'capability.switch' for Roku TV compatibility
                        input "routeTVSwitch_${i}", "capability.switch", title: "Entertainment / TV Device (Roku, TV, etc.)", multiple: true, required: false
                        input "routeGNSwitch_${i}", "capability.switch", title: "Mute Zone when this Switch is ON (Good Night)", required: false // <-- NEW MUTE SWITCH
                    }
                }
                def inPrev = applyDynamicVars(settings.indoorDoorbellMsg ?: "This is %butler%. %interruption%, but there is a visitor at the front door.")
                paragraph "<div style='${prevStyle}'><b>Live Indoor Routing Preview:</b><br><i>${inPrev}</i></div>"
            }
        }
        
        section("Meal Time Routine (Dinner Voice Butler)", hideable: true, hidden: true) {
            input "enableMealTime", "bool", title: "Enable Meal Time Routine?", defaultValue: false, submitOnChange: true
            if (enableMealTime) {
                input "mealTimeSwitch", "capability.switch", title: "Meal Time Trigger Switch", required: false
                
                // --- NEW: MEAL TIME BUTTON TRIGGER ---
                input "mealTimeButton", "capability.pushableButton", title: "Meal Time Trigger Button", required: false, submitOnChange: true
                if (mealTimeButton) {
                    input "mealTimeButtonNumber", "number", title: "Button Number", defaultValue: 1, required: true
                    input "mealTimeButtonAction", "enum", title: "Button Action", options: ["pushed", "held", "doubleTapped", "released"], defaultValue: "pushed", required: true
                    paragraph "<i>Note: Button presses bypass Global routing and exclusively announce to 'Follow-Me (Active Rooms Only)'.</i>"
                }
                // -------------------------------------

                input "mealTimeSpeaker", "capability.speechSynthesis", title: "Dedicated Meal Time Speaker", required: false
                input "mealTimeVolume", "number", title: "Announcement Volume (0-100)", required: false
                input "mealTimeRoutingMode", "enum", title: "Dinner Bell Routing Mode (For Switch/Test)", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only", submitOnChange: true
                
                input "mealTimeDinnerBell", "text", title: "Base Message", defaultValue: "%interruption%, but dinner is now served.", required: false
                input "mealTimeAbsentee", "bool", title: "Enable Absentee Roll Call?", defaultValue: false
                input "mealTimeOnThisDay", "bool", title: "Enable 'On This Day' Historical Fact?", defaultValue: false
                input "mealTimeNewsWeather", "bool", title: "Enable Evening Digest (News & Weather)?", defaultValue: false, submitOnChange: true
                if (mealTimeNewsWeather) { input "mealTimeNewsFeed", "text", title: "RSS News Feed URL", defaultValue: "https://feeds.npr.org/1001/rss.xml"; input "mealTimeWeatherDevice", "capability.temperatureMeasurement", title: "Weather / Temperature Device", required: false }
                
                input "enableMealQuestions", "bool", title: "Enable Meal Time Conversation Starters?", defaultValue: false, submitOnChange: true
                if (enableMealQuestions) {
                    input "mealTimeQuestionsFile", "text", title: "Custom Questions File (.txt)", description: "e.g. dinner_questions.txt", required: false
                }

                input "btnTestMealNews", "button", title: "▶️ Test Evening News Fetch"
                input "btnTestMealTime", "button", title: "▶️ Test Meal Time Audio"
                
                def mealPrev = settings.mealTimeDinnerBell ?: "%interruption%, but dinner is now served."
                mealPrev = applyDynamicVars(mealPrev)
                paragraph "<div style='${prevStyle}'><b>Live Meal Time Preview:</b><br><i>${mealPrev}</i></div>"
            }
        }

        // --- NEW: HEADED HOME FEATURE ---
        section("Headed Home (On The Way) Announcements", hideable: true, hidden: true) {
            input "enableHeadedHome", "bool", title: "Enable Headed Home Announcements?", defaultValue: false, submitOnChange: true
            if (enableHeadedHome) {
                paragraph "<i>Assign a virtual switch for each user. When turned ON (e.g. via a Google Home or Alexa driving routine), the Butler will announce they are on their way.</i>"
                input "numHeadedHome", "number", title: "Number of Headed Home Profiles (0-5)", defaultValue: 0, range: "0..5", submitOnChange: true
                
                if (numHeadedHome > 0) {
                    for (int i = 1; i <= (numHeadedHome as Integer); i++) {
                        paragraph "<b>Headed Home Profile ${i}</b>"
                        input "hhUser_${i}", "text", title: "User Name (replaces %name%)", required: false
                        input "hhSwitch_${i}", "capability.switch", title: "Trigger Switch", required: false
                        input "hhRouting_${i}", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only"
                        input "hhVolume_${i}", "number", title: "Announcement Volume (0-100)", required: false
                        input "hhMessage_${i}", "text", title: "Announcement Message", defaultValue: "%interruption%, but %name% is on their way home.", required: false
                        input "btnTestHeadedHome_${i}", "button", title: "▶️ Test Headed Home Audio ${i}"
                    }
                }
            }
        }
        // --------------------------------

        section("Guest / Party Mode (Doorbell Intercept)", hideable: true, hidden: true) {
            input "enablePartyMode", "bool", title: "Enable Guest/Party Mode Doorbell?", defaultValue: false, submitOnChange: true
            if (enablePartyMode) {
                paragraph "<i>Assign a virtual switch. When ON, the butler will override Do Not Disturb and After Hours to welcome expected guests.</i>"
                input "partyModeSwitch", "capability.switch", title: "Party Mode Virtual Switch", required: true
                input "partyRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Outdoor Speaker Only", submitOnChange: true
                input "partyVolume", "number", title: "Announcement Volume (0-100)", required: false
                input "partyDebounce", "number", title: "Cooldown (Minutes)", defaultValue: 2, required: false
                
                for (int d = 1; d <= 3; d++) { input "partyMessage_${d}", "text", title: "Party Greeting ${d}", required: false, defaultValue: getDefaultMessages("PartyMode")[d-1] }
                input "btnTestPartyMode", "button", title: "▶️ Test Party Mode Audio"
                
                def pPrev = applyDynamicVars(settings["partyMessage_1"] ?: getDefaultMessages("PartyMode")[0])
                paragraph "<div style='${prevStyle}'><b>Live Party Mode Preview:</b><br><i>${pPrev}</i></div>"
            }
        }

        section("Perimeter Guarding (Do Not Disturb)", hideable: true, hidden: true) {
            input "dndSwitch", "capability.switch", title: "Do Not Disturb Toggle Switch", required: false
            input "dndModes", "mode", title: "Do Not Disturb Modes", multiple: true, required: false
            input "dndRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Outdoor Speaker Only", submitOnChange: true
            input "dndMotionDebounce", "number", title: "Motion Sensor Cooldown (Minutes)", defaultValue: 10, required: false
            input "btnTestDND", "button", title: "▶️ Test DND Intercept Audio"
            for (int d = 1; d <= 10; d++) { input "dndMessage_${d}", "text", title: "DND Audio Message ${d}", required: false, defaultValue: getDefaultMessages("DND")[d-1] }

            def dndPrev = applyDynamicVars(settings["dndMessage_1"] ?: getDefaultMessages("DND")[0])
            paragraph "<div style='${prevStyle}'><b>Live DND Intercept Preview (Routing: ${settings.dndRoutingMode ?: 'Outdoor Speaker Only'}):</b><br><i>${dndPrev}</i></div>"
        }
        
        section("Daytime Doorbell Acknowledgment", hideable: true, hidden: true) {
            input "enableDaytimeDoorbell", "bool", title: "Enable Daytime Doorbell Acknowledgment?", defaultValue: false, submitOnChange: true
            if (enableDaytimeDoorbell) {
                input "daytimeRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Outdoor Speaker Only", submitOnChange: true
                input "daytimeDoorbellVolume", "number", title: "Announcement Volume (0-100)", required: false
                input "daytimeDoorbellDebounce", "number", title: "Cooldown (Minutes)", defaultValue: 2, required: false
                for (int d = 1; d <= 20; d++) { input "daytimeMessage_${d}", "text", title: "Daytime Message ${d}", required: false, defaultValue: getDefaultMessages("Daytime")[d-1] }
                input "btnTestDaytime", "button", title: "▶️ Test Daytime Audio"
            }
            paragraph "---"
            input "enableDaytimeFollowUp", "bool", title: "Enable Unanswered Door Follow-Up?", defaultValue: false, submitOnChange: true
            if (enableDaytimeFollowUp) {
                input "daytimeDoorContact", "capability.contactSensor", title: "Front Door Contact Sensor", required: true
                input "daytimeFollowUpDelay", "number", title: "Wait Time (Minutes)", defaultValue: 3, required: false
                for (int d = 1; d <= 5; d++) { input "daytimeNoAnswer_${d}", "text", title: "No Answer Message ${d}", required: false, defaultValue: getDefaultMessages("NoAnswer")[d-1] }
            }

            if (enableDaytimeDoorbell) {
                def dayPrev = applyDynamicVars(settings["daytimeMessage_1"] ?: getDefaultMessages("Daytime")[0])
                if (settings.enableDaytimeFollowUp) dayPrev += "</i><br><br><b>Unanswered Follow-Up Preview:</b><br><i>" + applyDynamicVars(settings["daytimeNoAnswer_1"] ?: getDefaultMessages("NoAnswer")[0])
                paragraph "<div style='${prevStyle}'><b>Live Daytime Preview (Routing: ${settings.daytimeRoutingMode ?: 'Outdoor Speaker Only'}):</b><br><i>${dayPrev}</i></div>"
            }
        }

        section("After Hours Doorbell Intercept", hideable: true, hidden: true) {
            input "enableAfterHours", "bool", title: "Enable After Hours Intercept?", defaultValue: false, submitOnChange: true
            if (enableAfterHours) {
                input "afterHoursTimeStart", "time", title: "After Hours Start Time", required: false
                input "afterHoursTimeEnd", "time", title: "After Hours End Time", required: false
                input "afterHoursRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Outdoor Speaker Only", submitOnChange: true
                input "afterHoursVolume", "number", title: "Announcement Volume (0-100)", required: false
                input "afterHoursDebounce", "number", title: "Cooldown (Minutes)", defaultValue: 5, required: false
                for (int d = 1; d <= 15; d++) { input "afterHoursMessage_${d}", "text", title: "After Hours Message ${d}", required: false, defaultValue: getDefaultMessages("AfterHours")[d-1] }
                input "btnTestAfterHours", "button", title: "▶️ Test After Hours Audio"
                
                def ahPrev = applyDynamicVars(settings["afterHoursMessage_1"] ?: getDefaultMessages("AfterHours")[0])
                paragraph "<div style='${prevStyle}'><b>Live After Hours Preview (Routing: ${settings.afterHoursRoutingMode ?: 'Outdoor Speaker Only'}):</b><br><i>${ahPrev}</i></div>"
            }
        }
        
        section("Nighttime Intruder Deterrent", hideable: true, hidden: true) {
            input "enableIntruder", "bool", title: "Enable Intruder Deterrent?", defaultValue: false, submitOnChange: true
            if (enableIntruder) {
                input "intruderModes", "mode", title: "Active Modes (e.g., Night)", multiple: true, required: false
                input "intruderMotion", "capability.motionSensor", title: "Trigger Motion Sensors", multiple: true, required: false
                input "intruderBypassDoors", "capability.contactSensor", title: "Bypass Doors (Dog Let-Out/User Exit)", multiple: true, required: false
                input "intruderBypassMinutes", "number", title: "Door Bypass Timeout (Minutes)", defaultValue: 5, required: false
                input "intruderRoutingMode", "enum", title: "Audio Routing Mode", options: getRoutingOptions(), defaultValue: "Outdoor Speaker Only", submitOnChange: true
                input "intruderDebounce", "number", title: "Deterrent Cooldown (Minutes)", defaultValue: 5, required: false
                input "intruderVolume", "number", title: "Deterrent Announcement Volume (0-100)", required: false
                input "smartCameraDevice", "capability.sensor", title: "Smart Camera", required: false
                input "smartAttribute", "text", title: "Smart Detection Attribute", defaultValue: "smartDetectType"

                for (int d = 1; d <= 3; d++) { input "intruderAnimal_${d}", "text", title: "Animal Message ${d}", required: false, defaultValue: ["Shoo! Get out of here!", "Go away!", "Move along animal!"][d-1] }
                for (int d = 1; d <= 3; d++) { input "intruderPerson_${d}", "text", title: "Person Message ${d}", required: false, defaultValue: ["Warning. You are trespassing. Security has been notified.", "Perimeter breach detected. Cameras are recording your face.", "Please step away from the house. You are being recorded."][d-1] }
                for (int d = 1; d <= 10; d++) { input "intruderMessage_${d}", "text", title: "Intruder Message ${d}", required: false, defaultValue: getDefaultMessages("Intruder")[d-1] }
                
                input "btnTestIntruder", "button", title: "▶️ Test Intruder Audio"

                def intPrev = applyDynamicVars(settings["intruderMessage_1"] ?: getDefaultMessages("Intruder")[0])
                def smartPrev = settings.smartCameraDevice ? "<br><br><b>Smart Person Detection Preview:</b><br><i>" + applyDynamicVars(settings["intruderPerson_1"] ?: "Warning. You are trespassing.") + "</i>" : ""
                paragraph "<div style='${prevStyle}'><b>Live Intruder Preview (Routing: ${settings.intruderRoutingMode ?: 'Outdoor Speaker Only'}):</b><br><i>${intPrev}</i>${smartPrev}</div>"
            }
        }

        section("Birthdays, Anniversaries & Holidays", hideable: true, hidden: true) {
            input "enableHolidays", "bool", title: "Enable Morning Holiday Announcements?", defaultValue: false, submitOnChange: true
            if (enableHolidays) { input "holidayMessage", "text", title: "Holiday Message Format", defaultValue: "By the way, don't forget today is %holiday%!" }
            
            paragraph "<hr>"
            input "enableParentsDay", "bool", title: "Enable Parent's Day Reminders? (Call Mom/Dad)", defaultValue: false, submitOnChange: true
            if (enableParentsDay) {
                input "mothersDayUser", "enum", title: "Who should be reminded to call Mom on Mother's Day?", options: lockUsers, multiple: true, required: false
                input "fathersDayUser", "enum", title: "Who should be reminded to call Dad on Father's Day?", options: lockUsers, multiple: true, required: false
            }

            paragraph "<hr>"
            input "enableAnniversary", "bool", title: "Enable House Anniversary Greetings?", defaultValue: false, submitOnChange: true
            if (enableAnniversary) {
                def months = ["01":"January", "02":"February", "03":"March", "04":"April", "05":"May", "06":"June", "07":"July", "08":"August", "09":"September", "10":"October", "11":"November", "12":"December"]
                input "annivMonth", "enum", title: "Anniversary Month", options: months, required: true
                input "annivDay", "number", title: "Anniversary Day (1-31)", range: "1..31", required: true
                input "annivAllowedUsers", "enum", title: "Limit Anniversary to Specific Users (Arrival/Departure)", options: lockUsers, multiple: true, required: false
                input "annivAllowedCustom", "text", title: "Limit Anniversary (Custom Names)", required: false
                input "annivMsgArrival", "text", title: "Arrival Append", defaultValue: "Happy Anniversary! Welcome home."
                input "annivMsgMorning", "text", title: "Good Morning Append", defaultValue: "Happy Anniversary! I hope you both have a fantastic day."
            }
            
            paragraph "<hr>"
            input "numBirthdays", "number", title: "Number of Birthdays to Track (0-10)", defaultValue: 0, submitOnChange: true
            if (numBirthdays > 0) {
                def months = ["01":"January", "02":"February", "03":"March", "04":"April", "05":"May", "06":"June", "07":"July", "08":"August", "09":"September", "10":"October", "11":"November", "12":"December"]
                
                for (int i = 1; i <= (numBirthdays as Integer); i++) {
                    paragraph "<b>Birthday Profile ${i}</b>"
                    input "bdayName_${i}", "text", title: "Person's Name ${i}", required: false
                    
                    input "bdayType_${i}", "enum", title: "Profile Type", options: ["Kid (30-Day Countdown)", "Adult (5-Day Gift Warning)"], defaultValue: "Kid (30-Day Countdown)", submitOnChange: true
                    
                    input "bdayMonth_${i}", "enum", title: "Birth Month ${i}", options: months, required: false
                    input "bdayDay_${i}", "number", title: "Birth Day ${i} (1-31)", range: "1..31", required: false
                    input "bdayNotifyUser_${i}", "enum", title: "Who needs to be home for this reminder?", options: lockUsers, multiple: true, required: false
                }
                
                input "bdayMsgArrival", "text", title: "Arrival Append", defaultValue: "Happy Birthday %name%!"
                input "bdayMsgMorning", "text", title: "Good Morning Append", defaultValue: "Happy Birthday %name%! I hope you have a fantastic day."
            }

            def holText = getTodayHoliday() ? (settings.holidayMessage ?: "By the way, don't forget today is %holiday%!").replace("%holiday%", getTodayHoliday()) : "None detected today."
            def bdayText = settings.bdayCountdownMsg ?: "By the way, you only have %days% days until your birthday!"
            paragraph "<div style='${prevStyle}'><b>Live Special Events Preview:</b><br><b>Today's Holiday:</b> <i>${holText}</i><br><b>Birthday Countdown Format:</b> <i>${bdayText.replace('%days%', '12').replace('%name%', 'Test User')}</i></div>"
        }
        
        section("User Aliases (Secret Identities)", hideable: true, hidden: true) {
            input "numAliases", "number", title: "Number of Aliases (0-5)", defaultValue: 0, range: "0..5", submitOnChange: true
            if (numAliases > 0) {
                for (int i = 1; i <= (numAliases as Integer); i++) {
                    input "aliasReal_${i}", lockUsers.size() > 0 ? "enum" : "text", title: "Real Name ${i}", options: lockUsers, required: false
                    input "aliasFake_${i}", "text", title: "Alias / Nickname ${i}", required: false
                }
            }
        }

        section("Advanced Features & Arrival Resets", hideable: true, hidden: true) {
            input "enableDebug", "bool", title: "Enable Debug Logging?", defaultValue: false
            input "resetModes", "mode", title: "Reset ALL Arrivals on Mode Change", multiple: true, required: false
            input "btnForceReset", "button", title: "🔄 Force Reset All Daily Statuses"
            input "awayIgnoreModes", "mode", title: "Ignore 'Away' Triggers during these Modes", description: "Prevent being marked away during Night or Sleep modes.", multiple: true, required: false
        }

        section("Screen Time Manager", hideable: true, hidden: true) {
            input "enableScreenTime", "bool", title: "Enable Screen Time Alerts?", defaultValue: false, submitOnChange: true
            if (enableScreenTime) {
                input "screenTimeSwitch", "capability.switch", title: "Screen Time Virtual Switch", required: true
                input "screenTimeSpeaker", "capability.speechSynthesis", title: "Dedicated Screen Time Speaker", required: false
                input "screenTimeVolume", "number", title: "Alert Volume (0-100)", required: false
                input "screenTimeRoutingMode", "enum", title: "Routing Mode", options: getRoutingOptions(), defaultValue: "Global Indoor Speaker Only"
                for (int d = 1; d <= 5; d++) { input "screenTimeMsg_${d}", "text", title: "Alert Message ${d}", required: false, defaultValue: getDefaultMessages("ScreenTime")[d-1] }
                input "btnTestScreenTime", "button", title: "▶️ Test Screen Time Audio"
            }
        }
    }
}

def roomPage(params) {
    def rNum = params?.roomNum ?: state.currentRoom ?: 1
    state.currentRoom = rNum
    def prevStyle = "margin-top: 15px; padding: 10px; background-color: #e9ecef; border-left: 4px solid #0b3b60; border-radius: 4px; font-size: 13px; line-height: 1.4;"
    
    dynamicPage(name: "roomPage", title: "Room Voice Setup", install: false, uninstall: false, previousPage: "mainPage") {
            section("Zone Identification & Occupant", hideable: true, hidden: true) {
            input "roomName_${rNum}", "text", title: "Custom Room Name", defaultValue: "Bedroom ${rNum}", submitOnChange: true
            input "roomOccupantName_${rNum}", "text", title: "Primary Occupant Name(s)", defaultValue: "Guest", required: false
            input "roomSpeaker_${rNum}", "capability.speechSynthesis", title: "Dedicated Room Speaker", required: false
            
            // --- NEW: GLOBAL SPEAKER TOGGLE ---
            input "roomGlobalMorning_${rNum}", "bool", title: "Also play Good Morning on Global Indoor Speaker?", defaultValue: false
            // ----------------------------------
            
            input "btnTestRoomSpk_${rNum}", "button", title: "▶️ Test Room Speaker Link"
            input "roomVolumeGN_${rNum}", "number", title: "Good Night Volume (0-100)", required: false
            input "roomVolumeGM_${rNum}", "number", title: "Good Morning Volume (0-100)", required: false
        }
        
        section("Logic & Automations Triggers", hideable: true, hidden: true) {
            input "roomGoodNightSwitch_${rNum}", "capability.switch", title: "Room Good Night Switch", required: false
            input "roomWakeupMode_${rNum}", "enum", title: "Good Morning Trigger Mode", options: ["1. Immediate (When Good Night Switch turns OFF)", "2. Verified (Wait for switch OFF, then wait for Motion)", "3. Motion Driven (Trigger when Motion activates while switch is ON)"], defaultValue: "1. Immediate (When Good Night Switch turns OFF)", submitOnChange: true
            if (settings["roomWakeupMode_${rNum}"] != "1. Immediate (When Good Night Switch turns OFF)") {
                input "roomMotion_${rNum}", "capability.motionSensor", title: "Wake-Up Motion Sensors", multiple: true, required: false
            }
            input "delayGreetingGN_${rNum}", "number", title: "Good Night Greeting Delay (Seconds)", defaultValue: 5, required: false
            input "delayGreetingGM_${rNum}", "number", title: "Good Morning Greeting Delay (Seconds)", defaultValue: 30, required: false
        }
        
        section("Personalized Greetings", hideable: true, hidden: true) {
            input "btnTestGN_${rNum}", "button", title: "▶️ Test Good Night Audio"
            input "btnTestGM_${rNum}", "button", title: "▶️ Test Good Morning Audio"
            input "useCustomRoomMessages_${rNum}", "bool", title: "Write Custom Overrides?", defaultValue: false, submitOnChange: true
            
            if (settings["useCustomRoomMessages_${rNum}"]) {
                paragraph "<b>Good Night Messages</b>"
                for (int m = 1; m <= 10; m++) { input "gnMessage_${rNum}_${m}", "text", title: "Good Night Message ${m}", required: false, defaultValue: getDefaultMessages("Good Night")[m-1] }
                paragraph "<b>Good Morning Messages</b>"
                for (int m = 1; m <= 10; m++) { input "gmMessage_${rNum}_${m}", "text", title: "Good Morning Message ${m}", required: false, defaultValue: getDefaultMessages("Good Morning")[m-1] }
            } else {
                paragraph "<i><b>Smart Mode Active:</b> The system will automatically inject <b>${settings["roomOccupantName_${rNum}"] ?: "Guest"}</b> into one of the smart defaults.</i>"
            }
        }
        
        section("Room Briefing Add-ons (News & Agenda)", hideable: true, hidden: true) {
            input "roomTimeDate_${rNum}", "bool", title: "Announce Current Time & Date?", defaultValue: false, submitOnChange: true
            input "roomAgendaEnable_${rNum}", "bool", title: "Enable Daily Agenda Reminders?", defaultValue: false, submitOnChange: true
            if (settings["roomAgendaEnable_${rNum}"]) {
                input "roomAgendaMonday_${rNum}", "text", title: "Monday", required: false
                input "roomAgendaTuesday_${rNum}", "text", title: "Tuesday", required: false
                input "roomAgendaWednesday_${rNum}", "text", title: "Wednesday", required: false
                input "roomAgendaThursday_${rNum}", "text", title: "Thursday", required: false
                input "roomAgendaFriday_${rNum}", "text", title: "Friday", required: false
                input "roomAgendaSaturday_${rNum}", "text", title: "Saturday", required: false
                input "roomAgendaSunday_${rNum}", "text", title: "Sunday", required: false
            }
            input "roomNewsEnable_${rNum}", "bool", title: "Enable Top Headlines News Fetcher?", defaultValue: false, submitOnChange: true
            if (settings["roomNewsEnable_${rNum}"]) {
                input "roomNewsFeed_${rNum}", "text", title: "RSS Feed URL", defaultValue: "https://feeds.npr.org/1001/rss.xml"
                input "btnTestRoomNews_${rNum}", "button", title: "▶️ Test Room News Fetch"
            }
            input "roomAnnounceNightMotion_${rNum}", "bool", title: "Announce Night Porch Motion in Morning Brief?", defaultValue: false, submitOnChange: true
            input "roomAnnounceAwayDoorbell_${rNum}", "bool", title: "Announce Away Doorbell Rings in Morning Brief?", defaultValue: false, submitOnChange: true
        }
        
            section("Kid-Friendly Features (Junior Concierge)", hideable: true, hidden: true) {
            input "roomKidsNightWatch_${rNum}", "bool", title: "Enable Anti-Monster Security Check?", defaultValue: false, submitOnChange: true
            input "roomKidsWeekend_${rNum}", "bool", title: "Enable No-School Weekend Reminder?", defaultValue: false, submitOnChange: true
            input "roomKidsMode_${rNum}", "bool", title: "Enable Morning Jokes & Facts?", defaultValue: false, submitOnChange: true
            if (settings["roomKidsMode_${rNum}"]) {
                input "roomJokesFile_${rNum}", "text", title: "Custom Jokes File (Hubitat File Manager)", description: "e.g., jokes.txt (One joke per line)", required: false
            }
        }

        section("Weather, Security & Briefing Integrations", hideable: true, hidden: true) {
            input "roomPerimeterCheck_${rNum}", "bool", title: "Run Perimeter Security Check on Good Night?", defaultValue: false, submitOnChange: true
            input "roomEnableAnniversary_${rNum}", "bool", title: "Play Anniversary Greeting in this Room?", defaultValue: false
            input "roomWeatherDevice_${rNum}", "capability.temperatureMeasurement", title: "Weather / Temperature Device", required: false, submitOnChange: true
            input "roomWeatherGM_${rNum}", "bool", title: "Append Forecast to Good Morning", defaultValue: false
            input "roomWeatherGN_${rNum}", "bool", title: "Append Forecast to Good Night", defaultValue: false
            input "roomMaintenanceDevice_${rNum}", "capability.sensor", title: "House Maintenance Dashboard Device", required: false
            input "roomAnnounceMaintenance_${rNum}", "bool", title: "Announce Overdue Maintenance in Morning Brief?", defaultValue: false
            
            def gnPrev = buildRoomGreeting(rNum, "Good Night", [isTest: true])
            def gmPrev = buildRoomGreeting(rNum, "Good Morning", [isTest: true])
            paragraph "<div style='${prevStyle}'><b>Live Routine Preview (${settings["roomName_${rNum}"] ?: "Room ${rNum}"}):</b><br><b>Good Night:</b> <i>${gnPrev}</i><br><br><b>Good Morning:</b> <i>${gmPrev}</i></div>"
        }
    }
}

def getDefaultMessages(type) {
    switch(type) {
        case "Good Night": return ["Good night %name%.", "Sweet dreams %name%.", "Rest well.", "Sleep tight %name%.", "Have a peaceful night %name%.", "Rest easy %name%.", "Sleep well %name%."]
        case "Good Morning": return ["Good morning %name%.", "Rise and shine %name%.", "Hello %name%.", "Good morning %name%, I hope you slept well."]
        case "PartyMode": return ["Welcome, the homeowners have been expecting you. Let me notify them of your arrival. If the door is unlocked, please come right in.", "Greetings! The family is expecting you. I am letting them know you are here. Feel free to come inside if the door is open.", "Welcome to the party. I will announce your arrival. If the door is open, please head on in and make yourself at home."]
        case "DND": return ["We cannot come to the door right now. The camera is recording, please leave your message.", "Please leave a package or a message. We are currently unavailable.", "Do not disturb is active. Please try again later.", "We are unable to answer the door right now. Video recording is active.", "No one is available to answer the door. Please leave a message."]
        case "Daytime": return ["Please wait a moment, I am notifying the homeowner.", "Someone will be right with you, please hold on.", "Thank you for ringing, please wait while I fetch someone.", "The residents have been notified, please wait.", "Just a moment please, someone is on the way."]
        case "AfterHours": return ["It is currently after hours, the homeowners are unavailable.", "The residents are done receiving visitors for the evening.", "It is too late for visitors. Please return tomorrow.", "The household is resting for the night. Please leave a message.", "We are no longer accepting visitors at this hour."]
        case "NoAnswer": return ["I am sorry, but the homeowners are currently unavailable to come to the door.", "Apologies, but it seems no one is able to answer the door right now. Please leave a message.", "The homeowners are unable to come to the door at this moment. Have a good day."]
        case "Intruder": return ["Unexpected motion detected. Cameras are currently recording.", "Warning. You are trespassing. Security has been notified.", "Perimeter breach detected. Video logging initiated.", "Please step away from the house. You are being recorded.", "Alert. Unauthorized movement detected. Activating security protocols."]
        case "Arrival": return ["Welcome home, %name%.", "Glad you're back, %name%.", "Welcome back %name%, the house is ready.", "Good to see you, %name%.", "Hello %name%. I've adjusted the climate for your arrival."]
        case "ScreenTime": return ["%interruption%, but the daily screen time allotment has expired. Please power down the device.", "Attention. Screen time is now over. Please find a non-digital activity.", "Sir, the screen time timer has reached zero. Please turn off the television.", "Excuse me, but screen time has concluded.", "The screen time switch has been deactivated. Please turn off the screens."]
        case "Calendar": return ["%interruption%, but you have %event% starting in %time%.", "Sir, please note that %event% will begin in %time%.", "A quick reminder that your schedule shows %event% in %time%.", "Excuse me, but %event% is coming up in %time%."]
        case "BreakingNews": return ["%interruption%, but a major news event has just occurred.", "Sir, I have an urgent news update.", "Excuse me, but there is breaking news.", "Pardon me, the news desk has just reported an update."]
    }
    return ["Message"]
}

def installed() {
    log.info "Advanced Voice Butler Installed."
    initialize()
}

def updated() {
    log.info "Advanced Voice Butler Updated."
    unsubscribe()
    unschedule()
    initialize()
}

def ensureStateMaps() {
    if (state.historyLog == null) state.historyLog = []
    if (state.lastRoomGreeting == null) state.lastRoomGreeting = [:]
    if (state.hasArrivedToday == null) state.hasArrivedToday = [:]
    if (state.hasDepartedToday == null) state.hasDepartedToday = [:]
    if (state.waitingForMotion == null) state.waitingForMotion = [:]
    if (state.roomAlreadyAwake == null) state.roomAlreadyAwake = [:]
    if (state.resetReasons == null) state.resetReasons = [:]
    if (state.globalResetReason == null) state.globalResetReason = "Awaiting First Entry"
    if (state.nightMotionCount == null) state.nightMotionCount = 0
    if (state.awayDoorbellCount == null) state.awayDoorbellCount = 0
    if (state.lastNightMotionCount == null) state.lastNightMotionCount = 0
    if (state.lastAwayDoorbellCount == null) state.lastAwayDoorbellCount = 0
    if (state.pendingMorningReport == null) state.pendingMorningReport = false
    if (state.pendingArrivalReport == null) state.pendingArrivalReport = false
    if (state.lastMode == null) state.lastMode = location.mode
    if (state.lastMailDeliveryTime == null) state.lastMailDeliveryTime = 0
    if (state.lastBypassDoorOpen == null) state.lastBypassDoorOpen = 0
    if (state.lastIntruderAlert == null) state.lastIntruderAlert = 0
    if (state.departureGracePeriodEnd == null) state.departureGracePeriodEnd = 0
    if (state.lastDepartureTime == null) state.lastDepartureTime = [:]
    if (state.internetActive == null) state.internetActive = true
    if (state.ttsQueue == null) state.ttsQueue = []
    if (state.speakingUntil == null) state.speakingUntil = 0
    if (state.currentPriority == null) state.currentPriority = 99
    if (state.lastMealTimeEvent == null) state.lastMealTimeEvent = 0
    if (state.scheduledCalendarAlerts == null) state.scheduledCalendarAlerts = []
    if (state.lastBreakingHeadline == null) state.lastBreakingHeadline = ""
    if (state.originalVolumes == null) state.originalVolumes = [:]
    if (state.calendarSyncTime == null) state.calendarSyncTime = ""
    if (state.breakingNewsSyncTime == null) state.breakingNewsSyncTime = ""
    if (state.mealNewsHeadline == null) state.mealNewsHeadline = ""
    if (state.mealNewsSyncTime == null) state.mealNewsSyncTime = ""
    if (state.learnedHabits == null) state.learnedHabits = [:]
    if (state.anomalyAlertedToday == null) state.anomalyAlertedToday = [:]
    if (state.spokenFacts == null) state.spokenFacts = [:]
    if (state.lastOfficeIntercept == null) state.lastOfficeIntercept = 0
    if (state.reminderCounts == null) state.reminderCounts = [:]
    if (state.messageInbox == null) state.messageInbox = []
    if (state.butlerNotes == null) state.butlerNotes = []
    if (state.lastOverdueTasks == null) state.lastOverdueTasks = []
}

def initialize() {
    ensureStateMaps()
    schedule("0 0 0 * * ?", "midnightReset") 
    schedule("0 0/15 * * * ?", "checkAnomalies")
    runEvery1Hour("pollPresenceSensors")
    
// --- Midday Maintenance Scheduler ---
    if (settings.enableMiddayMaintenance) {
        schedule("0 0 10 * * ?", "scheduleRandomMiddayMaintenance")
        // Failsafe: If you save the app between 10am and 3pm, schedule it for today immediately
        def cal = Calendar.getInstance(location.timeZone)
        if (cal.get(Calendar.HOUR_OF_DAY) >= 10 && cal.get(Calendar.HOUR_OF_DAY) < 15) scheduleRandomMiddayMaintenance()
    }
    
    // --- NEW: Real-Time Task Subscription ---
    if (settings.enableRealTimeTasks && settings.middayMaintenanceDevice) {
        subscribe(settings.middayMaintenanceDevice, "overdueTasks", realTimeTaskHandler)
    }

    // --- Health Window Scheduler ---
    if (settings.enableHealthWindow && settings.healthWindowStart && settings.healthWindowEnd) {
        schedule("0 5 0 * * ?", "scheduleHealthWindow")
        scheduleHealthWindow() // Run on init to queue today's check
    }
    
    // --- NEW: Quick Exit (Away Mode Farewell) ---
    if (settings.enableQuickExit && settings.quickExitDoors) {
        subscribe(settings.quickExitDoors, "contact.open", quickExitDoorHandler)
    }
    
    // Vital Checks & Internet Safety
    if (settings.enableInternetCheck) { runEvery5Minutes("checkInternetConnection"); checkInternetConnection() }
    
    // Perimeter & Visitor Handlers
    if (frontDoorbell) { subscribe(frontDoorbell, "pushed", visitorHandler); subscribe(frontDoorbell, "pushed", countDoorbellHandler) }
    if (frontDoorMotion) { subscribe(frontDoorMotion, "motion.active", visitorHandler); subscribe(frontDoorMotion, "motion.active", countMotionHandler) }
    if (settings.enableDaytimeFollowUp && settings.daytimeDoorContact) { subscribe(settings.daytimeDoorContact, "contact.open", daytimeDoorHandler) }
    
    // Intruder Deterrent Subscriptions
    if (enableIntruder) {
        if (intruderMotion) subscribe(intruderMotion, "motion.active", intruderMotionHandler)
        if (intruderBypassDoors) subscribe(intruderBypassDoors, "contact.open", intruderDoorHandler)
        if (smartCameraDevice && smartAttribute) { subscribe(smartCameraDevice, smartAttribute, unifiProtectHandler) }
    }
    
    // Smart Lock & Presence Arrival Handlers
    if (frontDoorLock) subscribe(frontDoorLock, "lock.unlocked", arrivalHandler)
    def numPres = settings.numPresenceMappings ? settings.numPresenceMappings as Integer : 0
    for (int i = 1; i <= numPres; i++) {
        if (settings["fallbackPresence_${i}"]) {
            subscribe(settings["fallbackPresence_${i}"], "presence.present", presenceFallbackHandler)
        }
    }
    
    // Household Routine Handlers (Mail, Meals, Departures, Screen Time)
    if (settings.enableMailCheck && settings.mailSwitch) { 
        subscribe(settings.mailSwitch, "switch.on", mailSwitchHandler)
        subscribe(settings.mailSwitch, "switch.off", mailClearedHandler)
    }
    // --- NEW HEADED HOME SUBSCRIPTIONS ---
    if (settings.enableHeadedHome) {
        def numHH = settings.numHeadedHome ? settings.numHeadedHome as Integer : 0
        for (int i = 1; i <= numHH; i++) {
            if (settings["hhSwitch_${i}"]) subscribe(settings["hhSwitch_${i}"], "switch.on", headedHomeHandler)
        }
    }
    // --- UPDATED MEAL TIME SUBSCRIPTIONS ---
    if (settings.enableMealTime) { 
        if (settings.mealTimeSwitch) subscribe(settings.mealTimeSwitch, "switch.on", mealTimeHandler) 
        if (settings.mealTimeButton && settings.mealTimeButtonAction) subscribe(settings.mealTimeButton, settings.mealTimeButtonAction, mealTimeButtonHandler)
    }
    if (frontDoorContact) subscribe(frontDoorContact, "contact", departureHandler)
    if (settings.enableScreenTime && settings.screenTimeSwitch) { subscribe(settings.screenTimeSwitch, "switch.off", screenTimeHandler) }
    if (settings.enableOffice && settings.officeSwitch) { subscribe(settings.officeSwitch, "switch.on", officeSwitchHandler) }

    // --- Estate Directory Subscriptions ---
    if (settings.enableDirectory) {
        def numC = settings.numContacts ? settings.numContacts as Integer : 0
        for (int i = 1; i <= numC; i++) {
            if (settings["contactSwitch_${i}"]) {
                subscribe(settings["contactSwitch_${i}"], "switch.on", directorySwitchHandler)
            }
        }
    }
    
    // Global Mode & Motion Tracking
    subscribe(location, "mode", modeChangeHandler)
    
    // --- FIX 3C: GLOBAL INCIDENT WARNINGS (MORNING MOTION) SUBSCRIPTION ---
    if (butlerLrMotion) { subscribe(butlerLrMotion, "motion.active", butlerLrMotionHandler) }
    // ----------------------------------------------------------------------
    
    if (awayCheckTime) { schedule(awayCheckTime, "scheduledAwayCheck") }
    
    // Manual Away Logic Mappings
    def numMappings = settings.numAwayMappings ? settings.numAwayMappings as Integer : 0
    for (int i = 1; i <= numMappings; i++) {
        if (settings["awayMappingSwitch_${i}"]) { subscribe(settings["awayMappingSwitch_${i}"], "switch.on", awaySwitchOnHandler) }
        if (settings["awayMappingPresence_${i}"]) { subscribe(settings["awayMappingPresence_${i}"], "presence", awayPresenceHandler) }
    }
    
    // Local Voice Zone (Room) Subscriptions
    def numRoomsSet = settings.numRooms ? settings.numRooms as Integer : 0
    for (int i = 1; i <= numRoomsSet; i++) {
        if (settings["roomGoodNightSwitch_${i}"]) {
            subscribe(settings["roomGoodNightSwitch_${i}"], "switch.on", goodNightOnHandler)
            subscribe(settings["roomGoodNightSwitch_${i}"], "switch.off", goodNightOffHandler)
        }
        
        // Setup for Verified and Motion Driven Wakeup Modes
        def mode = settings["roomWakeupMode_${i}"] ?: "1. Immediate (When Good Night Switch turns OFF)"
        if (mode != "1. Immediate (When Good Night Switch turns OFF)") {
            if (settings["roomMotion_${i}"]) subscribe(settings["roomMotion_${i}"], "motion.active", roomMotionHandler)
        }
    }
    
    // Calendar Integration Engine
    if (settings.enableCalendar) {
        if (settings.calendarType == "Built-In Device (Advanced Calendar App)" && settings.calendarDevice && settings.calEventTimeAttr) {
            subscribe(settings.calendarDevice, settings.calEventTimeAttr, calendarTimeHandler)
        } else if (settings.calendarUrl) {
            if (settings.calSyncMethod == "Standard .ics Polling (Delayed)") {
                def cInt = settings.calPollInterval ?: "1 Hour"
                if (cInt == "15 Minutes") runEvery15Minutes("pollCalendars")
                else if (cInt == "30 Minutes") runEvery30Minutes("pollCalendars")
                else if (cInt == "3 Hours") runEvery3Hours("pollCalendars")
                else runEvery1Hour("pollCalendars")
                
                pollCalendars() // Run it immediately on boot
            }
        }
    }
    
    // Organic Breaking News Engine
    if (settings.enableBreakingNews && settings.breakingNewsFeed) {
        scheduleNextNewsPoll()
    }
    
    // Status Tile Update
    if (settings.dashboardStatusDevice) {
        settings.dashboardStatusDevice.sendEvent(name: "appStatus", value: "Running and Active", descriptionText: "Voice Butler is active", isStateChange: true)
    }
}

// --- CENTRAL ROUTING ENGINE ---
// --- CENTRAL ROUTING ENGINE ---
def executeRoutedTTS(String msg, String mode, indoorVol, outdoorVol, int priority = 2, boolean fastTrack = false, dedicatedSpeaker = null) {
    def played = false
    def anyRouted = false
    mode = mode ?: "Global Indoor Speaker Only"
    def allTargetSpeakers = []

    if (mode.contains("Follow-Me")) {
        def numRoutes = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
        for (int i = 1; i <= numRoutes; i++) {
            def mSensors = [settings["routeMotion_${i}"]].flatten().findAll { it != null }
            if (mSensors && mSensors.any { it.currentValue("motion") == "active" }) {
                
                // --- NEW: LOCALIZED DO NOT DISTURB ---
                def gnSwitch = settings["routeGNSwitch_${i}"]
                if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                    if (settings.enableDebug) log.debug "ROUTING: Skipped ${settings["routeRoomName_${i}"] ?: "Zone ${i}"} (Good Night Switch is ON)."
                    continue // Skip to the next room!
                }
                // -------------------------------------
                
                if (settings["routeSpeaker_${i}"]) {
                    allTargetSpeakers << [spk: settings["routeSpeaker_${i}"], vol: (settings["routeVolume_${i}"] ?: indoorVol)]
                    anyRouted = true
                }
            }
        }
    }
    if (mode == "Dedicated Feature Speaker" && dedicatedSpeaker) allTargetSpeakers << [spk: dedicatedSpeaker, vol: indoorVol]
    if (mode == "Global Indoor Speaker Only" || mode == "Outdoor + Global Indoor" || mode.contains("Global Simultaneous") || (mode.contains("Fallback") && !anyRouted)) {
        if (globalIndoorSpeaker) [globalIndoorSpeaker].flatten().each { allTargetSpeakers << [spk: it, vol: indoorVol] }
    }
    if (mode == "Outdoor Speaker Only" || mode == "Outdoor + Global Indoor" || mode.contains("+ Outdoor")) {
        if (outdoorSpeaker) allTargetSpeakers << [spk: outdoorSpeaker, vol: outdoorVol]
    }

    if (allTargetSpeakers.size() > 0) {
        allTargetSpeakers.each { item ->
            enqueueTTS(item.spk, msg, item.vol, priority, fastTrack)
            played = true
        }
    }
    return played
}

// --- DASHBOARD SYNC FETCHERS ---
def parseRssResponse(resp) {
    def rawText = ""
    if (resp.data instanceof String || resp.data instanceof GString) {
        rawText = resp.data.toString()
    } else {
        try { rawText = resp.data.text } catch (e) { rawText = resp.data.toString() }
    }
    return new XmlSlurper().parseText(rawText)
}

def syncMealNews() {
    def feedUrl = settings.mealTimeNewsFeed ?: "https://feeds.npr.org/1001/rss.xml"
    try {
        httpGet([uri: feedUrl, headers: ["User-Agent": "Mozilla/5.0 (Hubitat; AdvancedVoiceButler)"], timeout: 10, textParser: true]) { resp ->
            if (resp.status == 200 && resp.data) {
                def rss = parseRssResponse(resp)
                def items = rss?.channel?.item
                if (items && items.size() >= 2) {
                    state.mealNewsHeadline = "${items[0].title.text().trim()} / ${items[1].title.text().trim()}"
                    state.mealNewsSyncTime = new Date().format("h:mm a", location.timeZone)
                }
            }
        }
    } catch (Exception e) { log.warn "Meal News Sync Error: ${e}"; state.mealNewsHeadline = "Fetch Error" }
}

def syncRoomNews(rNum) {
    def feedUrl = settings["roomNewsFeed_${rNum}"] ?: "https://feeds.npr.org/1001/rss.xml"
    try {
        httpGet([uri: feedUrl, headers: ["User-Agent": "Mozilla/5.0 (Hubitat; AdvancedVoiceButler)"], timeout: 10, textParser: true]) { resp ->
            if (resp.status == 200 && resp.data) {
                def rss = parseRssResponse(resp)
                def items = rss?.channel?.item
                if (items && items.size() >= 2) {
                    state."roomNewsHeadline_${rNum}" = "${items[0].title.text().trim()} / ${items[1].title.text().trim()}"
                    state."roomNewsSyncTime_${rNum}" = new Date().format("h:mm a", location.timeZone)
                }
            }
        }
    } catch (Exception e) { log.warn "Room News Sync Error: ${e}"; state."roomNewsHeadline_${rNum}" = "Fetch Error" }
}

// --- NEW OFFICE INTERCEPTOR ---
def officeSwitchHandler(evt) {
    ensureStateMaps()
    
    // --- NEW COOLDOWN LOGIC ---
    def lastOffice = state.lastOfficeIntercept ?: 0
    def debounceHours = settings.officeDebounceHours != null ? settings.officeDebounceHours.toInteger() : 4
    if ((new Date().time - lastOffice) < (debounceHours * 3600000)) {
        if (settings.enableDebug) log.debug "OFFICE INTERCEPTOR: Suppressed (Cooldown active: triggered within the last ${debounceHours} hours)."
        return
    }
    state.lastOfficeIntercept = new Date().time
    // --------------------------

    def allowedModes = [settings.officeModes].flatten().findAll { it != null }
    if (allowedModes.size() > 0 && !allowedModes.contains(location.mode)) {
        if (settings.enableDebug) log.debug "OFFICE INTERCEPTOR: Suppressed due to mode restriction."
        return
    }
    
    def feedUrl = ""
    if (settings.officeFeed == "Custom") feedUrl = settings.officeCustomUrl
    else if (settings.officeFeed == "Engadget") feedUrl = "https://www.engadget.com/rss.xml"
    else if (settings.officeFeed == "Wired") feedUrl = "https://www.wired.com/feed/rss"
    else if (settings.officeFeed == "Ars Technica") feedUrl = "https://feeds.arstechnica.com/arstechnica/index"
    else feedUrl = "https://techcrunch.com/feed/" 
    
    def finalMsg = "%interruption%, here is your latest science and technology update. "
    
    try {
        httpGet([uri: feedUrl, headers: ["User-Agent": "Mozilla/5.0"], timeout: 10, textParser: true]) { resp ->
            if (resp.status == 200 && resp.data) {
                def rss = parseRssResponse(resp)
                def items = rss?.channel?.item
                if (items && items.size() >= 2) {
                    def t1 = items[0].title.text().trim().replace("&", "and").replace("\"", "")
                    def t2 = items[1].title.text().trim().replace("&", "and").replace("\"", "")
                    finalMsg += "From ${settings.officeFeed}: ${t1}. In other news, ${t2}."
                }
            }
        }
    } catch (e) {
        log.warn "Office Interceptor News Fetch Error: ${e}"
        finalMsg += "I was unable to retrieve the latest headlines."
    }
    
    finalMsg = applyDynamicVars(finalMsg)
    enqueueTTS(settings.officeSpeaker, finalMsg, settings.officeVolume ?: settings.globalVolume, 2, false)
    addToHistory("OFFICE INTERCEPTOR: Science & Tech news delivered. Queued: '${finalMsg}'")
}

// --- ORGANIC BREAKING NEWS INTERCEPT LOGIC ---
def scheduleNextNewsPoll() {
    def bInt = settings.breakingNewsInterval ?: "1 Hour"
    def baseMins = 60
    if (bInt == "15 Minutes") baseMins = 15
    else if (bInt == "30 Minutes") baseMins = 30
    else if (bInt == "3 Hours") baseMins = 180

    def jitter = new Random().nextInt((baseMins / 3).toInteger() * 2) - (baseMins / 3).toInteger()
    def nextMins = baseMins + jitter
    if (nextMins < 5) nextMins = 5

    runIn(nextMins * 60, "pollBreakingNews")
    state.breakingNewsSyncTime = "Next check in ~${nextMins}m"
}

def pollBreakingNews() {
    scheduleNextNewsPoll() 
    if (!settings.enableBreakingNews || !settings.breakingNewsFeed) return
    
    try {
        // Switched to robust httpGet to properly handle NPR redirects
        httpGet([uri: settings.breakingNewsFeed, headers: ["User-Agent": "Mozilla/5.0 (Hubitat; AdvancedVoiceButler)"], timeout: 10, textParser: true]) { resp ->
            if (resp.status == 200 && resp.data) {
                def rss = parseRssResponse(resp)
                def items = rss?.channel?.item
                if (items && items.size() > 0) {
                    def topHeadline = items[0].title.text().trim().replace("&", "and").replace("\"", "")
                    
                    // Only broadcast if we have a baseline AND the headline is brand new
                    if (state.lastBreakingHeadline != "" && state.lastBreakingHeadline != topHeadline) {
                        if (settings.enableDebug) log.debug "SYSTEM: New breaking news detected: '${topHeadline}'"
                        executeBreakingNews(topHeadline)
                    } else if (state.lastBreakingHeadline == "") {
                        if (settings.enableDebug) log.debug "SYSTEM: Breaking News baseline set. Waiting for next new headline."
                    }
                    
                    state.lastBreakingHeadline = topHeadline
                }
            }
        }
    } catch (Exception e) { 
        log.warn "Voice Butler: Breaking News Fetch Error - ${e}" 
    }
}

def executeBreakingNews(headline, isTest = false) {
    ensureStateMaps()
    
    if (!isTest) {
        def allowedModes = [settings.breakingNewsModes].flatten().findAll { it != null }
        if (allowedModes.size() > 0 && !allowedModes.contains(location.mode)) return
    }

    def messages = []
    for (int d = 1; d <= 4; d++) { if (settings["breakingNewsPrefix_${d}"]) messages << settings["breakingNewsPrefix_${d}"] }
    if (!messages) messages = getDefaultMessages("BreakingNews")
    
    def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())]) + " " + headline + "."
    
    executeRoutedTTS(randomMsg, settings.breakingNewsRoutingMode ?: "Follow-Me + Fallback (Global ONLY if no motion)", settings.breakingNewsVolume ?: settings.globalVolume, settings.outdoorVolume, 2)
    addToHistory("BREAKING NEWS: Interpolated organic fetch triggered. Queued: '${randomMsg}'")
}

// --- CALENDAR & SECRECY LOGIC ---
def pollCalendars() {
    if (settings.calendarType == "Built-In Device (Advanced Calendar App)") return
    
    // FIX: Prevent background polling and the Force Sync button from wiping the 
    // dashboard if you are actively using the Google Apps Script Webhook!
    if (settings.calSyncMethod == "Google Apps Script Webhook (Instant)") return 
    
    if (!settings.calendarUrl) return
    try {
        asynchttpGet("iCalResponseHandler", [uri: settings.calendarUrl, headers: ["User-Agent": "Mozilla/5.0"], timeout: 15])
    } catch (Exception e) { log.error "Failed to fetch iCal/GCal URL: ${e}" }
}

def iCalResponseHandler(response, data) {
    if (response.hasError() || response.status != 200) {
        log.warn "Calendar Fetch failed. Status: ${response.status}"
        return
    }
    try {
        // Asynchronous Hubitat responses require .getData() to guarantee safe string extraction
        def text = response.getData() ?: ""
        
        // Remove line folding (CRLF followed by space/tab)
        text = text.replaceAll(/\r?\n[ \t]/, "")
        
        def nowMs = new Date().time
        def nextEventName = ""
        def nextEventTime = Long.MAX_VALUE
        
        def events = text.findAll(/(?s)BEGIN:VEVENT.*?END:VEVENT/)
        events.each { evtStr ->
            def summaryMatch = evtStr =~ /SUMMARY[^:]*:([^\r\n]+)/
            def dtstartMatch = evtStr =~ /DTSTART[^:]*:([^\r\n]+)/
            def locationMatch = evtStr =~ /LOCATION[^:]*:([^\r\n]+)/
            
            if (summaryMatch && dtstartMatch) {
                def eName = summaryMatch[0][1].trim()
                def tStr = dtstartMatch[0][1].trim()
                def eDate
                
                try {
                    if (tStr.length() == 8) {
                        def sdf = new java.text.SimpleDateFormat("yyyyMMdd")
                        sdf.setTimeZone(location.timeZone)
                        eDate = sdf.parse(tStr)
                        
                        def cal = Calendar.getInstance(location.timeZone)
                        cal.setTime(eDate)
                        cal.set(Calendar.HOUR_OF_DAY, 8)
                        eDate = cal.getTime()
                    } else {
                        def format = tStr.endsWith("Z") ? "yyyyMMdd'T'HHmmss'Z'" : "yyyyMMdd'T'HHmmss"
                        def tz = tStr.endsWith("Z") ? TimeZone.getTimeZone("UTC") : location.timeZone
                        def sdf = new java.text.SimpleDateFormat(format)
                        sdf.setTimeZone(tz)
                        eDate = sdf.parse(tStr)
                    }
                    
                    if (eDate.time > nowMs && eDate.time < nextEventTime) {
                        nextEventTime = eDate.time
                        nextEventName = eName
                        
                        if (locationMatch) {
                            def rawLoc = locationMatch[0][1].trim()
                            state.nextEventLocation = rawLoc.replace("\\,", ",").replace("\\;", ";")
                        } else {
                            state.nextEventLocation = null
                        }
                    }
                } catch(Exception dateEx) {}
            }
        }
        
        if (nextEventTime != Long.MAX_VALUE) {
            calendarTimeHandler([value: nextEventTime.toString()], nextEventName)
        } else {
            state.nextEventName = "No Upcoming Events"
            state.nextEventTimeStr = "--"
            state.nextEventLocation = null
            state.calendarSyncTime = new Date().format("h:mm a", location.timeZone)
        }
    } catch (Exception e) { log.error "iCal Parse Error: ${e}" }
}

def calendarTimeHandler(evt, passedTitle = null) {
    ensureStateMaps()
    def epochStr = evt.value
    if (!epochStr || !epochStr.isNumber()) return
    def eventEpoch = epochStr.toLong()
    def now = new Date().time
    
    unschedule("executeCalendarAlert")
    if (eventEpoch > now) {
        def title = passedTitle ?: (settings.calendarType == "Built-In Device (Advanced Calendar App)" ? settings.calendarDevice.currentValue(settings.calEventTitleAttr) : "an appointment")
        state.nextEventName = title
        state.nextEventEpoch = eventEpoch
        state.nextEventTimeStr = new Date(eventEpoch).format("MMM d 'at' h:mm a", location.timeZone)

        // 1. Schedule standard intervals
        def intervals = [settings.calAlertIntervals].flatten().findAll { it != null }
        intervals.each { interval ->
            def offsetMs = (interval == "1 Hour" ? 3600000 : interval == "30 Minutes" ? 1800000 : 900000)
            def alertTime = eventEpoch - offsetMs
            if (alertTime > now) runOnce(new Date(alertTime), "executeCalendarAlert", [data: [title: title, timeStr: interval], overwrite: false])
        }

        // 2. NEW: THE PROACTIVE TRAVEL CHECK
        // Butler pings Google 1 hour before the event to see if traffic is so bad you need to leave NOW.
        def travelCheckTime = eventEpoch - 3600000 
        if (travelCheckTime > now) {
            runOnce(new Date(travelCheckTime), "executeCalendarAlert", [data: [title: title, timeStr: "Travel Check", isProactive: true], overwrite: false])
        }

        runOnce(new Date(eventEpoch), "executeCalendarAlert", [data: [title: title, timeStr: "0 Minutes"], overwrite: false])
    }
}

def getSmartEventContext(String title) {
    if (!title) return [text: "", reason: null]
    def tLow = title.toLowerCase()
    
    if (tLow.contains("birthday") || tLow.contains("anniversary") || tLow.contains("surprise") || tLow.contains("gift") || tLow.contains("present") || tLow.contains("mother's day") || tLow.contains("father's day") || tLow.contains("valentine") || tLow.contains("christmas") || tLow.contains("xmas")) {
        def targetSpoiled = false
        def matchedName = ""
        def presentUsers = getPresentUsers()
        
        presentUsers.each { pName ->
            if (tLow.contains(pName.toLowerCase()) || tLow.contains(applyAlias(pName).toLowerCase())) {
                targetSpoiled = true
                matchedName = applyAlias(pName)
            }
        }
        
        if (targetSpoiled) return [text: "SECRET", reason: "Target (${matchedName}) is currently home."]
        
        if (tLow.contains("gift") || tLow.contains("present") || tLow.contains("surprise") || tLow.contains("christmas") || tLow.contains("xmas")) {
            if (presentUsers.size() > 1) return [text: "SECRET", reason: "Multiple people are home during a gift/surprise event."]
        }
        
        if (tLow.contains("anniversary")) return [text: "I see an anniversary is approaching. Have you secured a gift and reservations yet?", reason: null]
        if (tLow.contains("birthday")) return [text: "A birthday is coming up. I advise preparing a gift if you have not already.", reason: null]
        return [text: "As this is a special occasion, please remember to prepare your gifts or arrangements.", reason: null]
    }

    if (tLow.contains("dinner") || tLow.contains("restaurant") || tLow.contains("reservation") || tLow.contains("supper")) {
        return [text: "Since this is a dining event, I recommend verifying that your reservations are confirmed.", reason: null]
    } else if (tLow.contains("flight") || tLow.contains("airport") || tLow.contains("travel")) {
        return [text: "As you will be traveling, please ensure you have your identification and travel documents ready.", reason: null]
    } else if (tLow.contains("doctor") || tLow.contains("dentist") || tLow.contains("appointment")) {
        return [text: "Please remember to bring any necessary identification and insurance information to your appointment.", reason: null]
    }
    return [text: "", reason: null]
}

def executeCalendarAlert(data) {
    ensureStateMaps()
    def title = data.title
    def timeStr = data.timeStr
    def location = state.nextEventLocation
    
    // --- TRAVEL TIME LOGIC ---
    def travelWarning = ""
    if (location && (timeStr == "30 Minutes" || timeStr == "15 Minutes" || data.isProactive)) {
        def minsToDrive = getTravelInfo(location)
        if (minsToDrive) {
            def now = new Date().time
            def eventStart = state.nextEventEpoch
            def buffer = settings.leaveNowBuffer ?: 5
            def minutesUntilEvent = ((eventStart - now) / 60000).toInteger()

            def gasData = getCheapestGas()

            // If drive time + buffer is greater than or equal to the time left...
            if ((minsToDrive + buffer) >= minutesUntilEvent) {
                travelWarning = "CRITICAL: If you don't leave now, you will be late for ${title}. Traffic is heavy, and it currently takes ${minsToDrive} minutes to get to ${location}."
                if (gasData) travelWarning += gasData.speech 
            }
            
            // Send the Push Notification ONLY during the 1-hour Proactive check so we don't spam their phone 3 times
            if (data.isProactive && settings.enableTravelPush && settings.notificationDevice) {
                def pushMsg = "Event: ${title}\nLocation: ${location}"
                if (gasData) pushMsg += "\n\nCheapest Gas on the way:\n${gasData.rawName}\n${gasData.rawAddress}"
                settings.notificationDevice.each { dev ->
                    try { dev.deviceNotification(pushMsg) } catch(e) {}
                }
                addToHistory("PUSH: Travel intel sent to phones for ${title}.")
            }
        }
    }

    // 1. Enforce Allowed Modes
    def allowedModes = [settings.calAlertModes].flatten().findAll { it != null }
    if (allowedModes.size() > 0 && !allowedModes.contains(location.mode)) return

    def finalMsg = ""
    if (travelWarning) {
        finalMsg = applyDynamicVars(travelWarning)
    } else {
        // Standard Calendar Message Logic
        def messages = []
        for (int d = 1; d <= 4; d++) { if (settings["calMessage_${d}"]) messages << settings["calMessage_${d}"] }
        if (!messages) messages = getDefaultMessages("Calendar")
        def rawMsg = messages[new Random().nextInt(messages.size())]
        
        if (timeStr == "0 Minutes") {
            rawMsg = rawMsg.replace("%event%", title).replace(" in %time%", " right now").replace("%time%", "right now")
        } else {
            rawMsg = rawMsg.replace("%event%", title).replace("%time%", timeStr)
        }
        finalMsg = applyDynamicVars(rawMsg)
    }

    executeRoutedTTS(finalMsg, settings.calRoutingMode, settings.calVolume ?: settings.globalVolume, settings.outdoorVolume, 2)
    addToHistory("TRAVEL CONCIERGE: ${finalMsg}")
}

// --- AI HABIT & ANOMALY ENGINE ---
def checkAndRegisterFact(String factText, int ttlHours = 4, String contextKey = "global", boolean isTest = false) {
    if (!factText) return ""
    if (isTest) return factText // <--- Prevents memory poisoning during tests
    
    ensureStateMaps()
    def now = new Date().time
    
    // Clean up expired memories
    def keysToRemove = []
    state.spokenFacts.each { k, v -> if (now > v) keysToRemove << k }
    keysToRemove.each { state.spokenFacts.remove(it) }
    
    def factHash = contextKey + "_" + factText.hashCode().toString()
    
    if (state.spokenFacts[factHash] && now < state.spokenFacts[factHash]) {
        if (settings.enableDebug) log.debug "MEMORY FILTER: Suppressed repeated fact for context '${contextKey}'"
        return "" 
    } else {
        state.spokenFacts[factHash] = now + (ttlHours * 3600000)
        return factText
    }
}

def updateHabit(String uName, Long epochTime, String habitType = "departure") {
    ensureStateMaps()
    if (!state.learnedHabits) state.learnedHabits = [:]
    if (!state.learnedHabits[uName]) state.learnedHabits[uName] = [avgDepartureMins: 0, count: 0, avgArrivalMins: 0, arrCount: 0, avgSleepMins: 0, sleepCount: 0]
    
    def cal = Calendar.getInstance(location.timeZone)
    cal.setTime(new Date(epochTime))
    def minsPastMidnight = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
    
    if (habitType == "departure") {
        def currentAvg = state.learnedHabits[uName].avgDepartureMins ?: 0
        def currentCount = state.learnedHabits[uName].count ?: 0
        state.learnedHabits[uName].avgDepartureMins = (((currentAvg * currentCount) + minsPastMidnight) / (currentCount + 1)).toInteger()
        state.learnedHabits[uName].count = currentCount + 1
    } else if (habitType == "arrival") {
        def currentAvg = state.learnedHabits[uName].avgArrivalMins ?: 0
        def currentCount = state.learnedHabits[uName].arrCount ?: 0
        state.learnedHabits[uName].avgArrivalMins = (((currentAvg * currentCount) + minsPastMidnight) / (currentCount + 1)).toInteger()
        state.learnedHabits[uName].arrCount = currentCount + 1
    } else if (habitType == "sleep") {
        // Shift midnight crossover times so 11 PM and 1 AM average out correctly
        def shiftedMins = minsPastMidnight < 720 ? minsPastMidnight + 1440 : minsPastMidnight 
        def currentAvg = state.learnedHabits[uName].avgSleepMins ?: 0
        def currentCount = state.learnedHabits[uName].sleepCount ?: 0
        state.learnedHabits[uName].avgSleepMins = (((currentAvg * currentCount) + shiftedMins) / (currentCount + 1)).toInteger()
        state.learnedHabits[uName].sleepCount = currentCount + 1
    }
}

def formatMinsToTime(int mins) {
    def h = (mins / 60).toInteger()
    def m = mins % 60
    def ampm = h >= 12 ? "PM" : "AM"
    if (h > 12) h -= 12
    if (h == 0) h = 12
    return String.format("%d:%02d %s", h, m, ampm)
}

def checkAnomalies() {
    ensureStateMaps()
    def cal = Calendar.getInstance(location.timeZone)
    def dow = cal.get(Calendar.DAY_OF_WEEK)
    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return 
    
    def nowMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
    
    state.learnedHabits?.each { uName, habitData ->
        if (habitData.avgDepartureMins && state.hasArrivedToday[uName]) {
            if (!state.anomalyAlertedToday[uName]) {
                def expected = habitData.avgDepartureMins
                if (nowMins > (expected + 15)) { 
                    def expectedTimeStr = formatMinsToTime(expected)
                    def dispName = applyAlias(uName)
                    def msg = "Pardon me, ${dispName}. I noticed you normally depart around ${expectedTimeStr}, and you are still home. Are we running behind schedule today?"
                    
                    executeRoutedTTS(msg, "Global Indoor Speaker Only", settings.globalVolume, settings.outdoorVolume, 2)
                    addToHistory("AI ANOMALY: ${dispName} missed learned departure window (${expectedTimeStr}). Proactive check initiated.")
                    state.anomalyAlertedToday[uName] = true
                }
            }
        }
    }
}

// --- MAIL DELIVERY HANDLER ---
def mailSwitchHandler(evt) {
    ensureStateMaps()
    state.lastMailDeliveryTime = new Date().time
    addToHistory("SYSTEM: Mail delivery logged via switch.")
}

// --- MEAL TIME HANDLERS ---
def mealTimeButtonHandler(evt) {
    def btnNum = settings.mealTimeButtonNumber ?: 1
    if (evt.value.toString() == btnNum.toString()) {
        mealTimeHandler([value: "button_trigger"])
    }
}

def mealTimeHandler(evt) {
    if (evt.value != "on" && evt.value != "test" && evt.value != "button_trigger") return
    ensureStateMaps()
    
    def now = new Date().time
    def lastMeal = state.lastMealTimeEvent ?: 0
    if ((now - lastMeal) < 60000 && evt.value != "test") return
    state.lastMealTimeEvent = now
    
    // Determine the routing mode based on how it was triggered
    def rMode = settings.mealTimeRoutingMode ?: "Global Indoor Speaker Only"
    if (evt.value == "button_trigger") {
        rMode = "Follow-Me (Active Rooms Only)"
        addToHistory("MEAL TIME: Triggered via physical button. Constraining to Active Rooms Only.")
    }
    
    // --- PART 1: THE DINNER BELL & ROLL CALL (Chime Active) ---
    def bellMsg = settings.mealTimeDinnerBell ?: "%interruption%, but dinner is now served."
    if (settings.mealTimeAbsentee) {
        def missing = []
        def allTracked = []
        if (settings.arrivalMode == "Automatic (Reads lock memory)" && settings.trackedLockCodes) {
            settings.trackedLockCodes.each { c -> allTracked << (c.toLowerCase() == "admin code" && settings.adminUserAlias ? settings.adminUserAlias : c) }
        } else if (settings.numLockUsers) {
            for (int i = 1; i <= (settings.numLockUsers as Integer); i++) { if (settings["lockUserName_${i}"]) allTracked << settings["lockUserName_${i}"] }
        }
        allTracked = allTracked.unique()
        def guestList = [settings.guestUsers].flatten().findAll { it != null }.collect { it.toLowerCase() }
        if (settings.guestCustomUsers) guestList += settings.guestCustomUsers.split(',').collect { it.trim().toLowerCase() }
        
        allTracked.each { u -> 
            if (!state.hasArrivedToday || state.hasArrivedToday[u] != true) {
                if (!guestList.contains(u.toLowerCase()) && !guestList.contains(applyAlias(u).toLowerCase())) {
                    missing << applyAlias(u) 
                }
            }
        }
        if (missing.size() > 0) {
            def names = (missing.size() == 1) ? missing[0] : (missing.size() == 2) ? "${missing[0]} and ${missing[1]}" : "${missing[0..-2].join(', ')}, and ${missing.last()}"
            bellMsg += " Please note that ${names} have not yet returned home."
        }
    }
    // Send Part 1
    executeRoutedTTS(applyDynamicVars(bellMsg), rMode, settings.mealTimeVolume ?: settings.globalVolume, settings.outdoorVolume, 2, false, settings.mealTimeSpeaker)

    // --- PART 2: THE NEWS DIGEST (Fast Tracked / No Chime) ---
    if (settings.mealTimeNewsWeather) {
        def newsMsg = ""
        def wDevice = settings.mealTimeWeatherDevice
        if (wDevice) {
             def wText = getWeatherReport(wDevice)
             if (wText) newsMsg += wText + " "
        }
        def feedUrl = settings.mealTimeNewsFeed ?: "https://feeds.npr.org/1001/rss.xml"
        try {
            httpGet([uri: feedUrl, headers: ["User-Agent": "Mozilla/5.0"], timeout: 10, textParser: true]) { resp ->
                if (resp.status == 200 && resp.data) {
                    def rss = parseRssResponse(resp)
                    def items = rss?.channel?.item
                    if (items && items.size() >= 2) {
                        def t1 = items[0].title.text().trim().replace("&", "and")
                        def t2 = items[1].title.text().trim().replace("&", "and")
                        newsMsg += "In the news this evening: ${t1}. Additionally, ${t2}."
                    }
                }
            }
        } catch (Exception e) {}
        if (newsMsg) {
            executeRoutedTTS(applyDynamicVars(newsMsg), rMode, settings.mealTimeVolume ?: settings.globalVolume, settings.outdoorVolume, 3, true, settings.mealTimeSpeaker)
        }
    }

    // --- PART 3: THE CONVERSATION STARTER (Fast Tracked / No Chime) ---
    if (settings.enableMealQuestions) {
        def questionMsg = getMealTimeQuestion()
        executeRoutedTTS(applyDynamicVars(questionMsg), rMode, settings.mealTimeVolume ?: settings.globalVolume, settings.outdoorVolume, 4, true, settings.mealTimeSpeaker)
    }
    
    if (evt.value != "button_trigger") addToHistory("MEAL TIME: Routine triggered in 3 parts for clarity.")
}

// --- HEADED HOME HANDLERS ---
def headedHomeHandler(evt) {
    ensureStateMaps()
    def devId = evt.device.id
    def matchIdx = 0
    def numHH = settings.numHeadedHome ? settings.numHeadedHome as Integer : 0
    
    for (int i = 1; i <= numHH; i++) {
        if (settings["hhSwitch_${i}"]?.id == devId) { matchIdx = i; break }
    }
    
    if (matchIdx > 0) {
        // Auto-turn off the virtual switch so it functions like a momentary trigger
        try { settings["hhSwitch_${matchIdx}"].off() } catch(e) {} 
        executeHeadedHome(matchIdx)
    }
}

def executeHeadedHome(int idx) {
    def uName = settings["hhUser_${idx}"] ?: "Someone"
    def displayUserName = applyAlias(uName)
    def msg = settings["hhMessage_${idx}"] ?: "%interruption%, but %name% is on their way home."
    
    def finalMsg = applyDynamicVars(msg.replace("%name%", displayUserName))
    def rMode = settings["hhRouting_${idx}"] ?: "Global Indoor Speaker Only"
    def targetVol = settings["hhVolume_${idx}"] != null ? settings["hhVolume_${idx}"] : settings.globalVolume
    
    executeRoutedTTS(finalMsg, rMode, targetVol, settings.outdoorVolume, 2)
    addToHistory("HEADED HOME: Announced ${displayUserName} is on their way.")
}

def testMealNews() {
    def feedUrl = settings.mealTimeNewsFeed ?: "https://feeds.npr.org/1001/rss.xml"
    def finalMsg = ""
    try {
        httpGet([uri: feedUrl, headers: ["User-Agent": "Mozilla/5.0 (Hubitat; AdvancedVoiceButler)"], timeout: 10, textParser: true]) { resp ->
            if (resp.status == 200 && resp.data) {
                def rss = parseRssResponse(resp)
                def items = rss?.channel?.item
                if (items && items.size() >= 2) {
                    def title1 = items[0].title.text().trim().replace("&", "and").replace("\"", "")
                    def title2 = items[1].title.text().trim().replace("&", "and").replace("\"", "")
                    finalMsg = "Testing evening news fetch. In the news this evening: ${title1}. In other news, ${title2}."
                }
            }
        }
    } catch (Exception e) { finalMsg = "Error fetching news." }
    
    if (finalMsg) executeRoutedTTS(finalMsg, settings.mealTimeRoutingMode ?: "Global Indoor Speaker Only", settings.mealTimeVolume ?: settings.globalVolume, settings.outdoorVolume, 1, false, settings.mealTimeSpeaker)
}

// --- ALIAS & DYNAMIC HELPERS ---
def getTrackedUsers() {
    def allNames = []
    // FIX: Properly scoped settings to prevent Endpoint Crashes
    if (settings.arrivalMode == "Automatic (Reads lock memory)") {
        settings.trackedLockCodes?.each { codeName ->
            if (codeName.toLowerCase() == "admin code" && settings.adminUserAlias) { allNames << settings.adminUserAlias } else { allNames << codeName }
        }
    } else if (settings.numLockUsers && settings.numLockUsers > 0) {
        for (int i = 1; i <= (settings.numLockUsers as Integer); i++) {
            if (settings["lockUserName_${i}"]) allNames << settings["lockUserName_${i}"]
        }
    }
    allNames += (state.hasArrivedToday ?: [:]).keySet().findAll { it != "global" }
    return allNames.unique().collect { applyAlias(it) }.sort()
}

def getPresentUsers() {
    ensureStateMaps()
    def present = []
    def allNames = []
    
    if (arrivalMode == "Automatic (Reads lock memory)") {
        settings.trackedLockCodes?.each { codeName ->
            if (codeName.toLowerCase() == "admin code" && settings.adminUserAlias) { allNames << settings.adminUserAlias } else { allNames << codeName }
        }
    } else if (numLockUsers && numLockUsers > 0) {
        for (int i = 1; i <= (numLockUsers as Integer); i++) {
            if (settings["lockUserName_${i}"]) allNames << settings["lockUserName_${i}"]
        }
    }
    allNames += (state.hasArrivedToday ?: [:]).keySet().findAll { it != "global" }
    allNames = allNames.unique()
    
    allNames.each { uName -> if (state.hasArrivedToday[uName] == true && uName != "global") present << applyAlias(uName) }
    return present
}

def applyAlias(String name) {
    if (!name) return name
    def num = settings.numAliases ? settings.numAliases as Integer : 0
    for (int i = 1; i <= num; i++) {
        def real = settings["aliasReal_${i}"]
        def fake = settings["aliasFake_${i}"]
        if (real && fake && real.trim().equalsIgnoreCase(name.trim())) return fake.trim()
    }
    return name
}

def applyDynamicVars(String msg) {
    if (!msg) return ""
    def tz = location?.timeZone ?: TimeZone.getDefault()
    def now = new Date()
    def tStr = now.format("h:mm a", tz)
    def dStr = now.format("EEEE, MMMM d", tz)
    
    def hour = now.format("H", tz).toInteger()
    def timeOfDay = "evening"
    if (hour >= 4 && hour < 12) timeOfDay = "morning"
    else if (hour >= 12 && hour < 17) timeOfDay = "afternoon"

    def bName = settings.butlerName ?: "the concierge"
    
    def present = getPresentUsers()
    def interruptStr = "Pardon the interruption"
    if (present.size() == 1) interruptStr = "Pardon the interruption, ${present[0]}"
    else if (present.size() > 1) interruptStr = "Pardon the interruption everyone"

    return msg.replace("%time%", tStr).replace("%date%", dStr).replace("%butler%", bName).replace("%timeOfDay%", timeOfDay).replace("%interruption%", interruptStr)
}

// --- WEATHER HELPER ---
def getWeatherReport(wDevice, String contextKey = "global", boolean isTest = false) {
    if (!wDevice) return ""
    def wText = ""
    try {
        def lastUpdateObj = wDevice.currentState("temperature") ?: wDevice.currentState("meteorologistScript")
        def lastUpdate = lastUpdateObj?.date?.time ?: 0
        def now = new Date().time
        
        if ((now - lastUpdate) < 21600000) { 
            wText = wDevice.currentValue("meteorologistScript")
            if (!wText) {
                def temp = wDevice.currentValue("temperature")
                def cond = wDevice.currentValue("weather") ?: "clear conditions"
                if (temp) wText = "The current temperature is ${temp} degrees and it is ${cond}."
            }
        }
    } catch (Exception e) {}
    
    return wText ? checkAndRegisterFact(wText, 3, contextKey, isTest) : ""
}

// --- TTS ENGINE & PRIORITY QUEUE ---
def enqueueTTS(speakerInput, msg, originalVol, priority, fastTrack = false) {
    if (!speakerInput) return
    
    def isMuted = false
    def muteReason = ""

    if (settings.masterSwitch && settings.masterSwitch.currentValue("switch") == "off") { isMuted = true; muteReason = "Master Switch OFF" } 
    else if (settings.guestModeSwitch && settings.guestModeSwitch.currentValue("switch") == "on") { isMuted = true; muteReason = "Guest Mode ON" } 
    else if (settings.enableInternetCheck && state.internetActive == false) { isMuted = true; muteReason = "Internet Offline" }

    if (isMuted) {
        if (settings.enableDebug) log.debug "TTS Suppressed (${muteReason}). Skipped Message: '${msg}'"
        return
    }
    
    def speakers = speakerInput instanceof List ? speakerInput : [speakerInput]
    def speakerIds = speakers.collect { it?.id }

    if (state.ttsQueue.any { it.msg == msg }) return

    state.ttsQueue.add([
        id: java.util.UUID.randomUUID().toString(),
        speakerIds: speakerIds,
        msg: msg,
        vol: originalVol,
        priority: priority,
        fastTrack: fastTrack,
        queuedAt: new Date().time
    ])
    state.ttsQueue = state.ttsQueue.sort { it.priority }
    processQueue()
}

def processQueue() {
    if (!state.ttsQueue || state.ttsQueue.size() == 0) { state.currentPriority = 99; state.originalVolumes = [:]; return }

    def now = new Date().time
    def isSpeaking = (now < (state.speakingUntil ?: 0))
    def nextItem = state.ttsQueue[0]
    
    def ttlMins = settings.ttsTTL != null ? settings.ttsTTL.toInteger() : 5
    if ((ttlMins * 60000) > 0 && (now - nextItem.queuedAt) > (ttlMins * 60000)) {
        addToHistory("SYSTEM: Dropped stale message (Age > ${ttlMins}m): '${nextItem.msg}'")
        state.ttsQueue.remove(0)
        runIn(1, "processQueue", [overwrite: true])
        return
    }

    if (isSpeaking) {
        if (nextItem.priority >= state.currentPriority) {
            runIn(Math.max(1, Math.ceil((state.speakingUntil - now) / 1000.0).toInteger()), "processQueue", [overwrite: true])
            return
        }
    }

    state.ttsQueue.remove(0)
    state.currentPriority = nextItem.priority

    def durationMs = executeTTS(nextItem)
    state.speakingUntil = now + durationMs + 1500 
    
    def nextQueueCheckDelay = Math.ceil(durationMs / 1000.0).toInteger() + 2
    if (state.ttsQueue.size() > 0) runIn(nextQueueCheckDelay, "processQueue", [overwrite: true])
    else runIn(nextQueueCheckDelay, "resetQueuePriority", [overwrite: true])
}

def resetQueuePriority() { state.currentPriority = 99 }

def executeTTS(item) {
    def msg = item.msg
    def vol = item.vol
    def fastTrack = item.fastTrack
    def speakerIds = item.speakerIds

    def speakers = getAllSpeakers().findAll { speakerIds.contains(it.id) }
    def mediaToResume = []
    def devicesToSilence = []
    
    if (settings.mediaPauseList) devicesToSilence += settings.mediaPauseList
    if (settings.globalTVSwitch && speakers.any { s -> settings.globalIndoorSpeaker?.find { it.id == s.id } }) devicesToSilence << settings.globalTVSwitch
    def numR = settings.numRooms ? settings.numRooms as Integer : 0
    for (int i = 1; i <= numR; i++) { if (settings["roomTVSwitch_${i}"] && speakers.any { s -> settings["roomSpeaker_${i}"]?.id == s.id }) devicesToSilence << settings["roomTVSwitch_${i}"] }
    def numRoute = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
    for (int i = 1; i <= numRoute; i++) { if (settings["routeTVSwitch_${i}"] && speakers.any { s -> settings["routeSpeaker_${i}"]?.id == s.id }) devicesToSilence << settings["routeTVSwitch_${i}"] }

    devicesToSilence = devicesToSilence.flatten().findAll { it != null }.unique { it.id }

    devicesToSilence.each { m ->
        try {
            def isPlaying = m.currentValue("transportStatus") == "playing" || m.currentValue("status") == "playing"
            def isMuted = m.currentValue("mute") == "muted"
            def isSwitchOn = m.currentValue("switch") == "on"
            
            if (isPlaying && m.hasCommand("pause")) { m.pause(); mediaToResume << [dev: m, cmd: "play"] } 
            else if (!isMuted && isSwitchOn && m.hasCommand("mute")) { m.mute(); mediaToResume << [dev: m, cmd: "unmute"] }
        } catch(Exception e) {}
    }

    if (mediaToResume.size() > 0) pauseExecution(1500) 

    def finalVol = vol
    if (quietHoursStart && quietHoursEnd && quietVolume != null) {
        try { if (timeOfDayIsBetween(toDateTime(quietHoursStart), toDateTime(quietHoursEnd), new Date(), location.timeZone)) finalVol = quietVolume as Integer } catch(Exception e) {}
    }
    
    def finalMsg = msg.replace("&", "and")
    def padSecs = settings.wakeupPadDelay != null ? settings.wakeupPadDelay.toInteger() : 0
    
    // --- STAGE 1: VOLUME ADJUSTMENT (Simultaneous) ---
    def restoredVolumes = []
    def volumeChanged = false
    speakers.each { spk ->
        try {
            def currentVol = spk.currentValue("volume")
            if (state.originalVolumes[spk.id] == null) state.originalVolumes[spk.id] = currentVol
            else currentVol = state.originalVolumes[spk.id]
            
            def targetVol = finalVol != null ? (finalVol as Integer) : currentVol
            
            if (targetVol != null && currentVol != targetVol) {
                spk.setVolume(targetVol as Integer)
                restoredVolumes << [id: spk.id, vol: currentVol]
                volumeChanged = true
            }
        } catch (Exception ve) {}
    }
    
    // Single Global Pause for volume hardware to catch up
    if (padSecs > 0 && !fastTrack) {
        pauseExecution(padSecs * 1000)
    } else if (volumeChanged) {
        if (!fastTrack) pauseExecution(1000) else pauseExecution(50)
    }

    // --- STAGE 2: PRE-SPEECH CHIME (Simultaneous) ---
    def chimePlayed = false
    if (settings.enableChime && settings.chimeUrl && !fastTrack) {
        speakers.each { spk ->
            try {
                if (spk.hasCommand("playTrack")) {
                    spk.playTrack(settings.chimeUrl)
                    chimePlayed = true
                }
            } catch(Exception ce) {}
        }
    }
    
    // Single Global Pause for chime file to play
    if (chimePlayed) pauseExecution(1500)
    
    // --- STAGE 3: SPEECH (Simultaneous) ---
    speakers.each { spk ->
        try {
            spk.speak(finalMsg)
        } catch (Exception e) {}
    }
    
    // --- STAGE 4: RESTORE TASKS ---
    if (restoredVolumes.size() > 0) {
        def volDelay = Math.max(10, (finalMsg.length() / 7).toInteger() + (fastTrack ? 6 : 10))
        // Calls the new restoreMultiVolumeTask
        runIn(volDelay, "restoreMultiVolumeTask", [data: [volumes: restoredVolumes], overwrite: false])
    }
    
    def speechDuration = Math.max(6, (finalMsg.length() / 7).toInteger()) * 1000
    if (chimePlayed) speechDuration += 2500
    
    if (mediaToResume.size() > 0) {
        def resumeDelay = Math.ceil(speechDuration / 1000.0).toInteger() + 6
        runIn(resumeDelay, "restoreMediaTask", [data: [resumeList: mediaToResume.collect { [id: it.dev.id, cmd: it.cmd] }], overwrite: false])
    }
    
    return speechDuration
}
def restoreMediaTask(data) {
    def resumeList = data.resumeList ?: []
    def allMedia = []
    
    if (settings.mediaPauseList) allMedia += settings.mediaPauseList
    if (settings.globalTVSwitch) allMedia << settings.globalTVSwitch
    def numR = settings.numRooms ? settings.numRooms as Integer : 0
    for(int i=1; i<=numR; i++) { if (settings["roomTVSwitch_${i}"]) allMedia << settings["roomTVSwitch_${i}"] }
    def numRoute = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
    for(int i=1; i<=numRoute; i++) { if (settings["routeTVSwitch_${i}"]) allMedia << settings["routeTVSwitch_${i}"] }
    allMedia = allMedia.flatten().findAll { it != null }.unique { it.id }
    
    resumeList.each { item ->
        def dev = allMedia.find { it.id == item.id }
        if (dev) { try { dev."${item.cmd}"() } catch(Exception e) {} }
    }
}

def restoreVolumeTask(data) {
    def id = data.speakerId
    def vol = data.oldVol
    if (id != null) {
        def spk = getAllSpeakers().find { it.id == id }
        if (spk && vol != null) { try { spk.setVolume(vol as Integer) } catch(Exception e) {} }
        state.originalVolumes.remove(id)
    }
}

def restoreMultiVolumeTask(data) {
    def vols = data.volumes ?: []
    def allSpks = getAllSpeakers()
    vols.each { item ->
        def spk = allSpks.find { it.id == item.id }
        if (spk && item.vol != null) { 
            try { spk.setVolume(item.vol as Integer) } catch(Exception e) {} 
        }
        state.originalVolumes.remove(item.id)
    }
}

def getAllSpeakers() {
    def list = []
    if (outdoorSpeaker) list << outdoorSpeaker
    if (globalIndoorSpeaker) list.addAll(globalIndoorSpeaker)
    if (butlerLrSpeaker) list << butlerLrSpeaker
    if (arrivalFoyerSpeaker) list << arrivalFoyerSpeaker
    if (settings.officeSpeaker) list << settings.officeSpeaker
    def numRoomsSet = settings.numRooms ? settings.numRooms as Integer : 0
    for (int i = 1; i <= numRoomsSet; i++) { if (settings["roomSpeaker_${i}"]) list << settings["roomSpeaker_${i}"] }
    def numRouteSet = settings.numRoutingRooms ? settings.numRoutingRooms as Integer : 0
    for (int i = 1; i <= numRouteSet; i++) { if (settings["routeSpeaker_${i}"]) list << settings["routeSpeaker_${i}"] }
    if (settings.screenTimeSpeaker) list << settings.screenTimeSpeaker
    if (settings.mealTimeSpeaker) list << settings.mealTimeSpeaker
    return list.flatten().findAll { it != null }.unique { it.id }
}

def resetDepartureMessages(int i) {
    def workMsgs = getDefaultMessages("Work")
    def schoolMsgs = getDefaultMessages("School")
    def genMsgs = getDefaultMessages("General")
    def type = settings["depType_${i}"] ?: "Work"
    def selMsgs = type == "School" ? schoolMsgs : (type == "Work" ? workMsgs : genMsgs)
    for (int m = 1; m <= 10; m++) { app.updateSetting("depMessage_${i}_${m}", [type: "text", value: selMsgs[m-1]]) }
    addToHistory("SYSTEM: Reset departure messages for Profile ${i} to ${type} defaults.")
}

// --- DEPARTURE LOGIC ---
def departureHandler(evt) {
    if (evt.value != "open") return
    ensureStateMaps()
    def nowTime = new Date().time
    def numDep = settings.numDepartureUsers ? settings.numDepartureUsers as Integer : 0
    if (numDep == 0) return
    def now = new Date()
    def departedIndexes = []
    
    for (int i = 1; i <= numDep; i++) {
        def uName = settings["depUserName_${i}"]
        def ctxSwitch = settings["depSwitch_${i}"]
        def sickSwitch = settings["depSickSwitch_${i}"]
        def tStart = settings["depTimeStart_${i}"]
        def tEnd = settings["depTimeEnd_${i}"]
        def dModes = [settings["depModes_${i}"]].flatten().findAll { it != null }
        
            if (uName && ctxSwitch && tStart && tEnd) {
            if (state.hasDepartedToday[uName]) continue
            if (sickSwitch && sickSwitch.currentValue("switch") == "on") continue
            if (dModes.size() > 0 && !dModes.contains(location.mode)) continue
            if (ctxSwitch.currentValue("switch") != "on") continue
            try { if (!timeOfDayIsBetween(toDateTime(tStart), toDateTime(tEnd), now, location.timeZone)) continue } catch (Exception e) { continue }

            def splitNames = uName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
            splitNames.each { n ->
                state.hasDepartedToday[n] = true
                state.lastDepartureTime[n] = nowTime
                updateHabit(n, nowTime)
            }
            departedIndexes << i
        }
    }
    
    if (departedIndexes.size() > 0) {
        state.departureGracePeriodEnd = nowTime + 300000 
        departedIndexes.each { idx ->
            def uName = settings["depUserName_${idx}"]
            def displayUserName = applyAlias(uName)
            def messages = []
            for (int m = 1; m <= 10; m++) { if (settings["depMessage_${idx}_${m}"]) messages << settings["depMessage_${idx}_${m}"] }
            if (!messages) messages = ["Have a good trip %name%."]
            def rawMsg = messages[new Random().nextInt(messages.size())].replace("%name%", displayUserName)
            
            def bdayMsg = getBirthdayMessage(uName, "Departure")
            if (bdayMsg) { rawMsg = "${rawMsg} ${bdayMsg}" }
            def annivMsg = getAnniversaryMessage("Departure", uName)
            if (annivMsg) { rawMsg = "${rawMsg} ${annivMsg}" }
            
            def finalMsg = applyDynamicVars(rawMsg)
            
            // --- NEW: COAT CHECK ---
            if (settings.enableCoatCheck && settings.depWeatherDevice) {
                def wDevice = settings.depWeatherDevice
                def temp = wDevice.currentValue("temperature")?.toString()?.replaceAll("[^0-9.-]", "")?.toFloat()?.toInteger() ?: 50
                def cond = wDevice.currentValue("weather")?.toString()?.toLowerCase() ?: ""
                
                def weatherWarning = ""
                if (cond.contains("rain") || cond.contains("storm") || cond.contains("shower")) {
                    weatherWarning = " Please note, precipitation is currently detected, an umbrella is highly advisable."
                } else if (cond.contains("snow") || cond.contains("ice")) {
                    weatherWarning = " Please note, it is currently snowing. Drive safely."
                } else if (temp <= 32) {
                    weatherWarning = " Please note, it is freezing outside. Stay warm."
                } else if (temp >= 90) {
                    weatherWarning = " Please note, it is quite hot out there today. Stay hydrated."
                }
                
                if (weatherWarning) finalMsg += weatherWarning
            }
            // -----------------------
            
            def delay = settings["depDelay_${idx}"] != null ? settings["depDelay_${idx}"].toInteger() : 5
            def outVol = settings["depVolume_${idx}"] ?: settings.outdoorVolume
            def rMode = settings["depRoutingMode_${idx}"] ?: "Outdoor Speaker Only"
            
            runIn(delay, "playDepartureGreeting", [data: [user: uName, message: finalMsg, routing: rMode, outVol: outVol], overwrite: false])
        }
    }
}

def playDepartureGreeting(data) {
    executeRoutedTTS(data.message, data.routing, settings.globalVolume, data.outVol, 3)
    addToHistory("DEPARTURE: Contextual departure window matched for [${data.user}]. Queued: '${data.message}'")
}

// --- NIGHTTIME INTRUDER DETERRENT ---
def intruderDoorHandler(evt) {
    ensureStateMaps()
    state.lastBypassDoorOpen = new Date().time
}

def canTriggerIntruder() {
    def now = new Date().time
    if (state.departureGracePeriodEnd && now < state.departureGracePeriodEnd) return false
    def activeModes = [settings.intruderModes].flatten().findAll { it != null }
    if (!activeModes.contains(location.mode)) return false
    
    def isDoorOpen = false
    if (settings.intruderBypassDoors) { settings.intruderBypassDoors.each { door -> if (door.currentValue("contact") == "open") isDoorOpen = true } }
    if (isDoorOpen) return false
    
    def lastDoor = atomicState.lastBypassDoorOpen ?: state.lastBypassDoorOpen ?: 0
    def bpVal = settings.intruderBypassMinutes
    def bypassMins = (bpVal != null && bpVal.toString().isInteger()) ? bpVal.toInteger() : 5
    if ((now - lastDoor) < (bypassMins * 60000)) return false
    
    def dbVal = settings.intruderDebounce
    def dbMins = (dbVal != null && dbVal.toString().isInteger()) ? dbVal.toInteger() : 5
    if (dbMins <= 0) dbMins = 1
    def debounceMs = dbMins * 60000
    
    def lastAlert = atomicState.lastIntruderAlert ?: state.lastIntruderAlert ?: 0
    if ((now - lastAlert) <= debounceMs) return false
    return true
}

def intruderMotionHandler(evt) {
    ensureStateMaps()
    
    // FIX: Send data directly to the Morning Report Tracker!
    countMotionHandler(evt) 
    
    if (!canTriggerIntruder()) return
    if (settings.smartCameraDevice && settings.smartAttribute) {
        runIn(6, "executeGenericIntruder", [data: [deviceName: evt.device.displayName], overwrite: true])
    } else {
        executeGenericIntruder([deviceName: evt.device.displayName])
    }
}

def executeGenericIntruder(data) {
    if (!canTriggerIntruder()) return
    atomicState.lastIntruderAlert = new Date().time
    atomicState.lastOutdoorGreeting = new Date().time
    
    def messages = []
    for (int d = 1; d <= 10; d++) { if (settings["intruderMessage_${d}"]) messages << settings["intruderMessage_${d}"] }
    if (!messages) messages = getDefaultMessages("Intruder")
    
    def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())])
    def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
    def rMode = settings.intruderRoutingMode ?: "Outdoor Speaker Only"
    
    // Added 'true' at the end to fast-track the alert and skip the chime
    executeRoutedTTS(randomMsg, rMode, settings.globalVolume, targetVol, 1, true)
    addToHistory("INTRUDER DETERRENT: Generic motion detected. Queued: '${randomMsg}'")
}

def unifiProtectHandler(evt) {
    ensureStateMaps()
    def detectStr = evt.value?.toLowerCase() ?: ""
    if (detectStr == "none" || detectStr == "waiting" || detectStr == "null" || detectStr == "") return
    
    // --- NEW: SMART PACKAGE CONCIERGE ---
    if (detectStr.contains("package")) {
        // Respect the dedicated Package Tracking toggle!
        if (settings.enableDeliveryTracking == false) {
            if (settings.enableDebug) log.debug "GOOGLE DELIVERY: Suppressed (Package Tracking is toggled OFF)."
            return
        }
        
        if (getPresentUsers().size() == 0) {
            stashMessage("a package was delivered to the house")
            return
        }
        
        def debounceMs = 5 * 60000 // 5 minute cooldown for packages
        def lastPkg = state.lastPackageAlert ?: 0
        if ((new Date().time - lastPkg) > debounceMs) {
            state.lastPackageAlert = new Date().time
            
            // 1. Thank the driver outside
            if (outdoorSpeaker) {
                def outMsg = "Thank you for the delivery. Please leave the package right there."
                enqueueTTS(outdoorSpeaker, outMsg, settings.outdoorVolume, 2, true)
            }
            
            // 2. Alert the house inside
            def inMsg = "%interruption%, but the security camera has just detected a package delivery at the front door."
            executeRoutedTTS(applyDynamicVars(inMsg), "Global Indoor Speaker Only", settings.globalVolume, 0, 2)
            
            addToHistory("SMART CAMERA: Package delivery detected and announced.")
        }
        return
    }
    // ------------------------------------
    
    // FIX: Send Smart Detection data to the Morning Report Tracker!
    countMotionHandler(evt) 
    
    if (!canTriggerIntruder()) return
    
    unschedule("executeGenericIntruder")
    atomicState.lastIntruderAlert = new Date().time
    atomicState.lastOutdoorGreeting = new Date().time
    
    def isPerson = detectStr.contains("person")
    def isVehicle = detectStr.contains("vehicle")
    def isAnimal = detectStr.contains("animal")

    def messages = []
    if (isPerson) {
        for (int d = 1; d <= 3; d++) { if (settings["intruderPerson_${d}"]) messages << settings["intruderPerson_${d}"] }
        if (!messages) messages = ["Warning. You are trespassing. Security has been notified."]
    } else if (isVehicle) {
        for (int d = 1; d <= 3; d++) { if (settings["intruderVehicle_${d}"]) messages << settings["intruderVehicle_${d}"] }
        if (!messages) messages = ["Unauthorized vehicle detected. License plate logged."]
    } else if (isAnimal) {
        for (int d = 1; d <= 3; d++) { if (settings["intruderAnimal_${d}"]) messages << settings["intruderAnimal_${d}"] }
        if (!messages) messages = ["Shoo! Get out of here!"]
    } else {
        for (int d = 1; d <= 10; d++) { if (settings["intruderMessage_${d}"]) messages << settings["intruderMessage_${d}"] }
        if (!messages) messages = getDefaultMessages("Intruder")
    }

    def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())])
    def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
    
    // Added 'true' at the end to fast-track the alert and skip the chime
    executeRoutedTTS(randomMsg, settings.intruderRoutingMode ?: "Outdoor Speaker Only", settings.globalVolume, targetVol, 1, true)
}

// --- BUTLER EVENT TRACKING & REPORTING ---
def countMotionHandler(evt) {
    ensureStateMaps()
    def nightModes = [settings.intruderModes].flatten().findAll { it != null }
    if (location.mode in nightModes || location.mode == "Night") {
        state.nightMotionCount = (state.nightMotionCount ?: 0) + 1
        state.pendingMorningReport = true
        if (settings.enableDebug) log.info "INCIDENT TRACKER: Logged Night Motion. Count is now ${state.nightMotionCount}"
    }
}

def countDoorbellHandler(evt) {
    ensureStateMaps()
    def awayModes = [settings.quickExitModes].flatten().findAll { it != null }
    if (location.mode in awayModes || location.mode == "Away") {
        state.awayDoorbellCount = (state.awayDoorbellCount ?: 0) + 1
        state.pendingArrivalReport = true
    }
}

def playButlerReport(data) {
    def type = data.type
    def msg = ""
    if (type == "Arrival") {
        msg = "%interruption%, but there were ${data.count} doorbell rings while you were away. Please check the cameras."
        state.awayDoorbellCount = 0 
    } else {
        msg = "There were ${data.count} motion events at the front door last night. Please check the cameras."
        state.nightMotionCount = 0 
    }
    
    def targetSpeaker = settings.butlerLrSpeaker ?: globalIndoorSpeaker
    if (targetSpeaker) enqueueTTS(targetSpeaker, applyDynamicVars(msg), settings.butlerLrVolume ?: globalVolume, 4)
}

// --- FRONT DOOR DND, AFTER HOURS, & DAYTIME LOGIC ---
def daytimeDoorHandler(evt) {
    ensureStateMaps()
    unschedule("playDaytimeFollowUp")
}

def playDaytimeFollowUp() {
    ensureStateMaps()
    def messages = []
    for (int d = 1; d <= 5; d++) { if (settings["daytimeNoAnswer_${d}"]) messages << settings["daytimeNoAnswer_${d}"] }
    if (!messages) messages = getDefaultMessages("NoAnswer")
    
    def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())])
    executeRoutedTTS(randomMsg, settings.daytimeRoutingMode ?: "Outdoor Speaker Only", settings.globalVolume, settings.daytimeDoorbellVolume != null ? settings.daytimeDoorbellVolume : settings.outdoorVolume, 2, true)
}

def visitorHandler(evt) {
    ensureStateMaps()
    def now = new Date().time
    if (state.departureGracePeriodEnd && now < state.departureGracePeriodEnd) return
    
    def lastIntruder = atomicState.lastIntruderAlert ?: state.lastIntruderAlert ?: 0
    if ((now - lastIntruder) < 60000) return
    
    def intruderModeList = [settings.intruderModes].flatten().findAll { it != null }
    if (evt.name == "motion" && settings.enableIntruder && intruderModeList.contains(location.mode)) {
        def intIds = [settings.intruderMotion].flatten().findAll { it != null }.collect { it?.id }
        if (settings.smartCameraDevice) intIds << settings.smartCameraDevice.id
        if (intIds.contains(evt.device.id)) return
    }
    
    def dndModesList = [settings.dndModes].flatten().findAll { it != null }
    def isDndActive = (dndSwitch?.currentValue("switch") == "on") || dndModesList.contains(location.mode)
    def isPartyModeActive = settings.enablePartyMode && settings.partyModeSwitch && settings.partyModeSwitch.currentValue("switch") == "on"
    def isMotion = evt.name == "motion"
    def lastGreet = atomicState.lastOutdoorGreeting ?: state.lastOutdoorGreeting ?: 0
    def isDoorbell = !isMotion
    
    def isAfterHours = false
    if (enableAfterHours && afterHoursTimeStart && afterHoursTimeEnd) {
        try { isAfterHours = timeOfDayIsBetween(toDateTime(afterHoursTimeStart), toDateTime(afterHoursTimeEnd), new Date(), location.timeZone) } catch(Exception e) {}
    }

    if (isDoorbell && settings.enableIndoorRouting) {
        def shouldRoute = true
        if (settings.indoorRouteMuteDND && isDndActive && !isPartyModeActive) shouldRoute = false
        def restrictedModes = [settings.indoorRouteRestrictedModes].flatten().findAll { it != null }
        if (restrictedModes.contains(location.mode)) shouldRoute = false

        if (shouldRoute) {
            executeRoutedTTS(applyDynamicVars(settings.indoorDoorbellMsg ?: "This is %butler%. %interruption%, but there is a visitor at the front door."), settings.indoorDoorbellRoutingMode ?: "Follow-Me + Fallback (Global ONLY if no motion)", settings.globalVolume, settings.outdoorVolume, 2)
        }
    }
    
    if (isPartyModeActive && isDoorbell) {
        def debounceMs = (settings.partyDebounce != null ? settings.partyDebounce.toInteger() : 2) * 60000
        if ((now - lastGreet) > debounceMs) {
            def messages = []
            for (int d = 1; d <= 3; d++) { if (settings["partyMessage_${d}"]) messages << settings["partyMessage_${d}"] }
            if (!messages) messages = getDefaultMessages("PartyMode")
            def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())])
            
            executeRoutedTTS(randomMsg, settings.partyRoutingMode ?: "Outdoor Speaker Only", settings.globalVolume, settings.partyVolume != null ? settings.partyVolume : settings.outdoorVolume, 2, true)
            atomicState.lastOutdoorGreeting = now
            addToHistory("PARTY MODE: Guest doorbell answered. Queued: '${randomMsg}'")
        }
    } else if (isDndActive) {
        def debounceMs = isMotion ? ((settings.dndMotionDebounce != null ? settings.dndMotionDebounce.toInteger() : 10) * 60000) : 30000 
        if ((now - lastGreet) > debounceMs) {
            def messages = []
            for (int d = 1; d <= 10; d++) { if (settings["dndMessage_${d}"]) messages << settings["dndMessage_${d}"] }
            if (!messages) messages = getDefaultMessages("DND")
            executeRoutedTTS(applyDynamicVars(messages[new Random().nextInt(messages.size())]), settings.dndRoutingMode ?: "Outdoor Speaker Only", settings.globalVolume, settings.outdoorVolume, 2, isDoorbell)
            atomicState.lastOutdoorGreeting = now 
        }
    } else if (isAfterHours && isDoorbell) {
        def debounceMs = (settings.afterHoursDebounce != null ? settings.afterHoursDebounce.toInteger() : 5) * 60000
        if ((now - lastGreet) > debounceMs) {
            def messages = []
            for (int d = 1; d <= 15; d++) { if (settings["afterHoursMessage_${d}"]) messages << settings["afterHoursMessage_${d}"] }
            if (!messages) messages = getDefaultMessages("AfterHours")
            executeRoutedTTS(applyDynamicVars(messages[new Random().nextInt(messages.size())]), settings.afterHoursRoutingMode ?: "Outdoor Speaker Only", settings.globalVolume, settings.afterHoursVolume != null ? settings.afterHoursVolume : settings.outdoorVolume, 2, true)
            atomicState.lastOutdoorGreeting = now
        }
    } else if (!isDndActive && !isAfterHours && isDoorbell && enableDaytimeDoorbell) {
        def debounceMs = (settings.daytimeDoorbellDebounce != null ? settings.daytimeDoorbellDebounce.toInteger() : 2) * 60000
        if ((now - lastGreet) > debounceMs) {
            def presentFolks = getPresentUsers()
            def greetingToPlay = ""
            def willFollowUp = false
            
            // --- ROSTER-AWARE DOORBELL ---
            if (presentFolks.size() == 0) {
                // House is empty! Don't pretend we are home.
                greetingToPlay = "The homeowners are currently away. Please leave a package or a message."
                willFollowUp = false // Don't do the 3-minute follow-up if we know no one is here
                addToHistory("DOORBELL: House is empty. Intercepted Daytime greeting.")
            } else {
                // Someone is home, proceed normally
                def messages = []
                for (int d = 1; d <= 20; d++) { if (settings["daytimeMessage_${d}"]) messages << settings["daytimeMessage_${d}"] }
                if (!messages) messages = getDefaultMessages("Daytime")
                greetingToPlay = messages[new Random().nextInt(messages.size())]
                willFollowUp = settings.enableDaytimeFollowUp
                addToHistory("DOORBELL: Daytime greeting triggered.")
            }
            // ----------------------------------

            executeRoutedTTS(applyDynamicVars(greetingToPlay), settings.daytimeRoutingMode ?: "Outdoor Speaker Only", settings.globalVolume, settings.daytimeDoorbellVolume != null ? settings.daytimeDoorbellVolume : settings.outdoorVolume, 2, true)
            atomicState.lastOutdoorGreeting = now
            
            if (willFollowUp && settings.daytimeDoorContact) {
                runIn((settings.daytimeFollowUpDelay != null ? settings.daytimeFollowUpDelay.toInteger() : 3) * 60, "playDaytimeFollowUp", [overwrite: true])
            }
        }
    }
}

// --- ARRIVAL & RESET LOGIC ---

def presenceFallbackHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    def numPres = settings.numPresenceMappings ? settings.numPresenceMappings as Integer : 0
    for (int i = 1; i <= numPres; i++) {
        if (settings["fallbackPresence_${i}"]?.id == deviceId) {
            def uName = settings["presenceUserName_${i}"]
            if (uName && !state.hasArrivedToday[uName]) {
                // Schedule the 10-minute (600 second) check
                runIn(600, "checkMissedArrival", [data: [user: uName, deviceId: deviceId, mapIdx: i], overwrite: false])
                addToHistory("SYSTEM: Presence detected for ${uName}. Starting 10-minute fallback timer.")
            }
        }
    }
}

def checkMissedArrival(data) {
    ensureStateMaps()
    def uName = data.user
    def i = data.mapIdx
    
    if (!state.hasArrivedToday[uName]) {
        def sensorStillPresent = false
        if (settings["fallbackPresence_${i}"]?.id == data.deviceId && settings["fallbackPresence_${i}"].currentValue("presence") == "present") {
            sensorStillPresent = true
        }
        
        if (sensorStillPresent) {
            state.hasArrivedToday[uName] = true
            state.resetReasons[uName] = "Presence Sensor Fallback (>10m)"
            
            def msg = "Pardon me, I didn't catch you coming through the door. Welcome home, ${applyAlias(uName)}."
            def finalMsg = applyDynamicVars(msg)
            
            def outdoorTargetVol = settings.arrivalVolume != null ? settings.arrivalVolume : settings.outdoorVolume
            def indoorTargetVol = settings.arrivalIndoorVolume != null ? settings.arrivalIndoorVolume : settings.globalVolume
            
            // 1. Play on Foyer Speaker (Standard Arrival Behavior)
            if (settings.arrivalFoyerSpeaker) enqueueTTS(settings.arrivalFoyerSpeaker, finalMsg, indoorTargetVol, 3, false)
            
            // 2. Play to Rest of House (If Enabled)
            if (settings.arrivalIndoorSpeaker) executeRoutedTTS(finalMsg, settings.arrivalNoticeRoutingMode ?: "Global Indoor Speaker Only", indoorTargetVol, outdoorTargetVol, 3)
            
            // 3. Failsafe: Play on Global Speaker if no other speakers caught it
            if (!settings.arrivalFoyerSpeaker && !settings.arrivalIndoorSpeaker && settings.globalIndoorSpeaker) {
                enqueueTTS(settings.globalIndoorSpeaker, finalMsg, indoorTargetVol, 3, false)
            }
            
            addToHistory("FALLBACK ARRIVAL: Missed door unlock. Auto-arrived ${uName}.")
        }
    }
}

def pollPresenceSensors() {
    ensureStateMaps()
    def numPres = settings.numPresenceMappings ? settings.numPresenceMappings as Integer : 0
    if (numPres == 0) return

    def missedUsers = []

    // Sweep all linked presence sensors
    for (int i = 1; i <= numPres; i++) {
        def sensor = settings["fallbackPresence_${i}"]
        def uName = settings["presenceUserName_${i}"]

        if (sensor && uName) {
            // If the sensor is home, but the Butler hasn't marked them arrived yet
            if (sensor.currentValue("presence") == "present" && !state.hasArrivedToday[uName]) {
                state.hasArrivedToday[uName] = true
                state.resetReasons[uName] = "Hourly Presence Sweep Fallback"
                missedUsers << applyAlias(uName)
                addToHistory("FALLBACK ARRIVAL: Hourly sweep detected ${uName} was physically present but not marked arrived.")
            }
        }
    }

    // If anyone was caught in the sweep, group their names and play the apology
    if (missedUsers.size() > 0) {
        def namesStr = ""
        if (missedUsers.size() == 1) namesStr = missedUsers[0]
        else if (missedUsers.size() == 2) namesStr = "${missedUsers[0]} and ${missedUsers[1]}"
        else { def last = missedUsers.pop(); namesStr = "${missedUsers.join(', ')}, and ${last}" }

        def msg = "Pardon me, I didn't catch you coming through the door earlier. Welcome home, ${namesStr}."
        def finalMsg = applyDynamicVars(msg)
        
        def outdoorTargetVol = settings.arrivalVolume != null ? settings.arrivalVolume : settings.outdoorVolume
        def indoorTargetVol = settings.arrivalIndoorVolume != null ? settings.arrivalIndoorVolume : settings.globalVolume
        
        // 1. Play on Foyer Speaker (Standard Arrival Behavior)
        if (settings.arrivalFoyerSpeaker) enqueueTTS(settings.arrivalFoyerSpeaker, finalMsg, indoorTargetVol, 3, false)
        
        // 2. Play to Rest of House (If Enabled)
        if (settings.arrivalIndoorSpeaker) executeRoutedTTS(finalMsg, settings.arrivalNoticeRoutingMode ?: "Global Indoor Speaker Only", indoorTargetVol, outdoorTargetVol, 3)
        
        // 3. Failsafe: Play on Global Speaker if no other speakers caught it
        if (!settings.arrivalFoyerSpeaker && !settings.arrivalIndoorSpeaker && settings.globalIndoorSpeaker) {
            enqueueTTS(settings.globalIndoorSpeaker, finalMsg, indoorTargetVol, 3, false)
        }
    }
}

def arrivalHandler(evt) {
    ensureStateMaps()
    def desc = evt.descriptionText ?: ""
    def actualUserName = "Guest"
    def trackingKey = "global"
    def isKeypadUnlock = false
    
    try {
        if (evt.data) {
            def parsedData = new groovy.json.JsonSlurper().parseText(evt.data)
            if (parsedData?.codeName) { actualUserName = parsedData.codeName; trackingKey = actualUserName; isKeypadUnlock = true }
        }
    } catch (Exception e) {}

    if (!isKeypadUnlock && desc.toLowerCase().contains("unlocked by")) {
        def match = desc =~ /unlocked by (.*)/
        if (match) { actualUserName = match[0][1].trim(); trackingKey = actualUserName; isKeypadUnlock = true }
    } else if (desc.toLowerCase().contains("code") || desc.toLowerCase().contains("keypad")) isKeypadUnlock = true
    
    def originalCodeName = actualUserName
    def lowerIgnored = [settings.ignoredCodes].flatten().findAll { it != null }.collect { it.toLowerCase() }
    if (settings.ignoredCustomCodes) lowerIgnored += settings.ignoredCustomCodes.split(',').collect { it.trim().toLowerCase() }
    
    if (lowerIgnored.contains(originalCodeName.toLowerCase()) || originalCodeName.toLowerCase().contains("ghost")) return
    
    def numServ = settings.numServiceCodes ? settings.numServiceCodes as Integer : 0
    for (int i = 1; i <= numServ; i++) {
        def sName = settings["serviceCodeName_${i}"]
        if (sName && (actualUserName.toLowerCase() == sName.toLowerCase() || desc.toLowerCase().contains(sName.toLowerCase()))) {
            def outMsg = applyDynamicVars(settings["serviceMsgOutdoor_${i}"])
            def inMsg = applyDynamicVars(settings["serviceMsgIndoor_${i}"])
            def outdoorTargetVol = settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"]
            def indoorTargetVol = settings["arrivalIndoorVolume"] != null ? settings["arrivalIndoorVolume"] : settings["globalVolume"]
            
            if (outMsg && outdoorSpeaker) enqueueTTS(outdoorSpeaker, outMsg, outdoorTargetVol, 3, true)
            if (outMsg && settings.arrivalFoyerSpeaker) enqueueTTS(settings.arrivalFoyerSpeaker, outMsg, indoorTargetVol, 3, true)
            if (settings.arrivalIndoorSpeaker && inMsg) executeRoutedTTS(inMsg, settings.arrivalNoticeRoutingMode ?: "Global Indoor Speaker Only", indoorTargetVol, outdoorTargetVol, 3, true)
            return 
        }
    }
    
    if (actualUserName.toLowerCase() == "admin code" && settings.adminUserAlias) { actualUserName = settings.adminUserAlias; trackingKey = actualUserName }
    if (arrivalMode == "Automatic (Reads lock memory)") {
        if (!(settings.trackedLockCodes ?: []).contains(originalCodeName)) return
    }
    
    def matchedUserIdx = null
    if (arrivalMode == "Manual (Assign names to slots)" && numLockUsers) {
        for (int i = 1; i <= (numLockUsers as Integer); i++) {
            def uName = settings["lockUserName_${i}"]
            if (uName && (actualUserName.toLowerCase() == uName.toLowerCase() || desc.toLowerCase().contains(uName.toLowerCase()))) {
                matchedUserIdx = i; trackingKey = uName; actualUserName = uName; isKeypadUnlock = true; break
            }
        }
    }
    
    if (!isKeypadUnlock) {
        if (disableGlobalAnnouncements) return
        trackingKey = "global"; actualUserName = "Guest"
    } else if (trackingKey == "global" && disableGlobalAnnouncements) return
    
    if (!state.hasArrivedToday[trackingKey]) {
        def nowTime = new Date().time
        def lastDepUser = state.lastDepartureTime[trackingKey] ?: 0
        if (lastDepUser > 0 && (nowTime - lastDepUser < ((settings.quickReturnGrace != null ? settings.quickReturnGrace.toInteger() : 5) * 60000))) {
            state.hasArrivedToday[trackingKey] = true 
            return
        }
        
        def splitNames = trackingKey.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
        splitNames.each { n -> 
            state.hasArrivedToday[n] = true 
            state.lastDepartureTime.remove(n)
            state.anomalyAlertedToday[n] = false
        }
        
        atomicState.lastOutdoorGreeting = new Date().time
        
        def messages = []
        def isExtended = false
        if (settings.enableExtendedAbsence && lastDepUser > 0) {
            if ((nowTime - lastDepUser) >= ((settings.extendedAbsenceHours != null ? settings.extendedAbsenceHours.toInteger() : 48) * 3600000)) isExtended = true
        }

        if (isExtended) {
            for (int m = 1; m <= 5; m++) { if (settings["extAbsenceMessage_${m}"]) messages << settings["extAbsenceMessage_${m}"] }
            if (!messages) messages = getDefaultMessages("ExtendedAbsence")
        } else {
            if (arrivalMode == "Automatic (Reads lock memory)") {
                for (int m = 1; m <= 10; m++) { if (settings["autoGreeting_${m}"]) messages << settings["autoGreeting_${m}"] }
            } else {
                if (matchedUserIdx) {
                    for (int m = 1; m <= 10; m++) { if (settings["lockGreeting_${matchedUserIdx}_${m}"]) messages << settings["lockGreeting_${matchedUserIdx}_${m}"] }
                } else {
                    for (int m = 1; m <= 10; m++) { if (settings["defaultArrivalMessage_${m}"]) messages << settings["defaultArrivalMessage_${m}"] }
                }
            }
            if (!messages) messages = getDefaultMessages("Arrival")
        }

        def displayUserName = applyAlias(actualUserName)
        def greetingToPlay = messages[new Random().nextInt(messages.size())].replace("%name%", displayUserName)
        
        if (settings.enableDurationAware && lastDepUser > 0) {
            def minsAway = (nowTime - lastDepUser) / 60000
            if (minsAway < 45) {
                greetingToPlay += " A quick trip, I see."
            } else if (minsAway >= 480) {
                greetingToPlay += " I hope you had a productive day."
            }
        }
        
        def splitActual = actualUserName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
        splitActual.each { n -> updateHabit(n, nowTime, "arrival") }
        
        if (state.learnedHabits[splitActual[0]]?.avgArrivalMins && state.learnedHabits[splitActual[0]].arrCount > 3) {
            def expected = state.learnedHabits[splitActual[0]].avgArrivalMins
            def cal = Calendar.getInstance(location.timeZone)
            def nowMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
            
            if (nowMins < (expected - 90)) greetingToPlay = "You are home quite early today! " + greetingToPlay
            else if (nowMins > (expected + 120)) greetingToPlay = "Working late today? " + greetingToPlay
        }
        
        def bdayMsg = getBirthdayMessage(actualUserName, "Arrival")
        if (bdayMsg) greetingToPlay = "${greetingToPlay} ${bdayMsg}"
        def annivMsg = getAnniversaryMessage("Arrival", actualUserName)
        if (annivMsg) greetingToPlay = "${greetingToPlay} ${annivMsg}"
        
        if (settings.enableAfterSchool && settings.afterSchoolStart && settings.afterSchoolEnd) {
            def dow = Calendar.getInstance(location.timeZone).get(Calendar.DAY_OF_WEEK)
            if (dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY) {
                try {
                    if (timeOfDayIsBetween(toDateTime(settings.afterSchoolStart), toDateTime(settings.afterSchoolEnd), new Date(), location.timeZone)) {
                        def asUsers = [settings.afterSchoolUsers].flatten().findAll { it != null }.collect { it.toLowerCase() }
                        if (settings.afterSchoolCustom) asUsers += settings.afterSchoolCustom.split(',').collect { it.trim().toLowerCase() }
                        if (asUsers.isEmpty() || asUsers.contains(actualUserName.toLowerCase()) || asUsers.contains(trackingKey.toLowerCase())) {
                            if (settings.afterSchoolMsg) greetingToPlay = "${greetingToPlay} ${settings.afterSchoolMsg}"
                        }
                    }
                } catch(Exception e) {}
            }
        }
        
        if (settings.enableHouseRoster) {
            def allowedRoster = [settings.rosterAllowedUsers].flatten().findAll { it != null }.collect { it.toLowerCase() }
            if (settings.rosterAllowedCustom) allowedRoster += settings.rosterAllowedCustom.split(',').collect { it.trim().toLowerCase() }
            if (allowedRoster.isEmpty() || allowedRoster.contains(actualUserName.toLowerCase()) || allowedRoster.contains(trackingKey.toLowerCase())) {
                def othersHome = state.hasArrivedToday.findAll { k, v -> v == true && k.toLowerCase() != trackingKey.toLowerCase() && k != "global" }.keySet().toList()
                if (othersHome.size() == 0) greetingToPlay += " You are the first to arrive. The house is empty."
                else if (othersHome.size() == 1) greetingToPlay += " ${othersHome[0]} is already home."
                else if (othersHome.size() == 2) greetingToPlay += " ${othersHome[0]} and ${othersHome[1]} are already home."
                else { def last = othersHome.pop(); greetingToPlay += " ${othersHome.join(', ')}, and ${last} are already home." }
            }
        }
        
        greetingToPlay = applyDynamicVars(greetingToPlay)

        // --- FIX 3B: GLOBAL INCIDENT WARNINGS (ARRIVAL DOORBELL) ---
        def doorbellCount = state.awayDoorbellCount ?: state.lastAwayDoorbellCount ?: 0
        if (state.pendingArrivalReport && doorbellCount > 0) {
            greetingToPlay += " Also, there were ${doorbellCount} doorbell rings while you were away. Please check the cameras."
            state.pendingArrivalReport = false 
            state.awayDoorbellCount = 0
            state.lastAwayDoorbellCount = 0
            addToHistory("INCIDENT REPORT: Delivered Global Away Doorbell warning on arrival.")
        }
        // -----------------------------------------------------------
        
        if (settings.enableMailCheck && settings.mailSwitch && settings.mailSwitch.currentValue("switch") == "on") {
            def allowedMail = [settings.mailAllowedUsers].flatten().findAll { it != null }.collect { it.toLowerCase() }
            if (settings.mailAllowedCustom) allowedMail += settings.mailAllowedCustom.split(',').collect { it.trim().toLowerCase() }
            if (allowedMail.isEmpty() || allowedMail.contains(actualUserName.toLowerCase()) || allowedMail.contains(trackingKey.toLowerCase())) {
                def mailTimeStr = (state.lastMailDeliveryTime && state.lastMailDeliveryTime > 0) ? new Date(state.lastMailDeliveryTime).format("h:mm a", location.timeZone) : "earlier today"
                
                def mCount = state.reminderCounts["mail"] ?: 0
                state.reminderCounts["mail"] = mCount + 1
                
                if (mCount == 0) {
                    greetingToPlay += " Pardon the reminder, but the mail was delivered at ${mailTimeStr} and still needs to be retrieved."
                } else if (mCount == 1) {
                    greetingToPlay += " As a gentle follow-up, the mail from ${mailTimeStr} is still waiting to be retrieved."
                } else {
                    greetingToPlay += " Please note, the mail remains uncollected."
                }
            }
        }

        if (settings.announceNotesArrival && state.butlerNotes && state.butlerNotes.size() > 0) {
            def pendingArrivalNotes = state.butlerNotes.findAll { (it.when == "Arrival" || it.when == "Pending") && (it.target == "Anyone" || it.target.equalsIgnoreCase(actualUserName) || it.target.equalsIgnoreCase(displayUserName)) }
            if (pendingArrivalNotes.size() > 0) {
                def readStr = "I have ${pendingArrivalNotes.size()} notes saved for you. "
                pendingArrivalNotes.eachWithIndex { note, idx ->
                    def senderTxt = note.sender != "Someone" ? "from ${note.sender}: " : ""
                    if (idx == 0) readStr += "First, ${senderTxt}${note.text}. "
                    else if (idx == pendingArrivalNotes.size() - 1) readStr += "And finally, ${senderTxt}${note.text}."
                    else readStr += "Next, ${senderTxt}${note.text}. "
                }
                if (pendingArrivalNotes.size() == 1) {
                    def senderTxt = pendingArrivalNotes[0].sender != "Someone" ? "from ${pendingArrivalNotes[0].sender}" : ""
                    readStr = "I have a note saved for you ${senderTxt}: ${pendingArrivalNotes[0].text}."
                }
                
                greetingToPlay += " " + readStr
                state.butlerNotes.removeAll { pendingArrivalNotes.contains(it) }
                addToHistory("NOTES: Delivered arrival/missed notes to ${displayUserName}.")
            }
        }
        
        if (settings.enableInbox && state.messageInbox && state.messageInbox.size() > 0) {
            def inboxMsgs = state.messageInbox.join(", and ")
            greetingToPlay += " By the way, while you were out, ${inboxMsgs}."
            state.messageInbox = [] 
            addToHistory("INBOX: Delivered stashed messages on arrival.")
        }
        
        def outdoorTargetVol = settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"]
        def indoorTargetVol = settings["arrivalIndoorVolume"] != null ? settings["arrivalIndoorVolume"] : settings["globalVolume"]
        
        if (outdoorSpeaker) enqueueTTS(outdoorSpeaker, greetingToPlay, outdoorTargetVol, 3, true)
        if (settings.arrivalFoyerSpeaker) enqueueTTS(settings.arrivalFoyerSpeaker, greetingToPlay, indoorTargetVol, 3, true)
        if (settings.arrivalIndoorSpeaker) executeRoutedTTS(applyDynamicVars((settings.indoorArrivalMessage ?: "%name% has arrived home.").replace("%name%", displayUserName)), settings.arrivalNoticeRoutingMode ?: "Global Indoor Speaker Only", indoorTargetVol, outdoorTargetVol, 3, true)
    }
}

def modeChangeHandler(evt) {
    ensureStateMaps()
    def newMode = evt.value
    def nowT = new Date().time
    
    if ([settings.resetModes].flatten().findAll { it != null }.contains(newMode)) {
        state.hasArrivedToday.each { k, v -> if (v) state.lastDepartureTime[k] = nowT }
        state.hasArrivedToday = [:]; state.hasDepartedToday = [:]; state.resetReasons = [:]; state.globalResetReason = "Reset by Mode Change (${newMode})"
    }
    
    def awayList = [settings.butlerAwayModes].flatten().findAll { it != null }
    def nightList = [settings.butlerNightModes].flatten().findAll { it != null }
    
    if (state.lastMode in awayList && !(newMode in awayList)) { if (state.awayDoorbellCount > 0) state.pendingArrivalReport = true }
    if (newMode in awayList) { state.awayDoorbellCount = 0; state.pendingArrivalReport = false }
    if (state.lastMode in nightList && !(newMode in nightList)) { if (state.nightMotionCount > 0) state.pendingMorningReport = true }
    if (newMode in nightList) { state.nightMotionCount = 0; state.pendingMorningReport = false }
    
    state.lastMode = newMode
}

def awaySwitchOnHandler(evt) {
    ensureStateMaps()
    if (settings.awayIgnoreModes?.contains(location.mode)) return

    for (int i = 1; i <= (settings.numAwayMappings ? settings.numAwayMappings as Integer : 0); i++) {
        if (settings["awayMappingSwitch_${i}"]?.id == evt.device.id) {
            def uName = settings["awayMappingUser_${i}"]
            if (uName) {
                def splitNames = uName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
                splitNames.each { n -> 
                    if (state.hasArrivedToday[n]) {
                        state.lastDepartureTime[n] = new Date().time
                        state.hasArrivedToday.remove(n)
                        state.resetReasons[n] = "Away Switch ON"
                    }
                }
            }
        }
    }
}

def awayPresenceHandler(evt) {
    if (evt.value != "not present") return
    ensureStateMaps()
    if (settings.awayIgnoreModes?.contains(location.mode)) return

    for (int i = 1; i <= (settings.numAwayMappings ? settings.numAwayMappings as Integer : 0); i++) {
        if (settings["awayMappingPresence_${i}"]?.id == evt.device.id) {
            def uName = settings["awayMappingUser_${i}"]
            if (uName) {
                def splitNames = uName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
                splitNames.each { n -> 
                    if (state.hasArrivedToday[n]) {
                        state.lastDepartureTime[n] = new Date().time
                        state.hasArrivedToday.remove(n)
                        state.resetReasons[n] = "Presence Sensor Departed"
                    }
                }
            }
        }
    }
}

def scheduledAwayCheck() {
    ensureStateMaps()
    for (int i = 1; i <= (settings.numAwayMappings ? settings.numAwayMappings as Integer : 0); i++) {
        if (settings["awayMappingSwitch_${i}"]?.currentValue("switch") == "on") {
            def uName = settings["awayMappingUser_${i}"]
            if (uName) {
                def splitNames = uName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
                splitNames.each { n -> 
                    if (state.hasArrivedToday[n]) {
                        state.lastDepartureTime[n] = new Date().time
                        state.hasArrivedToday.remove(n)
                        state.resetReasons[n] = "Away Switch ON (Scheduled Check)"
                    }
                }
            }
        }
    }
}

// --- CENTRAL GREETING BUILDER ---
def buildRoomGreeting(rNum, type, context = [:]) {
    ensureStateMaps()
    def rName = settings["roomName_${rNum}"] ?: "Room ${rNum}"
    def rawOccName = context.dynamicName ?: (settings["roomOccupantName_${rNum}"] ?: rName)
    def isTest = context.isTest ?: false 

    // --- NEW: DYNAMIC PRESENCE FILTERING ---
    // Split the names (e.g., "Shane and Christy" -> ["Shane", "Christy"])
    def splitOcc = rawOccName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
    def presentOcc = []
    
    if (isTest || rawOccName == rName) {
        presentOcc = splitOcc // In a test, or if it's just "Master Bedroom", assume everyone is there
    } else {
        // Only greet the people who are actually marked as Arrived
        splitOcc.each { n ->
            if (state.hasArrivedToday[n] == true) presentOcc << n
        }
        // Failsafe: if no one is marked home but the switch was hit, default to all assigned users
        if (presentOcc.size() == 0) presentOcc = splitOcc 
    }
    
    // Format the final display name with correct grammar
    def displayOccName = ""
    if (presentOcc.size() == 1) displayOccName = applyAlias(presentOcc[0])
    else if (presentOcc.size() == 2) displayOccName = "${applyAlias(presentOcc[0])} and ${applyAlias(presentOcc[1])}"
    else {
        def last = presentOcc.pop()
        displayOccName = "${presentOcc.collect{applyAlias(it)}.join(', ')}, and ${applyAlias(last)}"
        presentOcc.push(last) // Put it back just in case
    }
    // ---------------------------------------

    def parts = []
    def roomKey = "room_${rNum}" // Unique ID for the memory filter
    
    // Global variable to determine if this is a kid's room
    def isKidRoom = settings["roomKidsMode_${rNum}"] || settings["roomKidsNightWatch_${rNum}"]

    def rawMsg = ""
    if (type == "Good Night") {
        if (context.isNewArrival) {
            // --- THE APOLOGY FIX ---
            rawMsg = "Pardon me, I didn't catch you coming in earlier. Welcome home, %name%. Your space is prepared."
        } else if (settings["useCustomRoomMessages_${rNum}"]) {
            def msgs = []
            for (int m = 1; m <= 10; m++) { if (settings["gnMessage_${rNum}_${m}"]) msgs << settings["gnMessage_${rNum}_${m}"] }
            rawMsg = msgs ? msgs[new Random().nextInt(msgs.size())] : getDefaultMessages("Good Night")[0]
        } else {
            def defaults = getDefaultMessages("Good Night")
            rawMsg = defaults[new Random().nextInt(defaults.size())]
        }
    } else {
        if (settings["useCustomRoomMessages_${rNum}"]) {
            def msgs = []
            for (int m = 1; m <= 10; m++) { if (settings["gmMessage_${rNum}_${m}"]) msgs << settings["gmMessage_${rNum}_${m}"] }
            rawMsg = msgs ? msgs[new Random().nextInt(msgs.size())] : getDefaultMessages("Good Morning")[0]
        } else {
            def defaults = getDefaultMessages("Good Morning")
            rawMsg = defaults[new Random().nextInt(defaults.size())]
        }
    }
    
    def baseString = ""
    if (context.apology) baseString += context.apology + " "
    if (context.curfew) baseString += context.curfew + " "
    baseString += rawMsg.replace("%name%", displayOccName).replace("%room%", rName)

    // --- AI SLEEP HABIT CHECK ---
    // Use rawOccName so the habit tracks the core users, not just who is home tonight
    if (type == "Good Night" && rawOccName != "Guest") {
        def splitHabitOcc = rawOccName.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
        if (!isTest) splitHabitOcc.each { n -> updateHabit(n, new Date().time, "sleep") }
        
        def primaryOcc = splitHabitOcc[0]
        if (state.learnedHabits && state.learnedHabits[primaryOcc]?.avgSleepMins && state.learnedHabits[primaryOcc].sleepCount > 3) {
            def expected = state.learnedHabits[primaryOcc].avgSleepMins
            def cal2 = Calendar.getInstance(location.timeZone)
            def nowMins = (cal2.get(Calendar.HOUR_OF_DAY) * 60) + cal2.get(Calendar.MINUTE)
            def shiftedNow = nowMins < 720 ? nowMins + 1440 : nowMins 
            
            if (shiftedNow > (expected + 90)) baseString = "It is quite late. " + baseString
            else if (shiftedNow < (expected - 90)) baseString = "Turning in early tonight? " + baseString
        }
    }
    // ----------------------------

    parts << baseString

    // Use rawOccName for birthdays and anniversaries so it doesn't skip them if someone sneaks in
    def bdayMsg = getBirthdayMessage(rawOccName, type == "Good Night" ? "Night" : "Morning")
    if (bdayMsg) parts << bdayMsg

    if (settings["roomEnableAnniversary_${rNum}"]) {
        def annivMsg = getAnniversaryMessage(type == "Good Night" ? "Night" : "Morning")
        if (annivMsg) parts << annivMsg
    }

    if (settings.enableHolidays && type != "Good Night") {
        def holiday = getTodayHoliday()
        if (isTest && !holiday) holiday = "a Holiday"
        if (holiday) {
            def hMsg = settings.holidayMessage ?: "Also, don't forget today is %holiday%!"
            parts << hMsg.replace('%holiday%', holiday)
        }
    }

    def wDevice = settings["roomWeatherDevice_${rNum}"]
    def cal = Calendar.getInstance(location.timeZone)
    def dow = cal.get(Calendar.DAY_OF_WEEK)
    def dowString = new Date().format("EEEE", location.timeZone)

    // ==============================================
    //               GOOD MORNING LOGIC
    // ==============================================
    if (type == "Good Morning") {
        def timeDateEnabled = settings["roomTimeDate_${rNum}"]
        def agendaEnabled = settings["roomAgendaEnable_${rNum}"]
        def agendaText = settings["roomAgenda${dowString}_${rNum}"]

        if (timeDateEnabled && agendaEnabled && agendaText) {
            // --- FIX: AGENDA FLOW (Time only, cleaner transition) ---
            def timeNow = new Date().format("h:mm a", location.timeZone)
            def agendaStr = getBridge("agenda") + "It is currently ${timeNow}, and your agenda for today is: ${agendaText}."
            def smartContext = getSmartEventContext(agendaText)
            if (smartContext.text && smartContext.text != "SECRET") agendaStr += " " + smartContext.text
            parts << agendaStr
        } else if (timeDateEnabled) {
            parts << "The current time is %time% on %date%."
        } else if (agendaEnabled && agendaText) {
            def agendaStr = getBridge("agenda") + "Your agenda for today is: ${agendaText}."
            def smartContext = getSmartEventContext(agendaText)
            if (smartContext.text && smartContext.text != "SECRET") agendaStr += " " + smartContext.text
            parts << agendaStr
        }

        if (settings["roomAnnounceNightMotion_${rNum}"]) {
            def count = state.lastNightMotionCount ?: 0
            if (count > 0 || isTest) {
                def c = isTest ? 3 : count
                parts << "Please note, there were ${c} motion events on the porch last night."
            }
        }

        if (settings["roomAnnounceAwayDoorbell_${rNum}"]) {
            def count = state.lastAwayDoorbellCount ?: 0
            if (count > 0 || isTest) {
                def c = isTest ? 1 : count
                parts << "Also, the doorbell rang ${c} times while the house was vacant yesterday."
            }
        }

        if (wDevice && settings["roomWeatherGM_${rNum}"]) {
            def wText = getWeatherReport(wDevice, roomKey, isTest) 
            if (wText) {
                def weatherBlock = getBridge("weather") + wText
                if (settings["roomWardrobe_${rNum}"]) {
                    def wardText = getWardrobeAdvice(wDevice)
                    if (wardText) weatherBlock += " " + wardText
                }
                parts << weatherBlock
            }
        } else if (settings["roomWardrobe_${rNum}"] && wDevice) {
            def wardText = getWardrobeAdvice(wDevice)
            if (wardText) parts << getBridge("weather") + wardText
        }

        if (settings["roomBoredomBuster_${rNum}"] && (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY || isTest)) {
            def bText = getBoredomBuster(wDevice)
            if (bText) parts << bText
        }

        if (settings["roomNewsEnable_${rNum}"]) {
            def newsText = getRoomNews(rNum, isTest)
            if (newsText) {
                // --- FIX: NEWS SLASHES ---
                def cleanNews = newsText.trim()
                cleanNews = cleanNews.replaceAll(/\s*\/\s*/, ". In other news, ") 
                if (!cleanNews.endsWith(".")) cleanNews += "."
                parts << getBridge("news") + cleanNews
            }
        }

        if (settings["roomAnnounceMaintenance_${rNum}"] && settings["roomMaintenanceDevice_${rNum}"]) {
            def mText = getMaintenanceReport(settings["roomMaintenanceDevice_${rNum}"], isTest)
            if (mText) {
                // --- FIX: MAINTENANCE VARIABLE ---
                mText = mText.replace("%name%", displayOccName)
                parts << mText
            }
        }

        if (settings["roomKidsMode_${rNum}"]) {
            parts << getKidsFunFact(rNum)
        }
        
        // --- BUTLER NOTES (MORNING) ---
        if (settings.announceNotesMorning && state.butlerNotes && state.butlerNotes.size() > 0) {
            // We use displayOccName here so it only reads notes for the people currently in the room
            def pendingMorningNotes = state.butlerNotes.findAll { (it.when == "Morning" || it.when == "Pending") && (it.target == "Anyone" || it.target.equalsIgnoreCase(displayOccName) || it.target.equalsIgnoreCase(rawOccName)) }
            if (pendingMorningNotes.size() > 0) {
                def readStr = "I have ${pendingMorningNotes.size()} notes saved for you. "
                pendingMorningNotes.eachWithIndex { note, idx ->
                    def senderTxt = note.sender != "Someone" ? "from ${note.sender}: " : ""
                    if (idx == 0) readStr += "First, ${senderTxt}${note.text}. "
                    else if (idx == pendingMorningNotes.size() - 1) readStr += "And finally, ${senderTxt}${note.text}."
                    else readStr += "Next, ${senderTxt}${note.text}. "
                }
                if (pendingMorningNotes.size() == 1) {
                    def senderTxt = pendingMorningNotes[0].sender != "Someone" ? "from ${pendingMorningNotes[0].sender}" : ""
                    readStr = "I have a note saved for you ${senderTxt}: ${pendingMorningNotes[0].text}."
                }
                
                parts << getBridge("general") + readStr
                if (!isTest) {
                    state.butlerNotes.removeAll { pendingMorningNotes.contains(it) }
                    addToHistory("NOTES: Delivered morning/missed notes to ${displayOccName}.")
                }
            }
        }
        // ---------------------------------------
        
        // --- ADULT ONLY: MESSAGE INBOX (MORNING) ---
        if (!isKidRoom && settings.enableInbox && state.messageInbox && state.messageInbox.size() > 0) {
            def inboxMsgs = state.messageInbox.join(", and ")
            parts << getBridge("general") + "while you were asleep, ${inboxMsgs}."
            if (!isTest) {
                state.messageInbox = [] 
                addToHistory("INBOX: Delivered stashed messages during Morning Briefing.")
            }
        }
    }

    // ==============================================
    //               GOOD NIGHT LOGIC
    // ==============================================
    if (type == "Good Night") {
        if (settings["roomKidsWeekend_${rNum}"]) {
            if (dow == Calendar.FRIDAY || dow == Calendar.SATURDAY || isTest) {
                parts << "Since tomorrow is the weekend, there is no school! Sleep well and sleep in."
            }
        }
        
        if (settings["roomPerimeterCheck_${rNum}"]) {
            def perimText = getPerimeterReport(roomKey, isTest)
            if (perimText) parts << perimText
        }

        if (wDevice && settings["roomWeatherGN_${rNum}"]) {
            def wText = getWeatherReport(wDevice, roomKey, isTest)
            if (wText) parts << wText
        }
        
        def tomorrowMsg = getTomorrowPreview()
        if (tomorrowMsg) parts << tomorrowMsg

        if (settings["roomKidsNightWatch_${rNum}"]) {
            def monsterList = [
                "I have activated the monster shields. You are completely safe.",
                "Scanning the room... No monsters detected. Have a great sleep.",
                "My perimeter sensors are watching for bad guys so you can sleep peacefully.",
                "I will be keeping a close eye on the house tonight to keep you safe from any monsters.",
                "Sleep tight! The anti-monster forcefield is fully powered up.",
                "Do not worry, %butler% is on guard duty tonight to keep the bad guys away."
            ]
            parts << monsterList[new Random().nextInt(monsterList.size())]
        }
        
        // --- FIX 2: ADULT ONLY INBOX ON LATE ARRIVAL (GOOD NIGHT SWITCH) ---
        if (context.isNewArrival && !isKidRoom && settings.enableInbox && state.messageInbox && state.messageInbox.size() > 0) {
            def inboxMsgs = state.messageInbox.join(", and ")
            parts << getBridge("general") + "while you were away, ${inboxMsgs}."
            if (!isTest) {
                state.messageInbox = [] 
                addToHistory("INBOX: Delivered stashed messages during Late Night Arrival.")
            }
        }
        // -------------------------------------------------------------------
    }
    
def finalMsg = parts.join(" ")
    
    // --- THE GRAMMAR POLISHER ---
    // Fix double punctuation and spacing issues caused by modular injection
    finalMsg = finalMsg.replaceAll(/\!\./, "!") // Fixes "Church Day!."
    finalMsg = finalMsg.replaceAll(/\?\./, "?") // Fixes "?."
    finalMsg = finalMsg.replaceAll(/\.\./, ".") // Fixes ".."
    finalMsg = finalMsg.replaceAll(/\s+/, " ")  // Removes accidental double spaces
    finalMsg = finalMsg.trim()
    // ----------------------------
    
    return applyDynamicVars(finalMsg)
}

def executeGoodNightSequence(data) {
    def rNum = data.roomNum
    def rName = settings["roomName_${rNum}"] ?: "Room ${rNum}"
    
    def targetSpeaker = settings["roomSpeaker_${rNum}"]
    // THE FIX: Fallback to Global Volume, not a broken variable
    def targetVol = settings["roomVolumeGN_${rNum}"] != null ? settings["roomVolumeGN_${rNum}"] : settings.globalVolume
    
    if (!targetSpeaker && globalIndoorSpeaker) { targetSpeaker = globalIndoorSpeaker; targetVol = globalVolume }
    
    if (targetSpeaker) {
        def finalMsg = buildRoomGreeting(rNum, "Good Night", data)
        enqueueTTS(targetSpeaker, finalMsg, targetVol, 4)
        addToHistory("ROOM GREETING: Good Night sequence triggered for ${rName}. Queued: '${finalMsg}'")
    }
}

def goodNightOffHandler(evt) {
    ensureStateMaps()
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (settings.numRooms as Integer ?: 1); i++) {
        if (settings["roomGoodNightSwitch_${i}"]?.id == deviceId) {
            def mode = settings["roomWakeupMode_${i}"] ?: "1. Immediate (When Good Night Switch turns OFF)"
            
            if (mode == "1. Immediate (When Good Night Switch turns OFF)") { 
                triggerGoodMorningSequence(i) 
            } 
            else if (mode == "2. Verified (Wait for switch OFF, then wait for Motion)") { 
                state.waitingForMotion["${i}"] = true 
            } 
            else if (mode == "3. Motion Driven (Trigger when Motion activates while switch is ON)") {
                // Failsafe: If the switch turns off but they never triggered motion, play it now
                if (!state.roomAlreadyAwake["${i}"]) { 
                    triggerGoodMorningSequence(i) 
                }
                // Reset the awake flag for tomorrow night
                state.roomAlreadyAwake["${i}"] = false 
            }
            return
        }
    }
}

def triggerGoodMorningSequence(int i) {
    def delaySec = settings["delayGreetingGM_${i}"] != null ? settings["delayGreetingGM_${i}"].toInteger() : 30
    runIn(delaySec, "executeGoodMorningSequence", [data: [roomNum: i], overwrite: false])
}

def executeGoodMorningSequence(data) {
    def rNum = data.roomNum
    def rName = settings["roomName_${rNum}"] ?: "Room ${rNum}"
    
    def targetSpeaker = settings["roomSpeaker_${rNum}"]
    def targetVol = settings["roomVolumeGM_${rNum}"] != null ? settings["roomVolumeGM_${rNum}"] : settings.globalVolume
    
    def allSpeakers = []
    if (targetSpeaker) allSpeakers << targetSpeaker
    
    // --- SIMULTANEOUS GLOBAL ROUTING ---
    if (settings["roomGlobalMorning_${rNum}"] && globalIndoorSpeaker) {
        allSpeakers.addAll([globalIndoorSpeaker].flatten().findAll { it != null })
    }
    
    // Failsafe if no room speaker was set
    if (allSpeakers.size() == 0 && globalIndoorSpeaker) { 
        allSpeakers.addAll([globalIndoorSpeaker].flatten().findAll { it != null })
        targetVol = globalVolume 
    }
    
    // Strictly filter out duplicates in case the Master Bedroom is also in the Global list
    allSpeakers = allSpeakers.flatten().findAll { it != null }.unique { it.id }
    
    if (allSpeakers.size() > 0) {
        def finalMsg = buildRoomGreeting(rNum, "Good Morning", data)
        enqueueTTS(allSpeakers, finalMsg, targetVol, 4)
        addToHistory("ROOM GREETING: Good Morning sequence triggered for ${rName}. Queued: '${finalMsg}'")
    }
}

def testRoomGreeting(rNum, type, isNewArrival = false) {
    ensureStateMaps()
    def roomIdx = rNum.toInteger()
    def targetSpeaker = settings["roomSpeaker_${roomIdx}"]
    
    def allSpeakers = []
    if (targetSpeaker) allSpeakers << targetSpeaker
    
    if (type == "Good Morning" && settings["roomGlobalMorning_${roomIdx}"] && globalIndoorSpeaker) {
        allSpeakers.addAll([globalIndoorSpeaker].flatten().findAll { it != null })
    }
    
    if (allSpeakers.size() == 0 && globalIndoorSpeaker) {
        allSpeakers.addAll([globalIndoorSpeaker].flatten().findAll { it != null })
    }
    
    allSpeakers = allSpeakers.flatten().findAll { it != null }.unique { it.id }
    
    def vol = null
    if (type == "Good Morning") {
        vol = settings["roomVolumeGM_${roomIdx}"] != null ? settings["roomVolumeGM_${roomIdx}"] : settings.globalVolume
    } else {
        vol = settings["roomVolumeGN_${roomIdx}"] != null ? settings["roomVolumeGN_${roomIdx}"] : settings.globalVolume
    }
    
    if (allSpeakers.size() > 0) {
        def finalMsg = buildRoomGreeting(roomIdx, type, [isNewArrival: isNewArrival, isTest: true])
        enqueueTTS(allSpeakers, finalMsg, vol, 1)
        log.info "TESTING ${type.toUpperCase()} (${settings["roomName_${roomIdx}"]}): '${finalMsg}' at Volume: ${vol}"
    } else {
        log.warn "Cannot test Room Greeting - no speaker assigned for Room ${roomIdx}."
    }
}

// --- REMAINING TEST TRIGGERS ---

def testRoomNews(rNum) {
    def feedUrl = settings["roomNewsFeed_${rNum}"] ?: "https://feeds.npr.org/1001/rss.xml"
    def finalMsg = ""
    try {
        httpGet([uri: feedUrl, headers: ["User-Agent": "Mozilla/5.0 (Hubitat; AdvancedVoiceButler)"], timeout: 10, textParser: true]) { resp ->
            if (resp.status == 200 && resp.data) {
                def rss = parseRssResponse(resp)
                def items = rss?.channel?.item
                if (items && items.size() >= 2) {
                    def title1 = items[0].title.text().trim().replace("&", "and").replace("\"", "")
                    def title2 = items[1].title.text().trim().replace("&", "and").replace("\"", "")
                    finalMsg = "Here is your morning news briefing test. ${title1}. In other news, ${title2}."
                }
            }
        }
    } catch (Exception e) { log.warn "Voice Butler: Room News Fetch Error - ${e}"; finalMsg = "Error fetching news." }
    
    if (finalMsg) {
        def targetSpeaker = settings["roomSpeaker_${rNum}"] ?: globalIndoorSpeaker
        def targetVol = settings["roomVolumeGM_${rNum}"] != null ? settings["roomVolumeGM_${rNum}"] : settings["roomVolume_${rNum}"]
        if (!targetSpeaker && globalIndoorSpeaker) targetVol = globalVolume
        if (targetSpeaker) enqueueTTS(targetSpeaker, finalMsg, targetVol, 1)
        log.info "TESTING ROOM NEWS (${settings["roomName_${rNum}"]}): '${finalMsg}'"
    }
}

def testDepartureGreeting(int idx) {
    if (outdoorSpeaker) {
        def uName = settings["depUserName_${idx}"] ?: "Guest"
        def displayUserName = applyAlias(uName)
        def messages = []
        for (int m = 1; m <= 10; m++) {
            def msg = settings["depMessage_${idx}_${m}"]
            if (msg) messages << msg
        }
        if (!messages) messages = ["Have a good trip %name%."]
        
        def rawMsg = messages[new Random().nextInt(messages.size())]
        def finalMsg = rawMsg.replace("%name%", displayUserName)
        
        def bdayMsg = getBirthdayMessage(uName, "Departure")
        if (bdayMsg) {
            finalMsg = "${finalMsg} ${bdayMsg}"
        }
        
        def annivMsg = getAnniversaryMessage("Departure", uName)
        if (annivMsg) {
            finalMsg = "${finalMsg} ${annivMsg}"
        }
        
        finalMsg = applyDynamicVars(finalMsg)
        
        def profileVol = settings["depVolume_${idx}"]
        def targetVolume = profileVol != null ? profileVol : (settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"])
        def rMode = settings["depRoutingMode_${idx}"] ?: "Outdoor Speaker Only"
        
        log.info "TESTING DEPARTURE GREETING (Profile ${idx}): '${finalMsg}'"
        executeRoutedTTS(finalMsg, rMode, settings.globalVolume, targetVolume, 1)
    } else {
        log.warn "Cannot test Departure greeting - no outdoor speaker assigned."
    }
}

def testPartyModeGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 3; d++) {
            def msg = settings["partyMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = getDefaultMessages("PartyMode")
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def volLog = settings.partyVolume != null ? "${settings.partyVolume}%" : "Hardware Default"
        def rMode = settings.partyRoutingMode ?: "Outdoor Speaker Only"
        
        log.info "TESTING PARTY MODE GREETING: '${randomMsg}'"
        executeRoutedTTS(randomMsg, rMode, settings.globalVolume, settings.partyVolume != null ? settings.partyVolume : settings.outdoorVolume, 1, true)
    } else {
        log.warn "Cannot test Party Mode greeting - no outdoor speaker assigned."
    }
}

def testDndGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 10; d++) {
            def msg = settings["dndMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = getDefaultMessages("DND")
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def volLog = outdoorVolume != null ? "${outdoorVolume}%" : "Hardware Default"
        def rMode = settings.dndRoutingMode ?: "Outdoor Speaker Only"
        
        log.info "TESTING DND GREETING: '${randomMsg}'"
        executeRoutedTTS(randomMsg, rMode, settings.globalVolume, settings.outdoorVolume, 1, true)
    } else {
        log.warn "Cannot test DND greeting - no outdoor speaker assigned."
    }
}

def testAfterHoursGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 15; d++) {
            def msg = settings["afterHoursMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = getDefaultMessages("AfterHours")
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def targetVol = settings.afterHoursVolume != null ? settings.afterHoursVolume : settings.outdoorVolume
        def rMode = settings.afterHoursRoutingMode ?: "Outdoor Speaker Only"
        
        log.info "TESTING AFTER HOURS GREETING: '${randomMsg}'"
        executeRoutedTTS(randomMsg, rMode, settings.globalVolume, targetVol, 1, true)
    } else {
        log.warn "Cannot test After Hours greeting - no outdoor speaker assigned."
    }
}

def testDaytimeGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 20; d++) {
            def msg = settings["daytimeMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = getDefaultMessages("Daytime")
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def targetVol = settings.daytimeDoorbellVolume != null ? settings.daytimeDoorbellVolume : settings.outdoorVolume
        def rMode = settings.daytimeRoutingMode ?: "Outdoor Speaker Only"
        
        log.info "TESTING DAYTIME GREETING: '${randomMsg}'"
        executeRoutedTTS(randomMsg, rMode, settings.globalVolume, targetVol, 1, true)
    } else {
        log.warn "Cannot test Daytime greeting - no outdoor speaker assigned."
    }
}

def testArrivalGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        if (arrivalMode == "Automatic (Reads lock memory)") {
            for (int m = 1; m <= 10; m++) {
                def msg = settings["autoGreeting_${m}"]
                if (msg) messages << msg
            }
        } else {
            for (int m = 1; m <= 10; m++) {
                def msg = settings["defaultArrivalMessage_${m}"]
                if (msg) messages << msg
            }
        }
        if (!messages) messages = getDefaultMessages("Arrival")
        def rawMsg = messages[new Random().nextInt(messages.size())]
        
        def testName = settings.adminUserAlias ?: "Test User"
        def displayUserName = applyAlias(testName)
        def greetingToPlay = rawMsg.replace("%name%", displayUserName)
        
        def bdayMsg = getBirthdayMessage(testName, "Arrival")
        if (bdayMsg) {
            greetingToPlay = "${greetingToPlay} ${bdayMsg}"
        }
        
        def annivMsg = getAnniversaryMessage("Arrival", testName)
        if (annivMsg) {
            greetingToPlay = "${greetingToPlay} ${annivMsg}"
        }
        
        greetingToPlay = applyDynamicVars(greetingToPlay)
        
        def outdoorTargetVol = settings["arrivalVolume"] != null ? settings["arrivalVolume"] : settings["outdoorVolume"]
        def indoorTargetVol = settings["arrivalIndoorVolume"] != null ? settings["arrivalIndoorVolume"] : settings["globalVolume"]
        
        def indoorMsg = ""
        if (settings.arrivalIndoorSpeaker) {
            indoorMsg = (settings.indoorArrivalMessage ?: "%name% has arrived home.").replace("%name%", displayUserName)
            indoorMsg = applyDynamicVars(indoorMsg)
        }
        
        def played = false
        if (outdoorSpeaker) {
            log.info "TESTING OUTDOOR ARRIVAL: '${greetingToPlay}'"
            enqueueTTS(outdoorSpeaker, greetingToPlay, outdoorTargetVol, 1, true)
            played = true
        }
        
        if (settings.arrivalFoyerSpeaker) {
            log.info "TESTING FOYER ARRIVAL: '${greetingToPlay}'"
            enqueueTTS(settings.arrivalFoyerSpeaker, greetingToPlay, indoorTargetVol, 1, true)
            played = true
        }
        
        if (settings.arrivalIndoorSpeaker) {
            def rMode = settings.arrivalNoticeRoutingMode ?: "Global Indoor Speaker Only"
            log.info "TESTING INDOOR ARRIVAL NOTICE: '${indoorMsg}'"
            if (executeRoutedTTS(indoorMsg, rMode, indoorTargetVol, outdoorTargetVol, 1, true)) {
                played = true
            }
        }
        
        if (!played) {
            log.warn "Cannot test Arrival greeting - no speakers are configured to play it."
        }
    } else {
        log.warn "Cannot test Arrival greeting - no outdoor speaker assigned."
    }
}

def testIntruderGreeting() {
    if (outdoorSpeaker) {
        def messages = []
        for (int d = 1; d <= 10; d++) {
            def msg = settings["intruderMessage_${d}"]
            if (msg) messages << msg
        }
        if (!messages) messages = getDefaultMessages("Intruder")
        def randomMsg = messages[new Random().nextInt(messages.size())]
        randomMsg = applyDynamicVars(randomMsg)
        
        def targetVol = settings.intruderVolume != null ? settings.intruderVolume : settings.outdoorVolume
        def rMode = settings.intruderRoutingMode ?: "Outdoor Speaker Only"
        
        log.info "TESTING INTRUDER GREETING: '${randomMsg}'"
        executeRoutedTTS(randomMsg, rMode, settings.globalVolume, targetVol, 1)
    } else {
        log.warn "Cannot test Intruder greeting - no outdoor speaker assigned."
    }
}

// --- CALENDAR & HISTORY HELPERS ---

def getTodayHoliday() {
    def cal = Calendar.getInstance(location.timeZone)
    cal.setTime(new Date())
    int m = cal.get(Calendar.MONTH) + 1
    int d = cal.get(Calendar.DAY_OF_MONTH)
    int dow = cal.get(Calendar.DAY_OF_WEEK)
    int wom = cal.get(Calendar.DAY_OF_WEEK_IN_MONTH)
    
    if (m == 1 && d == 1) return "New Year's Day"
    if (m == 2 && d == 2) return "Groundhog Day"
    if (m == 2 && d == 14) return "Valentine's Day"
    if (m == 3 && d == 17) return "Saint Patrick's Day"
    if (m == 4 && d == 1) return "April Fools' Day"
    if (m == 4 && d == 22) return "Earth Day"
    if (m == 5 && d == 5) return "Cinco de Mayo"
    if (m == 6 && d == 14) return "Flag Day"
    if (m == 6 && d == 19) return "Juneteenth"
    if (m == 7 && d == 4) return "Independence Day"
    if (m == 9 && d == 11) return "Patriot Day"
    if (m == 10 && d == 31) return "Halloween"
    if (m == 11 && d == 11) return "Veterans Day"
    if (m == 12 && d == 24) return "Christmas Eve"
    if (m == 12 && d == 25) return "Christmas Day"
    if (m == 12 && d == 31) return "New Year's Eve"
    
    if (m == 1 && dow == Calendar.MONDAY && wom == 3) return "Martin Luther King Jr. Day"
    if (m == 2 && dow == Calendar.MONDAY && wom == 3) return "Presidents' Day"
    if (m == 5 && dow == Calendar.SUNDAY && wom == 2) return "Mother's Day"
    if (m == 5 && dow == Calendar.MONDAY && (d + 7 > 31)) return "Memorial Day"
    if (m == 6 && dow == Calendar.SUNDAY && wom == 3) return "Father's Day"
    if (m == 9 && dow == Calendar.MONDAY && wom == 1) return "Labor Day"
    if (m == 10 && dow == Calendar.MONDAY && wom == 2) return "Columbus Day"
    if (m == 11 && dow == Calendar.TUESDAY && d >= 2 && d <= 8) return "Election Day"
    if (m == 11 && dow == Calendar.THURSDAY && wom == 4) return "Thanksgiving"
    if (m == 11 && dow == Calendar.FRIDAY && d >= 23 && d <= 29) return "Black Friday"
    
    return null
}

def getAnniversaryMessage(String type, String userName = "") {
    if (!settings.enableAnniversary || !settings.annivMonth || !settings.annivDay) return ""
    def now = new Date()
    def currentMonth = now.format("MM", location.timeZone).toInteger()
    def currentDay = now.format("dd", location.timeZone).toInteger()

    if (settings.annivMonth.toInteger() == currentMonth && settings.annivDay.toInteger() == currentDay) {
        switch(type) {
            case "Arrival": return settings.annivMsgArrival ?: "Happy Anniversary! Welcome home."
            case "Morning": return settings.annivMsgMorning ?: "Happy Anniversary! I hope you both have a fantastic day."
            case "Departure": return "Have a wonderful anniversary today!"
            case "Night": return "Happy Anniversary. Sleep well."
        }
    }
    return ""
}

def stashMessage(String msg) {
    if (!settings.enableInbox) return
    ensureStateMaps()
    if (!state.messageInbox) state.messageInbox = []
    
    if (!state.messageInbox.contains(msg)) {
        state.messageInbox.add(msg)
        if (state.messageInbox.size() > 5) state.messageInbox.remove(0) 
        if (settings.enableDebug) log.debug "INBOX: Stashed message -> '${msg}'"
    }
}

/**
 * Advanced Voice Butler
 *
 * Author: ShaneAllen
 */

def getBirthdayMessage(String arrivingUser, String type) {
    if (!settings.numBirthdays) return ""
    def numBdays = settings.numBirthdays as Integer
    if (numBdays <= 0) return ""
    
    def now = new Date()
    def currentMonth = now.format("MM", location.timeZone).toInteger()
    def currentDay = now.format("dd", location.timeZone).toInteger()

    for (int i = 1; i <= numBdays; i++) {
        def bName = settings["bdayName_${i}"] ?: ""
        if (!bName) continue
        
        def bType = settings["bdayType_${i}"] ?: "Kid (30-Day Countdown)"
        def bMonthInt = settings["bdayMonth_${i}"]?.toInteger()
        def bDayInt = settings["bdayDay_${i}"]?.toInteger()
        
        // Safely extract the list of users who should hear this reminder
        def notifyUserSetting = settings["bdayNotifyUser_${i}"]
        def nUsers = [notifyUserSetting].flatten().findAll { it != null }.collect { it.toLowerCase() }
        
        // Check if the person in the room IS the birthday person
        def isBirthdayPerson = arrivingUser.toLowerCase().contains(bName.toLowerCase()) || arrivingUser.toLowerCase().contains(applyAlias(bName).toLowerCase())
        
        // Check if the people in the room are the designated "Notify Users" (e.g., Parents)
        def isTargetPresent = nUsers.isEmpty() || nUsers.contains(arrivingUser.toLowerCase()) || nUsers.any { nu -> state.hasArrivedToday?.keySet()?.any { k -> k.toLowerCase() == nu } }

        // If the person in the room is neither the birthday person NOR the designated notified user, skip.
        if (!isBirthdayPerson && !isTargetPresent) continue

        // 1. Exact Birthday Match
        if (bMonthInt == currentMonth && bDayInt == currentDay) {
            if (isBirthdayPerson) {
                if (type == "Arrival") return settings.bdayMsgArrival?.replace("%name%", applyAlias(bName)) ?: "Happy Birthday ${applyAlias(bName)}!"
                else if (type == "Morning") return settings.bdayMsgMorning?.replace("%name%", applyAlias(bName)) ?: "Happy Birthday ${applyAlias(bName)}! I hope you have a fantastic day."
                else return "Happy Birthday ${applyAlias(bName)}."
            } else {
                return "By the way, today is ${applyAlias(bName)}'s birthday. Please remember to wish them a Happy Birthday!"
            }
        }
        
        // 2. Birthday Month Countdown (Kids)
        if (bType.contains("Kid") && (type == "Morning" || type == "Arrival")) {
            def bdayCal = Calendar.getInstance(location.timeZone)
            bdayCal.set(Calendar.MONTH, bMonthInt - 1)
            bdayCal.set(Calendar.DAY_OF_MONTH, bDayInt)
            bdayCal.set(Calendar.HOUR_OF_DAY, 0)
            bdayCal.set(Calendar.MINUTE, 0)
            bdayCal.set(Calendar.SECOND, 0)
            
            // If the birthday passed earlier this year, look to next year
            if (bdayCal.time.time < now.time && !(bMonthInt == currentMonth && bDayInt == currentDay)) {
                bdayCal.add(Calendar.YEAR, 1)
            }
            
            def daysUntil = Math.round((bdayCal.time.time - now.time) / 86400000.0).toInteger()
            
            // Count down if it is exactly 30 days or less
            if (daysUntil > 0 && daysUntil <= 30) {
                if (isBirthdayPerson) {
                    def cdMsg = settings.bdayCountdownMsg ?: "By the way, you only have %days% days until your birthday!"
                    return cdMsg.replace("%days%", daysUntil.toString()).replace("%name%", applyAlias(bName))
                } else if (isTargetPresent) {
                    return "As a quick reminder, ${applyAlias(bName)}'s birthday is coming up in exactly ${daysUntil} days."
                }
            }
        }
        
        // 3. The 5-Day Adult Gift Warning
        if (bType.contains("Adult") && (type == "Morning" || type == "Arrival")) {
            def cal = Calendar.getInstance(location.timeZone)
            cal.add(Calendar.DAY_OF_YEAR, 5)
            if (cal.get(Calendar.MONTH) + 1 == bMonthInt && cal.get(Calendar.DAY_OF_MONTH) == bDayInt) {
                if (!isBirthdayPerson && isTargetPresent) {
                     return "As a quick reminder, ${applyAlias(bName)}'s birthday is in exactly 5 days. Please ensure you have secured a gift."
                }
            }
        }
    }
    
    // 4. Mother's Day / Father's Day check
    def holiday = getTodayHoliday()
    if (settings.enableParentsDay && holiday == "Mother's Day") {
        def mUser = settings.mothersDayUser
        def mUsers = [mUser].flatten().findAll { it != null }.collect { it.toLowerCase() }
        if (mUsers.isEmpty() || mUsers.contains(arrivingUser.toLowerCase())) {
            return "Also, today is Mother's Day. Please remember to call your mother."
        }
    }
    if (settings.enableParentsDay && holiday == "Father's Day") {
        def fUser = settings.fathersDayUser
        def fUsers = [fUser].flatten().findAll { it != null }.collect { it.toLowerCase() }
        if (fUsers.isEmpty() || fUsers.contains(arrivingUser.toLowerCase())) {
            return "Also, today is Father's Day. Please remember to call your father."
        }
    }

    return ""
}

def midnightReset() {
    ensureStateMaps()
    
    def newHasArrived = [:]
    def newResetReasons = [:]
    
    // 1. INTELLIGENT CARRY-OVER
    // We look at everyone currently home and move them to the new day's roster immediately.
    state.hasArrivedToday.each { uName, arrived ->
        if (arrived == true) {
            newHasArrived[uName] = true
            // We set the context to 'Present' so the dashboard doesn't show a reset message
            newResetReasons[uName] = "Present" 
        }
    }
    
    // 2. APPLY THE NEW ROSTER
    state.hasArrivedToday = newHasArrived
    state.hasDepartedToday = [:] // Clear departures so they can be tracked fresh today
    state.resetReasons = newResetReasons
    
    // Set the global status for anyone NOT already home
    state.globalResetReason = "Awaiting First Entry"
    
    // 3. LOG ROTATION (For the Morning Briefings)
    state.lastNightMotionCount = state.nightMotionCount ?: 0
    state.lastAwayDoorbellCount = state.awayDoorbellCount ?: 0
    state.nightMotionCount = 0
    state.awayDoorbellCount = 0
    state.anomalyAlertedToday = [:]
    
    // --- OPTION 3: PROGRESSIVE ESCALATION RESET ---
    state.reminderCounts = [:]
    // ----------------------------------------------
    
    // 4. QUEUE MAINTENANCE
    state.ttsQueue = []
    state.speakingUntil = 0
    state.currentPriority = 99
    state.originalVolumes = [:] 
    
    def residentList = newHasArrived.keySet().join(', ')
    addToHistory("SYSTEM: Midnight transition complete. Residents carried over: ${residentList ?: 'None'}")
}

def appButtonHandler(btn) {
    ensureStateMaps()
    
    if (btn != "btnRefresh") {
        addToHistory("SYSTEM: Action triggered via UI Dashboard - ${btn}")
    }
    
    if (btn == "btnForceReset") {
        state.hasArrivedToday = [:]
        state.hasDepartedToday = [:]
        state.resetReasons = [:]
        state.globalResetReason = "Reset manually via Dashboard"
        state.lastDepartureTime = [:]
        state.ttsQueue = []
        state.speakingUntil = 0
        state.currentPriority = 99
        addToHistory("SYSTEM: Manual reset of all Daily statuses triggered. TTS Queue Flushed.")
    } else if (btn == "btnForceSync") {
        pollCalendars()
        pollBreakingNews()
        if (settings.enableMealTime && settings.mealTimeNewsWeather) syncMealNews()
        def numRoomsSet = settings.numRooms ? settings.numRooms as Integer : 0
        for (int i = 1; i <= numRoomsSet; i++) {
            if (settings["roomNewsEnable_${i}"]) syncRoomNews(i)
        }
    } else if (btn == "btnTestGoogleApi") {
        testGoogleIntegration() 
    } else if (btn == "btnTestQuickExit") {
        def gasData = getCheapestGas()
        def msg = "Farewell. The security perimeter is active. Have a safe trip."
        if (gasData && gasData.speech) msg += gasData.speech
        executeRoutedTTS(msg, settings.quickExitRoutingMode ?: "Global Indoor Speaker Only", settings.globalVolume, settings.quickExitVolume ?: settings.globalVolume, 1, true)
        if (settings.enableTravelPush && settings.notificationDevice && gasData?.rawAddress) {
            settings.notificationDevice.each { dev ->
                try { dev.deviceNotification("Test Alert - Gas: ${gasData.rawName} (${gasData.rawAddress})") } catch(e) {}
            }
        }
    } else if (btn == "btnTestGlobal") {
        enqueueTTS(globalIndoorSpeaker, applyDynamicVars("This is a test of the global indoor speakers. The time is %time%."), globalVolume, 1)
    } else if (btn == "btnTestOutdoor") {
        enqueueTTS(outdoorSpeaker, applyDynamicVars("This is a test of the outdoor speaker. The date is %date%."), outdoorVolume, 1)
    } else if (btn == "btnTestIndoorRouting") {
        executeRoutedTTS(applyDynamicVars(settings.indoorDoorbellMsg ?: "This is %butler%. %interruption%, but there is a visitor at the front door."), settings.indoorDoorbellRoutingMode ?: "Follow-Me + Fallback (Global ONLY if no motion)", settings.globalVolume, settings.outdoorVolume, 1)
    } else if (btn == "btnTestMealNews") testMealNews()
    else if (btn == "btnTestBreakingNews") executeBreakingNews("The Hubitat Voice Butler successfully received a breaking news transmission", true)
    else if (btn == "btnTestMealTime") mealTimeHandler([value: "test"])
    else if (btn == "btnTestScreenTime") {
        def msgs = []
        for (int d = 1; d <= 5; d++) { if (settings["screenTimeMsg_${d}"]) msgs << settings["screenTimeMsg_${d}"] }
        if (!msgs) msgs = getDefaultMessages("ScreenTime")
        executeRoutedTTS(applyDynamicVars(msgs[new Random().nextInt(msgs.size())]), settings.screenTimeRoutingMode ?: "Global Indoor Speaker Only", settings.screenTimeVolume ?: globalVolume, settings.outdoorVolume, 1, false, settings.screenTimeSpeaker)
    } else if (btn == "btnTestCalendar") executeCalendarAlert([title: "a test appointment", timeStr: "1 Hour"])
    else if (btn.startsWith("btnTestRoomNews_")) testRoomNews(btn.split("_")[1].toInteger())
    else if (btn.startsWith("btnTestRoomSpk_")) {
        def rNum = btn.split("_")[1].toInteger()
        enqueueTTS(settings["roomSpeaker_${rNum}"] ?: globalIndoorSpeaker, applyDynamicVars("Testing the speaker connection for ${settings["roomName_${rNum}"] ?: "Room ${rNum}"}."), settings["roomVolumeGN_${rNum}"] != null ? settings["roomVolumeGN_${rNum}"] : settings["roomVolume_${rNum}"], 1)
    } else if (btn.startsWith("btnTestDeparture_")) testDepartureGreeting(btn.split("_")[1].toInteger())
    else if (btn.startsWith("btnResetDepMsgs_")) resetDepartureMessages(btn.split("_")[1].toInteger())
    else if (btn.startsWith("btnTestGN_")) testRoomGreeting(btn.split("_")[1].toInteger(), "Good Night")
    else if (btn.startsWith("btnTestGM_")) testRoomGreeting(btn.split("_")[1].toInteger(), "Good Morning")
    else if (btn == "btnTestPartyMode") testPartyModeGreeting()
    else if (btn == "btnTestDND") testDndGreeting()
    else if (btn == "btnTestAfterHours") testAfterHoursGreeting()
    else if (btn == "btnTestDaytime") testDaytimeGreeting()
    else if (btn == "btnTestArrival") testArrivalGreeting()
    else if (btn == "btnTestIntruder") testIntruderGreeting()
    else if (btn == "btnTestMidday") executeMiddayMaintenance(true)
    else if (btn == "btnTestMorningReport") playButlerReport([type: "Morning", count: 3])
    else if (btn == "btnTestArrivalReport") playButlerReport([type: "Arrival", count: 2])
    else if (btn == "btnClearNotes") clearNotesEndpoint()
    else if (btn.startsWith("btnTestHeadedHome_")) executeHeadedHome(btn.split("_")[1].toInteger())    
    else if (btn == "btnTestHealth") executeHealthWindow(true)    
}


def testGoogleIntegration() {
    def testDest = state.nextEventLocation ?: "4538 US-231, Wetumpka, AL 36092"
    log.info "TEST: Pinging Google for travel to ${testDest}..."
    
    def mins = getTravelInfo(testDest)
    def gasData = getCheapestGas()
    
    def msg = ""
    if (mins != null) {
        msg = "Google API Test Successful. It currently takes ${mins} minutes to get to your destination from your home."
        if (gasData) { msg += gasData.speech }
        
        // --- SEND THE TEST PUSH NOTIFICATION WITH LINK ---
        if (settings.enableTravelPush && settings.notificationDevice) {
            def pushMsg = "TEST ALERT - Travel Intel:\nDestination: ${testDest}\nDrive Time: ${mins} minutes"
            if (gasData?.rawAddress) {
                pushMsg += "\n\nCheapest Gas:\n${gasData.rawName}\n${gasData.rawAddress}\nTap to Navigate: ${gasData.navLink}"
            }
            settings.notificationDevice.each { dev ->
                try { dev.deviceNotification(pushMsg) } catch(e) {}
            }
        }
        
    } else {
        msg = "Google API Test Failed. Please check your API Key, Home Address, and Hubitat Logs."
    }
    
    def targetSpeaker = globalIndoorSpeaker
    if (!targetSpeaker && arrivalFoyerSpeaker) targetSpeaker = arrivalFoyerSpeaker
    if (targetSpeaker) enqueueTTS(targetSpeaker, msg, globalVolume, 1)
    
    addToHistory("TEST: Google Maps/Gas logic check performed.")
}

def addToHistory(String msg) {
    ensureStateMaps()
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "[${timestamp}] ${msg}")
    if (state.historyLog.size() > 30) state.historyLog = state.historyLog.take(30)
}

def checkInternetConnection() {
    try {
        // Ping a reliable endpoint to verify outbound TTS traffic will work
        httpGet([uri: "https://www.google.com", timeout: 5]) { resp ->
            if (resp.status == 200) {
                if (state.internetActive == false) {
                    log.info "Voice Butler: Internet connection restored. TTS resumed."
                }
                state.internetActive = true
            }
        }
    } catch (Exception e) {
        if (state.internetActive != false) {
            log.warn "Voice Butler: Internet connection lost. TTS temporarily suppressed."
        }
        state.internetActive = false
    }
}

def screenTimeHandler(evt) {
    ensureStateMaps()
    def messages = []
    
    // Fetch user-defined messages or fallback to defaults
    for (int d = 1; d <= 5; d++) { 
        if (settings["screenTimeMsg_${d}"]) messages << settings["screenTimeMsg_${d}"] 
    }
    if (!messages) messages = getDefaultMessages("ScreenTime")

    def randomMsg = applyDynamicVars(messages[new Random().nextInt(messages.size())])
    def rMode = settings.screenTimeRoutingMode ?: "Global Indoor Speaker Only"
    def targetVol = settings.screenTimeVolume != null ? settings.screenTimeVolume : settings.globalVolume

    executeRoutedTTS(randomMsg, rMode, targetVol, settings.outdoorVolume, 2, false, settings.screenTimeSpeaker)
    addToHistory("SCREEN TIME: Timer expired. Queued: '${randomMsg}'")
}

// --- GOOGLE WEBHOOK HANDLERS ---

def handleGoogleEmail() {
    // 1. Check if the master feature switch is turned on
    if (!settings.enableEmailAlerts) {
        if (settings.enableDebug) log.debug "GOOGLE EMAIL: Alert ignored (Feature is disabled in app settings)."
        return [status: "disabled"]
    }
    
    // 2. Check if the house is in an allowed mode
    def allowedModes = [settings.emailAlertModes].flatten().findAll { it != null }
    if (allowedModes.size() > 0 && !allowedModes.contains(location.mode)) {
        if (settings.enableDebug) log.debug "GOOGLE EMAIL: Alert suppressed due to mode restriction (Current mode: ${location.mode})."
        return [status: "suppressed_by_mode"]
    }

    def body = request.JSON
    if (body) {
        def sender = body.sender?.replaceAll(/<.*?>/, "")?.trim() ?: "an unknown sender"
        def subject = body.subject ?: "No Subject"
        def targetVol = settings.emailVolume != null ? settings.emailVolume : settings.globalVolume
        def rMode = settings.emailRoutingMode ?: "Global Indoor Speaker Only"
        def msg = ""

        // --- NEW: Handle Ooma Voicemails ---
        if (body.isVoicemail) {
            // Respect the dedicated Ooma Voicemail toggle!
            if (settings.enableOomaVoicemail == false) {
                if (settings.enableDebug) log.debug "OOMA VOICEMAIL: Suppressed (Ooma Voicemail is toggled OFF)."
                return [status: "ooma_disabled"]
            }
            
            def caller = body.sender ?: "an unknown caller"
            def time = body.time ?: "just now"
            def transcript = body.msgContent ?: "The caller did not leave a transcription."
            
            if (getPresentUsers().size() == 0) {
                stashMessage("you received a voicemail from ${caller}")
                return [status: "stashed"]
            }
            
            msg = "%interruption%, you have a new Ooma voicemail from ${caller}, received at ${time}. The message says: ${transcript}"
            
            if (settings.enableDebug) log.debug "OOMA VOICEMAIL RECEIVER: Voicemail notification received from ${caller}."
            
            executeRoutedTTS(applyDynamicVars(msg), rMode, settings.globalVolume, targetVol, 2)
            addToHistory("VOICEMAIL ALERT: Missed call announced from ${caller} at ${time}.")
            return [status: "success"]
        }

        // 3. Handle Delivery Notifications
        if (body.isDelivery) {
            // Respect the dedicated Package Tracking toggle!
            if (settings.enableDeliveryTracking == false) {
                if (settings.enableDebug) log.debug "GOOGLE DELIVERY: Suppressed (Package Tracking is toggled OFF)."
                return [status: "delivery_disabled"]
            }
            
            if (getPresentUsers().size() == 0) {
                stashMessage("a package was delivered to the house")
                return [status: "stashed"]
            }
            
            msg = "%interruption%, I am seeing a delivery notification from ${sender}. The subject is: ${subject}. You have a package arriving today."
            if (settings.enableDebug) log.debug "GOOGLE DELIVERY RECEIVER: Package notification received from ${sender}."
            
            executeRoutedTTS(applyDynamicVars(msg), rMode, settings.globalVolume, targetVol, 2)
            addToHistory("DELIVERY ALERT: Package notification announced from ${sender}.")
            return [status: "success"]
        }

        // 4. Handle Standard Important Emails
        if (subject) {
            def snippet = body.snippet ?: ""
            def prefix = settings.emailPrefix ?: "%interruption%, you have just received an important email from"
            
            msg = "${prefix} ${sender}. The subject is: ${subject}. "
            if (snippet.length() > 0) {
                msg += "The email begins with: ${snippet}"
            }
            
            if (settings.enableDebug) log.debug "GOOGLE EMAIL RECEIVER: New email triggered from ${sender}."
            
            executeRoutedTTS(applyDynamicVars(msg), rMode, settings.globalVolume, targetVol, 2)
            addToHistory("EMAIL ALERT: Important email announced from ${sender}.")
        }
    }
    return [status: "success"]
}

def handleGoogleCalendar() {
    def body = request.JSON
    if (body && body.events != null) {
        
        // 1. Hash Check: Stop CPU spikes. Only reschedule if the calendar actually changed.
        def newHash = body.events.toString().hashCode()
        if (state.calendarHash == newHash) {
            return [status: "unchanged"]
        }
        state.calendarHash = newHash
        
        // 2. Clear old schedules
        unschedule("executeCalendarAlert")
        state.scheduledCalendarAlerts = []
        
        def now = new Date().time
        def nextTitle = "No Upcoming Events"
        def nextEpoch = null
        
        // 3. Loop through the array and schedule all future events
        body.events.eachWithIndex { evt, idx ->
            def eventEpoch = evt.epoch.toLong()
            def title = evt.title
            
            // Save the very next event for the Dashboard UI
            if (idx == 0) {
                nextTitle = title
                nextEpoch = eventEpoch
            }
            
            if (eventEpoch > now) {
                def intervals = [settings.calAlertIntervals].flatten().findAll { it != null }
                intervals.each { interval ->
                    def offsetMs = 0
                    if (interval == "3 Hours") offsetMs = 3 * 3600000
                    if (interval == "2 Hours") offsetMs = 2 * 3600000
                    if (interval == "1 Hour") offsetMs = 1 * 3600000
                    if (interval == "30 Minutes") offsetMs = 30 * 60000
                    if (interval == "15 Minutes") offsetMs = 15 * 60000
                    
                    def alertTime = eventEpoch - offsetMs
                    if (alertTime > now) {
                        runOnce(new Date(alertTime), "executeCalendarAlert", [data: [title: title, timeStr: interval], overwrite: false])
                    }
                }
                // Schedule Exact Start Time alert (0 Minutes)
                runOnce(new Date(eventEpoch), "executeCalendarAlert", [data: [title: title, timeStr: "0 Minutes"], overwrite: false])
            }
        }
        
        // 4. Update the live dashboard
        state.nextEventName = nextTitle
        state.nextEventEpoch = nextEpoch
        state.nextEventTimeStr = nextEpoch ? new Date(nextEpoch).format("MMM d 'at' h:mm a", location.timeZone) : "--"
        state.calendarSyncTime = new Date().format("h:mm a", location.timeZone)
        
        if (settings.enableDebug) log.debug "GOOGLE CALENDAR: Multi-Event sync complete. Loaded ${body.events.size()} events into the schedule."
    }
    return [status: "success"]
}

def getBridge(String type = "general") {
    def bridges = []
    if (type == "weather") bridges = ["Turning to the weather.", "Here is your forecast.", "Checking the conditions outside."]
    else if (type == "news") bridges = ["Moving on to the news.", "Here are the latest headlines.", "In the news today:"]
    else if (type == "agenda") bridges = ["Let's take a look at your schedule.", "Here is your agenda for today.", "Checking your calendar."]
    else bridges = ["Also,", "Additionally,", "One other thing,"]
    return bridges[new Random().nextInt(bridges.size())] + " "
}

def mailClearedHandler(evt) {
    ensureStateMaps()
    state.reminderCounts["mail"] = 0
    addToHistory("SYSTEM: Mail retrieved. Escalation counter reset.")
}

def getMaintenanceReport(mDevice, boolean isTest = false) {
    if (!mDevice) return ""
    try {
        def maxOverdue = mDevice.currentValue("maxOverdueDays") as Integer ?: 0
        def tasks = mDevice.currentValue("overdueTasks") ?: ""

        if (maxOverdue > 0 && tasks != "") {
            def taskList = tasks.split(",").collect { it.trim() }
            def taskString = ""
            def plural = taskList.size() > 1
            def isAre = plural ? "are" : "is"
            def requireRequires = plural ? "require" : "requires"

            if (taskList.size() == 1) taskString = taskList[0]
            else if (taskList.size() == 2) taskString = "${taskList[0]} and ${taskList[1]}"
            else {
                def last = taskList.pop()
                taskString = "${taskList.join(', ')}, and ${last}"
            }

            if (maxOverdue <= 3) {
                return "Just a friendly reminder %name%, the ${taskString} ${requireRequires} your attention when you have a moment."
            } else if (maxOverdue <= 7) {
                return "Pardon the reminder, but the ${taskString} ${isAre} now past due. Please try to get to them soon."
            } else if (maxOverdue <= 14) {
                return "I must advise you to address the house maintenance, %name%. The ${taskString} ${isAre} significantly overdue."
            } else {
                return "Critical alert. The ${taskString} have been neglected for over two weeks. Immediate action is strongly recommended to prevent systemic wear and tear."
            }
        }
    } catch (Exception e) { log.warn "Voice Butler: Failed to fetch maintenance report - ${e}" }
    return ""
}

def scheduleRandomMiddayMaintenance() {
    if (!settings.enableMiddayMaintenance) return
    
    // Pick a random minute between 660 (11:00 AM) and 900 (3:00 PM) -> 240 minute range
    int randomMinute = 660 + new Random().nextInt(241)
    int h = (randomMinute / 60).toInteger()
    int m = randomMinute % 60
    
    def cal = Calendar.getInstance(location.timeZone)
    cal.set(Calendar.HOUR_OF_DAY, h)
    cal.set(Calendar.MINUTE, m)
    cal.set(Calendar.SECOND, 0)
    
    // Only schedule if the randomly selected time hasn't already passed today
    if (cal.getTime().time > new Date().time) {
        runOnce(cal.getTime(), "executeMiddayMaintenance", [overwrite: true])
        if (settings.enableDebug) log.debug "SYSTEM: Scheduled Random Midday Maintenance Reminder for ${cal.getTime().format('h:mm a', location.timeZone)}"
    }
}

def executeMiddayMaintenance(isTest = false) {
    ensureStateMaps()
    
    if (!isTest) {
        // Gate 1: Mode Check
        def allowedModes = [settings.middayMaintenanceModes].flatten().findAll { it != null }
        if (allowedModes.size() > 0 && !allowedModes.contains(location.mode)) return
        
        def presentFolks = getPresentUsers()
        // Gate 2: Empty House Check
        if (presentFolks.size() == 0) return 
        
        // Gate 3: Privacy/Guest Check
        def guestList = [settings.guestUsers].flatten().findAll { it != null }.collect { it.toLowerCase() }
        if (settings.guestCustomUsers) guestList += settings.guestCustomUsers.split(',').collect { it.trim().toLowerCase() }
        
        def hasGuest = presentFolks.any { person -> 
            guestList.contains(person.toLowerCase()) || guestList.contains(applyAlias(person).toLowerCase())
        }
        if (hasGuest) {
            addToHistory("MIDDAY MAINTENANCE: Suppressed. A recognized guest is currently in the house.")
            return 
        }
    }
    
    def mDevice = settings.middayMaintenanceDevice
    if (!mDevice) return
    
    def mText = getMiddayMaintenanceReport(mDevice)
    if (!mText && !isTest) return // Nothing is due, stay silent!
    
    if (isTest && !mText) mText = "%interruption%, but this is a test of the midday reminder system. If you had overdue tasks, I would politely list them here."
    
    def finalMsg = applyDynamicVars(mText)
    def targetVol = settings.middayVolume != null ? settings.middayVolume : settings.globalVolume
    
    executeRoutedTTS(finalMsg, settings.middayRoutingMode ?: "Global Indoor Speaker Only", settings.globalVolume, targetVol, 2)
    addToHistory("MIDDAY MAINTENANCE: Random reminder fired. Queued: '${finalMsg}'")
}

def getMiddayMaintenanceReport(mDevice) {
    if (!mDevice) return ""
    try {
        def maxOverdue = mDevice.currentValue("maxOverdueDays") as Integer ?: 0
        def tasks = mDevice.currentValue("overdueTasks") ?: ""

        if (maxOverdue > 0 && tasks != "") {
            def taskList = tasks.split(",").collect { it.trim() }
            def taskString = ""
            def plural = taskList.size() > 1
            def isAre = plural ? "are" : "is"

            if (taskList.size() == 1) taskString = taskList[0]
            else if (taskList.size() == 2) taskString = "${taskList[0]} and ${taskList[1]}"
            else {
                def last = taskList.pop()
                taskString = "${taskList.join(', ')}, and ${last}"
            }

            // Escalating Grammar
            if (maxOverdue <= 7) {
                return "%interruption%, but as a friendly midday reminder, the ${taskString} ${isAre} due for maintenance when you have a free moment."
            } else if (maxOverdue <= 14) {
                return "%interruption%. I wanted to remind you that the ${taskString} ${isAre} significantly overdue for maintenance."
            } else {
                return "%interruption%. Please be advised that the ${taskString} have been neglected for over two weeks. It is highly recommended to address this today."
            }
        }
    } catch (Exception e) { log.warn "Voice Butler: Failed to fetch midday report - ${e}" }
    return ""
}

// --- NEW: NOTES PORTAL WEB HANDLERS ---
def serveNotesPage() {
    try {
        ensureStateMaps()
        def trackedNames = getTrackedUsers()
        def userOptions = ""
        trackedNames.each { u -> userOptions += "<option value='${u}'>${u}</option>" }

        def apiUrl = getFullApiServerUrl()

        if (state.butlerNotes && state.butlerNotes.size() > 0 && state.butlerNotes[0] instanceof String) {
            state.butlerNotes = []
        }

        // --- 1. DYNAMICALLY BUILD PRESENCE CARDS WITH MANUAL OVERRIDE ---
        def presenceHtml = "<div style='display: flex; flex-wrap: wrap; gap: 10px; margin-top: 15px;'>"
        if (trackedNames.size() > 0) {
            trackedNames.each { u ->
                def arrived = state.hasArrivedToday != null && (state.hasArrivedToday[u] == true || state.hasArrivedToday[u] == "true")
                def departed = state.hasDepartedToday != null && (state.hasDepartedToday[u] == true || state.hasDepartedToday[u] == "true")
                def isHome = arrived && !departed
                
                def statusColor = isHome ? "#27ae60" : "#c0392b"
                def statusIcon = isHome ? "🏠 Home" : "🚗 Away"
                
                presenceHtml += "<div style='background-color: #1e1e1e; padding: 12px; border-radius: 6px; border-left: 4px solid ${statusColor}; flex: 1 1 calc(50% - 10px); box-sizing: border-box; font-size: 14px; display: flex; justify-content: space-between; align-items: center;'>"
                presenceHtml += "<div><b>${u}</b><br><span style='color:${statusColor}; font-weight: bold;'>${statusIcon}</span></div>"
                
                // If they are home, show the manual "Mark Away" button
                if (isHome) {
                    presenceHtml += """
                        <form action="${apiUrl}/presence/depart?access_token=${state.accessToken}" method="POST" style="margin:0;">
                            <input type="hidden" name="targetUser" value="${u}">
                            <button type="submit" style="padding: 6px 10px; margin: 0; width: auto; font-size: 12px; background-color: #c0392b; color: #fff; border-radius: 4px;">Mark Away</button>
                        </form>
                    """
                }
                presenceHtml += "</div>"
            }
        } else {
            presenceHtml += "<p style='color:#aaa; font-style:italic;'>No presence users configured.</p>"
        }
        presenceHtml += "</div>"
        
        // --- 2. DYNAMICALLY BUILD ROOM OPTIONS FOR AGENDA ---
        def roomOptionsHtml = ""
        def numR = settings.numRooms ? settings.numRooms as Integer : 0
        for (int i = 1; i <= numR; i++) {
            def rName = settings["roomName_${i}"] ?: "Room ${i}"
            roomOptionsHtml += "<option value='${i}'>${rName}</option>"
        }

        // --- 3. DYNAMICALLY BUILD THE DIRECTORY CARDS ---
        def numC = settings.numContacts ? settings.numContacts as Integer : 0
        def directoryHtml = ""
        if (settings.enableDirectory && numC > 0) {
            directoryHtml += "<h3 style='margin-top: 30px; margin-bottom: 10px; color:#82b1ff; border-bottom: 2px solid #333; padding-bottom: 5px;'>📞 Request Announcement</h3>"
            for (int i = 1; i <= numC; i++) {
                def cName = settings["contactName_${i}"]
                def cInfo = settings["contactInfo_${i}"]
                if (cName && cInfo) {
                    directoryHtml += """
                        <div class='note-item' style='display: flex; justify-content: space-between; align-items: center; border-left-color: #8e44ad; background: #222;'>
                            <div style='flex-grow: 1; padding-right: 10px;'><b>${cName}</b><br><span style='color:#aaa;'>${cInfo}</span></div>
                            <form action="${apiUrl}/directory/announce?access_token=${state.accessToken}" method="POST" style="margin:0; flex-shrink: 0;">
                                <input type="hidden" name="contactId" value="${i}">
                                <button type="submit" style="padding: 8px 12px; margin-bottom: 0; width: auto; font-size: 14px; background-color: #8e44ad; color: #fff; border-radius: 6px;">Announce</button>
                            </form>
                        </div>
                    """
                }
            }
        }

        // --- 4. DYNAMICALLY BUILD THE WI-FI CARD ---
        def wifiHtml = ""
        if (settings.enableWifiPortal && settings.wifiSSID) {
            wifiHtml = """
                <h3 style='margin-top: 30px; margin-bottom: 10px; color:#82b1ff; border-bottom: 2px solid #333; padding-bottom: 5px;'>📶 Guest Wi-Fi</h3>
                <div class='note-item' style='display: flex; justify-content: space-between; align-items: center; border-left-color: #3498db; background: #222;'>
                    <div style='flex-grow: 1; padding-right: 10px;'><b>${settings.wifiSSID}</b><br><span style='color:#aaa;'>Tap to announce & push password</span></div>
                    <form action="${apiUrl}/wifi/announce?access_token=${state.accessToken}" method="POST" style="margin:0; flex-shrink: 0;">
                        <input type="hidden" name="dummyData" value="trigger">
                        <button type="submit" style="padding: 8px 12px; margin-bottom: 0; width: auto; font-size: 14px; background-color: #3498db; color: #fff; border-radius: 6px;">Announce</button>
                    </form>
                </div>
            """
        }

        // --- 5. DYNAMICALLY BUILD QUICK REPLIES ---
        def quickReplyHtml = ""
        def numQR = settings.numQuickReplies ? settings.numQuickReplies as Integer : 0
        if (numQR > 0) {
            quickReplyHtml += "<h3 style='margin-top: 10px; margin-bottom: 10px; color:#f39c12; border-bottom: 2px solid #333; padding-bottom: 5px;'>⚡ Quick Replies (Outdoor)</h3>"
            quickReplyHtml += "<div style='display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 35px;'>"
            for (int i = 1; i <= numQR; i++) {
                def qrName = settings["quickReplyName_${i}"] ?: "Reply ${i}"
                quickReplyHtml += """
                    <form action="${apiUrl}/reply/quick?access_token=${state.accessToken}" method="POST" style="margin:0; flex: 1 1 calc(50% - 10px);">
                        <input type="hidden" name="dummyData" value="trigger">
                        <input type="hidden" name="replyId" value="${i}">
                        <button type="submit" style="background-color: #f39c12; color: #fff; padding: 10px; border-radius: 6px; font-size: 14px; width: 100%; height: 100%;">${qrName}</button>
                    </form>
                """
            }
            quickReplyHtml += "</div>"
        }

        // --- 6. DYNAMICALLY BUILD TASK DASHBOARD SHORTCUT ---
        def dashboardBtnHtml = ""
        if (settings.taskDashboardUrl) {
            dashboardBtnHtml = """
                <div style='margin-bottom: 25px;'>
                    <a href="${settings.taskDashboardUrl}" target="_blank" style="text-decoration: none;">
                        <button type="button" style="background-color: #27ae60; padding: 15px; border-radius: 8px; font-size: 16px; width: 100%; color: white; font-weight: bold; border: none; cursor: pointer; box-shadow: 0 4px 6px rgba(0,0,0,0.3);">
                            ✅ Open Maintenance Dashboard
                        </button>
                    </a>
                </div>
            """
        }

        // --- 7. DYNAMICALLY BUILD PA ROUTING OPTIONS ---
        def paRoutingHtml = ""
        getRoutingOptions().each { r ->
            paRoutingHtml += "<option value=\"${r}\">${r}</option>"
        }

        // --- MAIN HTML BUILDER ---
        def html = new StringBuilder()
        html.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Butler Portal</title>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; padding: 20px; background-color: #0d0d0d; color: #e0e0e0; }
                .container { max-width: 600px; margin: 0 auto; background: #151515; padding: 25px; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.8); }
                
                /* Chat-style inputs */
                textarea { width: 100%; height: 80px; padding: 15px; margin-bottom: 15px; border: 1px solid #333; border-radius: 12px; box-sizing: border-box; font-family: inherit; font-size: 16px; background-color: #222; color: #ffffff; resize: none; }
                textarea:focus { outline: none; border-color: #1f618d; }
                select, input[type="time"], input[type="text"] { width: 100%; padding: 12px; margin-bottom: 15px; border: 1px solid #333; border-radius: 8px; box-sizing: border-box; font-family: inherit; font-size: 15px; background-color: #222; color: #ffffff; }
                
                /* Buttons */
                button { background-color: #1f618d; color: white; border: none; padding: 14px 20px; border-radius: 8px; cursor: pointer; width: 100%; font-size: 16px; font-weight: 600; transition: background 0.2s; }
                button:hover { background-color: #1a5276; }
                button.clear { background-color: transparent; color: #c0392b; border: 1px solid #c0392b; margin-top: 10px; }
                button.danger { background-color: #c0392b; }
                button.danger:hover { background-color: #922b21; }
                
                /* Accordion for Dashboard features */
                details { background: #1a1a1a; padding: 15px; border-radius: 8px; margin-top: 40px; border: 1px solid #333; }
                summary { font-weight: bold; color: #82b1ff; cursor: pointer; outline: none; font-size: 15px; }
                summary::marker { color: #82b1ff; }
                
                .note-list { margin-top: 25px; text-align: left; }
                .note-item { background: #222; padding: 15px; border-radius: 8px; margin-bottom: 10px; border-left: 4px solid #1f618d; font-size: 15px; line-height: 1.5; }
                option { background-color: #222; color: #ffffff; }
            </style>
            <script>
                function toggleTime() {
                    var sel = document.getElementById('whenSelect').value;
                    document.getElementById('timeDiv').style.display = (sel === 'Time') ? 'block' : 'none';
                }
            </script>
        </head>
        <body>
            <div class="container">
                <h2 style="text-align: center; color: #ffffff; margin-top: 0; margin-bottom: 5px;">Estate Command Deck</h2>
                <p style="text-align: center; font-size: 14px; color: #888; margin-bottom: 30px;">Manage messaging, context, and live broadcasts.</p>

                ${dashboardBtnHtml}

                ${quickReplyHtml}

                <h3 style='margin-top: 10px; margin-bottom: 10px; color:#e74c3c; border-bottom: 2px solid #333; padding-bottom: 5px;'>🎙️ Live Intercom (PA)</h3>
                <form action="${apiUrl}/pa/announce?access_token=${state.accessToken}" method="POST" style="margin-bottom: 35px;">
                    <textarea name="paText" placeholder="Type a message to be announced immediately..." required style="height: 60px; border-color: #555;"></textarea>
                    
                    <label style="font-size: 12px; color: #aaa;">Target Speakers:</label>
                    <select name="paRoute">
                        ${paRoutingHtml}
                    </select>

                    <button type="submit" class="danger">Broadcast Now</button>
                </form>

                <h3 style='margin-top: 10px; margin-bottom: 10px; color:#82b1ff; border-bottom: 2px solid #333; padding-bottom: 5px;'>📝 Schedule a Note</h3>
                <form action="${apiUrl}/notes/add?access_token=${state.accessToken}" method="POST">
                    <textarea name="noteText" placeholder="What would you like the Butler to say later?..." required></textarea>
                    
                    <div style="display: flex; gap: 10px;">
                        <div style="flex: 1;">
                            <label style="font-size: 12px; color: #aaa;">From:</label>
                            <select name="senderName">
                                <option value="Someone">Select...</option>
                                ${userOptions}
                            </select>
                        </div>
                        <div style="flex: 1;">
                            <label style="font-size: 12px; color: #aaa;">To:</label>
                            <select name="targetUser">
                                <option value='Anyone'>Anyone</option>
                                ${userOptions}
                            </select>
                        </div>
                    </div>
                    
                    <label style="font-size: 12px; color: #aaa;">Delivery Trigger:</label>
                    <select name="deliveryWhen" id="whenSelect" onchange="toggleTime()">
                        <option value="Arrival">When they arrive home</option>
                        <option value="Morning">During their morning briefing</option>
                        <option value="Time">At a specific time</option>
                    </select>
                    
                    <div id="timeDiv" style="display:none;">
                        <label style="font-size: 12px; color: #aaa;">Time:</label>
                        <input type="time" name="deliveryTime">
                    </div>

                    <button type="submit">Queue Message</button>
                </form>

        """)
        
        if (state.butlerNotes && state.butlerNotes.size() > 0) {
            html.append("<div class='note-list'><h4 style='margin-bottom: 10px; color:#aaa; font-size: 13px; text-transform: uppercase;'>Pending Deliveries</h4>")
            state.butlerNotes.each { note -> 
                def timeTxt = note.when == "Time" ? " (At specific time)" : " (On ${note.when})"
                html.append("<div class='note-item'><b>From ${note.sender} to ${note.target}</b>${timeTxt}<br><span style='color:#ccc;'>\"${note.text}\"</span></div>")
            }
            html.append("""
                <form action="${apiUrl}/notes/clear?access_token=${state.accessToken}" method="POST">
                    <button type="submit" class="clear">Clear Delivery Queue</button>
                </form>
            </div>""")
        }
        
        // WIFI SECTION
        html.append(wifiHtml)

        // DIRECTORY SECTION
        html.append(directoryHtml)

        // HIDDEN DASHBOARD ACCORDION
        html.append("""
                <details>
                    <summary>⚙️ Butler Memory & Settings</summary>
                    
                    <div style="margin-top: 15px;">
                        <h4 style='margin-bottom: 5px; color:#fff; font-size: 14px;'>Current Presence</h4>
                        ${presenceHtml}
                    </div>

                    <hr style='border:none; border-top:1px solid #333; margin: 20px 0;'>

                    <h4 style='margin-bottom: 10px; color:#fff; font-size: 14px;'>Update Context Agenda</h4>
                    <form action="${apiUrl}/agenda/update?access_token=${state.accessToken}" method="POST">
                        <select name="roomSelect" required style="padding: 8px;">
                            ${roomOptionsHtml}
                        </select>
                        <select name="daySelect" required style="padding: 8px;">
                            <option value="Monday">Monday</option><option value="Tuesday">Tuesday</option><option value="Wednesday">Wednesday</option><option value="Thursday">Thursday</option><option value="Friday">Friday</option><option value="Saturday">Saturday</option><option value="Sunday">Sunday</option>
                        </select>
                        <textarea name="agendaText" placeholder="New agenda items..." required style="height: 50px; margin-bottom: 10px;"></textarea>
                        <button type="submit" style="background-color: #333; padding: 10px;">Update Memory</button>
                    </form>
                </details>
            </div>
        </body>
        </html>
        """)
        
        return render(contentType: "text/html", data: html.toString(), status: 200)
        
    } catch (Exception e) {
        log.error "Notes Portal Crash: ${e}"
        return render(contentType: "text/html", data: "<h3 style='color:white;'>Portal Error:</h3><p style='color:white;'>${e}</p><p style='color:white;'>Please check your Hubitat logs.</p>", status: 500)
    }
}

def getRedirectHtml() {
    // FIX: Using ../notes tells the browser to go up one directory level from /wifi/announce back to the main /notes page
    return """<!DOCTYPE html><html><head><meta http-equiv="refresh" content="0;url=../notes?access_token=${state.accessToken}"></head><body style="background-color:#0d0d0d;color:#fff;text-align:center;padding-top:50px;font-family:sans-serif;"><h3>Executing command...</h3></body></html>"""
}

def addNoteEndpoint() {
    try {
        ensureStateMaps()
        def bodyText = request?.body ? request.body.toString() : ""
        def newNote = [id: java.util.UUID.randomUUID().toString(), addedAt: new Date().time]
        
        def params = bodyText.split('&').collectEntries {
            def parts = it.split('=')
            [parts[0], parts.size() > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : ""]
        }
        
        newNote.sender = params.senderName ?: "Someone"
        newNote.target = params.targetUser ?: "Anyone"
        newNote.when = params.deliveryWhen ?: "Arrival"
        newNote.text = params.noteText ?: ""
        
        if (newNote.text.trim().length() > 0) {
            if (state.butlerNotes && state.butlerNotes.size() > 0 && state.butlerNotes[0] instanceof String) {
                state.butlerNotes = []
            }
            
            if (newNote.when == "Time" && params.deliveryTime) {
                def timeParts = params.deliveryTime.split(":")
                def cal = Calendar.getInstance(location.timeZone)
                cal.set(Calendar.HOUR_OF_DAY, timeParts[0].toInteger())
                cal.set(Calendar.MINUTE, timeParts[1].toInteger())
                cal.set(Calendar.SECOND, 0)
                if (cal.time.time < new Date().time) cal.add(Calendar.DAY_OF_YEAR, 1) // Tomorrow
                newNote.timeEpoch = cal.time.time
                runOnce(new Date(newNote.timeEpoch), "executeTimeNote", [data: [id: newNote.id], overwrite: false])
                addToHistory("NOTES: Scheduled note from ${newNote.sender} to ${newNote.target} at ${params.deliveryTime}.")
            } else {
                addToHistory("NOTES: Added note from ${newNote.sender} to ${newNote.target} on ${newNote.when}.")
            }
            state.butlerNotes.add(newNote)
        }
    } catch (Exception e) { 
        log.warn "Failed to parse or add note: ${e}" 
    }
    
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

def clearNotesEndpoint() {
    try {
        state.butlerNotes = []
        addToHistory("NOTES: All notes cleared via web portal.")
    } catch(Exception e) {}
    
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

def formatNotesForReading() {
    return "" // Handled dynamically in the routines now
}

def executeTimeNote(data) {
    ensureStateMaps()
    def note = state.butlerNotes.find { it.id == data.id }
    if (!note) return
    
    def isTargetHome = false
    def presentFolks = getPresentUsers()
    
    if (note.target == "Anyone" && presentFolks.size() > 0) isTargetHome = true
    else if (presentFolks.any { it.equalsIgnoreCase(note.target) }) isTargetHome = true
    
    if (isTargetHome) {
        def targetName = note.target == "Anyone" ? "the household" : applyAlias(note.target)
        def senderTxt = note.sender != "Someone" ? "from ${note.sender} " : ""
        def msg = "%interruption%. I have a scheduled note ${senderTxt}for ${targetName}. It says: ${note.text}"
        
        executeRoutedTTS(applyDynamicVars(msg), "Follow-Me + Fallback (Global ONLY if no motion)", settings.globalVolume, settings.outdoorVolume, 2)
        state.butlerNotes.remove(note)
        addToHistory("NOTES: Time-based note from ${note.sender} delivered to ${note.target}.")
    } else {
        // FIX: Keep the note and flag it as Pending so it isn't lost!
        note.when = "Pending"
        addToHistory("NOTES: Scheduled note missed (${note.target} not home). Converted to a pending greeting note.")
    }
}

def getPerimeterReport(String contextKey = "global", boolean isTest = false) {
    def openDoors = []
    def unlockedLocks = []
    
    // 1. Scan for open contact sensors
    settings.each { k, v ->
        if (v instanceof com.hubitat.app.DeviceWrapper) {
            if (v.hasCapability("ContactSensor") && v.currentValue("contact") == "open") openDoors << v.displayName
        } else if (v instanceof List) {
            v.each { dev -> if (dev instanceof com.hubitat.app.DeviceWrapper && dev.hasCapability("ContactSensor") && dev.currentValue("contact") == "open") openDoors << dev.displayName }
        }
    }
    
    // 2. Scan for unlocked locks
    settings.each { k, v ->
        if (v instanceof com.hubitat.app.DeviceWrapper) {
            if (v.hasCapability("Lock") && v.currentValue("lock") == "unlocked") unlockedLocks << v.displayName
        } else if (v instanceof List) {
            v.each { dev -> if (dev instanceof com.hubitat.app.DeviceWrapper && dev.hasCapability("Lock") && dev.currentValue("lock") == "unlocked") unlockedLocks << dev.displayName }
        }
    }
    
    def report = ""
    if (openDoors.size() > 0 || unlockedLocks.size() > 0) {
        report = "Security note: "
        if (openDoors.size() > 0) {
            def doorList = openDoors.unique().join(", and ")
            report += "The ${doorList} ${openDoors.size() > 1 ? 'are' : 'is'} still open. "
        }
        if (unlockedLocks.size() > 0) {
            def lockList = unlockedLocks.unique().join(", and ")
            report += "Also, the ${lockList} ${unlockedLocks.size() > 1 ? 'are' : 'is'} currently unlocked. "
        }
        report += "I will monitor the perimeter while you rest."
    } else {
        return "" 
    }
    
    return checkAndRegisterFact(report, 2, contextKey, isTest)
}

// --- NEWS RETRIEVAL HELPER ---
def getRoomNews(rNum, isTest) {
    def headline = state."roomNewsHeadline_${rNum}"
    
    if (isTest && !headline) {
        return "This is a test of the morning news briefing. Once your RSS feed syncs, the latest headlines will appear here."
    }
    
    return headline ?: ""
}

def getWardrobeAdvice(wDevice) {
    if (!wDevice) return ""
    def temp = wDevice.currentValue("temperature") ?: 70
    def cond = wDevice.currentValue("weather")?.toLowerCase() ?: ""
    
    if (temp < 45) return "It is quite chilly, so a heavy coat is recommended."
    if (temp < 65) return "A light jacket or sweater should be perfect for today."
    if (cond.contains("rain") || cond.contains("storm")) return "Don't forget an umbrella, as precipitation is expected."
    if (temp > 85) return "It's going to be a warm one, I suggest dressing light and staying hydrated."
    return "The weather looks pleasant, standard attire should be fine."
}

def getBoredomBuster(wDevice) {
    def suggestions = [
        "Since it is the weekend, perhaps a visit to the local park would be refreshing.",
        "It might be a good day to catch up on that book you've been meaning to read.",
        "I suggest checking out a new recipe for lunch today.",
        "A perfect day for a long walk or some light gardening."
    ]
    return suggestions[new Random().nextInt(suggestions.size())]
}

def getKidsFunFact(rNum) {
    def customFile = settings["roomJokesFile_${rNum}"]
    if (customFile) {
        // Use the safe helper we just fixed above
        def fileContents = readLocalFile(customFile) 
        if (fileContents) {
            def lines = fileContents.split('\n').findAll { it.trim().length() > 0 }
            if (lines) return "Here is your morning joke: " + lines[new Random().nextInt(lines.size())]
        }
    }
    
    def jokes = ["Why don't scientists trust atoms? Because they make up everything!", "What do you call a fake noodle? An impasta!"]
    return "Here is a fun fact for your morning: " + jokes[new Random().nextInt(jokes.size())]
}

def getMealTimeQuestion() {
    def questions = [
        "If you could have any superpower for just one hour, what would it be?",
        "What was the most interesting thing that happened to you today?",
        "If we could move the house to any country in the world tomorrow, where should we go?",
        "What is one thing you are grateful for today?",
        "If you had to eat only one food for the rest of your life, what would you choose?"
    ]
    
    if (settings.mealTimeQuestionsFile) {
        try {
            def fileContents = readLocalFile(settings.mealTimeQuestionsFile)
            if (fileContents) {
                def lines = fileContents.split('\n').findAll { it.trim().length() > 0 }
                if (lines) questions = lines
            }
        } catch (e) { 
            log.warn "Meal Questions File Error: ${e}. Using defaults." 
        }
    }
    
    return "To start our dinner conversation: ${questions[new Random().nextInt(questions.size())]}"
}
def readLocalFile(fileName) {
    try {
        def params = [ uri: "http://127.0.0.1:8080/local/${fileName}", timeout: 5 ]
        def text = ""
        httpGet(params) { resp ->
            if (resp.status == 200 && resp.data != null) {
                // We use a safer way to extract text that Hubitat's sandbox allows
                if (resp.data instanceof String) {
                    text = resp.data
                } else {
                    try {
                        // This reads the stream/reader without needing to name the class
                        text = resp.data.text 
                    } catch (e) {
                        text = resp.data.toString()
                    }
                }
            }
        }
        return text
    } catch (e) {
        if (settings.enableDebug) log.error "readLocalFile failed for ${fileName}: ${e}"
        return null
    }
}

def getTravelInfo(destination) {
    if (!settings.googleMapsApiKey || !settings.homeAddress) return null
    if (!checkAndIncrementApiLimit()) return null
    
    def encodedHome = java.net.URLEncoder.encode(settings.homeAddress, "UTF-8")
    def encodedDest = java.net.URLEncoder.encode(destination, "UTF-8")
    def url = "https://maps.googleapis.com/maps/api/distancematrix/json?origins=${encodedHome}&destinations=${encodedDest}&departure_time=now&key=${settings.googleMapsApiKey}"
    
    def travelMins = null // <-- Hold the value outside the closure!
    
    try {
        httpGet([uri: url, timeout: 10]) { resp ->
            if (resp.status == 200 && resp.data?.rows && resp.data.rows[0]?.elements) {
                def element = resp.data.rows[0].elements[0]
                if (element?.status == "OK") {
                    def trafficSecs = element.duration_in_traffic?.value ?: element.duration?.value ?: 0
                    travelMins = (trafficSecs / 60).toInteger() // <-- Assign it here
                }
            }
        }
    } catch (e) { 
        if (settings.enableDebug) log.warn "Travel Info Error: ${e}" 
    }
    
    return travelMins // <-- Safely return it to the Butler!
}

def getCheapestGas() {
    if (!settings.googleMapsApiKey || !settings.homeAddress) return null
    if (!checkAndIncrementApiLimit()) return null
    
    def encodedHome = java.net.URLEncoder.encode(settings.homeAddress, "UTF-8")
    def url = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=cheapest+gas+near+${encodedHome}&key=${settings.googleMapsApiKey}"
    
    def gasResult = null 
    
    try {
        httpGet([uri: url, timeout: 10]) { resp ->
            if (resp.status == 200 && resp.data?.results && resp.data.results.size() > 0) {
                def topStation = resp.data.results[0]
                def addressObj = topStation.formatted_address ?: ""
                def streetOnly = addressObj.contains(",") ? addressObj.split(',')[0] : addressObj
                
                // NEW: Build a direct, clickable Google Maps link using the Place ID
                def placeId = topStation.place_id ?: ""
                def encodedName = java.net.URLEncoder.encode(topStation.name, "UTF-8")
                def mapLink = "https://www.google.com/maps/search/?api=1&query=${encodedName}&query_place_id=${placeId}"
                
                gasResult = [
                    speech: " By the way, the best value for fuel on your way appears to be at ${topStation.name} on ${streetOnly}.",
                    rawName: topStation.name,
                    rawAddress: addressObj,
                    navLink: mapLink // <-- Storing the link here!
                ]
            }
        }
    } catch (e) { 
        if (settings.enableDebug) log.warn "Gas Scout Error: ${e}" 
    }
    
    return gasResult 
}

def goodNightOnHandler(evt) {
    ensureStateMaps()
    def dev = evt.getDevice()
    def rNum = 0
    
    def numRoomsSet = settings.numRooms ? settings.numRooms as Integer : 0
    for (int i = 1; i <= numRoomsSet; i++) {
        if (settings["roomGoodNightSwitch_${i}"]?.id == dev.id) { rNum = i; break }
    }
    
    if (rNum > 0) {
        def isArrival = false
        def rName = settings["roomName_${rNum}"] ?: "Room ${rNum}"
        def occNameSetting = settings["roomOccupantName_${rNum}"] ?: rName
        def splitNames = occNameSetting.split(/(?i)\s+and\s+|\s*&\s*|\s*,\s*/).collect { it.trim() }
        
        // FIX: Removed the broken global presence sensor check. 
        // This now properly checks the live roster and corrects it.
        splitNames.each { n ->
            def isHome = (state.hasArrivedToday[n] == true || state.hasArrivedToday[n] == "true") && !(state.hasDepartedToday[n] == true || state.hasDepartedToday[n] == "true")
            if (!isHome) {
                state.hasArrivedToday[n] = true
                state.hasDepartedToday.remove(n)
                state.resetReasons[n] = "Good Night Failsafe"
                isArrival = true
            }
        }
        
        def fullMsg = buildRoomGreeting(rNum, "Good Night", [isNewArrival: isArrival, isTest: false])
        
        def targetSpeaker = settings["roomSpeaker_${rNum}"] ?: globalIndoorSpeaker
        def vol = settings["roomVolumeGN_${rNum}"] != null ? settings["roomVolumeGN_${rNum}"] : settings.globalVolume
        
        if (targetSpeaker && fullMsg) {
            enqueueTTS(targetSpeaker, fullMsg, vol, 1)
            addToHistory("ROOM ENGINE: Good Night Routine executed for ${rName}. Queued: '${fullMsg}'")
        }
    }
}

def roomMotionHandler(evt) {
    if (evt.value != "active") return
    ensureStateMaps()
    def deviceId = evt.device.id
    
    for (int i = 1; i <= (settings.numRooms as Integer ?: 1); i++) {
        def mSensors = [settings["roomMotion_${i}"]].flatten().findAll { it != null }
        
        if (mSensors.any { it.id == deviceId }) {
            def mode = settings["roomWakeupMode_${i}"] ?: "1. Immediate (When Good Night Switch turns OFF)"
            
            // Mode 2: Waiting for motion AFTER the switch was turned off
            if (mode == "2. Verified (Wait for switch OFF, then wait for Motion)") {
                if (state.waitingForMotion["${i}"]) {
                    state.waitingForMotion["${i}"] = false
                    triggerGoodMorningSequence(i)
                }
            } 
            // Mode 3: Waiting for motion BEFORE the switch is turned off
            else if (mode == "3. Motion Driven (Trigger when Motion activates while switch is ON)") {
                def gnSwitch = settings["roomGoodNightSwitch_${i}"]
                if (gnSwitch && gnSwitch.currentValue("switch") == "on") {
                    if (!state.roomAlreadyAwake["${i}"]) {
                        state.roomAlreadyAwake["${i}"] = true
                        triggerGoodMorningSequence(i)
                    }
                }
            }
        }
    }
}

def checkAndIncrementApiLimit() {
    ensureStateMaps()
    def currentMonth = new Date().format("MM")
    if (state.apiResetMonth != currentMonth) {
        state.apiCallCount = 0
        state.apiResetMonth = currentMonth
    }
    def limit = settings.apiCallLimit != null ? settings.apiCallLimit.toInteger() : 500
    if (state.apiCallCount >= limit) {
        log.warn "Voice Butler: Monthly Google API Limit (${limit}) reached. Disabling Travel/Gas scout until next month."
        return false
    }
    state.apiCallCount = (state.apiCallCount ?: 0) + 1
    return true
}

def getTomorrowPreview() {
    if (!state.nextEventName || !state.nextEventEpoch) return ""
    
    def now = new Date()
    def tomorrow = now + 1
    def startOfTomorrow = tomorrow.clone()
    startOfTomorrow.set(hourOfDay: 0, minute: 0, second: 0)
    def endOfTomorrow = tomorrow.clone()
    endOfTomorrow.set(hourOfDay: 23, minute: 59, second: 59)

    if (state.nextEventEpoch >= startOfTomorrow.time && state.nextEventEpoch <= endOfTomorrow.time) {
        def timeStr = new Date(state.nextEventEpoch).format("h:mm a", location.timeZone)
        return "As a heads up for tomorrow, your first event is ${state.nextEventName} at ${timeStr}."
    }
    return ""
}

// --- FIX 3C: GLOBAL INCIDENT WARNINGS (MORNING MOTION) ---
def butlerLrMotionHandler(evt) {
    ensureStateMaps()
    if (evt.value != "active") return
    
    def now = new Date()
    def hour = now.format("H", location.timeZone).toInteger()
    
    // Only trigger in the morning between 4 AM and 11 AM
    if (hour >= 4 && hour < 11) {
        if (state.pendingMorningReport && state.lastNightMotionCount > 0) {
            def targetSpeaker = settings.butlerLrSpeaker ?: globalIndoorSpeaker
            def vol = settings.butlerLrVolume ?: globalVolume
            if (targetSpeaker) {
                def msg = "Good morning. Please note, there were ${state.lastNightMotionCount} motion events at the front door last night. Please check the cameras."
                enqueueTTS(targetSpeaker, applyDynamicVars(msg), vol, 4)
                
                state.pendingMorningReport = false
                state.lastNightMotionCount = 0
                addToHistory("INCIDENT REPORT: Delivered Global Night Motion warning in Living Room.")
            }
        }
    }
}
// --- QUICK EXIT (FAREWELL & GAS SCOUT) ---
def quickExitDoorHandler(evt) {
    if (evt.value != "open") return
    ensureStateMaps()
    
    def qModes = [settings.quickExitModes].flatten().findAll { it != null }
    if (qModes.size() == 0 || !qModes.contains(location.mode)) return
    
    // 1. Verify the mode changed to Away within the last 5 minutes
    def modeState = location.currentState("mode")
    def modeChangeTime = modeState?.date?.time ?: 0
    def now = new Date().time
    
    if ((now - modeChangeTime) > 300000) { 
        if (settings.enableDebug) log.debug "QUICK EXIT: Ignored. House entered Away mode more than 5 minutes ago."
        return
    }
    
    // 2. Prevent spamming if the door is opened multiple times
    def lastQuickExit = state.lastQuickExitTime ?: 0
    if ((now - lastQuickExit) < 120000) return 
    state.lastQuickExitTime = now
    
    // 3. Fetch Gas & Build Farewell
    def gasData = getCheapestGas()
    def msg = "Farewell. The security perimeter is active. Have a safe trip."
    if (gasData && gasData.speech) msg += gasData.speech
    
    // 4. Play Audio (Fast-tracked so it plays instantly)
    executeRoutedTTS(msg, settings.quickExitRoutingMode ?: "Outdoor Speaker Only", settings.globalVolume, settings.quickExitVolume ?: settings.outdoorVolume, 1, true)
    
    // 5. Send Push Notification if enabled
    if (settings.enableTravelPush && settings.notificationDevice && gasData?.rawAddress) {
        settings.notificationDevice.each { dev ->
            try { dev.deviceNotification("Travel Alert - Gas: ${gasData.rawName} (${gasData.rawAddress})") } catch(e) {}
        }
    }
    addToHistory("QUICK EXIT: Farewell and gas scout triggered.")
}

// --- ESTATE DIRECTORY HANDLER ---
// --- REFACTORED SWITCH HANDLER ---
def directorySwitchHandler(evt) {
    ensureStateMaps()
    def devId = evt.device.id
    def matchIdx = 0

    def numC = settings.numContacts ? settings.numContacts as Integer : 0
    for (int i = 1; i <= numC; i++) {
        if (settings["contactSwitch_${i}"]?.id == devId) {
            matchIdx = i; break
        }
    }

    if (matchIdx > 0) {
        // Auto-turn off the switch so it acts like a momentary button!
        try { settings["contactSwitch_${matchIdx}"].off() } catch(e) {}
        executeDirectoryAnnouncement(matchIdx)
    }
}

// --- WEB ENDPOINT FOR DIRECTORY ---
def announceDirectoryEndpoint() {
    try {
        ensureStateMaps()
        def bodyText = request?.body ? request.body.toString() : ""
        def params = bodyText.split('&').collectEntries {
            def parts = it.split('=')
            [parts[0], parts.size() > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : ""]
        }
        def cId = params.contactId ? params.contactId.toInteger() : 0
        if (cId > 0) {
            runIn(1, "asyncDirectoryExecution", [data: [id: cId], overwrite: false])
        }
    } catch (Exception e) {
        log.warn "Failed to trigger directory from web portal: ${e}"
    }
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

def asyncDirectoryExecution(data) {
    executeDirectoryAnnouncement(data.id as Integer)
}

// --- CENTRAL DIRECTORY ANNOUNCEMENT ENGINE ---
def executeDirectoryAnnouncement(int matchIdx) {
    if (matchIdx <= 0) return
    def cName = settings["contactName_${matchIdx}"] ?: "the requested service"
    def cInfo = settings["contactInfo_${matchIdx}"] ?: "No information provided."

    // 1. Check if anyone is home
    def presentFolks = getPresentUsers()
    def isAnyoneHome = presentFolks.size() > 0

    // 2. Build the TTS Message
    def msg = "%interruption%, the contact information for ${cName} is: ${cInfo}."
    if (settings.notificationDevice && isAnyoneHome) {
        msg += " I have also sent these details to your mobile device."
    }

    // 3. Play Audio
    def targetVol = settings.directoryVolume != null ? settings.directoryVolume : settings.globalVolume
    executeRoutedTTS(applyDynamicVars(msg), settings.directoryRoutingMode ?: "Follow-Me + Fallback (Global ONLY if no motion)", settings.globalVolume, settings.outdoorVolume, 2)

    // 4. Send Targeted Push Notification
    if (settings.notificationDevice && isAnyoneHome) {
        def pushMsg = "📖 ESTATE DIRECTORY\nService: ${cName}\nDetails: ${cInfo}"
        settings.notificationDevice.each { dev ->
            try { dev.deviceNotification(pushMsg) } catch(e) {}
        }
    }

    addToHistory("DIRECTORY: Contact info for ${cName} announced and pushed.")
}

def cleanDeviceName(String name) {
    if (!name) return ""
    // Strips out technical jargon so "Front Door Lock" becomes "Front Door"
    return name.replaceAll(/(?i)\s*(open|close|contact|motion|water|temperature|humidity)\s*sensor$/, "")
               .replaceAll(/(?i)\s*sensor$/, "")
               .replaceAll(/(?i)\s*lock$/, "")
               .trim()
}

// --- WEB ENDPOINT FOR AGENDA UPDATES ---
def updateAgendaEndpoint() {
    try {
        ensureStateMaps()
        def bodyText = request?.body ? request.body.toString() : ""
        def params = bodyText.split('&').collectEntries {
            def parts = it.split('=')
            [parts[0], parts.size() > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : ""]
        }
        
        def rNum = params.roomSelect
        def dayStr = params.daySelect
        def newAgenda = params.agendaText ?: ""

        if (rNum && dayStr) {
            app.updateSetting("roomAgenda${dayStr}_${rNum}", [type: "text", value: newAgenda])
            if (settings.enableDebug) log.info "PORTAL: Agenda updated for Room ${rNum} on ${dayStr} to: ${newAgenda}"
        }
    } catch (Exception e) {
        log.warn "Failed to update agenda from web portal: ${e}"
    }
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

// --- WI-FI ANNOUNCEMENT ENGINE ---
def announceWifiEndpoint() {
    try {
        ensureStateMaps()
        // Force Hubitat to clear the request buffer so it doesn't hang
        def safeBody = request?.body ? request.body.toString() : "" 
        
        runIn(1, "executeWifiAnnouncement", [overwrite: false])
    } catch (Exception e) {
        log.warn "Wi-Fi Portal Error: ${e}"
    }
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

def executeWifiAnnouncement() {
    ensureStateMaps()
    
    def ssid = settings.wifiSSID ?: "Unknown Network"
    def rawPwd = settings.wifiPassword ?: "Unknown Password"
    
    // Translate special characters into spoken words so the TTS XML parser doesn't crash
    def spokenPwd = rawPwd.replace("&", " ampersand ")
                          .replace("<", " less than ")
                          .replace(">", " greater than ")
                          .replace("\"", " quote ")
                          .replace("'", " apostrophe ")
                          .replace("%", " percent ")
                          .replace("\$", " dollar sign ")
                          .replace("@", " at symbol ")
                          .replace("!", " exclamation point ")
                          .replace("?", " question mark ")
                          .replace("*", " asterisk ")
                          .replace("#", " hashtag ")
                          .replace("-", " dash ")
                          .replace("_", " underscore ")

    def presentFolks = []
    try { presentFolks = getPresentUsers() } catch (e) {}
    def isAnyoneHome = presentFolks.size() > 0

    def msg = "%interruption%, the guest Wi-Fi network is ${ssid}, and the password is: ${spokenPwd}."
    if (settings.notificationDevice && isAnyoneHome) {
        msg += " I have also sent the exact details to your mobile device for easy copying."
    }

    def targetVol = settings.wifiVolume != null ? settings.wifiVolume : settings.globalVolume
    
    // Execute routing with the safe spoken password
    executeRoutedTTS(applyDynamicVars(msg), settings.wifiRoutingMode ?: "Follow-Me + Fallback (Global ONLY if no motion)", settings.globalVolume, settings.outdoorVolume, 2)

    // Push the raw, unedited password to the phones
    if (settings.notificationDevice && isAnyoneHome) {
        def pushMsg = "📶 GUEST WI-FI\nNetwork: ${ssid}\nPassword: ${rawPwd}"
        settings.notificationDevice.each { dev ->
            try { dev.deviceNotification(pushMsg) } catch(e) {}
        }
    }
    addToHistory("WIFI: Network details announced and pushed via portal.")
}

// --- LIVE INTERCOM (PA) ENGINE ---
def instantPAEndpoint() {
    try {
        ensureStateMaps()
        def bodyText = request?.body ? request.body.toString() : ""
        def params = bodyText.split('&').collectEntries {
            def parts = it.split('=')
            [parts[0], parts.size() > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : ""]
        }
        
        def msg = params.paText ?: ""
        def route = params.paRoute ?: "Global Indoor Speaker Only"
        
        if (msg.trim().length() > 0) {
            runIn(1, "asyncPAExecution", [data: [msg: msg, route: route], overwrite: false])
        }
    } catch (Exception e) {
        log.warn "Portal PA Error: ${e}"
    }
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

def asyncPAExecution(data) {
    executeRoutedTTS(applyDynamicVars(data.msg), data.route, settings.globalVolume, settings.outdoorVolume, 1, false)
    addToHistory("LIVE PA: Broadcast dispatched to ${data.route}. Queued: '${data.msg}'")
}

// --- MANUAL DEPARTURE OVERRIDE ---
def manualDepartEndpoint() {
    try {
        ensureStateMaps()
        def bodyText = request?.body ? request.body.toString() : ""
        def params = bodyText.split('&').collectEntries {
            def parts = it.split('=')
            [parts[0], parts.size() > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : ""]
        }
        
        def target = params.targetUser ?: ""
        if (target && state.hasArrivedToday[target]) {
            state.hasArrivedToday.remove(target)
            state.hasDepartedToday[target] = true
            state.lastDepartureTime[target] = new Date().time
            state.resetReasons[target] = "Marked Away via Portal"
            addToHistory("PORTAL: ${target} was manually marked as departed.")
        }
    } catch (Exception e) {
        log.warn "Portal Depart Error: ${e}"
    }
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

// --- QUICK REPLY ENGINE ---
def quickReplyEndpoint() {
    try {
        ensureStateMaps()
        def bodyText = request?.body ? request.body.toString() : ""
        def params = bodyText.split('&').collectEntries {
            def parts = it.split('=')
            [parts[0], parts.size() > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : ""]
        }
        
        def rId = params.replyId ? params.replyId.toInteger() : 0
        if (rId > 0) {
            runIn(1, "asyncQuickReplyExecution", [data: [id: rId], overwrite: false])
        }
    } catch (Exception e) {
        log.warn "Failed to trigger Quick Reply from web portal: ${e}"
    }
    return render(contentType: "text/html", data: getRedirectHtml(), status: 200)
}

def asyncQuickReplyExecution(data) {
    def idx = data.id as Integer
    def msg = settings["quickReplyText_${idx}"]
    
    if (msg) {
        // --- CANCEL AUTOMATED RESPONSES ---
        // This stops the Butler from playing the "No Answer" message since you replied
        unschedule("playDaytimeFollowUp")
        
        def finalMsg = applyDynamicVars(msg)
        def targetVol = settings.outdoorVolume ?: settings.globalVolume
        
        if (outdoorSpeaker) {
            // Priority 1 + FastTrack (true) to skip the chime and answer them instantly
            enqueueTTS(outdoorSpeaker, finalMsg, targetVol, 1, true)
            addToHistory("QUICK REPLY: Portal button '${settings["quickReplyName_${idx}"]}' triggered. Queued: '${finalMsg}'")
        } else {
            log.warn "Quick Reply failed: No outdoor speaker is assigned in the app."
        }
    }
}

def scheduleHealthWindow() {
    if (!settings.enableHealthWindow || !settings.healthWindowStart || !settings.healthWindowEnd) return
    try {
        def startCal = Calendar.getInstance(location.timeZone)
        startCal.setTime(toDateTime(settings.healthWindowStart))
        def startMins = (startCal.get(Calendar.HOUR_OF_DAY) * 60) + startCal.get(Calendar.MINUTE)

        def endCal = Calendar.getInstance(location.timeZone)
        endCal.setTime(toDateTime(settings.healthWindowEnd))
        def endMins = (endCal.get(Calendar.HOUR_OF_DAY) * 60) + endCal.get(Calendar.MINUTE)

        if (endMins <= startMins) endMins += 1440

        def diff = endMins - startMins
        if (diff > 0) {
            def randomMins = startMins + new Random().nextInt(diff)
            def runCal = Calendar.getInstance(location.timeZone)
            runCal.set(Calendar.HOUR_OF_DAY, (randomMins / 60).toInteger() % 24)
            runCal.set(Calendar.MINUTE, randomMins % 60)
            runCal.set(Calendar.SECOND, 0)

            // Ensure it only schedules if the random time hasn't already passed today
            if (runCal.getTime().time > new Date().time) {
                runOnce(runCal.getTime(), "executeHealthWindow", [overwrite: true])
                if (settings.enableDebug) log.debug "SYSTEM: Scheduled Health Window Reminder for ${runCal.getTime().format('h:mm a', location.timeZone)}"
            }
        }
    } catch (e) { log.warn "Failed to schedule health window: ${e}" }
}

def executeHealthWindow(isTest = false) {
    ensureStateMaps()
    def msg = buildGlobalHealthReport(!isTest) // Pass false if it's a test to ignore presence checks

    if (!msg) {
        if (isTest) executeRoutedTTS("There are currently no overdue health appointments for anyone present in the home.", settings.healthRoutingMode ?: "Global Indoor Speaker Only", settings.globalVolume, settings.healthVolume ?: settings.globalVolume, 2)
        return
    }

    // Memory filter: 168 hours = 7 days. The Butler will only nag you once a week.
    def filteredMsg = checkAndRegisterFact(msg, 168, "health_window_global", isTest)
    
    if (filteredMsg) {
        def targetVol = settings.healthVolume != null ? settings.healthVolume : settings.globalVolume
        executeRoutedTTS(applyDynamicVars(filteredMsg), settings.healthRoutingMode ?: "Global Indoor Speaker Only", settings.globalVolume, targetVol, 2)
        addToHistory("HEALTH: Executed daytime window reminder.")
    } else if (isTest) {
        executeRoutedTTS("The health reminder is currently in its 7-day cooldown period. It will be suppressed until next week.", settings.healthRoutingMode ?: "Global Indoor Speaker Only", settings.globalVolume, settings.healthVolume ?: settings.globalVolume, 2)
    }
}

def buildGlobalHealthReport(boolean checkPresence = true) {
    if (!settings.numHealthProfiles || settings.numHealthProfiles == 0) return ""
    ensureStateMaps()
    def now = new Date().time
    def sixMonthsMs = 15768000000L
    def oneYearMs = 31536000000L

    def presentUsers = checkPresence ? getPresentUsers() : []
    def userNeeds = [:]

    // 1. Gather all overdue appointments for users currently home
    for (int i = 1; i <= settings.numHealthProfiles; i++) {
        def hUser = settings["healthUser_${i}"]
        if (!hUser) continue

        def isHome = !checkPresence || presentUsers.any { it.equalsIgnoreCase(hUser) || it.equalsIgnoreCase(applyAlias(hUser)) }
        if (!isHome) continue

        def dispName = applyAlias(hUser)
        def needs = []

        if (settings["lastDental_${i}"]) {
            try { if (now - new java.text.SimpleDateFormat("yyyy-MM-dd").parse(settings["lastDental_${i}"]).time > sixMonthsMs) needs << "a dental appointment" } catch(e){}
        }
        if (settings["lastMedical_${i}"]) {
            try { if (now - new java.text.SimpleDateFormat("yyyy-MM-dd").parse(settings["lastMedical_${i}"]).time > oneYearMs) needs << "an annual physical" } catch(e){}
        }
        if (settings["lastVision_${i}"]) {
            try { if (now - new java.text.SimpleDateFormat("yyyy-MM-dd").parse(settings["lastVision_${i}"]).time > oneYearMs) needs << "an eye exam" } catch(e){}
        }

        if (needs.size() > 0) userNeeds[dispName] = needs
    }

    if (userNeeds.size() == 0) return ""

    // 2. Group the data by "Need" instead of by "User" for cleaner grammar
    def needsToUsers = [:]
    userNeeds.each { name, needs ->
        needs.each { n ->
            if (!needsToUsers[n]) needsToUsers[n] = []
            needsToUsers[n] << name
        }
    }

    // 3. Assemble the sentences dynamically based on how many people need it
    def finalSentences = []
    needsToUsers.each { need, names ->
        def namesStr = ""
        if (names.size() == 1) namesStr = names[0]
        else if (names.size() == 2) namesStr = "${names[0]} and ${names[1]}"
        else namesStr = "${names[0..-2].join(', ')}, and ${names.last()}"

        def pronoun = "you"
        if (names.size() == 2) pronoun = "both of you"
        else if (names.size() >= 3) pronoun = "all ${names.size()} of you"

        finalSentences << "${namesStr}, ${pronoun} require ${need}."
    }

    return "%interruption%, as a wellness reminder: " + finalSentences.join(" ")
}

def getHealthReminders(String userName) {
    if (!settings.numHealthProfiles || settings.numHealthProfiles == 0) return ""
    def reminders = []
    def now = new Date().time
    
    // Time thresholds in milliseconds
    def sixMonthsMs = 15768000000L 
    def oneYearMs = 31536000000L

    for (int i = 1; i <= settings.numHealthProfiles; i++) {
        def hUser = settings["healthUser_${i}"]
        // Check if the profile matches the person currently in the room
        if (hUser && (userName.toLowerCase().contains(hUser.toLowerCase()) || userName.toLowerCase().contains(applyAlias(hUser).toLowerCase()))) {
            
            // Check Dental (Overdue after 6 months)
            if (settings["lastDental_${i}"]) {
                try {
                    def dDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(settings["lastDental_${i}"]).time
                    if (now - dDate > sixMonthsMs) reminders << "a dental cleaning"
                } catch(e) { log.warn "Voice Butler: Invalid Dental Date for ${hUser}" }
            }
            // Check Medical (Overdue after 1 year)
            if (settings["lastMedical_${i}"]) {
                try {
                    def mDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(settings["lastMedical_${i}"]).time
                    if (now - mDate > oneYearMs) reminders << "an annual physical"
                } catch(e) {}
            }
            // Check Vision (Overdue after 1 year)
            if (settings["lastVision_${i}"]) {
                try {
                    def vDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(settings["lastVision_${i}"]).time
                    if (now - vDate > oneYearMs) reminders << "an eye exam"
                } catch(e) {}
            }
        }
    }

    if (reminders.size() > 0) {
        def rStr = reminders.size() == 1 ? reminders[0] : (reminders.size() == 2 ? "${reminders[0]} and ${reminders[1]}" : "${reminders[0..-2].join(', ')}, and ${reminders.last()}")
        return "As a wellness reminder, it has been a while since your last visit, so I recommend scheduling ${rStr} soon."
    }
    return ""
}

def realTimeTaskHandler(evt) {
    ensureStateMaps()
    def currentTasksStr = evt.value ?: ""
    def currentTasks = currentTasksStr.split(",").collect { it.trim() }.findAll { it != "" }
    
    def previousTasks = state.lastOverdueTasks ?: []
    // Find tasks that are in the new list but weren't in the old list
    def newTasks = currentTasks - previousTasks
    
    // Update the memory for next time
    state.lastOverdueTasks = currentTasks
    
    if (newTasks.size() > 0) {
        // Suppress if the house is empty
        def presentFolks = getPresentUsers()
        if (presentFolks.size() == 0) return 
        
        // Respect the midday maintenance mode restrictions if set
        def allowedModes = [settings.middayMaintenanceModes].flatten().findAll { it != null }
        if (allowedModes.size() > 0 && !allowedModes.contains(location.mode)) return
        
        def taskStr = ""
        if (newTasks.size() == 1) taskStr = newTasks[0]
        else if (newTasks.size() == 2) taskStr = "${newTasks[0]} and ${newTasks[1]}"
        else {
            def last = newTasks.pop()
            taskStr = "${newTasks.join(', ')}, and ${last}"
        }
        
        def msgs = [
            "%interruption%, but the ${taskStr} has just become due. Please complete it at your earliest convenience.",
            "Pardon me, a new maintenance item requires your attention. The ${taskStr} is now due.",
            "Excuse me, the ${taskStr} has just been added to the due list. Please address it when you have a moment."
        ]
        def randomMsg = msgs[new Random().nextInt(msgs.size())]
        
        def finalMsg = applyDynamicVars(randomMsg)
        def targetVol = settings.realTimeTaskVolume != null ? settings.realTimeTaskVolume : settings.globalVolume
        
        executeRoutedTTS(finalMsg, settings.realTimeTaskRoutingMode ?: "Global Indoor Speaker Only", settings.globalVolume, targetVol, 2)
        addToHistory("TASK ALERT: Announced newly due task: ${taskStr}")
    }
}
