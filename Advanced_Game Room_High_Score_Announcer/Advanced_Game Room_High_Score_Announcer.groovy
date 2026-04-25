/**
 * Advanced Game Room High Score Announcer
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Game Room High Score Announcer",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Monitors game room switches and announces dynamic overall/weekly high scores. Features Top 3 tracking, Weekly Chase Games, quiet hours, Guest Mode, and dedicated machine child devices.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    oauth: true
)

preferences {
    page(name: "mainPage")
    page(name: "gamePage")
}

mappings {
    path("/log") { action: [GET: "serveScoreForm"] }
    path("/submit") { action: [GET: "handleScoreSubmit", POST: "handleScoreSubmit"] }
}

def mainPage() {
    if (!state.accessToken) {
        try {
            createAccessToken()
        } catch (e) {
            log.error "OAuth is not enabled. Please enable OAuth in the App Code page."
        }
    }
    
    ensureStateMaps()

    dynamicPage(name: "mainPage", title: "Game Room Configuration", install: true, uninstall: true) {
        
        section("Live High Score Dashboard") {
            paragraph "<i>Below is a real-time view of your machines and the #1 champions for the <b>last game played</b> on each.</i>"
            input "btnRefresh", "button", title: "🔄 Refresh Data Dashboard"
            
            def statusText = "<h4 style='margin-bottom: 5px; color: #333; font-family: sans-serif;'>Active Leaderboard</h4>"
            statusText += "<table style='width:100%; border-collapse: collapse; font-size: 13px; font-family: sans-serif; background-color: #fcfcfc; border: 1px solid #ccc; margin-bottom: 15px;'>"
            statusText += "<tr style='background-color: #eee; border-bottom: 2px solid #ccc; text-align: left;'><th style='padding: 8px;'>Machine (Pwr State)</th><th style='padding: 8px;'>Active Game/Table</th><th style='padding: 8px;'>#1 Overall</th><th style='padding: 8px;'>#1 Weekly</th></tr>"
            
            def numG = settings.numGames ?: 0
            if (numG > 0) {
                for (int i = 1; i <= (numG as Integer); i++) {
                    def gName = settings["gameName_${i}"] ?: "Machine ${i}"
                    def gSwitch = settings["gameSwitch_${i}"]
                    
                    def pwrState = "<span style='color: #7f8c8d;'>OFF</span>"
                    if (gSwitch) {
                        pwrState = gSwitch.currentValue("switch") == "on" ? "<span style='color: #27ae60; font-weight: bold;'>ON</span>" : "<span style='color: #7f8c8d;'>OFF</span>"
                    }
                    
                    def isMulti = settings["gameType_${i}"] == "Multi-Game (Arcade/Pinball)"
                    def mStats = state.gameStats["${i}"]
                    
                    def activeGame = gName
                    if (isMulti && mStats?.lastPlayed && mStats.lastPlayed != "Machine ${i}") {
                        activeGame = mStats.lastPlayed
                    }
                    
                    def gScores = mStats?.scores?."${activeGame}"
                    
                    def allList = gScores?.overall ?: []
                    def weekList = gScores?.weekly ?: []
                    
                    def allUser = allList.size() > 0 ? allList[0].user : "N/A"
                    def allScore = allList.size() > 0 ? allList[0].score : 0L
                    def allFmt = "<b>${formatScore(allScore)}</b> <span style='color: #555;'>(by ${allUser})</span>"
                    
                    def weekUser = weekList.size() > 0 ? weekList[0].user : "N/A"
                    def weekScore = weekList.size() > 0 ? weekList[0].score : 0L
                    def weekFmt = "<b>${formatScore(weekScore)}</b> <span style='color: #555;'>(by ${weekUser})</span>"
                    
                    def displayGame = isMulti ? "<span style='color: #2980b9; font-weight: bold;'>${activeGame}</span>" : "<span style='color: #7f8c8d;'><i>(Single Game)</i></span>"
                    
                    statusText += "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 8px;'><b>${gName}</b> [${pwrState}]</td><td style='padding: 8px;'>${displayGame}</td><td style='padding: 8px;'>${allFmt}</td><td style='padding: 8px;'>${weekFmt}</td></tr>"
                }
            } else {
                statusText += "<tr><td colspan='4' style='padding: 8px; text-align: center; color: #7f8c8d;'><i>No machines configured yet.</i></td></tr>"
            }
            statusText += "</table>"
            
            paragraph statusText
        }

        section("Weekly Chase Game (Room Occupied Trigger)") {
            paragraph "<i>Select a virtual switch that turns ON when the game room is occupied. The app will automatically select a random 'Chase Game' for the week and announce it BEFORE announcing the rest of the booting arcades.</i>"
            
            if (state.currentChaseGame) {
                def cg = state.currentChaseGame
                paragraph "<div style='padding: 10px; background: #2c3e50; color: #ecf0f1; border-radius: 5px; border-left: 4px solid #f1c40f;'><b>🏆 Current Weekly Chase Game:</b> ${cg.gameName}<br><b>Score to Beat:</b> ${formatScore(cg.topScore)} (by ${cg.topUser})</div>"
            }
            
            input "occupiedSwitch", "capability.switch", title: "Game Room Occupied Switch", required: false
            
            def defChaseMsg = "Welcome to the Game Room! This week's Chase Game is %game%. The score to beat is %allScore% by %allUser%!"
            input "chaseGameMsg", "text", title: "Chase Game Announcement", defaultValue: defChaseMsg, required: true
            
            input "btnForceChase", "button", title: "🎲 Force Select New Chase Game", description: "Randomly pick a new Chase Game from the database right now."
        }

        section("Dashboard Integration (Global Leaderboard Tile)", hideable: true, hidden: true) {
            paragraph "<i>Push a consolidated HTML leaderboard of ALL machines to a virtual device. (Individual machine devices can be created in the machine setup pages below).</i>"
            input "leaderboardDevice", "capability.actuator", title: "Target Virtual Device", required: false
            input "leaderboardAttribute", "text", title: "Target Attribute Name", defaultValue: "leaderboardTile", required: true
            input "leaderboardType", "enum", title: "Leaderboard Data Type", options: ["Overall Champions", "Weekly Champions", "Both"], defaultValue: "Overall Champions", required: true
            
            input "btnUpdateTile", "button", title: "🔄 Force Update All Tiles"
        }

        section("External Web Logging (Player Portal)") {
            if (state.accessToken) {
                def localUrl = "${getFullLocalApiServerUrl()}/log?access_token=${state.accessToken}"
                def cloudUrl = "${getFullApiServerUrl()}/log?access_token=${state.accessToken}"
                
                paragraph "<b>Local Network Link:</b><br><a href='${localUrl}' target='_blank' style='font-size: 12px; word-break: break-all;'>${localUrl}</a>"
                paragraph "<b>Cloud/Remote Link:</b><br><a href='${cloudUrl}' target='_blank' style='font-size: 12px; word-break: break-all;'>${cloudUrl}</a>"
            } else {
                paragraph "<span style='color:red'><b>OAuth not enabled!</b> You must enable OAuth in the Hubitat App Code editor, then open this app again.</span>"
            }
        }
        
        section("Global Audio Settings & Restrictions") {
            input "masterEnableSwitch", "capability.switch", title: "Master Enable/Disable Switch", required: false, description: "If selected, the app will ONLY function when this switch is ON."
            input "guestModeSwitch", "capability.switch", title: "Guest Mode Switch", required: false, description: "When ON, standard 'Powering up' announcements and Chase Games are skipped to keep the room quiet. New High Score alerts will still play."
            input "requireInternet", "bool", title: "Require Internet for TTS?", defaultValue: true, description: "If ON, the app runs a lightning-fast ping to check connectivity. If offline, it silently drops announcements to prevent cloud-TTS log errors."
            
            input "ttsSpeaker", "capability.speechSynthesis", title: "Game Room Speaker(s)", multiple: true, required: true
            input "ttsVolume", "number", title: "Announcement Default Volume (0-100)", required: false
            input "enableWakeupPad", "bool", title: "Enable Speaker Wake-Up Padding?", defaultValue: false
            
            paragraph "<b>Mode Restrictions</b>"
            input "allowedModes", "mode", title: "Allowed House Modes", multiple: true, required: false, description: "Only allow TTS announcements if the house is in one of these modes."
            
            paragraph "<b>Quiet Hours Threshold</b>"
            input "quietHoursStart", "time", title: "Quiet Hours Start Time", required: false
            input "quietHoursEnd", "time", title: "Quiet Hours End Time", required: false
            input "quietVolume", "number", title: "Quiet Hours Target Volume (0-100)", required: false, description: "Forces the speaker to this lower volume during the configured hours."
        }

        section("Machine Configuration") {
            input "numGames", "number", title: "Number of Machines (1-20)", required: true, defaultValue: 1, range: "1..20", submitOnChange: true
        }

        if ((settings.numGames ?: 0) > 0) {
            for (int i = 1; i <= (settings.numGames as Integer); i++) {
                section("${settings["gameName_${i}"] ?: "Machine ${i}"}", hideable: true, hidden: true) { 
                    href(name: "gameHref${i}", page: "gamePage", params: [gameNum: i], title: "Configure ${settings["gameName_${i}"] ?: "Machine ${i}"}") 
                }
            }
        }
        
        section("Automated Weekly Resets", hideable: true, hidden: true) {
            input "enableAutoReset", "bool", title: "Enable Automated Weekly Reset?", defaultValue: false, submitOnChange: true
            if (enableAutoReset) {
                input "resetDay", "enum", title: "Reset Day", options: ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"], defaultValue: "SUN", required: true
                input "resetTime", "time", title: "Reset Time", required: true
            }
        }

        section("System History & Manual Override", hideable: true, hidden: true) {
            if (state.historyLog && state.historyLog.size() > 0) {
                def histHtml = "<div style='max-height: 250px; overflow-y: auto; background-color: #f4f4f4; border: 1px solid #ccc; padding: 10px; font-family: monospace; font-size: 12px; line-height: 1.4;'>"
                state.historyLog.each { logEntry ->
                    histHtml += "<div style='margin-bottom: 6px; border-bottom: 1px dashed #ddd; padding-bottom: 6px;'>${logEntry}</div>"
                }
                histHtml += "</div>"
                paragraph histHtml
            } else {
                paragraph "<i>No history logged yet.</i>"
            }
            input "btnResetWeekly", "button", title: "⚠️ Force Reset ALL Weekly Scores Now"
        }
    }
}

def gamePage(params) {
    def gNum = params?.gameNum ?: state.currentGame ?: 1
    state.currentGame = gNum
    
    dynamicPage(name: "gamePage", title: "Machine Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Hardware & Identification") {
            input "gameName_${gNum}", "text", title: "Machine Name", defaultValue: "Arcade Cabinet", required: true, submitOnChange: true
            input "gameType_${gNum}", "enum", title: "Machine Type", options: ["Single Game (e.g. Pop A Shot)", "Multi-Game (Arcade/Pinball)"], defaultValue: "Single Game (e.g. Pop A Shot)", required: true, submitOnChange: true
            input "gameSwitch_${gNum}", "capability.switch", title: "Power Switch / Trigger", required: true
        }
        
        section("Dedicated Child Device") {
            paragraph "<i>Create a virtual device dedicated entirely to this machine. The app will automatically push this machine's Top 3 HTML leaderboard to the child device so you can display it on a dashboard.</i>"
            input "btnCreateChild_${gNum}", "button", title: "➕ Create High Score Child Device"
            
            def childDni = "gameAnnouncer-${app.id}-m${gNum}"
            if (getChildDevice(childDni)) {
                paragraph "<span style='color: #27ae60;'><b>Child Device Exists:</b> ${settings["gameName_${gNum}"] ?: "Machine ${gNum}"} Leaderboard</span>"
            }
        }
        
        section("Database Management") {
            input "btnWipeMachine_${gNum}", "button", title: "🗑️ Wipe Database for ${settings["gameName_${gNum}"] ?: "This Machine"}"
        }
        
        section("Custom Announcement") {
            paragraph "<i>Variables:</i><br>• <b>%machine%</b> - Hardware Name<br>• <b>%game%</b> - Specific Game Name<br>• <b>%allUser%</b> - #1 Overall Champ<br>• <b>%allScore%</b> - #1 Overall Score<br>• <b>%weekUser%</b> - #1 Weekly Champ<br>• <b>%weekScore%</b> - #1 Weekly Score"
            
            def defaultMsg = "The last game played on %machine% was %game%. The overall high score is %allScore%, held by %allUser%. This week's current leader is %weekUser% with a score of %weekScore%."
            input "customMsg_${gNum}", "text", title: "TTS Announcement String", required: true, defaultValue: defaultMsg
            input "btnTestGame_${gNum}", "button", title: "▶️ Test Announcement Audio"
        }
    }
}

def installed() {
    log.info "Game Room Announcer Installed."
    initialize()
}

def updated() {
    log.info "Game Room Announcer Updated."
    unsubscribe()
    unschedule()
    
    def numG = settings.numGames ?: 0
    if (numG > 0 && state.gameStats) {
        for (int i = 1; i <= (numG as Integer); i++) {
            def gName = settings["gameName_${i}"] ?: "Machine ${i}"
            def mStats = state.gameStats["${i}"]
            
            if (mStats) {
                if (mStats.lastPlayed == "Machine ${i}") mStats.lastPlayed = gName
                if (mStats.scores && mStats.scores["Machine ${i}"] && gName != "Machine ${i}") {
                    mStats.scores[gName] = mStats.scores["Machine ${i}"]
                    mStats.scores.remove("Machine ${i}")
                }
            }
        }
    }
    
    initialize()
    updateLeaderboardTile()
}

def ensureStateMaps() {
    if (state.historyLog == null) state.historyLog = []
    if (atomicState.ttsQueue == null) atomicState.ttsQueue = []
    if (atomicState.isSpeaking == null) atomicState.isSpeaking = false
    if (state.gameStats == null) state.gameStats = [:]
}

def initialize() {
    ensureStateMaps()
    
    if (occupiedSwitch) {
        subscribe(occupiedSwitch, "switch.on", occupiedOnHandler)
    }
    
    def numG = settings.numGames ?: 0
    if (numG > 0) {
        for (int i = 1; i <= (numG as Integer); i++) {
            def sw = settings["gameSwitch_${i}"]
            if (sw) subscribe(sw, "switch.on", switchOnHandler)
            
            if (!state.gameStats["${i}"]) {
                state.gameStats["${i}"] = [lastPlayed: null, scores: [:]]
            }
        }
    }
    
    if (settings.enableAutoReset && settings.resetTime && settings.resetDay) {
        try {
            def scheduleTime = toDateTime(settings.resetTime)
            def h = scheduleTime.hours
            def m = scheduleTime.minutes
            def cronStr = "0 ${m} ${h} ? * ${settings.resetDay}"
            schedule(cronStr, "clearWeeklyScores")
            log.info "Automated Weekly Reset Scheduled for ${settings.resetDay} at ${h}:${m}"
        } catch (e) {
            log.error "Failed to schedule automated reset: ${e}"
        }
    }
}

// --- UTILITIES & NUMBER FORMATTING ---
def formatScore(val) {
    if (val == null) return "0"
    try {
        return String.format("%,d", val as Long)
    } catch(e) {
        return val.toString()
    }
}

def checkInternetStatus() {
    def isOnline = false
    try {
        httpGet([uri: "http://clients3.google.com/generate_204", timeout: 2]) { resp ->
            isOnline = true
        }
    } catch (e) {
        isOnline = false
    }
    return isOnline
}

def updateTop3(list, user, score) {
    if (!list) list = []
    list << [user: user, score: score as Long]
    list = list.sort { a, b -> (b.score as Long) <=> (a.score as Long) }
    
    def uniqueUsers = []
    def result = []
    list.each { entry ->
        if (!uniqueUsers.contains(entry.user)) {
            uniqueUsers << entry.user
            result << entry
        }
    }
    return result.take(3)
}

// --- CHASE GAME LOGIC ---
def selectNewChaseGame() {
    def eligibleGames = []
    state.gameStats?.each { gNum, mStats ->
        def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
        def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
        
        mStats.scores?.each { gameName, gScores ->
            if (gScores.overall && gScores.overall.size() > 0) {
                def displayName = isMulti ? gameName : mName
                eligibleGames << [gNum: gNum, gameName: displayName, topScore: gScores.overall[0].score as Long, topUser: gScores.overall[0].user]
            }
        }
    }
    
    if (eligibleGames.size() > 0) {
        def pick = eligibleGames[new Random().nextInt(eligibleGames.size())]
        state.currentChaseGame = pick
        state.chaseWeekOfYear = Calendar.getInstance(location.timeZone).get(Calendar.WEEK_OF_YEAR)
        addToHistory("SYSTEM: Selected new Weekly Chase Game: ${pick.gameName}")
    } else {
        log.info "Could not select Chase Game - no games have overall scores logged yet."
        state.currentChaseGame = null
    }
}

def occupiedOnHandler(evt) {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") != "on") return
    
    if (settings.guestModeSwitch && settings.guestModeSwitch.currentValue("switch") == "on") {
        log.debug "Guest Mode is ON. Suppressing Chase Game announcement."
        return
    }
    
    if (settings.allowedModes) {
        def modes = [settings.allowedModes].flatten().findAll{it}
        if (!modes.contains(location.mode)) return
    }
    
    runIn(2, "executeOccupiedChaseGame")
}

def executeOccupiedChaseGame() {
    ensureStateMaps()
    def currentWeek = Calendar.getInstance(location.timeZone).get(Calendar.WEEK_OF_YEAR)
    
    if (!state.currentChaseGame || state.chaseWeekOfYear != currentWeek) {
        selectNewChaseGame()
    }
    
    if (state.currentChaseGame) {
        def cg = state.currentChaseGame
        def rawMsg = settings.chaseGameMsg ?: "Welcome to the Game Room! This week's Chase Game is %game%. The score to beat is %allScore% by %allUser%!"
        def finalMsg = rawMsg.replace("%game%", cg.gameName)
                             .replace("%allUser%", cg.topUser.toString())
                             .replace("%allScore%", formatScore(cg.topScore))
        
        addToHistory("CHASE GAME: Queuing Chase Game to FRONT of queue: ${cg.gameName}")
        
        def q = atomicState.ttsQueue ?: []
        q.add(0, finalMsg) 
        atomicState.ttsQueue = q
        
        if (!atomicState.isSpeaking) {
            processQueue()
        }
    }
}

// --- HTML TILE ENGINE ---
def buildTop3Html(gScores, type) {
    def html = ""
    if (type == "Overall Champions" || type == "Both") {
        html += "<div style='margin-bottom: 5px;'><span style='color: #aaa; font-size: 11px; text-transform: uppercase;'>Overall Top 3</span><br>"
        if (gScores?.overall && gScores.overall.size() > 0) {
            gScores.overall.eachWithIndex { entry, idx ->
                def medal = idx == 0 ? "🥇" : (idx == 1 ? "🥈" : "🥉")
                html += "<div style='font-size: 12px;'>${medal} <span style='color: #2ecc71; font-weight: bold;'>${formatScore(entry.score)}</span> <span style='color: #888;'>(${entry.user})</span></div>"
            }
        } else {
            html += "<div style='font-size: 12px; color: #7f8c8d;'>No records</div>"
        }
        html += "</div>"
    }
    
    if (type == "Weekly Champions" || type == "Both") {
        html += "<div><span style='color: #aaa; font-size: 11px; text-transform: uppercase;'>Weekly Top 3</span><br>"
        if (gScores?.weekly && gScores.weekly.size() > 0) {
            gScores.weekly.eachWithIndex { entry, idx ->
                def medal = idx == 0 ? "🥇" : (idx == 1 ? "🥈" : "🥉")
                html += "<div style='font-size: 12px;'>${medal} <span style='color: #e67e22; font-weight: bold;'>${formatScore(entry.score)}</span> <span style='color: #888;'>(${entry.user})</span></div>"
            }
        } else {
            html += "<div style='font-size: 12px; color: #7f8c8d;'>No records</div>"
        }
        html += "</div>"
    }
    return html
}

def updateLeaderboardTile() {
    def type = settings.leaderboardType ?: "Overall Champions"
    def attrName = settings.leaderboardAttribute ?: "leaderboardTile"

    if (leaderboardDevice && attrName) {
        def globalHtml = "<div style='font-family: sans-serif; font-size: 13px; color: #e0e0e0; background-color: #1a1a1a; padding: 12px; border-radius: 8px; box-shadow: inset 0 0 10px rgba(0,0,0,0.5); border: 1px solid #333;'>"
        globalHtml += "<div style='text-align: center; color: #f1c40f; font-weight: bold; margin-bottom: 8px; border-bottom: 2px solid #444; padding-bottom: 5px; text-transform: uppercase; letter-spacing: 1px;'>🏆 Arcade Leaderboard 🏆</div>"
        
        def hasData = false
        state.gameStats?.each { gNum, mStats ->
            def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
            def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
            
            def activeGame = mName
            if (isMulti && mStats?.lastPlayed && mStats.lastPlayed != "Machine ${gNum}") {
                activeGame = mStats.lastPlayed
            }
            
            def gScores = mStats?.scores?."${activeGame}"
            
            if (gScores && ((gScores.overall && gScores.overall.size() > 0) || (gScores.weekly && gScores.weekly.size() > 0))) {
                hasData = true
                globalHtml += "<div style='margin-bottom: 8px; padding: 6px; background-color: #252525; border-radius: 4px; border-left: 4px solid #3498db;'>"
                globalHtml += "<div style='color: #3498db; font-weight: bold; margin-bottom: 3px;'>${activeGame}</div>"
                globalHtml += buildTop3Html(gScores, type)
                globalHtml += "</div>"
            }
        }
        if (!hasData) globalHtml += "<div style='text-align: center; color: #7f8c8d; padding: 15px 0; font-style: italic;'>No scores logged yet.</div>"
        globalHtml += "</div>"
        
        try {
            leaderboardDevice.sendEvent(name: attrName, value: globalHtml, isStateChange: true)
        } catch (e) {
            log.error "Failed to push global leaderboard HTML tile: ${e}"
        }
    }
    
    state.gameStats?.each { gNum, mStats ->
        def childDni = "gameAnnouncer-${app.id}-m${gNum}"
        def child = getChildDevice(childDni)
        
        if (child) {
            def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
            def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
            
            def activeGame = mName
            if (isMulti && mStats?.lastPlayed && mStats.lastPlayed != "Machine ${gNum}") {
                activeGame = mStats.lastPlayed
            }
            
            def gScores = mStats?.scores?."${activeGame}"
            
            def childHtml = "<div style='font-family: sans-serif; font-size: 13px; color: #e0e0e0; background-color: #1a1a1a; padding: 12px; border-radius: 8px; box-shadow: inset 0 0 10px rgba(0,0,0,0.5); border: 1px solid #333;'>"
            childHtml += "<div style='text-align: center; color: #3498db; font-weight: bold; margin-bottom: 8px; border-bottom: 2px solid #444; padding-bottom: 5px; text-transform: uppercase; letter-spacing: 1px;'>${activeGame}</div>"
            
            if (gScores && ((gScores.overall && gScores.overall.size() > 0) || (gScores.weekly && gScores.weekly.size() > 0))) {
                childHtml += buildTop3Html(gScores, type)
            } else {
                childHtml += "<div style='text-align: center; color: #7f8c8d; padding: 15px 0; font-style: italic;'>No scores logged yet.</div>"
            }
            childHtml += "</div>"
            
            try {
                child.sendEvent(name: "leaderboardTile", value: childHtml, isStateChange: true)
            } catch (e) {
                log.error "Failed to push child leaderboard HTML tile: ${e}"
            }
        }
    }
}

// --- WEB ENDPOINT LOGIC ---
def serveScoreForm() {
    def optionsHtml = ""
    def multiGameMap = [:]
    
    def numG = settings.numGames ?: 0
    if (numG > 0) {
        for (int i = 1; i <= (numG as Integer); i++) {
            def gName = settings["gameName_${i}"] ?: "Machine ${i}"
            def isMulti = settings["gameType_${i}"] == "Multi-Game (Arcade/Pinball)"
            optionsHtml += "<option value='${i}'>${gName}</option>"
            multiGameMap["${i}"] = isMulti
        }
    } else {
        optionsHtml = "<option value=''>No Machines Configured</option>"
    }

    def multiMapJson = new groovy.json.JsonBuilder(multiGameMap).toString()

    def html = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>Arcade Logger</title>
        <style>
            body { background: #121212; color: #e0e0e0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 20px; text-align: center; }
            .container { max-width: 400px; margin: 0 auto; background: #1e1e1e; padding: 20px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.5); border: 1px solid #333; }
            h2 { margin-top: 0; color: #27ae60; text-transform: uppercase; letter-spacing: 2px; }
            label { display: block; text-align: left; margin-bottom: 5px; font-size: 14px; color: #aaa; }
            select, input[type='text'], input[type='number'] { width: 100%; padding: 12px; margin-bottom: 20px; border: 1px solid #444; border-radius: 6px; background: #2a2a2a; color: #fff; font-size: 16px; box-sizing: border-box; }
            input[type='submit'] { width: 100%; padding: 14px; background: #27ae60; color: #fff; border: none; border-radius: 6px; font-size: 16px; font-weight: bold; cursor: pointer; text-transform: uppercase; transition: background 0.3s; }
            input[type='submit']:hover { background: #219653; }
            #subGameContainer { display: none; margin-bottom: 0px; padding-top: 10px; border-top: 1px dashed #444; }
        </style>
        <script>
            const multiMap = ${multiMapJson};
            function checkMachineType() {
                const sel = document.getElementById("gNum").value;
                const container = document.getElementById("subGameContainer");
                const subInput = document.getElementById("subGame");
                if (multiMap[sel]) {
                    container.style.display = "block";
                    subInput.required = true;
                } else {
                    container.style.display = "none";
                    subInput.required = false;
                    subInput.value = "";
                }
            }
            window.onload = checkMachineType;
        </script>
    </head>
    <body>
        <div class="container">
            <h2>Log High Score</h2>
            <form action="submit" method="GET">
                <input type="hidden" name="access_token" value="${state.accessToken}" />
                
                <label for="gNum">Select Machine</label>
                <select id="gNum" name="gNum" required onchange="checkMachineType()">
                    ${optionsHtml}
                </select>
                
                <div id="subGameContainer">
                    <label for="subGame">Specific Game / Table</label>
                    <input type="text" id="subGame" name="subGame" placeholder="e.g., Pac-Man or Addams Family" autocomplete="off" />
                </div>
                
                <label for="player">Player Name</label>
                <input type="text" id="player" name="player" placeholder="Enter your initials or name" required autocomplete="off" />
                
                <label for="score">Final Score</label>
                <input type="number" id="score" name="score" placeholder="0" required />
                
                <input type="submit" value="Submit Record" />
            </form>
        </div>
    </body>
    </html>
    """
    render contentType: "text/html", data: html, status: 200
}

def handleScoreSubmit() {
    def gNum = params.gNum?.toString()
    def subGameRaw = params.subGame?.trim()
    def player = params.player?.trim()
    def score = params.score != null ? params.score.toLong() : null
    def html = ""

    if (!gNum || !player || score == null) {
        html = generateResultHtml("Error", "Missing required fields.", false)
        render contentType: "text/html", data: html, status: 400
        return
    }

    def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
    def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
    def activeGameName = isMulti && subGameRaw ? subGameRaw : mName

    def mStats = state.gameStats[gNum] ?: [lastPlayed: activeGameName, scores: [:]]
    def gScores = mStats.scores[activeGameName] ?: [overall: [], weekly: []]

    def currentOverallTop = gScores.overall.size() > 0 ? (gScores.overall[0].score as Long) : 0L
    def currentWeeklyTop = gScores.weekly.size() > 0 ? (gScores.weekly[0].score as Long) : 0L

    def beatOverallTop = false
    def beatWeeklyTop = false
    def msgList = []

    if (score > currentOverallTop) {
        beatOverallTop = true
        msgList << "New #1 Overall High Score!"
    }
    
    if (score > currentWeeklyTop) {
        beatWeeklyTop = true
        if (!beatOverallTop) msgList << "New #1 Weekly High Score!"
    }

    gScores.overall = updateTop3(gScores.overall, player, score)
    gScores.weekly = updateTop3(gScores.weekly, player, score)

    mStats.lastPlayed = activeGameName
    mStats.scores[activeGameName] = gScores
    state.gameStats[gNum] = mStats

    if (beatOverallTop || beatWeeklyTop) {
        addToHistory("WEB PORTAL: [${mName} - ${activeGameName}] ${player} logged score of ${formatScore(score)}. (${msgList.join(', ')})")
        updateLeaderboardTile() 
        
        def cg = state.currentChaseGame
        def displayCGName = isMulti ? activeGameName : mName
        
        if (cg && cg.gameName == displayCGName && score > (cg.topScore as Long)) {
            cg.topScore = score
            cg.topUser = player
            state.currentChaseGame = cg
        }

        def sw = settings["gameSwitch_${gNum}"]
        if (sw && sw.currentValue("switch") == "on") {
            def alertMsg = "Alert! ${player} just logged a massive new score of ${formatScore(score)} on ${activeGameName}. ${msgList.join(' ')}"
            def q = atomicState.ttsQueue ?: []
            q.add(alertMsg)
            atomicState.ttsQueue = q
            if (!atomicState.isSpeaking) processQueue()
        }

        html = generateResultHtml("Score Saved!", "Congratulations ${player}! Your score of ${formatScore(score)} on ${activeGameName} has been recorded.", true)
    } else {
        addToHistory("WEB PORTAL: [${mName} - ${activeGameName}] ${player} logged ${formatScore(score)}, placing in the Top 3 or lower.")
        updateLeaderboardTile()
        html = generateResultHtml("Score Logged", "Good try ${player}, your score of ${formatScore(score)} has been saved to the database.", true)
    }

    render contentType: "text/html", data: html, status: 200
}

def generateResultHtml(title, message, success) {
    def color = success ? "#27ae60" : "#c0392b"
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>${title}</title>
        <style>
            body { background: #121212; color: #e0e0e0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 20px; text-align: center; }
            .container { max-width: 400px; margin: 50px auto; background: #1e1e1e; padding: 30px 20px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.5); border: 1px solid #333; }
            h2 { margin-top: 0; color: ${color}; }
            p { font-size: 16px; color: #ccc; margin-bottom: 30px;}
            a { display: inline-block; padding: 12px 24px; background: #333; color: #fff; text-decoration: none; border-radius: 6px; font-weight: bold; border: 1px solid #555; transition: background 0.3s;}
            a:hover { background: #444; }
        </style>
    </head>
    <body>
        <div class="container">
            <h2>${title}</h2>
            <p>${message}</p>
            <a href="log?access_token=${state.accessToken}">Log Another Game</a>
        </div>
    </body>
    </html>
    """
}

// --- CORE EVENT HANDLER ---
def switchOnHandler(evt) {
    if (settings.masterEnableSwitch && settings.masterEnableSwitch.currentValue("switch") != "on") return
    
    if (settings.guestModeSwitch && settings.guestModeSwitch.currentValue("switch") == "on") {
        log.debug "Guest Mode is ON. Suppressing machine power-up announcement."
        return
    }
    
    ensureStateMaps()
    def deviceId = evt.device.id
    
    if (settings.allowedModes) {
        def modes = [settings.allowedModes].flatten().findAll{it}
        if (!modes.contains(location.mode)) {
            log.debug "TTS Announcement blocked. Current mode '${location.mode}' is not in allowed modes."
            return
        }
    }
    
    def numG = settings.numGames ?: 0
    for (int i = 1; i <= (numG as Integer); i++) {
        if (settings["gameSwitch_${i}"]?.id == deviceId) {
            pauseExecution(new Random().nextInt(2000) + 100)
            queueAnnouncement(i)
            return
        }
    }
}

def queueAnnouncement(int gNum) {
    ensureStateMaps()
    
    def mName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
    def isMulti = settings["gameType_${gNum}"] == "Multi-Game (Arcade/Pinball)"
    def mStats = state.gameStats["${gNum}"]
    
    def activeGameName = mName
    if (isMulti && mStats?.lastPlayed && mStats.lastPlayed != "Machine ${gNum}") {
        activeGameName = mStats.lastPlayed
    }
    
    def gScores = mStats?.scores?."${activeGameName}"
    
    def allList = gScores?.overall ?: []
    def weekList = gScores?.weekly ?: []
    
    def allUser = allList.size() > 0 ? allList[0].user : "Nobody"
    def allScore = allList.size() > 0 ? allList[0].score : 0L
    def weekUser = weekList.size() > 0 ? weekList[0].user : "Nobody"
    def weekScore = weekList.size() > 0 ? weekList[0].score : 0L
    
    def rawMsg = settings["customMsg_${gNum}"] ?: "The last game played on %machine% was %game%. The overall high score is %allScore%, held by %allUser%. This week's current leader is %weekUser% with a score of %weekScore%."
    
    def finalMsg = rawMsg.replace("%machine%", mName.toString())
                         .replace("%game%", activeGameName.toString())
                         .replace("%allUser%", allUser.toString())
                         .replace("%allScore%", formatScore(allScore))
                         .replace("%weekUser%", weekUser.toString())
                         .replace("%weekScore%", formatScore(weekScore))
    
    addToHistory("QUEUED: [${mName}] - '${finalMsg}'")
    
    def q = atomicState.ttsQueue ?: []
    q.add(finalMsg)
    atomicState.ttsQueue = q
    
    if (!atomicState.isSpeaking) {
        processQueue()
    }
}

// --- TTS QUEUE ENGINE ---
def processQueue() {
    ensureStateMaps()
    
    def q = atomicState.ttsQueue ?: []
    if (q.size() > 0) {
        if (settings.requireInternet && !checkInternetStatus()) {
            log.warn "No internet connection detected. Emptying TTS queue to prevent errors."
            atomicState.ttsQueue = []
            atomicState.isSpeaking = false
            return
        }
        
        atomicState.isSpeaking = true
        def msgToSpeak = q[0]
        
        q = q.drop(1)
        atomicState.ttsQueue = q
        
        speakMessage(msgToSpeak)
        
        def delay = Math.max(5, (msgToSpeak.length() / 10).toInteger() + 2)
        runIn(delay, "processQueue", [overwrite: true])
        
    } else {
        atomicState.isSpeaking = false
    }
}

def speakMessage(String msg) {
    if (!ttsSpeaker) return
    def speakers = ttsSpeaker instanceof List ? ttsSpeaker : [ttsSpeaker]
    
    def safeMsg = msg.replace("&", "and")
    def finalMsg = safeMsg
    if (settings.enableWakeupPad) {
        finalMsg = ", , , " + safeMsg
    }
    
    speakers.each { spk ->
        try {
            def currentVol = spk.currentValue("volume")
            def targetVol = settings.ttsVolume != null ? (settings.ttsVolume as Integer) : currentVol
            
            // Apply Quiet Hours logic
            if (settings.quietHoursStart && settings.quietHoursEnd && settings.quietVolume != null) {
                try {
                    if (timeOfDayIsBetween(toDateTime(settings.quietHoursStart), toDateTime(settings.quietHoursEnd), new Date(), location.timeZone)) {
                        targetVol = settings.quietVolume as Integer
                        log.debug "Quiet Hours active. Throttling volume to ${targetVol}%"
                    }
                } catch(e) { log.error "Quiet Hours check failed: ${e}" }
            }
            
            if (targetVol != null) {
                spk.setVolume(targetVol)
                pauseExecution(800)
            }
            
            spk.speak(finalMsg)
            
            try {
                if (spk.hasCommand("play")) spk.play()
            } catch (ex) {}
            
            if (currentVol != null && targetVol != null && currentVol != targetVol) {
                def delay = Math.max(5, (finalMsg.length() / 10).toInteger() + 3)
                // runIn(delay, "restoreVolumeTask", [data: [speakerId: spk.id, oldVol: currentVol], overwrite: false])
            }
        } catch (e) {
            log.error "Announcer TTS Error sending to ${spk.displayName}: ${e}"
        }
    }
}

def restoreVolumeTask(data) {
    def id = data.speakerId
    def vol = data.oldVol
    def speakers = ttsSpeaker instanceof List ? ttsSpeaker : [ttsSpeaker]
    def spk = speakers.find { it.id == id }
    if (spk && vol != null) {
        spk.setVolume(vol as Integer)
    }
}

// --- BUTTON ACTIONS ---
def appButtonHandler(btn) {
    ensureStateMaps()
    
    if (btn == "btnRefresh") {
        log.info "Dashboard Refresh Triggered."
    } else if (btn == "btnUpdateTile") {
        updateLeaderboardTile()
    } else if (btn == "btnForceChase") {
        selectNewChaseGame()
    } else if (btn.startsWith("btnCreateChild_")) {
        def gNum = btn.split("_")[1]
        def gName = settings["gameName_${gNum}"] ?: "Machine ${gNum}"
        def childDni = "gameAnnouncer-${app.id}-m${gNum}"
        def existing = getChildDevice(childDni)
        
        if (!existing) {
            try {
                addChildDevice("hubitat", "Virtual Omni Sensor", childDni, null, [name: "${gName} Leaderboard", label: "${gName} Leaderboard"])
                log.info "Created child device: ${gName} Leaderboard"
                updateLeaderboardTile()
            } catch (e) {
                log.error "Error creating child device: ${e}"
            }
        } else {
            log.warn "Child device already exists for ${gName}."
        }
    } else if (btn.startsWith("btnTestGame_")) {
        def gNum = btn.split("_")[1].toInteger()
        queueAnnouncement(gNum)
    } else if (btn == "btnResetWeekly") {
        clearWeeklyScores()
    } else if (btn.startsWith("btnWipeMachine_")) {
        def gNum = btn.split("_")[1]
        state.gameStats.remove(gNum)
        addToHistory("SYSTEM: Database completely wiped for Machine ${gNum}.")
        updateLeaderboardTile()
    }
}

def clearWeeklyScores() {
    state.gameStats?.each { gNum, mStats ->
        mStats.scores?.each { gameName, gScores ->
            gScores.weekly = []
        }
    }
    addToHistory("SYSTEM: All Weekly Scores have been wiped via automated or manual reset.")
    selectNewChaseGame() 
    updateLeaderboardTile()
    log.info "All weekly scores cleared."
}

def addToHistory(String msg) {
    ensureStateMaps()
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "[${timestamp}] ${msg}")
    if (state.historyLog.size() > 20) state.historyLog = state.historyLog.take(20)
    log.info "ANNOUNCER HISTORY: [${timestamp}] ${msg}"
}
