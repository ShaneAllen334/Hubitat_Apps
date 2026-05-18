/**
 * Advanced Sleep Metrics
 *
 * Author: ShaneAllen
 */
definition(
    name: "Advanced Sleep Metrics",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Clinical-grade sleep orchestrator with EWMA tracking, environmental correlation, and Predictive Wake Triggers.",
    category: "Health & Wellness",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "roomPage")
}

def mainPage() {
    // Auto-refresh removed as requested
    dynamicPage(name: "mainPage", title: "BMS Configuration", install: true, uninstall: true) {
        
        section("Live System Dashboard") {
            input "btnRefresh", "button", title: "🔄 Force Manual Refresh"
            
            if (numRooms > 0) {
                def statusText = ""
                
                def now = new Date().time
                def watchdogMillis = (sensorWatchdogHours != null ? sensorWatchdogHours.toInteger() : 48) * 3600000
                def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                def isWkndGlobal = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)

                for (int i = 1; i <= (numRooms as Integer); i++) {
                    def isMaster = (i == 1)
                    def numUsers = isMaster ? 2 : 1
                    
                    for (int u = 1; u <= numUsers; u++) {
                        def uId = "${i}_${u}"
                        def uName = settings["userName_${uId}"] ?: (isMaster ? (u == 1 ? "Left Side" : "Right Side") : "Occupant")
                        def vSensor = settings["vibrationSensor_${uId}"]
                        def vSensor2 = settings["vibrationSensor2_${uId}"]
                        def pMat = settings["pressureMat_${uId}"]
                        
                        if (!vSensor && !vSensor2 && !pMat) continue
                        
                        // Live Hardware Status Checks
                        def vSensState = vSensor ? (vSensor.currentValue("acceleration") ?: "inactive") : "none"
                        def pMatState = pMat ? (pMat.currentValue("contact") ?: pMat.currentValue("presence") ?: "open") : "none"
                        
                        def vIndicator = vSensState == "active" ? "<span style='color:#e74c3c;font-weight:bold;'>ACTIVE 📳</span>" : "<span style='color:#95a5a6;'>INACTIVE</span>"
                        def pIndicator = (pMatState == "closed" || pMatState == "present") ? "<span style='color:#2980b9;font-weight:bold;'>CLOSED 🛏️</span>" : "<span style='color:#95a5a6;'>OPEN</span>"

                        // BMS Health Check
                        def lastVibGlobal = state.lastVibrationTime?."${uId}" ?: 0
                        def isOffline = (lastVibGlobal > 0 && (now - lastVibGlobal) > watchdogMillis)
                        
                        def cState = state.sleepState?."${uId}" ?: "EMPTY"
                        def stateColor = (cState == "SLEEPING") ? "#2980b9" : (cState == "IN BED" ? "#8e44ad" : (cState == "PENDING ENTRY" ? "#1abc9c" : (cState == "BATHROOM TRIP" ? "#e67e22" : "#27ae60")))
                        if (isOffline) stateColor = "#c0392b"
                        
                        // Efficiency Score Calculation & AI Data
                        def score = calculateEfficiencyScore(uId)
                        def scoreColor = score >= 85 ? "#27ae60" : (score >= 70 ? "#f39c12" : "#c0392b")
                        
                        def mlEnabled = (settings["enableML_${uId}"] != false)
                        def aiStats = ""
                        def aiTimes = ""
                        def tripMins = state.bathroomDuration?."${uId}" ?: 0
                        def trips = state.bathroomTrips?."${uId}" ?: 0
                        
                        def averages = calculateUserAverages(uId)
                        
                        if (mlEnabled) {
                            def learningDisplay = averages.daysLearned >= 14 ? "🧠 Learned" : "🧠 Learning (${averages.daysLearned}/14)"
                            aiStats = "<span style='font-size: 12px; color: #555;'>${learningDisplay} | 7D Avg: <b>${averages.avgScore7 ?: "--"}%</b></span>"
                            def todayAvgIn = isWkndGlobal ? averages.avgInWe : averages.avgInWd
                            def todayAvgOut = isWkndGlobal ? averages.avgOutWe : averages.avgOutWd
                            aiTimes = "<span style='font-size: 12px; color: #555;'>Expected In: <b>${formatMinutesFromNoon(todayAvgIn)}</b> | Out: <b>${formatMinutesFromNoon(todayAvgOut)}</b></span>"
                        } else {
                            aiStats = "<span style='font-size: 12px; color: #888;'><i>🧠 AI Disabled</i></span>"
                            aiTimes = "<span style='font-size: 12px; color: #888;'><i>Predictions Disabled</i></span>"
                        }

                        def inBedTimeStr = state.inBedTime?."${uId}" ? formatTimestamp(state.inBedTime."${uId}") : "--:--"
                        def exitTimeStr = state.lastExitTime?."${uId}" ? formatTimestamp(state.lastExitTime."${uId}") : "--:--"
                        def asleepTimeStr = state.asleepTime?."${uId}" ? formatTimestamp(state.asleepTime."${uId}") : "--:--"
                        
                        // Live Duration & Advanced Tracking
                        def liveInBed = 0
                        def liveAsleep = 0
                        if (cState == "IN BED" || cState == "SLEEPING" || cState == "PENDING ENTRY" || cState == "BATHROOM TRIP") {
                            if (state.inBedTime?."${uId}") liveInBed = ((now - state.inBedTime."${uId}") / 60000).toInteger()
                            if (state.asleepTime?."${uId}") liveAsleep = ((now - state.asleepTime."${uId}") / 60000).toInteger()
                        } else {
                            liveInBed = state.lastSessionInBed?."${uId}" ?: 0
                            liveAsleep = state.lastSessionAsleep?."${uId}" ?: 0
                        }
                        
                        def deepMins = state.deepSleepDuration?."${uId}" ?: 0
                        if (cState == "SLEEPING") {
                            def stillStart = state.lastStillStartTime["${uId}"] ?: state.asleepTime["${uId}"] ?: now
                            def gap = now - stillStart
                            if (gap >= 2700000) deepMins += (gap / 60000).toInteger()
                        }
                        
                        def lightMins = Math.max(0, liveAsleep - deepMins)
                        def awakeMins = Math.max(0, liveInBed - liveAsleep)

                        def totalMins = liveInBed > 0 ? liveInBed : 1
                        def pDeep = Math.min(100, ((deepMins / totalMins) * 100).toInteger())
                        def pLight = Math.min(100, ((lightMins / totalMins) * 100).toInteger())
                        def pAwake = Math.min(100, ((awakeMins / totalMins) * 100).toInteger())
                        
                        if ((pDeep + pLight + pAwake) == 0) pAwake = 100
                        def dEndApp = ((pDeep / 100.0) * 360).toInteger()
                        def lEndApp = dEndApp + (((pLight / 100.0) * 360).toInteger())
                        
                        def moves = state.movements?."${uId}" ?: 0
                        def liveAsleepForIndex = cState == "SLEEPING" ? liveAsleep : (state.lastSessionAsleep?."${uId}" ?: 0)
                        def rIndex = liveAsleepForIndex > 0 ? (Math.round((moves / (liveAsleepForIndex / 60.0)) * 10) / 10.0) : 0.0
                        def rIndexFmt = rIndex as Double 
                        
                        def eff = 0
                        if (totalMins >= 30 && liveAsleep > 0) eff = Math.min(100, ((liveAsleep / totalMins) * 100) as Integer)
                        
                        // Rescaled movement penalty logic
                        def rawPen = state.weightedMovementPenalty?."${uId}" != null ? state.weightedMovementPenalty["${uId}"] : (moves * 0.25)
                        if (rawPen > moves) rawPen = moves * 0.25 // Backward compatibility fix for the massive 2.0 point explosion
                        def movPenFmt = Math.min(30.0, rawPen as Double)
                        
                        def currentEnv = ""
                        if (state.envStats?."${uId}"?.tCnt > 0) {
                            def curT = Math.round((state.envStats["${uId}"].tSum / state.envStats["${uId}"].tCnt) * 10) / 10.0
                            def curH = Math.round((state.envStats["${uId}"].hSum / state.envStats["${uId}"].hCnt) * 10) / 10.0
                            currentEnv = "Temp: ${curT}° | Hum: ${curH}%"
                        } else {
                            currentEnv = "N/A"
                        }
                        
                        def advText = ""
                        if (settings["enableCircadianScaling_${uId}"]) advText += " 🌖 <i>Circadian movement scaling active.</i>"
                        if (settings["enableClinicalScoring_${uId}"]) advText += "<br>⚕️ <b>Clinical Scoring:</b> Active (Duration Goal: ${settings["targetSleepHours_${uId}"] ?: 7.5}h). Latency and Sleep Jet-Lag considered in final score."
                        if (settings["enableAdvancedStages_${uId}"] && cState == "SLEEPING") {
                            def stage = state.currentSleepStage?."${uId}" ?: "DEEP"
                            def ewmaVal = state.ewmaMovement?."${uId}" ?: 0.0
                            def sColor = stage == "LIGHT" ? "#3498db" : "#2980b9"
                            advText += "<br>📈 <b>Real-Time EWMA Stage:</b> <span style='color:${sColor}; font-weight:bold;'>${stage}</span> <i>(Index: ${String.format("%.2f", ewmaVal as Double)})</i>"
                        }
                        if (settings["enableEnvCorrelation_${uId}"] && averages.optimalTemp != null) {
                            def oH = averages.optimalHumid != null ? "at ${averages.optimalHumid}% Hum" : ""
                            advText += "<br>🌡️ <b>Learned Optimal Env:</b> <b>${averages.optimalTemp}°</b> ${oH} yields highest scores."
                        }

                        def timers = []
                        if (isOffline) {
                            timers << "<span style='color: red;'>⚠️ Sensor Stale</span>"
                        } else {
                            def deafenedUntil = state.deafenedUntil?."${uId}" ?: 0
                            if (now < deafenedUntil) timers << "<span style='color: #e67e22;'>🛡️ Kinetic Shield</span>"
                            
                            def lastMove = state.lastVibrationTime?."${uId}" ?: 0
                            if (cState == "IN BED" && (now - lastMove) < ((fallAsleepThreshold ?: 15) * 60000)) {
                                def rem = (((fallAsleepThreshold ?: 15) * 60000) - (now - lastMove)) / 60000
                                timers << "<span style='color: #3498db;'>Settling: ${rem.toInteger()}m</span>"
                            }
                            if (cState == "PENDING ENTRY") {
                                def pendingStart = state.pendingEntryTime?."${uId}" ?: now
                                def abWait = state.pendingAntiBounceWait?."${uId}" != null ? state.pendingAntiBounceWait["${uId}"] : (settings.antiBounceWait ?: 3)
                                def rem = ((abWait * 60000) - (now - pendingStart)) / 60000
                                timers << "<span style='color: #1abc9c;'>Verifying: ${Math.max(0, rem.toInteger())}m</span>"
                            }
                            if (isSettlingLockActive(uId)) {
                                def inBed = state.inBedTime?."${uId}" ?: 0
                                def resumed = state.sessionResumedTime?."${uId}" ?: 0
                                def lockStart = Math.max(inBed as Long, resumed as Long)
                                if (lockStart == 0) lockStart = now
                                def rem = (((settlingLockTime ?: 30) * 60000) - (now - lockStart)) / 60000
                                timers << "<span style='color: #e74c3c;'>🔒 Locked: ${Math.max(0, rem.toInteger())}m</span>"
                            }
                        }
                        def timerStr = timers ? timers.join(" | ") : "<span style='color: #95a5a6;'>Monitored Active</span>"

                        def insightText = "<b>Deep Sleep Insight:</b><br>"
                        insightText += "Based on telemetry from your designated pressure and kinetic vibration sensors, you established a base sleep efficiency of <b>${eff}%</b> (Time Asleep vs. Time In Bed). "
                        if (moves > 0) {
                            insightText += "Throughout the session, sensors recorded <b>${moves}</b> distinct restlessness events (resulting in a capped <b>-${String.format("%.1f", movPenFmt)} point</b> penalty). "
                        } else {
                            insightText += "Your sleep was incredibly still, with 0 recorded restlessness events. "
                        }
                        if (trips > 0) {
                            insightText += "You registered <b>${trips}</b> away/bathroom trip(s), spending <b>${tripMins} minutes</b> out of bed. "
                        }
                        insightText += "Combined with room health telemetry (${currentEnv}), your final calculated BMS Sleep Score is <b>${score}%</b>."
                        if (advText != "") insightText += "<br><br>${advText}"

                        statusText += """
                        <details style='background: #fdfdfd; border: 1px solid #ccc; border-radius: 8px; margin-bottom: 15px; box-shadow: 0 3px 6px rgba(0,0,0,0.08); font-family: sans-serif;'>
                            
                            <summary style='padding: 16px 20px; background: linear-gradient(180deg, #ffffff 0%, #f4f4f4 100%); font-size: 16px; cursor: pointer; border-bottom: 1px solid #ddd; outline: none; display: flex; align-items: center; border-radius: 8px 8px 0 0;'>
                                <div style='flex: 1; font-weight: bold; color: #2c3e50; font-size: 20px;'>
                                    ${uName} <span style='font-size: 14px; color: #7f8c8d; font-weight: normal; margin-left: 8px;'>${getRoomName(i)}</span>
                                </div>
                                <div style='flex: 1; text-align: center;'>
                                    <span style='background: ${stateColor}15; color: ${stateColor}; border: 1px solid ${stateColor}; padding: 6px 16px; border-radius: 14px; font-size: 13px; font-weight: bold; letter-spacing: 1px;'>${isOffline ? "OFFLINE" : cState}</span>
                                </div>
                                <div style='flex: 1; text-align: right; font-size: 15px; color: #555;'>
                                    Score: <span style='color: ${scoreColor}; font-size: 24px; font-weight: bold;'>${score}%</span>
                                </div>
                            </summary>
                            
                            <div style='padding: 24px; display: flex; flex-direction: column; gap: 20px;'>
                                
                                <div style='display: flex; flex-wrap: wrap; gap: 30px; align-items: flex-start;'>
                                    
                                    <div style='display: flex; flex-direction: column; align-items: center;'>
                                        <div style='position: relative; width: 100px; height: 100px; border-radius: 50%; background: conic-gradient(${scoreColor} ${score*3.6}deg, #eee 0); display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 10px rgba(0,0,0,0.1);'>
                                            <div style='width: 76px; height: 76px; background: #fdfdfd; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: bold; color: ${scoreColor};'>${score}</div>
                                        </div>
                                        <span style='margin-top: 10px; font-size: 14px; font-weight: bold; color: #555;'>BMS SCORE</span>
                                    </div>

                                    <div style='display: flex; align-items: center; gap: 24px; border-left: 1px solid #eee; padding-left: 30px;'>
                                        <div style='width: 100px; height: 100px; border-radius: 50%; background: conic-gradient(#2980b9 0 ${dEndApp}deg, #3498db 0 ${lEndApp}deg, #e67e22 0); border: 2px solid #ddd; box-shadow: 0 4px 10px rgba(0,0,0,0.1);'></div>
                                        
                                        <div style='display: flex; flex-direction: column; gap: 8px; font-size: 14px; color: #333;'>
                                            <div style='display: flex; align-items: center;'><span style='display: inline-block; width: 14px; height: 14px; background: #2980b9; margin-right: 10px; border-radius: 3px;'></span> <b>Deep:</b> ${formatDuration(deepMins)} <span style='color:#777; margin-left: 6px;'>(${pDeep}%)</span></div>
                                            <div style='display: flex; align-items: center;'><span style='display: inline-block; width: 14px; height: 14px; background: #3498db; margin-right: 10px; border-radius: 3px;'></span> <b>Light:</b> ${formatDuration(lightMins)} <span style='color:#777; margin-left: 6px;'>(${pLight}%)</span></div>
                                            <div style='display: flex; align-items: center;'><span style='display: inline-block; width: 14px; height: 14px; background: #e67e22; margin-right: 10px; border-radius: 3px;'></span> <b>Awake:</b> ${formatDuration(awakeMins)} <span style='color:#777; margin-left: 6px;'>(${pAwake}%)</span></div>
                                        </div>
                                    </div>

                                    <div style='flex: 1; border-left: 1px solid #eee; padding-left: 30px; display: flex; flex-direction: column; gap: 12px;'>
                                        <div style='font-size: 14px;'>🛏️ In Bed: <b>${inBedTimeStr}</b> ➔ Out: <b>${exitTimeStr}</b></div>
                                        <div style='font-size: 14px;'>💤 Asleep: <b>${asleepTimeStr}</b> (Latency: <b>${state.sleepLatency?."${uId}" ?: "--"}m</b>)</div>
                                        <div style='font-size: 14px;'>🏃‍♂️ Movements: <b>${moves}</b> (Index: <b>${String.format("%.1f", rIndexFmt)}/hr</b>)</div>
                                        <div style='font-size: 14px;'>📡 Hardware: Vib: ${vIndicator} | Mat: ${pIndicator}</div>
                                        <div style='font-size: 14px;'>🌡️ Room Env: <b>${currentEnv}</b></div>
                                    </div>
                                </div>

                                <div style='width: 100%;'>
                                    <div style='width: 100%; height: 16px; background: #eee; border-radius: 8px; display: flex; overflow: hidden; border: 1px solid #ccc;'>
                                        <div style='width: ${pDeep}%; background: linear-gradient(90deg, #1A2980 0%, #26D0CE 100%);' title='Deep Sleep'></div>
                                        <div style='width: ${pLight}%; background: linear-gradient(90deg, #2980b9 0%, #3498db 100%); border-left: 1px solid #fff;' title='Light Sleep'></div>
                                        <div style='width: ${pAwake}%; background: linear-gradient(90deg, #d35400 0%, #e67e22 100%); border-left: 1px solid #fff;' title='Awake/Restless'></div>
                                    </div>
                                </div>

                                <div style='background: #f4f6f7; border-left: 5px solid ${scoreColor}; padding: 16px; border-radius: 6px; font-size: 14px; color: #2c3e50; line-height: 1.6; box-shadow: inset 0 0 10px rgba(0,0,0,0.02);'>
                                    ${insightText}
                                </div>
                                
                                <div style='display: flex; justify-content: space-between; font-size: 13px; border-top: 1px solid #eee; padding-top: 12px;'>
                                    <div>${aiStats} | ${aiTimes}</div>
                                    <div><b>System Activity:</b> ${timerStr}</div>
                                </div>

                            </div>
                        </details>
                        """
                    }
                }
                
                def globalStatus = (masterEnableSwitch && masterEnableSwitch.currentValue("switch") == "off") ? "<span style='color: red; font-weight: bold;'>PAUSED</span>" : (isTrackingAllowed() ? "<span style='color: green; font-weight: bold;'>ACTIVE TRACKING</span>" : "<span style='color: #e65100; font-weight: bold;'>RESTRICTED (Out of Bounds)</span>")
                
                statusText += "<div style='margin-top: 10px; padding: 12px; background: #e9e9e9; border-radius: 6px; font-size: 14px; border: 1px solid #ccc; text-align: center;'>"
                statusText += "<b>Master System Tracking:</b> ${globalStatus}"
                statusText += "</div>"
                
                paragraph statusText
            }
        }

        section("System Event History", hideable: true, hidden: true) {
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
        }
        
        section("Global Restrictions (Time & Mode)", hideable: true, hidden: true) {
            input "activeSleepModes", "mode", title: "Allowed Sleep Modes", multiple: true, required: false
            input "sleepStartTime", "time", title: "Tracking Start Time", required: false
            input "sleepEndTime", "time", title: "Tracking End Time", required: false
        }
        
        section("Global BMS Controls", hideable: true, hidden: true) {
            input "masterEnableSwitch", "capability.switch", title: "Master System Enable Switch", required: false
            input "numRooms", "number", title: "Number of Bedrooms (1-3)", required: true, defaultValue: 1, range: "1..3", submitOnChange: true
            
            paragraph "<b>Smart Logic Configuration</b>"
            input "vibrationsToEnterBed", "number", title: "Vibrations Required to Enter Bed", defaultValue: 1, required: true
            input "fallAsleepThreshold", "number", title: "Fall Asleep Duration (Minutes)", defaultValue: 15
            input "exitBedThreshold", "number", title: "Bed Exit Delay (Minutes)", defaultValue: 5
            input "stitchingWindow", "number", title: "Standard Stitching Window (Minutes)", defaultValue: 15
            
            paragraph "<b>Advanced Signal Processing</b>"
            input "enableSettlingLock", "bool", title: "🔒 Enable Settling Lock?", defaultValue: true, submitOnChange: true, description: "Completely locks the user IN BED for a set time after entry to prevent tossing/adjusting from causing false exits."
            if (enableSettlingLock) {
                input "settlingLockTime", "number", title: "Settling Lock Duration (Mins)", defaultValue: 30
            }

            input "enableGhostFilter", "bool", title: "👻 Enable Pre-Emptive Presence Lockout?", defaultValue: true, description: "Ignores bed exits if the room motion sensor was already active BEFORE the bed vibration stopped (blocks parents checking on kids)."
            
            paragraph "<b>False Positive & Pet Rejection</b>"
            input "enableTeleportFilter", "bool", title: "🚫 Enable Teleportation Filter?", defaultValue: true, description: "Blocks bed entries if there has been no room motion in the last 10 minutes (People don't teleport into bed)."
            input "teleportWindow", "number", title: "Teleportation Window (Mins)", defaultValue: 10
            
            input "enableAntiBounce", "bool", title: "🛏️ Enable Sustained Entry Verification?", defaultValue: true, submitOnChange: true, description: "Wait a few minutes after bed entry to ensure user isn't just folding laundry before committing to IN BED state."
            if (enableAntiBounce) {
                input "antiBounceWait", "number", title: "Verification Wait Time (Mins)", defaultValue: 3
                input "antiBounceMaxMotions", "number", title: "Max Allowed Room Motions During Wait", defaultValue: 2
            }
            
            paragraph "<b>Dynamic Stitching (Time-of-Night Context)</b>"
            input "deepSleepStart", "time", title: "Deep Sleep Window Start", required: false
            input "deepSleepEnd", "time", title: "Deep Sleep Window End", required: false
            input "deepSleepStitchWindow", "number", title: "Deep Sleep Stitching Window (Mins)", defaultValue: 45, description: "Allows much longer bathroom/baby trips during dead of night without breaking the session."

            paragraph "<b>Quiet House Auto-Recovery</b>"
            input "enableQuietHouseReturn", "bool", title: "🤫 Enable Quiet House Auto-Return?", defaultValue: true, submitOnChange: true, description: "If a user is stuck in a Bathroom Trip but the selected house motion sensors go completely quiet, the system assumes they sneaked back into bed without triggering the vibration sensor."
            if (enableQuietHouseReturn) {
                input "globalMotionSensors", "capability.motionSensor", title: "Global House Motion Sensors", multiple: true, required: true
                input "quietHouseThreshold", "number", title: "Quiet House Threshold (Mins)", defaultValue: 20
            }

            paragraph "<b>Cross-Talk Cancellation (The Partner Shield)</b>"
            input "enableCrossTalk", "bool", title: "Enable Multi-User Kinetic Shielding?", defaultValue: true, submitOnChange: true
            if (enableCrossTalk) {
                input "crossTalkDeafenTime", "number", title: "Shield Duration (Seconds)", defaultValue: 60, required: true
            }
        }
        
        section("Advanced Features", hideable: true, hidden: true) {
            input "sensorWatchdogHours", "number", title: "Sensor Watchdog (Hours)", defaultValue: 48
            input "enableTelemetryTracking", "bool", title: "Enable Telemetry Dashboard?", defaultValue: true
            input "btnForceReset", "button", title: "🔄 Force Reset All Beds to EMPTY"
        }

        if (numRooms > 0) {
            for (int i = 1; i <= (numRooms as Integer); i++) {
                section("${getRoomName(i)} Configuration", hideable: true, hidden: true) { 
                    href(name: "roomHref${i}", page: "roomPage", params: [roomNum: i], title: "Configure ${getRoomName(i)} Devices & Users") 
                }
            }
        }
    }
}

def roomPage(params) {
    def rNum = params?.roomNum ?: state.currentRoom ?: 1
    state.currentRoom = rNum
    def isMaster = (rNum == 1)
    
    dynamicPage(name: "roomPage", title: "Room Setup", install: false, uninstall: false, previousPage: "mainPage") {
        section("Identification", hideable: true, hidden: false) {
            input "roomName_${rNum}", "text", title: "Custom Room Name", defaultValue: (isMaster ? "Master Bedroom" : "Bedroom ${rNum}"), submitOnChange: true
        }
        
        section("Room Orchestrator & Controls", hideable: true, hidden: true) {
            input "goodNightSwitch_${rNum}", "capability.switch", title: "Room Good Night Switch", required: false
            input "enableOrchestrator_${rNum}", "bool", title: "Enable Room Orchestrator?", defaultValue: false, submitOnChange: true
            
            if (settings["enableOrchestrator_${rNum}"]) {
                input "goodNightDelay_${rNum}", "number", title: "In-Bed Time to Trigger Switch ON (Mins)", defaultValue: 30
            }
            input "wakeDelay_${rNum}", "number", title: "Wake Confirmation Timeout (Mins)", defaultValue: 45
        }

        section("Room Environment Diagnostics (Optional)", hideable: true, hidden: true) {
            paragraph "Provides Environmental AI correlation mapping and Real-Time external disturbance disruption tracking."
            input "tempSensor_${rNum}", "capability.temperatureMeasurement", title: "Room Temperature Sensor", required: false
            input "humidSensor_${rNum}", "capability.relativeHumidityMeasurement", title: "Room Humidity Sensor", required: false
            input "luxSensor_${rNum}", "capability.illuminanceMeasurement", title: "Room Lux/Light Sensor", required: false
            input "noiseSensor_${rNum}", "capability.soundPressureLevel", title: "Room Decibel/Noise Sensor", required: false
        }
        
        section("Fallback Room Sensors", hideable: true, hidden: true) {
            input "motionSensor_${rNum}", "capability.motionSensor", title: "Fallback Room Motion Sensor", required: true
            input "bathroomMotion_${rNum}", "capability.motionSensor", title: "Fallback En-Suite Bathroom Motion", required: false
        }
        
        def users = isMaster ? 2 : 1
        for (int u = 1; u <= users; u++) {
            section("User ${u} Settings", hideable: true, hidden: true) {
                input "userName_${rNum}_${u}", "text", title: "Name", defaultValue: (isMaster ? (u == 1 ? "Shane" : "Christy") : "User"), submitOnChange: true
                
                input "usePressureMat_${rNum}_${u}", "bool", title: "🛏️ Use Tuya Pressure Mat for Primary Presence?", defaultValue: false, submitOnChange: true
                if (settings["usePressureMat_${rNum}_${u}"]) {
                    input "pressureMat_${rNum}_${u}", "capability.contactSensor", title: "Primary Bed Pressure Mat (Contact)", required: true, description: "When selected, vibration sensors are only used to track exact moment of sleep and nightly restlessness."
                    input "matExitDelay_${rNum}_${u}", "number", title: "Mat Open Exit Force Delay (Mins)", defaultValue: 10, description: "If the mat remains open for this long, bypass all motion checks and absolutely force an exit."
                }

                input "vibrationSensor_${rNum}_${u}", "capability.accelerationSensor", title: "Primary Vibration Sensor (Mattress)", required: true
                input "vibrationSensor2_${rNum}_${u}", "capability.accelerationSensor", title: "Secondary Vibration Sensor (Headboard)", required: false
                
                input "enableML_${rNum}_${u}", "bool", title: "🧠 Enable AI Behavioral Learning?", defaultValue: true, description: "Builds a rolling profile to predict sleep/wake times. Turn OFF for Guest Rooms."

                input "btnClearAI_${rNum}_${u}", "button", title: "🗑️ Clear AI Data for ${settings["userName_${rNum}_${u}"] ?: "User"}"

                def dni = "ASM_INFO_${rNum}_${u}"
                if (getChildDevice(dni)) {
                    paragraph "✅ <b>Dashboard Info Device Linked.</b>"
                } else {
                    input "btnCreateInfo_${rNum}_${u}", "button", title: "➕ Create Dashboard Info Device for ${settings["userName_${rNum}_${u}"]}"
                }

                def dniSw = "ASM_SW_${rNum}_${u}"
                if (getChildDevice(dniSw)) {
                    paragraph "✅ <b>User Virtual Switch Linked.</b>"
                } else {
                    input "btnCreateSwitch_${rNum}_${u}", "button", title: "➕ Create User Virtual Switch"
                }
            }

            section("User ${u} Next-Gen Analytics & Triggers", hideable: true, hidden: true) {
                input "enableClinicalScoring_${rNum}_${u}", "bool", title: "⚕️ Enable Clinical Scoring Model?", defaultValue: false, submitOnChange: true, description: "Overhauls base math to factor in Total Sleep Duration vs Goal, Latency efficiency, and Bedtime Regularity."
                if (settings["enableClinicalScoring_${rNum}_${u}"]) {
                    input "targetSleepHours_${rNum}_${u}", "decimal", title: "Target Sleep Duration Goal (Hours)", defaultValue: 7.5
                }
                
                input "enableAdvancedStages_${rNum}_${u}", "bool", title: "📊 Enable EWMA Sleep Stage Tracking?", defaultValue: false, submitOnChange: true, description: "Replaces standard timer gaps with an Exponentially Weighted Moving Average engine to detect true Light vs Deep sleep phases."
                input "enableCircadianScaling_${rNum}_${u}", "bool", title: "🌖 Enable Circadian Movement Scaling?", defaultValue: false, description: "Dynamically halves the score penalty for restless twitches if they occur during typical early morning REM hours (3 AM - 8 AM)."
                input "enableEnvCorrelation_${rNum}_${u}", "bool", title: "🌡️ Enable Environmental Correlation?", defaultValue: false, description: "Analyzes historical data natively to find your optimal sleep temperature and humidity zones for maximum BMS scores."
                
                input "enableSmartAlarm_${rNum}_${u}", "bool", title: "⏰ Enable Predictive Smart Alarm?", defaultValue: false, submitOnChange: true, description: "Triggers a virtual switch if kinetic movement or LIGHT sleep is detected while inside your established Wake Window, allowing you to run natural wake-up automations."
                if (settings["enableSmartAlarm_${rNum}_${u}"]) {
                    input "smartAlarmSwitch_${rNum}_${u}", "capability.switch", title: "Smart Alarm Trigger Switch", required: true
                }
            }
            
            section("User ${u} Advanced Wake Tracking", hideable: true, hidden: true) {
                input "parentalGuard_${rNum}_${u}", "bool", title: "🛡️ Enable Parental Guard (Strict Exit)?", defaultValue: false
                input "kineticDelaySeconds_${rNum}_${u}", "number", title: "⏱️ Kinetic Speed Limit (Seconds)", defaultValue: 3, description: "Minimum physically possible time it takes to travel between Step 1 and Step 2."
                input "wakeMotion1_${rNum}_${u}", "capability.motionSensor", title: "Sequence Step 1: Bedside Motion", required: false
                input "wakeMotion2_${rNum}_${u}", "capability.motionSensor", title: "Sequence Step 2: Room Exit or Hallway Motion", required: false
                input "bathroomPathMotion_${rNum}_${u}", "capability.motionSensor", title: "Alternative Path: Bathroom Motion", required: false
                input "showerMotion_${rNum}_${u}", "capability.motionSensor", title: "Terminal Wake: Shower Motion Sensor", required: false, description: "If triggered within 10 mins of bed exit or bathroom trip, confirms final wake-up."
                
                paragraph "<b>Expected Wake Windows</b>"
                input "weekdayWakeStart_${rNum}_${u}", "time", title: "Weekday Wake Start", required: false
                input "weekdayWakeEnd_${rNum}_${u}", "time", title: "Weekday Wake End", required: false
                input "weekendWakeStart_${rNum}_${u}", "time", title: "Weekend Wake Start", required: false
                input "weekendWakeEnd_${rNum}_${u}", "time", title: "Weekend Wake End", required: false
                
                paragraph "<b>Strict Wake Lockout (Safety Net)</b>"
                input "enableWakeLockout_${rNum}_${u}", "bool", title: "🔒 Enable Strict Wake Lockout?", defaultValue: false, submitOnChange: true, description: "Prevents terminal wake-ups during these hours. Exits are logged as extended bathroom trips until the window ends."
                if (settings["enableWakeLockout_${rNum}_${u}"]) {
                    input "lockoutStart_${rNum}_${u}", "time", title: "Lockout Start Time", required: true
                    input "lockoutEnd_${rNum}_${u}", "time", title: "Lockout End Time", required: true
                }
            }
        }
    }
}

def installed() {
    log.info "Advanced Sleep Metrics Installed."
    initialize()
}

def updated() {
    log.info "Advanced Sleep Metrics Updated."
    unsubscribe()
    unschedule()
    initialize()
}

// --- SELF-HEALING ENGINE ---
def ensureStateMaps() {
    if (state.sleepState == null) state.sleepState = [:]
    if (state.inBedTime == null) state.inBedTime = [:]
    if (state.asleepTime == null) state.asleepTime = [:]
    if (state.lastSessionInBed == null) state.lastSessionInBed = [:]
    if (state.lastSessionAsleep == null) state.lastSessionAsleep = [:]
    if (state.lastVibrationTime == null) state.lastVibrationTime = [:]
    if (state.lastRoomMotionTime == null) state.lastRoomMotionTime = [:]
    if (state.lastGlobalMotionTime == null) state.lastGlobalMotionTime = new Date().time
    if (state.lastValidExitMotion == null) state.lastValidExitMotion = [:]
    if (state.lastExitTime == null) state.lastExitTime = [:]
    if (state.pendingExit == null) state.pendingExit = [:]
    if (state.exitSequenceProgress == null) state.exitSequenceProgress = [:] 
    if (state.sequenceStep1Time == null) state.sequenceStep1Time = [:]
    if (state.movements == null) state.movements = [:]
    if (state.roomEmptyTime == null) state.roomEmptyTime = [:]
    if (state.deafenedUntil == null) state.deafenedUntil = [:]
    if (state.bathroomTrips == null) state.bathroomTrips = [:]
    if (state.bathroomDuration == null) state.bathroomDuration = [:]
    if (state.telemetry == null) state.telemetry = [:]
    if (state.historyLog == null) state.historyLog = []
    if (state.entryVibrationCount == null) state.entryVibrationCount = [:]
    if (state.roomGoodNightTriggered == null) state.roomGoodNightTriggered = [:]
    if (state.pendingEntryTime == null) state.pendingEntryTime = [:]
    if (state.pendingRoomMotions == null) state.pendingRoomMotions = [:]
    if (state.pendingAntiBounceWait == null) state.pendingAntiBounceWait = [:]
    if (state.dailyStats == null) state.dailyStats = [:]
    if (state.sessionResumedTime == null) state.sessionResumedTime = [:]
    if (state.sessionStartTime == null) state.sessionStartTime = [:]
    if (state.envStats == null) state.envStats = [:] 
    if (state.deepSleepDuration == null) state.deepSleepDuration = [:]
    if (state.lastStillStartTime == null) state.lastStillStartTime = [:]
    if (state.sleepLatency == null) state.sleepLatency = [:]
    
    if (state.weightedMovementPenalty == null) state.weightedMovementPenalty = [:]
    if (state.ewmaMovement == null) state.ewmaMovement = [:]
    if (state.lastMoveTimeForEwma == null) state.lastMoveTimeForEwma = [:]
    if (state.currentSleepStage == null) state.currentSleepStage = [:]
    if (state.smartAlarmTriggeredDate == null) state.smartAlarmTriggeredDate = [:]
    
    if (state.lastEnvDisturbanceTime == null) state.lastEnvDisturbanceTime = [:]
    if (state.lastEnvDisturbanceType == null) state.lastEnvDisturbanceType = [:]
}

def initialize() {
    ensureStateMaps()
    
    if (settings["globalMotionSensors"]) {
        subscribe(settings["globalMotionSensors"], "motion", globalMotionHandler)
    }

    for (int i = 1; i <= 3; i++) {
        if (!state.roomEmptyTime["${i}"]) state.roomEmptyTime["${i}"] = 0
        if (!state.lastRoomMotionTime["${i}"]) state.lastRoomMotionTime["${i}"] = 0
        if (!state.roomGoodNightTriggered["${i}"]) state.roomGoodNightTriggered["${i}"] = false
        
        if (settings["tempSensor_${i}"]) subscribe(settings["tempSensor_${i}"], "temperature", envHandler)
        if (settings["humidSensor_${i}"]) subscribe(settings["humidSensor_${i}"], "humidity", envHandler)
        if (settings["luxSensor_${i}"]) subscribe(settings["luxSensor_${i}"], "illuminance", noiseLuxHandler)
        if (settings["noiseSensor_${i}"]) subscribe(settings["noiseSensor_${i}"], "soundPressureLevel", noiseLuxHandler)
        
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            if (!state.sleepState["${uId}"]) state.sleepState["${uId}"] = "EMPTY"
            if (!state.lastValidExitMotion["${uId}"]) state.lastValidExitMotion["${uId}"] = 0
            if (!state.deafenedUntil["${uId}"]) state.deafenedUntil["${uId}"] = 0
            if (!state.bathroomTrips["${uId}"]) state.bathroomTrips["${uId}"] = 0
            if (!state.bathroomDuration["${uId}"]) state.bathroomDuration["${uId}"] = 0
            if (!state.entryVibrationCount["${uId}"]) state.entryVibrationCount["${uId}"] = 0
            if (!state.exitSequenceProgress["${uId}"]) state.exitSequenceProgress["${uId}"] = 0
            if (!state.sequenceStep1Time["${uId}"]) state.sequenceStep1Time["${uId}"] = 0
            if (!state.pendingRoomMotions["${uId}"]) state.pendingRoomMotions["${uId}"] = 0
            if (!state.pendingAntiBounceWait["${uId}"]) state.pendingAntiBounceWait["${uId}"] = 0
            if (!state.sessionResumedTime["${uId}"]) state.sessionResumedTime["${uId}"] = 0
            if (!state.dailyStats["${uId}"]) state.dailyStats["${uId}"] = []
            if (!state.envStats["${uId}"]) state.envStats["${uId}"] = [tSum: 0.0, tCnt: 0, hSum: 0.0, hCnt: 0]
            if (!state.deepSleepDuration["${uId}"]) state.deepSleepDuration["${uId}"] = 0
            if (!state.lastStillStartTime["${uId}"]) state.lastStillStartTime["${uId}"] = 0
            if (!state.sleepLatency["${uId}"]) state.sleepLatency["${uId}"] = 0
            
            if (!state.weightedMovementPenalty["${uId}"]) state.weightedMovementPenalty["${uId}"] = 0.0
            if (!state.ewmaMovement["${uId}"]) state.ewmaMovement["${uId}"] = 0.0
            if (!state.lastMoveTimeForEwma["${uId}"]) state.lastMoveTimeForEwma["${uId}"] = 0
            if (!state.currentSleepStage["${uId}"]) state.currentSleepStage["${uId}"] = "DEEP"
            if (!state.smartAlarmTriggeredDate["${uId}"]) state.smartAlarmTriggeredDate["${uId}"] = ""
            
            if (!state.telemetry["${uId}"] || !state.telemetry["${uId}"].today) {
                state.telemetry["${uId}"] = [today: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0], overall: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0]]
            }
            
            if (settings["wakeMotion1_${uId}"]) subscribe(settings["wakeMotion1_${uId}"], "motion", sequenceMotionHandler)
            if (settings["wakeMotion2_${uId}"]) subscribe(settings["wakeMotion2_${uId}"], "motion", sequenceMotionHandler)
            if (settings["bathroomPathMotion_${uId}"]) subscribe(settings["bathroomPathMotion_${uId}"], "motion", sequenceMotionHandler)
            if (settings["showerMotion_${uId}"]) subscribe(settings["showerMotion_${uId}"], "motion", sequenceMotionHandler)
        }
    }
    
    schedule("0 0 12 * * ?", "middayReset")
    schedule("0 0/5 * * * ?", "orchestrateRooms") 
    
    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["motionSensor_${i}"]) subscribe(settings["motionSensor_${i}"], "motion", fallbackMotionHandler)
        if (settings["bathroomMotion_${i}"]) subscribe(settings["bathroomMotion_${i}"], "motion", fallbackBathroomMotionHandler)
        if (settings["goodNightSwitch_${i}"]) {
            subscribe(settings["goodNightSwitch_${i}"], "switch.on", goodNightOnHandler)
            subscribe(settings["goodNightSwitch_${i}"], "switch.off", goodNightOffHandler)
        }
        
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            
            if (settings["usePressureMat_${uId}"] && settings["pressureMat_${uId}"]) {
                subscribe(settings["pressureMat_${uId}"], "contact", pressureMatHandler)
                subscribe(settings["pressureMat_${uId}"], "presence", pressureMatHandler) 
            }

            def vSensor = settings["vibrationSensor_${uId}"]
            if (vSensor) subscribe(vSensor, "acceleration", vibrationHandler)
            
            def vSensor2 = settings["vibrationSensor2_${uId}"]
            if (vSensor2) subscribe(vSensor2, "acceleration", vibrationHandler)
        }
    }
}

def globalMotionHandler(evt) {
    if (evt.value == "active") {
        state.lastGlobalMotionTime = new Date().time
    }
}

def noiseLuxHandler(evt) {
    if (isSystemPaused()) return
    def rNum = getRoomNumFromDevice(evt.device.id, evt.name == "illuminance" ? "luxSensor" : "noiseSensor")
    if (!rNum) return
    
    def val = evt.value as Double
    def isSpike = false
    def typeStr = ""
    
    if (evt.name == "illuminance" && val > 10) { 
        isSpike = true
        typeStr = "Light Bleed (${val} lx)"
    } else if (evt.name == "soundPressureLevel" && val > 55) { 
        isSpike = true
        typeStr = "Noise Spike (${val} dB)"
    }

    if (isSpike) {
        ensureStateMaps()
        state.lastEnvDisturbanceTime["${rNum}"] = new Date().time
        state.lastEnvDisturbanceType["${rNum}"] = typeStr
    }
}

def envHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused() || !evt.value) return
    def type = evt.name == "temperature" ? "tempSensor" : "humidSensor"
    def rNum = getRoomNumFromDevice(evt.device.id, type)
    if (!rNum) return
    
    def val = evt.value as Double
    def numUsers = (rNum == "1" || rNum == 1) ? 2 : 1
    
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        if (state.sleepState["${uId}"] == "SLEEPING") {
            if (!state.envStats["${uId}"]) state.envStats["${uId}"] = [tSum: 0.0, tCnt: 0, hSum: 0.0, hCnt: 0]
            if (evt.name == "temperature") {
                state.envStats["${uId}"].tSum += val
                state.envStats["${uId}"].tCnt += 1
            } else {
                state.envStats["${uId}"].hSum += val
                state.envStats["${uId}"].hCnt += 1
            }
            updateInfoDevice(uId)
        }
    }
}

def calculateRobustAverage(list) {
    if (!list || list.size() == 0) return null
    if (list.size() < 4) return (list.sum() / list.size()).toInteger()
    def sorted = list.collect() 
    sorted.sort()
    def trimmed = sorted[1..-2] 
    return (trimmed.sum() / trimmed.size()).toInteger()
}

def calculateUserAverages(uId) {
    ensureStateMaps()
    def stats = state.dailyStats?."${uId}" ?: []
    def days = stats.size()
    if (days == 0) return [daysLearned: 0, avgInWd: null, avgInWe: null, avgOutWd: null, avgOutWe: null, avgScore7: null, avgTrips: null, optimalTemp: null, optimalHumid: null]
    
    def inBedWdList = []
    def inBedWeList = []
    def outWdList = []
    def outWeList = []
    def totalTrips = 0
    def tempScores = [:]
    def humidScores = [:]
    
    stats.each { s ->
        totalTrips += (s.trips ?: 0)
        if (s.isWeekend) {
            if (s.inBedMins != null) inBedWeList << s.inBedMins
            if (s.outBedMins != null) outWeList << s.outBedMins
        } else {
            if (s.inBedMins != null) inBedWdList << s.inBedMins
            if (s.outBedMins != null) outWdList << s.outBedMins
        }
        
        if (settings["enableEnvCorrelation_${uId}"] && s.score != null && s.score > 0) {
            if (s.avgTemp != null) {
                def tRound = Math.round(s.avgTemp)
                if (!tempScores[tRound]) tempScores[tRound] = [sum: 0, cnt: 0]
                tempScores[tRound].sum += s.score
                tempScores[tRound].cnt += 1
            }
            if (s.avgHumid != null) {
                def hRound = Math.round(s.avgHumid / 5) * 5 
                if (!humidScores[hRound]) humidScores[hRound] = [sum: 0, cnt: 0]
                humidScores[hRound].sum += s.score
                humidScores[hRound].cnt += 1
            }
        }
    }
    
    def score7 = 0
    def score7Count = 0
    def recent7 = stats.reverse().take(7)
    recent7.each { s ->
        score7 += (s.score ?: 0)
        score7Count++
    }
    
    def optTemp = null
    def maxAvgScoreT = 0
    tempScores.each { k, v ->
        def avgForTemp = v.sum / v.cnt
        if (v.cnt >= 2 && avgForTemp > maxAvgScoreT) { 
            maxAvgScoreT = avgForTemp
            optTemp = k
        }
    }
    
    def optHumid = null
    def maxAvgScoreH = 0
    humidScores.each { k, v ->
        def avgForHumid = v.sum / v.cnt
        if (v.cnt >= 2 && avgForHumid > maxAvgScoreH) { 
            maxAvgScoreH = avgForHumid
            optHumid = k
        }
    }
    
    return [
        daysLearned: days,
        avgInWd: calculateRobustAverage(inBedWdList),
        avgInWe: calculateRobustAverage(inBedWeList),
        avgOutWd: calculateRobustAverage(outWdList),
        avgOutWe: calculateRobustAverage(outWeList),
        avgScore7: score7Count > 0 ? (score7 / score7Count).toInteger() : 0,
        avgTrips: Math.round((totalTrips / days) * 10) / 10.0,
        optimalTemp: optTemp,
        optimalHumid: optHumid
    ]
}

def getMinutesFromNoon(timestamp) {
    if (!timestamp) return 0
    def cal = Calendar.getInstance(location.timeZone)
    cal.setTimeInMillis(timestamp as Long)
    def hours = cal.get(Calendar.HOUR_OF_DAY)
    def mins = cal.get(Calendar.MINUTE)
    def shiftedHours = (hours + 12) % 24
    return (shiftedHours * 60) + mins
}

def formatMinutesFromNoon(mins) {
    if (mins == null) return "--:--"
    def unshiftedHours = (mins.toInteger() / 60).toInteger()
    def actualHours = (unshiftedHours - 12 + 24) % 24
    def actualMins = mins.toInteger() % 60
    def ampm = actualHours >= 12 ? "PM" : "AM"
    def displayHours = actualHours % 12
    if (displayHours == 0) displayHours = 12
    return "${displayHours}:${actualMins.toString().padLeft(2, '0')} ${ampm}"
}

def isWithinWakeLockout(uId) {
    if (!settings["enableWakeLockout_${uId}"]) return false
    def startTime = settings["lockoutStart_${uId}"]
    def endTime = settings["lockoutEnd_${uId}"]
    if (!startTime || !endTime) return false

    def tz = location.timeZone ?: TimeZone.getDefault()
    def start = timeToday(startTime, tz)
    def end = timeToday(endTime, tz)
    def nowTime = new Date().time

    if (start.time > end.time) {
        return (nowTime >= start.time || nowTime <= end.time)
    } else {
        return (nowTime >= start.time && nowTime <= end.time)
    }
}

def getLockoutEndTimeMillis(uId) {
    def startTime = settings["lockoutStart_${uId}"]
    def endTime = settings["lockoutEnd_${uId}"]
    def tz = location.timeZone ?: TimeZone.getDefault()
    def start = timeToday(startTime, tz)
    def end = timeToday(endTime, tz)
    def nowTime = new Date().time

    if (start.time > end.time) {
        if (nowTime >= start.time) return end.time + 86400000 
        else return end.time 
    } else {
        if (nowTime > end.time) return end.time + 86400000 
        return end.time
    }
}

def processUserMovement(uId, rNum) {
    def now = new Date().time
    state.movements["${uId}"] = (state.movements["${uId}"] ?: 0) + 1
    
    // Environmental Check
    def lastDisturbTime = state.lastEnvDisturbanceTime?."${rNum}" ?: 0
    if (lastDisturbTime > 0 && (now - lastDisturbTime) <= 180000) { 
         def dType = state.lastEnvDisturbanceType?."${rNum}" ?: "Disturbance"
         addToHistory("ENVIRONMENTAL DISTURBANCE: ${getUserName(uId)} movement likely caused by ${dType}.")
         state.lastEnvDisturbanceTime["${rNum}"] = 0 
    }
    
    def penalty = 0.25
    if (settings["enableCircadianScaling_${uId}"]) {
        def hour = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault()).get(Calendar.HOUR_OF_DAY)
        if (hour >= 3 && hour <= 8) penalty = 0.10 
    }
    state.weightedMovementPenalty["${uId}"] = (state.weightedMovementPenalty["${uId}"] ?: 0.0) + penalty
    
    if (settings["enableAdvancedStages_${uId}"]) {
        def lastT = state.lastMoveTimeForEwma["${uId}"] ?: now
        def diffMins = (now - lastT) / 60000.0
        def decay = Math.pow(0.5, diffMins / 15.0) 
        state.ewmaMovement["${uId}"] = (state.ewmaMovement["${uId}"] ?: 0.0) * decay + 1.0
        state.lastMoveTimeForEwma["${uId}"] = now
        
        def newStage = state.ewmaMovement["${uId}"] > 1.5 ? "LIGHT" : "DEEP"
        if (state.currentSleepStage["${uId}"] != newStage) {
            state.currentSleepStage["${uId}"] = newStage
        }
    }
    
    if (settings["enableSmartAlarm_${uId}"] && settings["smartAlarmSwitch_${uId}"]) {
        def todayStr = new Date().format("yyyy-MM-dd", location.timeZone ?: TimeZone.getDefault())
        if (state.smartAlarmTriggeredDate["${uId}"] != todayStr && isWithinWakeWindow(uId)) {
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (cState == "SLEEPING" || cState == "IN BED") {
                settings["smartAlarmSwitch_${uId}"].on()
                state.smartAlarmTriggeredDate["${uId}"] = todayStr
                addToHistory("⏰ SMART ALARM: ${getUserName(uId)} registered movement/LIGHT sleep inside Wake Window. Fired smart alarm trigger.")
            }
        }
    }
    
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    if (cState == "SLEEPING") {
        def stillStart = state.lastStillStartTime["${uId}"] ?: state.asleepTime["${uId}"] ?: now
        def gap = now - stillStart
        if (gap >= 2700000) { 
            state.deepSleepDuration["${uId}"] = (state.deepSleepDuration["${uId}"] ?: 0) + (gap / 60000).toInteger()
        }
        state.lastStillStartTime["${uId}"] = now
    } else if (cState == "IN BED") {
        runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
    }
}


def pressureMatHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def uId = getUserIdFromDevice(evt.device.id, "pressureMat")
    if (!uId) return
    
    def now = new Date().time
    def rNum = uId.split('_')[0]
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    def uName = getUserName(uId)

    if (evt.value == "closed" || evt.value == "present") { 
        state.pendingExit["${uId}"] = 0
        state.exitSequenceProgress["${uId}"] = 0 
        
        if (cState == "EMPTY" || cState == "BATHROOM TRIP") {
            if (cState == "EMPTY" && !isTrackingAllowed()) return
            
            if (settings["enableTeleportFilter"] != false && cState == "EMPTY") {
                def lastRoomMot = state.lastRoomMotionTime["${rNum}"] ?: 0
                def teleWindowMillis = (settings["teleportWindow"] != null ? settings["teleportWindow"].toInteger() : 10) * 60000
                if (lastRoomMot == 0 || (now - lastRoomMot) > teleWindowMillis) {
                    log.warn "BMS: Blocked Mat Entry for ${uName}. No room motion detected in the last ${(teleWindowMillis/60000).toInteger()} minutes."
                    return 
                }
            }
            
            if (cState == "EMPTY" && !state.sessionStartTime["${uId}"]) {
                state.sessionStartTime["${uId}"] = now
            }
            
            def lastExit = state.lastExitTime?."${uId}" ?: 0
            def stitchMillis = getDynamicStitchMillis()
            
            if ((lastExit > 0 && (now - lastExit) < stitchMillis) || cState == "BATHROOM TRIP") {
                def awayMins = ((now - lastExit) / 60000).toInteger()
                if (cState == "BATHROOM TRIP") {
                    state.bathroomTrips["${uId}"] = (state.bathroomTrips["${uId}"] ?: 0) + 1
                    state.bathroomDuration["${uId}"] = (state.bathroomDuration["${uId}"] ?: 0) + awayMins
                    addToHistory("BATHROOM RETURN: ${uName} returned to bed via Mat (Away: ${awayMins}m). Session seamlessly stitched.")
                } else {
                    addToHistory("SESSION STITCHED: ${uName} returned within window via Mat (Away: ${awayMins}m). Continuing previous session.")
                }
                state.sleepState["${uId}"] = "IN BED"
                state.sessionResumedTime["${uId}"] = now 
                updateVirtualSwitch(uId, "on")
            } else {
                state.inBedTime["${uId}"] = now
                state.sessionResumedTime["${uId}"] = 0
                state.movements["${uId}"] = 0
                state.weightedMovementPenalty["${uId}"] = 0.0
                state.asleepTime["${uId}"] = null
                state.bathroomTrips["${uId}"] = 0
                state.bathroomDuration["${uId}"] = 0
                
                if (settings["enableAntiBounce"]) {
                    def abWait = settings.antiBounceWait != null ? settings.antiBounceWait.toInteger() : 3
                    addToHistory("MAT ENTRY: ${uName} triggered pressure mat. Verifying against room activity for ${abWait} minutes...")
                    
                    state.sleepState["${uId}"] = "PENDING ENTRY"
                    state.pendingEntryTime["${uId}"] = now
                    state.pendingRoomMotions["${uId}"] = 0
                    state.pendingAntiBounceWait["${uId}"] = abWait
                    runIn(abWait * 60, "verifyBedEntry", [data: [uId: uId, roomNum: rNum], overwrite: false])
                } else {
                    addToHistory("NEW SESSION: ${uName} entered bed via Mat.")
                    state.sleepState["${uId}"] = "IN BED"
                    updateVirtualSwitch(uId, "on")
                }
            }
            
            state.roomEmptyTime["${rNum}"] = 0 
            if (settings["enableOrchestrator_${rNum}"] && !state.roomGoodNightTriggered["${rNum}"]) {
                runIn(10, "orchestrateRooms", [overwrite: true])
            }
        } else if (cState == "PENDING ENTRY") {
            def pStart = state.pendingEntryTime["${uId}"] ?: now
            def waitSecs = (state.pendingAntiBounceWait["${uId}"] ?: 3) * 60
            
            if (((now - pStart) / 1000) > waitSecs) {
                verifyBedEntry([uId: uId, roomNum: rNum])
            }
        }
        
        if (state.sleepState["${uId}"] == "IN BED") {
            runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
        }
        updateInfoDevice(uId)

        runIn(300, "verifySustainedPressureMat", [data: [uId: uId, roomNum: rNum], overwrite: true])
        
    } else if (evt.value == "open" || evt.value == "not present") { 
        
        // --- NEW: SUSTAINED EMPTY MAT BYPASS ---
        def forceDelay = settings["matExitDelay_${uId}"] != null ? settings["matExitDelay_${uId}"].toInteger() : 10
        runIn(forceDelay * 60, "verifySustainedEmptyMat", [data: [uId: uId, roomNum: rNum], overwrite: true])
        // ----------------------------------------
        
        def rMotion = settings["motionSensor_${rNum}"]?.currentValue("motion")
        if (settings["enableGhostFilter"] && rMotion == "active") {
            logTelemetryEvent(uId, "ghostBlocks")
            addToHistory("GHOST FILTER ACTIVE: External presence detected while Mat opened. Exit sequence pre-emptively locked out.")
        } else {
            state.pendingExit["${uId}"] = now
            def actualThresh = exitBedThreshold != null ? exitBedThreshold.toInteger() : 5
            
            def smartWake = false
            def mlEnabled = (settings["enableML_${uId}"] != false)
            if (mlEnabled) {
                def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                def isWknd = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                def averages = calculateUserAverages(uId)
                def targetAvgOut = isWknd ? averages.avgOutWe : averages.avgOutWd
                
                if (averages.daysLearned >= 14 && targetAvgOut != null) {
                    def nowMins = getMinutesFromNoon(now)
                    if (Math.abs(nowMins - targetAvgOut) <= 60) {
                        actualThresh = Math.max(1, (actualThresh / 2).toInteger())
                        smartWake = true
                    }
                }
            }
            
            if (settings["enableAdvancedStages_${uId}"]) {
                def stage = state.currentSleepStage["${uId}"] ?: "DEEP"
                if (stage == "LIGHT" && isWithinWakeWindow(uId)) {
                    actualThresh = 0 
                    smartWake = true
                    addToHistory("STAGE EXIT: ${uName} exited while in LIGHT sleep during Expected Wake Window. Accelerating exit.")
                } else if (stage == "DEEP") {
                    actualThresh = Math.max(actualThresh, 5) 
                }
            }
            actualThresh = Math.max(1, actualThresh)
            runIn(actualThresh * 60, "evaluateBedExit", [data: [uId: uId, roomNum: rNum, thresh: actualThresh, ai: smartWake], overwrite: true])
        }
    }
}

// --- NEW HARD-BYPASS: MAT EMPTY FORCE EXIT ---
def verifySustainedEmptyMat(data) {
    ensureStateMaps()
    def uId = data.uId
    def rNum = data.roomNum
    def mat = settings["pressureMat_${uId}"]
    if (!mat) return

    def matState = mat.currentValue("contact") ?: mat.currentValue("presence")
    if (matState == "open" || matState == "not present") {
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if (cState != "EMPTY" && cState != "BATHROOM TRIP") {
            addToHistory("SUSTAINED MAT EXIT: ${getUserName(uId)}'s mat has been open for continuous timeout. Bypassing motion checks and forcing bed exit.")
            forceBedExit(uId, rNum, true) // Pass true to retain the exact timestamp the mat originally opened
        }
    }
}

def verifySustainedPressureMat(data) {
    ensureStateMaps()
    def uId = data.uId
    def mat = settings["pressureMat_${uId}"]
    if (!mat) return

    def matState = mat.currentValue("contact") ?: mat.currentValue("presence")
    if (matState == "closed" || matState == "present") {
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if (cState == "EMPTY" || cState == "PENDING ENTRY") {
            addToHistory("SUSTAINED PRESSURE OVERRIDE: ${getUserName(uId)}'s mat has been closed for 5 continuous minutes despite room motion. Forcing IN BED state.")
            state.sleepState["${uId}"] = "IN BED"
            if (!state.sessionStartTime["${uId}"]) state.sessionStartTime["${uId}"] = new Date().time
            state.inBedTime["${uId}"] = state.pendingEntryTime["${uId}"] ?: new Date().time
            state.movements["${uId}"] = 0
            state.weightedMovementPenalty["${uId}"] = 0.0
            state.asleepTime["${uId}"] = null
            updateVirtualSwitch(uId, "on")
            updateInfoDevice(uId)
            runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
        }
    }
}


def vibrationHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def uId = getUserIdFromDevice(evt.device.id, "vibrationSensor")
    if (!uId) return
    
    def now = new Date().time
    def rNum = uId.split('_')[0]
    
    def deafened = state.deafenedUntil?."${uId}" ?: 0
    if (now < deafened) return
    
    def useMat = settings["usePressureMat_${uId}"] && settings["pressureMat_${uId}"]

    if (evt.value == "active") {
        state.lastVibrationTime["${uId}"] = now

        if (useMat) {
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (cState == "SLEEPING" || cState == "IN BED") {
                processUserMovement(uId, rNum)
                
                if (state.pendingExit["${uId}"] > 0) {
                    addToHistory("EDGE OF BED FUSION: ${getUserName(uId)} is off the mat but kinetic movement detected. Assuming roll-to-edge and canceling exit sequence.")
                    state.pendingExit["${uId}"] = 0 
                }
            }
            updateInfoDevice(uId)
            return 
        }

        state.pendingExit["${uId}"] = 0
        state.exitSequenceProgress["${uId}"] = 0 
        
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        
        if (cState == "EMPTY" || cState == "BATHROOM TRIP") {
            if (cState == "EMPTY" && !isTrackingAllowed()) return
            
            if (settings["enableTeleportFilter"] != false && cState == "EMPTY") {
                def lastRoomMot = state.lastRoomMotionTime["${rNum}"] ?: 0
                def teleWindowMillis = (settings["teleportWindow"] != null ? settings["teleportWindow"].toInteger() : 10) * 60000
                if (lastRoomMot == 0 || (now - lastRoomMot) > teleWindowMillis) {
                    log.warn "BMS: Blocked Kinetic Entry for ${getUserName(uId)}. No room motion detected in the last ${(teleWindowMillis/60000).toInteger()} minutes."
                    return 
                }
            }
            
            if (cState == "EMPTY" && !state.sessionStartTime["${uId}"]) {
                state.sessionStartTime["${uId}"] = now
            }
            
            def mlEnabled = (settings["enableML_${uId}"] != false)
            def reqVibs = settings.vibrationsToEnterBed != null ? settings.vibrationsToEnterBed.toInteger() : 1
            if (reqVibs < 2 && mlEnabled) reqVibs = 2 
            def smartEntry = false
            
            if (mlEnabled) {
                def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                def isWknd = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                def averages = calculateUserAverages(uId)
                def targetAvgIn = isWknd ? averages.avgInWe : averages.avgInWd
                
                if (averages.daysLearned >= 14 && targetAvgIn != null) {
                    def nowMins = getMinutesFromNoon(now)
                    if (Math.abs(nowMins - targetAvgIn) <= 60) {
                        reqVibs = Math.max(2, reqVibs - 1) 
                        smartEntry = true
                    }
                }
            }
            
            state.entryVibrationCount["${uId}"] = (state.entryVibrationCount["${uId}"] ?: 0) + 1
            
            if (state.entryVibrationCount["${uId}"] < reqVibs) {
                runIn(600, "clearEntryVibrationCount", [data: [uId: uId], overwrite: true])
                return
            }
            
            state.entryVibrationCount["${uId}"] = 0
            def lastExit = state.lastExitTime?."${uId}" ?: 0
            def stitchMillis = getDynamicStitchMillis()
            def uName = getUserName(uId)
            
            if ((lastExit > 0 && (now - lastExit) < stitchMillis) || cState == "BATHROOM TRIP") {
                def awayMins = ((now - lastExit) / 60000).toInteger()
                if (cState == "BATHROOM TRIP") {
                    state.bathroomTrips["${uId}"] = (state.bathroomTrips["${uId}"] ?: 0) + 1
                    state.bathroomDuration["${uId}"] = (state.bathroomDuration["${uId}"] ?: 0) + awayMins
                    addToHistory("BATHROOM RETURN: ${uName} returned to bed (Away: ${awayMins}m). Session seamlessly stitched.")
                } else {
                    addToHistory("SESSION STITCHED: ${uName} returned within window (Away: ${awayMins}m). Continuing previous session.")
                }
                state.sleepState["${uId}"] = "IN BED"
                state.sessionResumedTime["${uId}"] = now 
                updateVirtualSwitch(uId, "on")
            } else {
                state.inBedTime["${uId}"] = now
                state.sessionResumedTime["${uId}"] = 0
                state.movements["${uId}"] = 0
                state.weightedMovementPenalty["${uId}"] = 0.0
                state.asleepTime["${uId}"] = null
                state.bathroomTrips["${uId}"] = 0
                state.bathroomDuration["${uId}"] = 0
                
                if (settings["enableAntiBounce"]) {
                    def abWait = settings.antiBounceWait != null ? settings.antiBounceWait.toInteger() : 3
                    if (smartEntry) {
                        abWait = 1 
                        addToHistory("🧠 AI PREDICTION: ${uName} activity matches learned bedtime. Fast-tracking Anti-Bounce verification.")
                    } else {
                        addToHistory("SUSTAINED ENTRY VERIFICATION: ${uName} met entry threshold. Verifying against room activity for ${abWait} minutes...")
                    }
                    
                    state.sleepState["${uId}"] = "PENDING ENTRY"
                    state.pendingEntryTime["${uId}"] = now
                    state.pendingRoomMotions["${uId}"] = 0
                    state.pendingAntiBounceWait["${uId}"] = abWait
                    runIn(abWait * 60, "verifyBedEntry", [data: [uId: uId, roomNum: rNum], overwrite: false])
                } else {
                    if (smartEntry) addToHistory("🧠 AI PREDICTION: ${uName} activity matches learned bedtime. Instant Entry applied.")
                    else addToHistory("NEW SESSION: ${uName} entered bed.")
                    state.sleepState["${uId}"] = "IN BED"
                    updateVirtualSwitch(uId, "on")
                }
            }
            
            state.roomEmptyTime["${rNum}"] = 0 
            if (settings["enableOrchestrator_${rNum}"] && !state.roomGoodNightTriggered["${rNum}"]) {
                runIn(10, "orchestrateRooms", [overwrite: true])
            }
        } else if (cState == "PENDING ENTRY") {
            def pStart = state.pendingEntryTime["${uId}"] ?: now
            def waitSecs = (state.pendingAntiBounceWait["${uId}"] ?: 3) * 60
            
            if (((now - pStart) / 1000) > waitSecs) {
                log.warn "BMS: PENDING ENTRY timer was dropped for ${getUserName(uId)}. Forcing verification."
                verifyBedEntry([uId: uId, roomNum: rNum])
            }
        } else if (cState == "SLEEPING") {
            processUserMovement(uId, rNum)
        }
        
        if (state.sleepState["${uId}"] == "IN BED") {
            runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
        }
        updateInfoDevice(uId)
    } else {
        if (useMat) return 

        def rMotion = settings["motionSensor_${rNum}"]?.currentValue("motion")
        if (settings["enableGhostFilter"] && rMotion == "active") {
            logTelemetryEvent(uId, "ghostBlocks")
            addToHistory("GHOST FILTER ACTIVE: External presence detected during ${getUserName(uId)}'s bed movement. Exit sequence pre-emptively locked out.")
        } else {
            state.pendingExit["${uId}"] = now
            
            def mlEnabled = (settings["enableML_${uId}"] != false)
            def actualThresh = exitBedThreshold != null ? exitBedThreshold.toInteger() : 5
            def smartWake = false
            
            if (mlEnabled) {
                def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                def isWknd = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                def averages = calculateUserAverages(uId)
                def targetAvgOut = isWknd ? averages.avgOutWe : averages.avgOutWd
                
                if (averages.daysLearned >= 14 && targetAvgOut != null) {
                    def nowMins = getMinutesFromNoon(now)
                    if (Math.abs(nowMins - targetAvgOut) <= 60) {
                        actualThresh = Math.max(1, (actualThresh / 2).toInteger())
                        smartWake = true
                    }
                }
            }
            
            if (settings["enableAdvancedStages_${uId}"]) {
                def stage = state.currentSleepStage["${uId}"] ?: "DEEP"
                if (stage == "LIGHT" && isWithinWakeWindow(uId)) {
                    actualThresh = 0 
                    smartWake = true
                    addToHistory("STAGE EXIT: ${getUserName(uId)} exited while in LIGHT sleep during Expected Wake Window. Accelerating exit.")
                } else if (stage == "DEEP") {
                    actualThresh = Math.max(actualThresh, 5) 
                }
            }
            actualThresh = Math.max(1, actualThresh)
            runIn(actualThresh * 60, "evaluateBedExit", [data: [uId: uId, roomNum: rNum, thresh: actualThresh, ai: smartWake], overwrite: true])
        }
    }
}

def verifyBedEntry(data) {
    ensureStateMaps()
    def uId = data.uId
    def rNum = data.roomNum
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    
    if (cState == "PENDING ENTRY") {
        def motions = state.pendingRoomMotions["${uId}"] ?: 0
        def maxMotions = settings["antiBounceMaxMotions"] != null ? settings["antiBounceMaxMotions"].toInteger() : 2
        
        if (motions <= maxMotions) {
            addToHistory("VERIFIED ENTRY: Room settled down. Committing ${getUserName(uId)} to IN BED state retroactively.")
            state.sleepState["${uId}"] = "IN BED"
            updateVirtualSwitch(uId, "on")
            runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
        } else {
            addToHistory("ENTRY ABORTED: Excessive room motion detected during Anti-Bounce wait. Assuming ${getUserName(uId)} was awake/active.")
            state.sleepState["${uId}"] = "EMPTY"
            state.inBedTime["${uId}"] = null
        }
        updateInfoDevice(uId)
    }
}

def isSettlingLockActive(uId) {
    if (!settings["enableSettlingLock"]) return false
    def inBed = state.inBedTime?."${uId}" ?: 0
    def resumed = state.sessionResumedTime?."${uId}" ?: 0
    def lockStart = Math.max(inBed as Long, resumed as Long)
    if (lockStart == 0) return false
    def lockMillis = (settings["settlingLockTime"] != null ? settings["settlingLockTime"].toInteger() : 30) * 60000
    def now = new Date().time
    return (now - lockStart) < lockMillis
}

def getDynamicStitchMillis() {
    def baseWindow = (settings["stitchingWindow"] != null ? settings["stitchingWindow"].toInteger() : 15) * 60000
    def deepWindow = (settings["deepSleepStitchWindow"] != null ? settings["deepSleepStitchWindow"].toInteger() : 45) * 60000
    
    if (settings["deepSleepStart"] && settings["deepSleepEnd"]) {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def start = timeToday(settings["deepSleepStart"], tz)
        def end = timeToday(settings["deepSleepEnd"], tz)
        def nowTime = new Date().time
        
        def inDeepSleep = false
        if (start.time > end.time) {
            inDeepSleep = (nowTime >= start.time || nowTime <= end.time)
        } else {
            inDeepSleep = (nowTime >= start.time && nowTime <= end.time)
        }
        return inDeepSleep ? deepWindow : baseWindow
    }
    return baseWindow
}

def evaluateBathroomTimeout(data) {
    ensureStateMaps()
    def uId = data.uId
    def now = new Date().time
    
    if (state.sleepState["${uId}"] == "BATHROOM TRIP") {
        if (isWithinWakeLockout(uId)) {
            addToHistory("WAKE LOCKOUT: Bathroom trip expired for ${getUserName(uId)}, but inside Lockout Window. Extending trip until window ends.")
            def lockoutEnd = getLockoutEndTimeMillis(uId)
            def delaySecs = ((lockoutEnd - now) / 1000).toInteger() + 10
            runIn(delaySecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: data.roomNum], overwrite: true])
            return
        }

        if (settings["enableQuietHouseReturn"] && settings["globalMotionSensors"]) {
            def lastGlobal = state.lastGlobalMotionTime ?: 0
            def quietMins = settings["quietHouseThreshold"] != null ? settings["quietHouseThreshold"].toInteger() : 20
            def quietMillis = quietMins * 60000
            def awayMillis = now - (state.lastExitTime["${uId}"] ?: now)
            
            if ((now - lastGlobal) >= quietMillis) {
                addToHistory("🤫 QUIET HOUSE OVERRIDE: House motion inactive for ${quietMins}m. Assuming ${getUserName(uId)} sneaked back into bed. Auto-stitching session.")
                state.sleepState["${uId}"] = "IN BED"
                state.bathroomTrips["${uId}"] = (state.bathroomTrips["${uId}"] ?: 0) + 1
                state.bathroomDuration["${uId}"] = (state.bathroomDuration["${uId}"] ?: 0) + (awayMillis / 60000).toInteger()
                state.sessionResumedTime["${uId}"] = now
                updateVirtualSwitch(uId, "on")
                updateInfoDevice(uId)
                runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
                return
            } else if (awayMillis < quietMillis) {
                def delaySecs = ((quietMillis - awayMillis) / 1000).toInteger() + 10
                runIn(delaySecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: data.roomNum], overwrite: true])
                return
            }
        }

        addToHistory("BATHROOM TRIP EXPIRED: ${getUserName(uId)} did not return within the stitching window. Assuming terminal wake.")
        state.bathroomTrips["${uId}"] = Math.max(0, (state.bathroomTrips["${uId}"] ?: 1) - 1)
        forceBedExit(uId, data.roomNum, true)
    }
}

def sequenceMotionHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused() || evt.value != "active") return
    def devId = evt.device.id
    def now = new Date().time

    for (int i = 1; i <= (numRooms as Integer); i++) {
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (cState == "EMPTY") continue
            
            if (isSettlingLockActive(uId)) {
                logTelemetryEvent(uId, "settlingLockBlocks")
                continue
            }

            def m1 = settings["wakeMotion1_${uId}"]
            def m2 = settings["wakeMotion2_${uId}"]
            def bPath = settings["bathroomPathMotion_${uId}"]
            def sMotion = settings["showerMotion_${uId}"]

            def pending = state.pendingExit["${uId}"] ?: 0
            def progress = state.exitSequenceProgress["${uId}"] ?: 0

            if (sMotion?.id == devId) {
                def isRecentExit = false
                if (pending > 0 && (now - pending) <= 600000) isRecentExit = true 
                if (cState == "BATHROOM TRIP" && state.lastExitTime["${uId}"] && (now - state.lastExitTime["${uId}"]) <= 600000) isRecentExit = true 
                
                if (isRecentExit) {
                    if (isWithinWakeLockout(uId)) {
                        addToHistory("WAKE LOCKOUT: Shower detected for ${getUserName(uId)}, but inside Lockout Window. Delaying wake confirmation.")
                        if (cState != "BATHROOM TRIP") {
                            state.sleepState["${uId}"] = "BATHROOM TRIP"
                            state.lastExitTime["${uId}"] = pending > 0 ? pending : now
                            updateVirtualSwitch(uId, "off")
                            updateInfoDevice(uId)
                        }
                        def lockoutEnd = getLockoutEndTimeMillis(uId)
                        def delaySecs = ((lockoutEnd - now) / 1000).toInteger() + 10
                        runIn(delaySecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: i], overwrite: true])
                    } else {
                        addToHistory("SHOWER WAKE CONFIRMED: ${getUserName(uId)} entered the shower. Terminal Wake applied.")
                        if (cState == "BATHROOM TRIP") {
                            state.bathroomTrips["${uId}"] = Math.max(0, (state.bathroomTrips["${uId}"] ?: 1) - 1)
                        }
                        forceBedExit(uId, "${i}")
                    }
                    continue
                }
            }

            if (pending > 0 && (now - pending) < 300000) {
                if (progress == 0 && m1?.id == devId) {
                    state.exitSequenceProgress["${uId}"] = 1
                    state.sequenceStep1Time["${uId}"] = now
                    addToHistory("SEQUENCE: ${getUserName(uId)} triggered Step 1 (${m1.displayName}). Monitoring path...")
                    handlePartnerShield(uId, "${i}") 
                }
                else if (progress == 1 && m2?.id == devId) {
                    def step1Time = state.sequenceStep1Time["${uId}"] ?: 0
                    def kineticDelay = (settings["kineticDelaySeconds_${uId}"] ?: 3) * 1000
                    
                    if ((now - step1Time) >= kineticDelay) {
                        state.exitSequenceProgress["${uId}"] = 2
                        
                        if (isWithinWakeLockout(uId)) {
                            addToHistory("WAKE LOCKOUT: Sequence confirmed for ${getUserName(uId)}, but inside Strict Lockout Window. Converting to Bathroom Trip.")
                            state.sleepState["${uId}"] = "BATHROOM TRIP"
                            state.lastExitTime["${uId}"] = now
                            updateVirtualSwitch(uId, "off")
                            updateInfoDevice(uId)
                            def lockoutEnd = getLockoutEndTimeMillis(uId)
                            def delaySecs = ((lockoutEnd - now) / 1000).toInteger() + 10
                            runIn(delaySecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: i], overwrite: true])
                        } else {
                            addToHistory("SEQUENCE CONFIRMED: ${getUserName(uId)} triggered Step 2 (${m2.displayName}). WAKE PATH validated.")
                            forceBedExit(uId, "${i}") 
                        }
                    } else {
                        addToHistory("KINETIC FILTER: Step 2 triggered impossibly fast (${now - step1Time}ms). Rejected as false trigger.")
                    }
                }
                else if (progress == 1 && bPath?.id == devId) {
                    state.exitSequenceProgress["${uId}"] = 3
                    state.lastExitTime["${uId}"] = now 
                    addToHistory("SEQUENCE CONFIRMED: ${getUserName(uId)} triggered Alternative (${bPath.displayName}). BATHROOM TRIP validated.")
                    state.sleepState["${uId}"] = "BATHROOM TRIP"
                    updateInfoDevice(uId)
                    
                    def stitchSecs = (getDynamicStitchMillis() / 1000).toInteger()
                    runIn(stitchSecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: i], overwrite: true])
                }
            }
        }
    }
}

def fallbackBathroomMotionHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused() || evt.value != "active") return
    def rNum = getRoomNumFromDevice(evt.device.id, "bathroomMotion")
    if (!rNum) return
    
    def now = new Date().time
    def users = (rNum == "1") ? 2 : 1
    
    for (int u = 1; u <= users; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        
        if (cState == "IN BED" || cState == "SLEEPING") {
            def pending = state.pendingExit["${uId}"] ?: 0
            if (pending > 0 && !isSettlingLockActive(uId)) {
                state.lastExitTime["${uId}"] = pending 
                state.sleepState["${uId}"] = "BATHROOM TRIP"
                state.pendingExit["${uId}"] = 0
                state.exitSequenceProgress["${uId}"] = 0
                updateVirtualSwitch(uId, "off")
                addToHistory("BATHROOM MOTION DETECTED: ${getUserName(uId)} went straight to the bathroom. Logging trip.")
                updateInfoDevice(uId)
                
                def stitchSecs = (getDynamicStitchMillis() / 1000).toInteger()
                runIn(stitchSecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: rNum], overwrite: true])
            }
        }
    }
}

def fallbackMotionHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused() || evt.value != "active") return
    def rNum = getRoomNumFromDevice(evt.device.id, "motionSensor")
    if (!rNum) return
    
    def now = new Date().time
    state.lastRoomMotionTime["${rNum}"] = now

    def users = (rNum == "1") ? 2 : 1
    for (int u = 1; u <= users; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        
        if (cState == "PENDING ENTRY") {
            state.pendingRoomMotions["${uId}"] = (state.pendingRoomMotions["${uId}"] ?: 0) + 1
            continue
        }
        
        if (cState == "EMPTY" || cState == "BATHROOM TRIP") continue
        
        if (isSettlingLockActive(uId)) {
            logTelemetryEvent(uId, "settlingLockBlocks")
            continue
        }
        
        if (settings["parentalGuard_${uId}"]) {
            logTelemetryEvent(uId, "inBedMotionsIgnored")
            continue
        }

        def useMat = settings["usePressureMat_${uId}"] && settings["pressureMat_${uId}"]
        def matIsClosed = false
        if (useMat) {
            def mState = settings["pressureMat_${uId}"].currentValue("contact") ?: settings["pressureMat_${uId}"].currentValue("presence")
            if (mState == "closed" || mState == "present") matIsClosed = true
        }

        if (matIsClosed && (cState == "IN BED" || cState == "SLEEPING")) {
            processUserMovement(uId, rNum)
            updateInfoDevice(uId)
        }
        
        def lastVib = state.lastVibrationTime["${uId}"] ?: 0
        if ((now - lastVib) <= 90000) { 
            state.lastValidExitMotion["${uId}"] = now
            handlePartnerShield(uId, rNum)
            
            def pending = state.pendingExit["${uId}"] ?: 0
            if (pending > 0 && (now - pending) >= ((exitBedThreshold ?: 5) * 60000)) {
                runIn(1, "evaluateBedExit", [data: [uId: uId, roomNum: rNum], overwrite: true])
            }
        } else {
            logTelemetryEvent(uId, "inBedMotionsIgnored")
        }
    }
}

def clearEntryVibrationCount(data) {
    def uId = data.uId
    if (state.sleepState["${uId}"] == "EMPTY" || state.sleepState["${uId}"] == "BATHROOM TRIP") {
        state.entryVibrationCount["${uId}"] = 0
    }
}

def handlePartnerShield(uId, rNum) {
    def enableShield = settings["enableCrossTalk"] != null ? settings["enableCrossTalk"] : true
    if (!enableShield || rNum != "1") return
    
    def partnerId = (uId == "1_1") ? "1_2" : "1_1"
    def now = new Date().time
    def pState = state.sleepState["${partnerId}"] ?: "EMPTY"
    
    if (pState != "EMPTY") {
        state.deafenedUntil["${partnerId}"] = now + ((crossTalkDeafenTime ?: 60) * 1000)
        if (pState == "SLEEPING") {
            state.movements["${partnerId}"] = Math.max(0, (state.movements["${partnerId}"] ?: 1) - 1)
            logTelemetryEvent(partnerId, "crossTalkAvoided")
        }
    }
}

def forceBedExit(uId, rNum, keepExistingTime = false) {
    ensureStateMaps()
    def now = new Date().time
    
    def exitTime = (keepExistingTime && state.lastExitTime["${uId}"]) ? state.lastExitTime["${uId}"] : now
    state.lastExitTime["${uId}"] = exitTime
    
    def inBed = state.inBedTime["${uId}"] ?: exitTime
    def inBedMins = ((exitTime - inBed) / 60000).toInteger()
    state.lastSessionInBed["${uId}"] = inBedMins
    
    if (state.asleepTime["${uId}"]) {
        state.lastSessionAsleep["${uId}"] = ((exitTime - state.asleepTime["${uId}"]) / 60000).toInteger()
        
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if (cState == "SLEEPING") {
             def stillStart = state.lastStillStartTime["${uId}"] ?: state.asleepTime["${uId}"] ?: exitTime
             def gap = exitTime - stillStart
             if (gap >= 2700000) {
                 state.deepSleepDuration["${uId}"] = (state.deepSleepDuration["${uId}"] ?: 0) + (gap / 60000).toInteger()
             }
        }
    }
    
    def sleepScore = calculateEfficiencyScore(uId)
    addToHistory("BED EXIT: ${getUserName(uId)} verified out of bed. Session Duration: ${formatDuration(inBedMins)}. Sleep Score: ${sleepScore}%")
    
    state.sleepState["${uId}"] = "EMPTY"
    state.pendingExit["${uId}"] = 0
    state.exitSequenceProgress["${uId}"] = 0
    updateVirtualSwitch(uId, "off")
    updateInfoDevice(uId)
    checkRoomEmptyStatus(rNum)
}

def evaluateBedExit(data) {
    ensureStateMaps()
    def uId = data.uId
    def rNum = data.roomNum
    def thresh = data.thresh != null ? data.thresh.toInteger() : (exitBedThreshold != null ? exitBedThreshold.toInteger() : 5)
    def ai = data.ai ?: false
    
    def now = new Date().time
    def pending = state.pendingExit["${uId}"] ?: 0
    def lastValid = state.lastValidExitMotion["${uId}"] ?: 0
    def progress = state.exitSequenceProgress["${uId}"] ?: 0
    
    if (state.sleepState["${uId}"] == "EMPTY") return

    if (isWithinWakeLockout(uId)) {
        if (pending > 0 && (now - pending) >= (thresh * 60000)) {
            if (state.sleepState["${uId}"] != "BATHROOM TRIP") {
                addToHistory("WAKE LOCKOUT: Wake condition met for ${getUserName(uId)}, but inside Strict Lockout Window. Converting to Bathroom Trip.")
                state.sleepState["${uId}"] = "BATHROOM TRIP"
                state.lastExitTime["${uId}"] = pending > 0 ? pending : now
                updateVirtualSwitch(uId, "off")
                updateInfoDevice(uId)

                def lockoutEnd = getLockoutEndTimeMillis(uId)
                def delaySecs = ((lockoutEnd - now) / 1000).toInteger() + 10
                runIn(delaySecs, "evaluateBathroomTimeout", [data: [uId: uId, roomNum: rNum], overwrite: true])
            }
        }
        return
    }

    if (pending > 0 && (now - pending) >= (thresh * 60000)) {
        if (lastValid > pending) {
            if (ai) addToHistory("🧠 AI PREDICTION: ${getUserName(uId)} learned wake pattern detected. Wake sequence accelerated.")
            forceBedExit(uId, rNum)
        } else if (progress == 1 && isWithinWakeWindow(uId)) {
            addToHistory("WINDOW EXIT: ${getUserName(uId)} triggered Step 1 inside Expected Wake Window. Assuming true wake.")
            forceBedExit(uId, rNum)
        }
    }
}

def isWithinWakeWindow(uId) {
    def tz = location.timeZone ?: TimeZone.getDefault()
    def cal = Calendar.getInstance(tz)
    def isWeekend = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    
    def wStart = isWeekend ? settings["weekendWakeStart_${uId}"] : settings["weekdayWakeStart_${uId}"]
    def wEnd = isWeekend ? settings["weekendWakeEnd_${uId}"] : settings["weekdayWakeEnd_${uId}"]

    if (!wStart || !wEnd) return false
    
    def start = timeToday(wStart, tz)
    def end = timeToday(wEnd, tz)
    def nowTime = new Date().time
    
    if (start.time > end.time) {
        return (nowTime >= start.time || nowTime <= end.time)
    } else {
        return (nowTime >= start.time && nowTime <= end.time)
    }
}

def orchestrateRooms() {
    ensureStateMaps()
    if (isSystemPaused()) return
    def now = new Date().time
    def watchdogMillis = (sensorWatchdogHours ?: 48) * 3600000

    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (!settings["enableOrchestrator_${i}"]) continue
        def gnSwitch = settings["goodNightSwitch_${i}"]
        
        def numUsers = (i == 1) ? 2 : 1
        def totalActiveUsers = 0
        def totalUsersReady = 0

        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            if (settings["vibrationSensor_${uId}"] || settings["vibrationSensor2_${uId}"] || settings["pressureMat_${uId}"]) {
                def lastVib = state.lastVibrationTime?."${uId}" ?: 0
                if (lastVib > 0 && (now - lastVib) > watchdogMillis) continue 
                
                totalActiveUsers++
                def cState = state.sleepState["${uId}"] ?: "EMPTY"
                def inBedTime = state.inBedTime?."${uId}" ?: 0
                
                if (cState == "BATHROOM TRIP") {
                    if (settings["enableQuietHouseReturn"] && settings["globalMotionSensors"]) {
                        def lastGlobal = state.lastGlobalMotionTime ?: 0
                        def quietMins = settings["quietHouseThreshold"] != null ? settings["quietHouseThreshold"].toInteger() : 20
                        def quietMillis = quietMins * 60000
                        if ((now - lastGlobal) >= quietMillis) {
                            def exitTime = state.lastExitTime["${uId}"] ?: now
                            if ((now - exitTime) >= quietMillis) {
                                addToHistory("🤫 QUIET HOUSE OVERRIDE (Watchdog): House motion inactive for ${quietMins}m. Rescuing stuck bathroom trip for ${getUserName(uId)}.")
                                state.sleepState["${uId}"] = "IN BED"
                                state.bathroomTrips["${uId}"] = (state.bathroomTrips["${uId}"] ?: 0) + 1
                                state.bathroomDuration["${uId}"] = (state.bathroomDuration["${uId}"] ?: 0) + ((now - exitTime) / 60000).toInteger()
                                state.sessionResumedTime["${uId}"] = now
                                updateVirtualSwitch(uId, "on")
                                updateInfoDevice(uId)
                                runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
                            }
                        }
                    }
                }

                if (gnSwitch && !state.roomGoodNightTriggered["${i}"] && gnSwitch.currentValue("switch") != "on") {
                    def requiredMillis = (settings["goodNightDelay_${i}"] ?: 30) * 60000
                    if (cState == "IN BED" || cState == "SLEEPING") {
                        if (inBedTime > 0 && (now - inBedTime) >= requiredMillis) totalUsersReady++
                    }
                }
            }
        }

        if (gnSwitch && !state.roomGoodNightTriggered["${i}"] && gnSwitch.currentValue("switch") != "on") {
            if (totalActiveUsers > 0 && totalUsersReady >= totalActiveUsers) {
                addToHistory("ROOM ORCHESTRATOR: All active users in ${getRoomName(i)} met In-Bed threshold. Engaging Good Night Switch.")
                state.roomGoodNightTriggered["${i}"] = true
                gnSwitch.on()
            }
        }
    }
}

def goodNightOnHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def now = new Date().time

    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            state.roomEmptyTime["${i}"] = 0 
            state.roomGoodNightTriggered["${i}"] = true
            def numUsers = (i == 1) ? 2 : 1
            
            for (int u = 1; u <= numUsers; u++) {
                def uId = "${i}_${u}"
                def cState = state.sleepState["${uId}"] ?: "EMPTY"

                if (cState == "EMPTY" || cState == "PENDING ENTRY") {
                    addToHistory("GOOD NIGHT OVERRIDE: Forcing ${getUserName(uId)} to IN BED.")
                    state.sleepState["${uId}"] = "IN BED"
                    if (!state.sessionStartTime["${uId}"]) state.sessionStartTime["${uId}"] = now
                    state.inBedTime["${uId}"] = now
                    state.movements["${uId}"] = 0
                    state.weightedMovementPenalty["${uId}"] = 0.0
                    state.asleepTime["${uId}"] = null
                    state.lastVibrationTime["${uId}"] = now
                    state.pendingExit["${uId}"] = now 
                    
                    updateVirtualSwitch(uId, "on")
                    updateInfoDevice(uId)
                    runIn((fallAsleepThreshold ?: 15) * 60, "evaluateSleepState", [data: [uId: uId], overwrite: true])
                }
            }
            if (settings["enableOrchestrator_${i}"]) runIn(10, "orchestrateRooms", [overwrite: true])
        }
    }
}

def goodNightOffHandler(evt) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def deviceId = evt.device.id
    def now = new Date().time

    for (int i = 1; i <= (numRooms as Integer); i++) {
        if (settings["goodNightSwitch_${i}"]?.id == deviceId) {
            state.roomGoodNightTriggered["${i}"] = false
            def numUsers = (i == 1) ? 2 : 1
            def externalOverrideTriggered = false
            
            for (int u = 1; u <= numUsers; u++) {
                def uId = "${i}_${u}"
                def cState = state.sleepState["${uId}"] ?: "EMPTY"

                if (cState != "EMPTY") {
                    externalOverrideTriggered = true
                    forceBedExit(uId, "${i}")
                }
            }
            
            if (externalOverrideTriggered) {
                state.roomEmptyTime["${i}"] = now
            }
        }
    }
}

def isTrackingAllowed() {
    if (isSystemPaused()) return false
    
    def modeAllowed = true
    if (activeSleepModes && !activeSleepModes.contains(location.mode)) modeAllowed = false
    
    def timeAllowed = true
    if (sleepStartTime && sleepEndTime) {
        def tz = location.timeZone ?: TimeZone.getDefault()
        def start = timeToday(sleepStartTime, tz)
        def end = timeToday(sleepEndTime, tz)
        def now = new Date()
        
        if (start.time > end.time) {
            timeAllowed = (now.time >= start.time || now.time <= end.time)
        } else {
            timeAllowed = (now.time >= start.time && now.time <= end.time)
        }
    }
    
    if (activeSleepModes && !modeAllowed) return false
    if (sleepStartTime && sleepEndTime && !timeAllowed) return false
    return true
}

def evaluateSleepState(data) {
    ensureStateMaps()
    def uId = data.uId
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    
    if (cState == "IN BED") {
        def now = new Date().time
        def lastVib = state.lastVibrationTime["${uId}"] ?: 0
        if ((now - lastVib) >= ((fallAsleepThreshold ?: 15) * 60000)) {
            
            def inBed = state.inBedTime["${uId}"] ?: now
            def actualSleepTime = (lastVib > inBed) ? lastVib : now
            
            state.sleepState["${uId}"] = "SLEEPING"
            state.asleepTime["${uId}"] = actualSleepTime
            state.lastStillStartTime["${uId}"] = actualSleepTime
            state.sleepLatency["${uId}"] = ((actualSleepTime - inBed) / 60000).toInteger()
            
            addToHistory("SLEEP DETECTED: ${getUserName(uId)} marking as SLEEPING retroactively to ${formatTimestamp(actualSleepTime)}. (Latency: ${state.sleepLatency["${uId}"]}m)")
            
            state.envStats["${uId}"] = [tSum: 0.0, tCnt: 0, hSum: 0.0, hCnt: 0]
            def rNum = uId.split('_')[0]
            def tSens = settings["tempSensor_${rNum}"]
            def hSens = settings["humidSensor_${rNum}"]
            
            if (tSens && tSens.currentValue("temperature") != null) {
                state.envStats["${uId}"].tSum += (tSens.currentValue("temperature") as Double)
                state.envStats["${uId}"].tCnt += 1
            }
            if (hSens && hSens.currentValue("humidity") != null) {
                state.envStats["${uId}"].hSum += (hSens.currentValue("humidity") as Double)
                state.envStats["${uId}"].hCnt += 1
            }

            updateInfoDevice(uId)
        }
    }
}

def checkRoomEmptyStatus(rNum) {
    ensureStateMaps()
    def numUsers = (rNum == "1" || rNum == 1) ? 2 : 1
    def roomIsVacantForWake = true
    
    for (int u = 1; u <= numUsers; u++) {
        def uId = "${rNum}_${u}"
        def cState = state.sleepState["${uId}"] ?: "EMPTY"
        if ((settings["vibrationSensor_${uId}"] || settings["vibrationSensor2_${uId}"] || settings["pressureMat_${uId}"]) && cState != "EMPTY") {
            roomIsVacantForWake = false
            break
        }
    }

    if (roomIsVacantForWake) {
        state.roomEmptyTime["${rNum}"] = new Date().time
        def wakeMins = settings["wakeDelay_${rNum}"] != null ? settings["wakeDelay_${rNum}"].toInteger() : 45
        addToHistory("ROOM EMPTY: ${getRoomName(rNum)} vacant. Starting ${wakeMins}m Wake Confirmation buffer.")
        runIn(wakeMins * 60, "evaluateRoomWake", [data: [roomNum: rNum], overwrite: true])
    }
}

def evaluateRoomWake(data) {
    ensureStateMaps()
    if (isSystemPaused()) return
    def rNum = data.roomNum
    def emptyTime = state.roomEmptyTime["${rNum}"] ?: 0

    if (emptyTime > 0) {
        def numUsers = (rNum == "1" || rNum == 1) ? 2 : 1
        def roomIsVacantForWake = true
        
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${rNum}_${u}"
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if ((settings["vibrationSensor_${uId}"] || settings["vibrationSensor2_${uId}"] || settings["pressureMat_${uId}"]) && cState != "EMPTY") {
                roomIsVacantForWake = false
                break
            }
        }

        if (roomIsVacantForWake) {
            def gnSwitch = settings["goodNightSwitch_${rNum}"]
            if (gnSwitch && gnSwitch.currentValue("switch") == "on" && settings["enableOrchestrator_${rNum}"]) {
                gnSwitch.off()
                addToHistory("WAKE CONFIRMED: ${getRoomName(rNum)} remained empty for the timeout period. Turning OFF Good Night switch.")
            } else {
                addToHistory("WAKE CONFIRMED: ${getRoomName(rNum)} remained empty for the timeout period.")
            }
            state.roomGoodNightTriggered["${rNum}"] = false
        }
    }
}

// --- BMS MATH & SCORING ---
def calculateEfficiencyScore(uId) {
    ensureStateMaps()
    def cState = state.sleepState["${uId}"] ?: "EMPTY"
    def inBed = cState == "EMPTY" ? (state.lastSessionInBed["${uId}"] ?: 0) : ((new Date().time - (state.inBedTime?."${uId}" ?: new Date().time)) / 60000)
    def asleep = cState == "EMPTY" ? (state.lastSessionAsleep["${uId}"] ?: 0) : ((new Date().time - (state.asleepTime?."${uId}" ?: new Date().time)) / 60000)
    
    if (!inBed || inBed < 30) return 0
    
    def moves = state.movements?."${uId}" ?: 0
    def rawPenalty = state.weightedMovementPenalty?."${uId}" != null ? state.weightedMovementPenalty["${uId}"].toDouble() : (moves * 0.25)
    
    // Backward compatibility fix for the 2.0 penalty math explosion
    if (rawPenalty > moves) rawPenalty = moves * 0.25 
    def movementPenalty = Math.min(30.0, rawPenalty)
    
    def efficiency = (asleep / inBed) * 100.0
    
    def baseScore = efficiency - movementPenalty
    def finalScore = baseScore

    def clinicalEnabled = settings["enableClinicalScoring_${uId}"]
    
    if (clinicalEnabled) {
        // 1. Duration Score
        def targetHours = settings["targetSleepHours_${uId}"] != null ? settings["targetSleepHours_${uId}"].toDouble() : 7.5
        def targetMins = targetHours * 60
        def durationRatio = Math.min(1.0, asleep / targetMins)
        // 50% Efficiency/Restlessness, 50% Duration Goal
        finalScore = (baseScore * 0.5) + ((durationRatio * 100.0) * 0.5)

        // 2. Latency Modifiers
        def latency = state.sleepLatency["${uId}"] ?: 0
        if (latency > 0 && latency < 5) finalScore -= 5.0
        else if (latency >= 10 && latency <= 20) finalScore += 5.0
        else if (latency >= 30 && latency < 45) finalScore -= 5.0
        else if (latency >= 45) finalScore -= 10.0

        // 3. Regularity Modifier (Social Jet Lag)
        def averages = calculateUserAverages(uId)
        def mlEnabled = (settings["enableML_${uId}"] != false)
        if (mlEnabled && averages.daysLearned >= 14) {
            def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
            def isWknd = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            def targetAvgIn = isWknd ? averages.avgInWe : averages.avgInWd
            if (targetAvgIn != null && state.sessionStartTime["${uId}"]) {
                def startMins = getMinutesFromNoon(state.sessionStartTime["${uId}"])
                def diff = Math.abs(startMins - targetAvgIn)
                if (diff > 60 && diff <= 120) finalScore -= 5.0
                else if (diff > 120) finalScore -= 10.0
            }
        }
    }

    return Math.max(0, Math.min(100, Math.round(finalScore).toInteger()))
}

def updateInfoDevice(uId) {
    ensureStateMaps()
    def dni = "ASM_INFO_${uId}"
    def dev = getChildDevice(dni)
    if (!dev) return
    
    def score = calculateEfficiencyScore(uId)
    def status = state.sleepState["${uId}"] ?: "EMPTY"
    
    dev.sendEvent(name: "status", value: status)
    dev.sendEvent(name: "sleepScore", value: score)
    dev.sendEvent(name: "html", value: generateHtmlTile(uId))
}

def generateHtmlTile(uId) {
    ensureStateMaps()
    def uName = getUserName(uId)
    def status = state.sleepState?."${uId}" ?: "EMPTY"
    def score = calculateEfficiencyScore(uId)
    def moves = state.movements?."${uId}" ?: 0
    def trips = state.bathroomTrips?."${uId}" ?: 0
    def tripMins = state.bathroomDuration?."${uId}" ?: 0

    // Fetch Hardware Statuses for Tile
    def vSens = settings["vibrationSensor_${uId}"]
    def pMat = settings["pressureMat_${uId}"]
    def vSensState = vSens ? (vSens.currentValue("acceleration") ?: "inactive") : "none"
    def pMatState = pMat ? (pMat.currentValue("contact") ?: pMat.currentValue("presence") ?: "open") : "none"
    def vIcon = vSensState == "active" ? "<span style='color:#e74c3c'>●</span>" : "<span style='color:#555'>●</span>"
    def pIcon = (pMatState == "closed" || pMatState == "present") ? "<span style='color:#2980b9'>●</span>" : "<span style='color:#555'>●</span>"

    def color = status == "SLEEPING" ? "#00aaff" : (status == "IN BED" ? "#9b59b6" : (status == "PENDING ENTRY" ? "#1abc9c" : (status == "BATHROOM TRIP" ? "#e67e22" : "#2ecc71")))
    def scoreColor = score >= 85 ? "#2ecc71" : (score >= 70 ? "#f39c12" : "#e74c3c")

    def inBedTimeStr = state.inBedTime?."${uId}" ? formatTimestamp(state.inBedTime."${uId}") : "--:--"
    def exitTimeStr = state.lastExitTime?."${uId}" ? formatTimestamp(state.lastExitTime."${uId}") : "--:--"

    def liveInBed = 0
    def liveAsleep = 0
    def now = new Date().time

    if (status == "IN BED" || status == "SLEEPING" || status == "PENDING ENTRY" || status == "BATHROOM TRIP") {
        if (state.inBedTime?."${uId}") liveInBed = ((now - state.inBedTime."${uId}") / 60000).toInteger()
        if (state.asleepTime?."${uId}") liveAsleep = ((now - state.asleepTime."${uId}") / 60000).toInteger()
    } else {
        liveInBed = state.lastSessionInBed?."${uId}" ?: 0
        liveAsleep = state.lastSessionAsleep?."${uId}" ?: 0
    }

    def deepSleep = state.deepSleepDuration?."${uId}" ?: 0
    if (status == "SLEEPING") {
         def stillStart = state.lastStillStartTime["${uId}"] ?: state.asleepTime["${uId}"] ?: now
         def gap = now - stillStart
         if (gap >= 2700000) deepSleep += (gap / 60000).toInteger()
    }

    def lightSleep = Math.max(0, liveAsleep - deepSleep)
    def awakeTime = Math.max(0, liveInBed - liveAsleep)

    def totalTime = liveInBed > 0 ? liveInBed : 1
    def pDeep = Math.min(100, ((deepSleep / totalTime) * 100) as Integer)
    def pLight = Math.min(100, ((lightSleep / totalTime) * 100) as Integer)
    def pAwake = Math.min(100, ((awakeTime / totalTime) * 100) as Integer)
    
    if ((pDeep + pLight + pAwake) == 0) pAwake = 100
    def dEnd = ((pDeep / 100.0) * 360) as Integer
    def lEnd = dEnd + (((pLight / 100.0) * 360) as Integer)
    
    def cDeep = "#2980b9"
    def cLight = "#3498db"
    def cAwake = "#e67e22"

    def eff = 0
    if (totalTime >= 30 && liveAsleep > 0) eff = Math.min(100, ((liveAsleep / totalTime) * 100) as Integer)
    
    def rawPen = state.weightedMovementPenalty?."${uId}" != null ? state.weightedMovementPenalty["${uId}"] : (moves * 0.25)
    if (rawPen > moves) rawPen = moves * 0.25 
    def movPenFmt = Math.min(30.0, rawPen as Double)

    def html = """
    <div style='background:#111;color:#ddd;padding:8px;border-radius:8px;font-family:sans-serif'>
        <div style='display:flex;justify-content:space-between;border-bottom:1px solid #333;padding-bottom:4px;margin-bottom:8px'>
            <b style='font-size:14px;color:#fff'>${uName}</b>
            <b style='color:${color};font-size:10px'>${status}</b>
        </div>
        <div style='display:flex;justify-content:space-evenly;align-items:center;margin-bottom:8px'>
            <div style='width:50px;height:50px;border-radius:50%;background:conic-gradient(${scoreColor} ${score*3.6}deg,#222 0);display:flex;align-items:center;justify-content:center'>
                <div style='width:40px;height:40px;background:#111;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:15px;font-weight:bold;color:${scoreColor}'>${score}</div>
            </div>
            <div style='display:flex;align-items:center;gap:6px'>
                <div style='width:40px;height:40px;border-radius:50%;background:conic-gradient(${cDeep} 0 ${dEnd}deg,${cLight} 0 ${lEnd}deg,${cAwake} 0)'></div>
                <div style='font-size:9px;line-height:1.2'>
                    <div style='color:${cDeep}'>■ <span style='color:#bbb'>${formatDuration(deepSleep)}</span></div>
                    <div style='color:${cLight}'>■ <span style='color:#bbb'>${formatDuration(lightSleep)}</span></div>
                    <div style='color:${cAwake}'>■ <span style='color:#bbb'>${formatDuration(awakeTime)}</span></div>
                </div>
            </div>
        </div>
        <div style='display:flex;height:6px;border-radius:3px;overflow:hidden;margin-bottom:6px'>
            <div style='width:${pDeep}%;background:${cDeep}'></div><div style='width:${pLight}%;background:${cLight}'></div><div style='width:${pAwake}%;background:${cAwake}'></div>
        </div>
        <div style='font-size:9px;color:#888;background:#1a1a1a;padding:4px;border-radius:4px;margin-bottom:6px;display:flex;justify-content:space-between'>
            <span><b style='color:#ccc'>Insight:</b> ${eff}% eff. ${moves} moves</span>
            <span>Vib:${vIcon} Mat:${pIcon}</span>
        </div>
        <div style='display:flex;justify-content:space-between;font-size:10px;text-align:center'>
            <div style='background:#1a1a1a;padding:4px;border-radius:4px;flex:1;margin-right:2px'>In/Out<br><b style='color:#ccc'>${inBedTimeStr}-${exitTimeStr}</b></div>
            <div style='background:#1a1a1a;padding:4px;border-radius:4px;flex:1;margin-left:2px'>Trips<br><b style='color:#ccc'>${trips}x (${tripMins}m)</b></div>
        </div>
    </div>
    """
    
    return html.replaceAll(/(?m)^\s+/, "").replaceAll(/\n/, "").replaceAll(/>\s+</, "><")
}

def appButtonHandler(btn) {
    ensureStateMaps()
    def parts = btn.split("_")
    
    if (btn.startsWith("btnCreateInfo_")) {
        def rNum = parts[1]
        def uNum = parts[2]
        def uId = "${rNum}_${uNum}"
        def dni = "ASM_INFO_${uId}"
        def name = "Sleep Info - ${getUserName(uId)}"
        addChildDevice("ShaneAllen", "ASM Dashboard Device", dni, null, [name: name, label: name])
        updateInfoDevice(uId)
    } else if (btn.startsWith("btnCreateSwitch_")) {
        def rNum = parts[1]
        def uNum = parts[2]
        def uId = "${rNum}_${uNum}"
        def dni = "ASM_SW_${uId}"
        def name = "(Virtual) ${getUserName(uId)} In Bed"
        addChildDevice("hubitat", "Virtual Switch", dni, null, [name: name, label: name])
    } else if (btn.startsWith("btnClearAI_")) {
        def rNum = parts[1]
        def uNum = parts[2]
        def uId = "${rNum}_${uNum}"
        state.dailyStats["${uId}"] = []
        addToHistory("AI Reset: Cleared learned data for ${getUserName(uId)}")
    } else if (btn == "btnForceReset") {
        forceResetAllBeds()
    } else if (btn == "btnRefresh") {
        log.info "BMS Refresh Triggered."
    }
}

def logTelemetryEvent(uId, eventType) {
    if (!enableTelemetryTracking) return
    ensureStateMaps()
    if (!state.telemetry["${uId}"]) {
        state.telemetry["${uId}"] = [today: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0], overall: [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0]]
    }
    state.telemetry["${uId}"].today[eventType] = (state.telemetry["${uId}"].today[eventType] ?: 0) + 1
    state.telemetry["${uId}"].overall[eventType] = (state.telemetry["${uId}"].overall[eventType] ?: 0) + 1
}

def forceResetAllBeds() {
    ensureStateMaps()
    def numR = numRooms ? numRooms.toInteger() : 1
    for (int i = 1; i <= numR; i++) {
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            state.sleepState["${uId}"] = "EMPTY"
            state.inBedTime["${uId}"] = null
            state.asleepTime["${uId}"] = null
            state.pendingExit["${uId}"] = 0
            state.movements["${uId}"] = 0
            state.lastValidExitMotion["${uId}"] = 0
            state.bathroomTrips["${uId}"] = 0
            state.bathroomDuration["${uId}"] = 0
            state.entryVibrationCount["${uId}"] = 0
            state.exitSequenceProgress["${uId}"] = 0
            state.pendingEntryTime["${uId}"] = 0
            state.pendingRoomMotions["${uId}"] = 0
            state.pendingAntiBounceWait["${uId}"] = 0
            state.sessionResumedTime["${uId}"] = 0
            state.sessionStartTime["${uId}"] = null
            state.envStats["${uId}"] = [tSum: 0.0, tCnt: 0, hSum: 0.0, hCnt: 0]
            state.deepSleepDuration["${uId}"] = 0
            state.lastStillStartTime["${uId}"] = 0
            state.sleepLatency["${uId}"] = 0
            state.weightedMovementPenalty["${uId}"] = 0.0
            state.ewmaMovement["${uId}"] = 0.0
            state.lastMoveTimeForEwma["${uId}"] = 0
            state.currentSleepStage["${uId}"] = "DEEP"
            state.smartAlarmTriggeredDate["${uId}"] = ""
            updateVirtualSwitch(uId, "off")
            updateInfoDevice(uId)
        }
        state.roomEmptyTime["${i}"] = 0
        state.roomGoodNightTriggered["${i}"] = false
        state.lastEnvDisturbanceTime["${i}"] = 0
        state.lastEnvDisturbanceType["${i}"] = ""
    }
    addToHistory("SYSTEM: Forced all beds to EMPTY via manual button override.")
}

def middayReset() {
    ensureStateMaps()
    state.lastResetTime = new Date().format("MM/dd HH:mm", location.timeZone)
    
    for (int i = 1; i <= 3; i++) {
        state.roomEmptyTime["${i}"] = 0
        state.roomGoodNightTriggered["${i}"] = false
        state.lastEnvDisturbanceTime["${i}"] = 0
        state.lastEnvDisturbanceType["${i}"] = ""
        
        def numUsers = (i == 1) ? 2 : 1
        for (int u = 1; u <= numUsers; u++) {
            def uId = "${i}_${u}"
            if (state.telemetry?."${uId}"?.today) {
                state.telemetry["${uId}"].today = [vibrations: 0, falseExits: 0, crossTalkAvoided: 0, inBedMotionsIgnored: 0, ghostBlocks: 0, settlingLockBlocks: 0]
            }
            
            def avgT = null
            def avgH = null
            if (state.envStats?."${uId}"?.tCnt > 0) avgT = Math.round((state.envStats["${uId}"].tSum / state.envStats["${uId}"].tCnt) * 10) / 10.0
            if (state.envStats?."${uId}"?.hCnt > 0) avgH = Math.round((state.envStats["${uId}"].hSum / state.envStats["${uId}"].hCnt) * 10) / 10.0

            def mlEnabled = (settings["enableML_${uId}"] != false)
            if (mlEnabled) {
                def inBed = state.sessionStartTime["${uId}"]
                def outBed = state.lastExitTime["${uId}"]
                if (inBed && outBed) {
                    def cal = Calendar.getInstance(location.timeZone ?: TimeZone.getDefault())
                    def isWknd = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                    
                    def sessionData = [
                        inBedMins: getMinutesFromNoon(inBed),
                        outBedMins: getMinutesFromNoon(outBed),
                        score: calculateEfficiencyScore(uId),
                        trips: state.bathroomTrips["${uId}"] ?: 0,
                        isWeekend: isWknd,
                        avgTemp: avgT,
                        avgHumid: avgH
                    ]
                    if (state.dailyStats["${uId}"] == null) state.dailyStats["${uId}"] = []
                    state.dailyStats["${uId}"].add(sessionData)
                    while (state.dailyStats["${uId}"].size() > 14) {
                        state.dailyStats["${uId}"].remove(0)
                    }
                }
            }
            
            def cState = state.sleepState["${uId}"] ?: "EMPTY"
            if (cState == "EMPTY" || cState == "BATHROOM TRIP") {
                state.inBedTime["${uId}"] = null
                state.asleepTime["${uId}"] = null
                state.movements["${uId}"] = 0
                state.deafenedUntil["${uId}"] = 0
                state.lastValidExitMotion["${uId}"] = 0
                state.bathroomTrips["${uId}"] = 0
                state.bathroomDuration["${uId}"] = 0
                state.entryVibrationCount["${uId}"] = 0
                state.exitSequenceProgress["${uId}"] = 0 
                state.pendingEntryTime["${uId}"] = 0
                state.pendingRoomMotions["${uId}"] = 0
                state.pendingAntiBounceWait["${uId}"] = 0
                state.sessionResumedTime["${uId}"] = 0
                state.sessionStartTime["${uId}"] = null
                state.envStats["${uId}"] = [tSum: 0.0, tCnt: 0, hSum: 0.0, hCnt: 0]
                state.deepSleepDuration["${uId}"] = 0
                state.lastStillStartTime["${uId}"] = 0
                state.sleepLatency["${uId}"] = 0
                state.weightedMovementPenalty["${uId}"] = 0.0
                state.ewmaMovement["${uId}"] = 0.0
                state.lastMoveTimeForEwma["${uId}"] = 0
                state.currentSleepStage["${uId}"] = "DEEP"
                updateVirtualSwitch(uId, "off") 
                state.sleepState["${uId}"] = "EMPTY" 
                updateInfoDevice(uId)
            }
        }
        if (settings["goodNightSwitch_${i}"]?.currentValue("switch") == "on" && settings["enableOrchestrator_${i}"]) {
            settings["goodNightSwitch_${i}"].off()
        }
    }
    
    addToHistory("SYSTEM: Midday Reset completed. True Session boundaries logged to AI. Cleared daily ledgers.")
}

def getUserIdFromDevice(id, type = "vibrationSensor") {
    for (int i = 1; i <= 3; i++) {
        for (int u = 1; u <= 2; u++) {
            if (type == "vibrationSensor") {
                if (settings["vibrationSensor_${i}_${u}"]?.id == id) return "${i}_${u}"
                if (settings["vibrationSensor2_${i}_${u}"]?.id == id) return "${i}_${u}"
            } else if (type == "pressureMat") {
                if (settings["pressureMat_${i}_${u}"]?.id == id) return "${i}_${u}"
            }
        }
    }
    return null
}

def getRoomNumFromDevice(id, type) {
    for (int i = 1; i <= 3; i++) {
        if (type == "tempSensor" || type == "humidSensor") {
            if (settings["${type}_${i}"]?.id == id) return "${i}"
        } else {
            if (settings["${type}_${i}"]?.id == id) return "${i}"
        }
    }
    return null
}

def getUserName(uId) { return settings["userName_${uId}"] ?: "User" }
def getRoomName(rNum) { return settings["roomName_${rNum}"] ?: "Room ${rNum}" }
def isSystemPaused() { return masterEnableSwitch?.currentValue("switch") == "off" }
def formatTimestamp(ms) { return ms ? new Date(ms as Long).format("h:mm a", location.timeZone) : "--:--" }
def formatDuration(m) { return m >= 60 ? "${(m/60).toInteger()}h ${m%60}m" : "${m}m" }

def updateVirtualSwitch(uId, val) {
    def dev = getChildDevice("ASM_SW_${uId}")
    if (dev) { if (val == "on") dev.on() else dev.off() }
}

def addToHistory(String msg) {
    ensureStateMaps()
    def timestamp = new Date().format("MM/dd HH:mm:ss", location.timeZone)
    state.historyLog.add(0, "[${timestamp}] ${msg}")
    if (state.historyLog.size() > 30) state.historyLog = state.historyLog.take(30)
    log.info "SLEEP HISTORY: [${timestamp}] ${msg}"
}
