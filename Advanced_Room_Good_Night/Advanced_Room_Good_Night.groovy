/**
 * Advanced Room Good Night
 */
definition(
    name: "Advanced Room Good Night",
    namespace: "ShaneAllen",
    author: "ShaneAllen",
    description: "Ultimate Good Night controller with Live Sleep Dashboard, Variable Speed Ceiling Fans, Power-Failure Recovery, Periodic State Enforcement, Hourly Fan Wiggle, Delayed Shut-Offs, Separate Light/Audio Fading, Emergency Life-Safety Override, and Command History.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "<b>Advanced Room Good Night</b>", install: true, uninstall: true) {
        
        section("<b>Live System Dashboard</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a real-time, top-down view of your home's sleep status, blocking devices, and individual room environments.</div>"
            input "refreshDataBtn", "button", title: "🔄 Refresh Data"
            input "forceEvalBtn", "button", title: "⚡ Force Global Sync Evaluation"
            
            def cssHTML = """
            <style>
                .dash-table { width: 100%; border-collapse: collapse; font-size: 13px; margin-top:10px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 15px; }
                .dash-table th, .dash-table td { border: 1px solid #ccc; padding: 8px; text-align: center; }
                .dash-table th { background-color: #343a40; color: white; }
                .dash-hl { background-color: #f8f9fa; font-weight:bold; text-align: left !important; padding-left: 15px !important; width: 35%; }
                .dash-subhead { background-color: #e9ecef; font-weight: bold; text-align: left !important; padding-left: 15px !important; text-transform: uppercase; font-size: 12px; color: #495057; }
                .dash-val { text-align: left !important; padding-left: 15px !important; }
            </style>
            """
            
            def syncExplanation = "Global Mode Sync is currently <b>Disabled</b>."
            if (settings.enableGlobalMode) {
                def activeBlockers = []
                if (settings.blockingSwitches) {
                    settings.blockingSwitches.each { bSw ->
                        if (bSw.currentValue("switch") == "on") {
                            activeBlockers << bSw.displayName
                        }
                    }
                }
                
                if (activeBlockers.size() > 0) {
                    syncExplanation = "<span style='color:#d9534f;'><b>⚠️ Night Mode Blocked By:</b> ${activeBlockers.join(", ")}</span>"
                } else {
                    syncExplanation = "<span style='color:#28a745;'><b>✅ No Blocking Devices Active</b></span>"
                }
                
                if (state.nightModeScheduledTime) {
                    def globalTimeSecs = Math.round((state.nightModeScheduledTime - now()) / 1000.0)
                    syncExplanation += "<br><span style='color:#007bff; margin-top:4px; display:inline-block;'><b>⏳ Global Night Mode Countdown:</b> ${globalTimeSecs > 60 ? '~' + Math.round(globalTimeSecs / 60.0) + ' min(s)' : globalTimeSecs + ' sec(s)'} until Good Night.</span>"
                } else {
                    syncExplanation += "<br><span style='color:#007bff; margin-top:4px; display:inline-block;'><b>⏳ Global Night Mode Countdown:</b> Not Available</span>"
                }

                if (state.wakeModeScheduledTime) {
                    def wakeTimeSecs = Math.round((state.wakeModeScheduledTime - now()) / 1000.0)
                    syncExplanation += "<br><span style='color:#28a745; margin-top:4px; display:inline-block;'><b>⏳ Morning Wake Mode Countdown:</b> ${wakeTimeSecs > 60 ? '~' + Math.round(wakeTimeSecs / 60.0) + ' min(s)' : wakeTimeSecs + ' sec(s)'} until Wake Up.</span>"
                } else {
                    syncExplanation += "<br><span style='color:#28a745; margin-top:4px; display:inline-block;'><b>⏳ Morning Wake Mode Countdown:</b> Not Available</span>"
                }

                if (settings.enableLeadRoomOverride && state.overrideScheduledTime) {
                    def timeLeft = Math.round((state.overrideScheduledTime - now()) / 60000.0)
                    if (timeLeft > 0) {
                        syncExplanation += "<br><span style='color:#f39c12; margin-top:4px; display:inline-block;'><b>⏳ Lead Room(s) Timer Active:</b> ~${timeLeft} min(s) until forced Good Night evaluation.</span>"
                    } else {
                        syncExplanation += "<br><span style='color:#f39c12; margin-top:4px; display:inline-block;'><b>⏳ Lead Room(s) Timer:</b> Evaluation Pending...</span>"
                    }
                } else if (settings.enableLeadRoomOverride) {
                    syncExplanation += "<br><span style='color:#6c757d; margin-top:4px; display:inline-block;'><b>⏳ Lead Room(s) Timer:</b> Not Available</span>"
                }
            }
            
            def dashHTML = cssHTML + "<div style='background-color:#e9ecef; padding:10px; border-radius:5px; border-left:5px solid #007bff; margin-bottom: 15px; font-size: 13px;'><b>Global Sleep Sync:</b><br>${syncExplanation}</div>"
            
            def hasConfiguredRooms = false
            for (int i = 1; i <= 4; i++) {
                if (settings["enableRoom${i}"]) {
                    hasConfiguredRooms = true
                    def rName = settings["roomName${i}"] ?: "Room ${i}"
                    
                    def tSensor = settings["tempSensor${i}"]
                    def hSensor = settings["humSensor${i}"]
                    def cTemp = tSensor ? tSensor.currentValue("temperature") : null
                    def cHum = hSensor ? hSensor.currentValue("humidity") : null
                    def sleepQuality = calculateSleepSuitability(cTemp, cHum)
                    
                    def overrideFadeInUri = settings["audioFadeInUri${i}"]
                    def tonightTrack = "Not Generated Yet"
                    def aType = settings["audioSourceType${i}"] ?: "uri"
                    
                    if (overrideFadeInUri) {
                        tonightTrack = "Override URI Active"
                    } else if (aType == "uri" && state."nextUri${i}") {
                        tonightTrack = state."nextUri${i}"
                    } else if (aType == "switch" && state."nextSwitchId${i}") {
                        def nId = state."nextSwitchId${i}"
                        for(int u = 1; u <= 5; u++) {
                            def s = settings["audioSwitch${i}_${u}"]
                            if (s?.id == nId) tonightTrack = s.displayName
                        }
                    }
                    
                    def sw = settings["roomSwitch${i}"]
                    def isAsleep = false
                    if (settings["enableDualOccupant${i}"]) {
                        def pSw = settings["partnerSwitch${i}"]
                        isAsleep = (sw?.currentValue("switch") == "on" && pSw?.currentValue("switch") == "on")
                    } else {
                        isAsleep = (sw?.currentValue("switch") == "on")
                    }
                    
                    def titleColor = isAsleep ? "#2e154f" : "#007bff"
                    
                    def expCeiling = "App Released"
                    def expStdFan = "App Released"
                    def expLights = "App Released"
                    def expAudio = "App Released"
                    def targetSpeedDisp = "N/A"
                    
                    if (isAsleep) {
                        def isFadingInAudio = state."fadeInActive${i}"
                        def isFadingAudio = state."fadeActive${i}"
                        def isFadingLight = state."lightFadeActive${i}"
                        
                        expLights = isFadingLight ? "Fading Out" : (settings["roomLightsOn${i}"] ? "OFF / Bedtime Plugs ON" : "OFF")
                        
                        def minVolDisp = settings["audioFadeMinVol${i}"] != null ? settings["audioFadeMinVol${i}"] : 0
                        def targetVolDisp = settings["audioVolume${i}"] != null ? settings["audioVolume${i}"] : 30
                        
                        if (isFadingInAudio) {
                            expAudio = "Fading In to ${targetVolDisp}%"
                        } else if (isFadingAudio) {
                            expAudio = "Fading Out to ${minVolDisp}%"
                        } else {
                            expAudio = "PLAYING (Unless Timer Ended)"
                        }
                        
                        if (cTemp != null) {
                            def stdSet = settings["fanSetpoint${i}"]
                            expStdFan = stdSet ? (cTemp >= stdSet ? "ON" : "OFF") : "Not Configured"
                            
                            def cSet = settings["ceilingFanSetpoint${i}"]
                            def delta = settings["fanSpeedDelta${i}"] ?: 1.0
                            def fType = settings["fanType${i}"] ?: "3_speed"
                            
                            if (cSet && settings["ceilingFanSwitch${i}"]) {
                                if (fType == "on_off") {
                                    expCeiling = "Power: ON | Speed: N/A"
                                    targetSpeedDisp = "N/A (On/Off)"
                                } else {
                                    def tSpeed = calculateTargetSpeed(cTemp, cSet, delta, fType).toUpperCase()
                                    targetSpeedDisp = tSpeed
                                    expCeiling = "Power: ON | Speed: ${tSpeed}"
                                }
                            } else {
                                expCeiling = "Not Configured"
                            }
                        } else {
                            expStdFan = "Awaiting Temp Data"
                            expCeiling = "Awaiting Temp Data"
                        }
                    }
                    
                    dashHTML += """
                    <div style='background-color:${titleColor}; color:white; padding: 8px; font-weight:bold; font-size: 14px; border-radius: 4px 4px 0 0;'>
                        ${rName} - ${isAsleep ? '🌙 ASLEEP' : '☀️ AWAKE'}
                    </div>
                    <table class="dash-table" style="margin-top: 0; margin-bottom: 5px;">
                        <tr><td colspan='2' class="dash-subhead">Live Environment</td></tr>
                        <tr><td class="dash-hl">Current Temp</td><td class="dash-val">${cTemp != null ? cTemp + '°F' : '--'}</td></tr>
                        <tr><td class="dash-hl">Humidity</td><td class="dash-val">${cHum != null ? cHum + '%' : '--'}</td></tr>
                        <tr><td class="dash-hl">Environment</td><td class="dash-val">${sleepQuality}</td></tr>
                        <tr><td class="dash-hl">Tonight's Audio</td><td class="dash-val"><span style='font-size:10px; font-family:monospace; word-break:break-all;'>${tonightTrack}</span></td></tr>
                        
                        <tr><td colspan='2' class="dash-subhead">Expected States</td></tr>
                        <tr><td class="dash-hl">Calculated Target Speed</td><td class="dash-val" style="color:#007bff; font-weight:bold;">${targetSpeedDisp}</td></tr>
                        <tr><td class="dash-hl">Ceiling Fan Hardware</td><td class="dash-val">${expCeiling}</td></tr>
                        <tr><td class="dash-hl">Standard Fans</td><td class="dash-val">${expStdFan}</td></tr>
                        <tr><td class="dash-hl">Lights/Shades</td><td class="dash-val">${expLights}</td></tr>
                        <tr><td class="dash-hl">Audio Track</td><td class="dash-val">${expAudio}</td></tr>
                    </table>
                    """
                }
            }
            
            if (hasConfiguredRooms) {
                paragraph dashHTML
            } else {
                paragraph dashHTML + "<i>Please enable and configure a room below to populate the dashboard.</i>"
            }
        }
        
        section("<b>Command History (Last 20)</b>") {
            paragraph "<div style='font-size:13px; color:#555;'><b>What it does:</b> Provides a transparent, rolling log of every command the system evaluates and sends.</div>"
            def logList = state.eventLog ?: []
            if (logList.size() > 0) {
                def logHtml = logList.join("<br>")
                paragraph "<span style='font-size: 13px; font-family: monospace;'>${logHtml}</span>"
            } else {
                paragraph "<i>No commands logged yet. Turn a Good Night switch on to begin tracking.</i>"
            }
            input "clearLogBtn", "button", title: "Clear Command History"
        }

        section("<b>Global Life-Safety Override</b>", hideable: true, hidden: true) {
            paragraph "<i>If smoke or carbon monoxide is detected, the app will instantly kill all fans (to prevent spreading smoke), turn all configured room lights to 100% for egress, and stop all audio playback.</i>"
            input "emergencyAlarms", "capability.smokeDetector", title: "Smoke Detectors", multiple: true, required: false
            input "emergencyCO", "capability.carbonMonoxideDetector", title: "Carbon Monoxide Detectors", multiple: true, required: false
        }

        section("<b>Global Settings & Logs</b>", hideable: true, hidden: true) {
            input "switchDebounceDelay", "number", title: "Switch Debounce/Delay (Seconds)", defaultValue: 3, required: true, description: "Waits X seconds before executing Good Night/Wake Up to absorb accidental rapid flips."
            input "enablePeriodicEnforcement", "bool", title: "<b>Enable Periodic State Enforcement</b><br><i>(Checks every 10 mins to ensure lights are off, fans are correct, and mode is synced)</i>", defaultValue: true
            input "enableWiggle", "bool", title: "<b>Master Enable: Hourly Fan Wiggle (Self-Healing)</b><br><i>(Global toggle to allow room fans to run their configured Wiggle routines)</i>", defaultValue: true
            input "txtLogEnable", "bool", title: "Enable Action Logging (Info)", defaultValue: true
            input "debugLogEnable", "bool", title: "Enable Debug Logging", defaultValue: false
        }
        
        section("<b>Global Handshake: Mode Synchronization</b>", hideable: true, hidden: true) {
            input "enableGlobalMode", "bool", title: "<b>Enable Global Mode Sync</b>", submitOnChange: true
            if (enableGlobalMode) {
                paragraph "<b>Entering Night Mode</b>"
                input "allowedNightModes", "mode", title: "Safety Whitelist: ONLY allow entering Night Mode if house is currently in these Modes", multiple: true, required: false
                input "syncRooms", "enum", title: "Require these rooms to be Asleep", options: ["1":"Room 1", "2":"Room 2", "3":"Room 3", "4":"Room 4"], multiple: true, required: true
                input "syncMotion", "capability.motionSensor", title: "Require NO motion on these sensors", multiple: true, required: false
                input "blockingSwitches", "capability.switch", title: "Blocking Devices: Prevent Night Mode if ANY of these are ON (e.g. TV, Pinball)", multiple: true, required: false
                
                paragraph "<b>Safety Sweep (Optional Security Verification)</b>"
                input "enableSafetySweep", "bool", title: "Verify Security before entering Night Mode", submitOnChange: true
                if (enableSafetySweep) {
                    input "sweepContacts", "capability.contactSensor", title: "Require these Doors/Windows to be CLOSED", multiple: true, required: false
                    input "sweepLocks", "capability.lock", title: "Require these Doors to be LOCKED", multiple: true, required: false
                    input "sweepSpeaker", "capability.speechSynthesis", title: "TTS Speaker for Warning Broadcasts", required: false
                }
                
                input "nightStartTime", "time", title: "Between Start Time", required: true
                input "nightEndTime", "time", title: "And End Time", required: true
                input "targetNightMode", "mode", title: "Change House Mode to", required: true
                input "nightModeDelay", "number", title: "Delay before entering Night Mode (seconds)", defaultValue: 60, required: false
                
                paragraph "<b>Morning Wake-Up Mode</b>"
                input "wakeStartTime", "time", title: "Between Start Time", required: true
                input "wakeEndTime", "time", title: "And End Time", required: true
                input "requireNightMode", "mode", title: "Only if currently in this Mode", required: true
                input "targetWakeMode", "mode", title: "Change House Mode to", required: true
                input "wakeModeDelay", "number", title: "Delay before entering Wake Mode (seconds)", defaultValue: 60, required: false

                paragraph "<b>Lead Room(s) Good Night Override</b>"
                input "enableLeadRoomOverride", "bool", title: "<b>Enable Lead Room Override</b>", submitOnChange: true
                if (enableLeadRoomOverride) {
                    input "leadRooms", "enum", title: "Select Lead Rooms (All selected must be asleep to trigger override)", options: ["1":"Room 1", "2":"Room 2", "3":"Room 3", "4":"Room 4"], multiple: true, required: true
                    input "leadRoomTimeout", "number", title: "Wait time (minutes) for other rooms to go to sleep", defaultValue: 30, required: true
                    input "leadRoomMotionSensors", "capability.motionSensor", title: "Require NO motion on these sensors to force Good Night", multiple: true, required: true
                    input "targetOverrideMode", "mode", title: "Change House Mode to (Good Night)", required: true
                }
            }
        }
        
        for (int i = 1; i <= 4; i++) {
            def rName = settings["roomName${i}"] ?: "Room ${i}"
            
            section("<b>${rName} Configuration</b>", hideable: true, hidden: true) {
                input "enableRoom${i}", "bool", title: "<b>Enable ${rName}</b>", submitOnChange: true
                
                if (settings["enableRoom${i}"]) {
                    input "roomName${i}", "text", title: "Custom Room Name", defaultValue: "Room ${i}", submitOnChange: true
                    input "roomSwitch${i}", "capability.switch", title: "${rName} Good Night Virtual Switch", required: true
                    
                    input "enableDualOccupant${i}", "bool", title: "<b>Enable Multi-Occupant (Partner Sync)</b>", submitOnChange: true
                    if (settings["enableDualOccupant${i}"]) {
                        paragraph "<i>Room will only go to sleep when BOTH switches are ON, and will wake up if EITHER is turned OFF.</i>"
                        input "partnerSwitch${i}", "capability.switch", title: "Partner Good Night Switch", required: true
                    }
                    
                    paragraph "<b>Good Night Toggle Button</b>"
                    input "gnButton${i}", "capability.pushableButton", title: "Toggle Button Device", required: false
                    input "gnButtonNum${i}", "number", title: "Button Number", required: false, defaultValue: 1
                    input "gnButtonAction${i}", "enum", title: "Button Action", options: ["pushed":"Pushed", "doubleTapped":"Double Tapped", "held":"Held", "released":"Released"], required: false, defaultValue: "pushed"
                    input "gnButtonModes${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    
                    paragraph "<b>1. Climate & Environment</b>"
                    input "tempSensor${i}", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
                    input "humSensor${i}", "capability.relativeHumidityMeasurement", title: "Humidity Sensor (Optional - for sleep rating)", required: false
                    
                    paragraph "<b>Standard ON/OFF Fans</b>"
                    input "fanSetpoint${i}", "decimal", title: "Turn ON Standard Fans if Temp reaches (°F)", required: false
                    input "roomFans${i}", "capability.switch", title: "Standard Fans (Select up to 2)", multiple: true, required: false
                    
                    paragraph "<b>Dynamic Ceiling Fan</b>"
                    paragraph "<i>Select your fan capability. The routine will automatically scale the speed commands as the room heats up.</i>"
                    
                    input "fanType${i}", "enum", title: "Ceiling Fan Type", options: ["on_off": "Simple On/Off", "3_speed": "3-Speed", "6_speed": "5/6-Speed"], defaultValue: "3_speed", required: true
                    input "ceilingFanSwitch${i}", "capability.switch", title: "Ceiling Fan Power Switch", required: false
                    input "ceilingFanSpeed${i}", "capability.fanControl", title: "Ceiling Fan Speed Control", required: false
                    input "ceilingFanSetpoint${i}", "decimal", title: "Ceiling Fan Base Setpoint (°F)", required: false
                    input "fanSpeedDelta${i}", "decimal", title: "Degrees above setpoint to step up speed (Default: 1.0)", required: false, defaultValue: 1.0
                    input "enableWiggle${i}", "bool", title: "Enable Hourly Wiggle for this specific fan", defaultValue: true
                    
                    paragraph "<b>2. Lighting & Shades</b>"
                    input "roomLights${i}", "capability.switch", title: "Lights to Turn OFF immediately (if fade disabled)", multiple: true, required: false
                    
                    input "enableLightFade${i}", "bool", title: "<b>Enable Smooth Light Fade-Out</b>", submitOnChange: true
                    if (settings["enableLightFade${i}"]) {
                        paragraph "<i>Slowly dims dimmable lights to zero over the specified timeframe when going to sleep. Non-dimmable lights will turn off immediately.</i>"
                        input "lightFadeDuration${i}", "number", title: "Light Fade Duration (Minutes)", defaultValue: 15, required: true
                    }
                    
                    input "roomLightsOn${i}", "capability.switch", title: "Lights/Plugs to Turn ON (Turns OFF when waking)", multiple: true, required: false
                    
                    paragraph "<b>Delayed Shut-Off (e.g., Nightlights/RGB)</b>"
                    input "delayedOffSwitches${i}", "capability.switch", title: "Switches to turn OFF later", multiple: true, required: false
                    input "delayedOffTime${i}", "number", title: "Delay Time (Minutes)", required: false, defaultValue: 120

                    input "pauseLightingEnforcement${i}", "capability.switch", title: "Pause Lighting Enforcement Switch", required: false
                    input "romanceSwitch${i}", "capability.switch", title: "Romance / Override Switch (Stops 10-min cycle)", required: false
                    input "shadeContact${i}", "capability.contactSensor", title: "Shade Open/Close Contact Sensor", required: false
                    input "roomShade${i}", "capability.windowShade", title: "Window Shade to Close", required: false
                    input "shadeHoldRelease${i}", "capability.switch", title: "Manual Hold Release Switch", required: false
                    
                    paragraph "<b>Reading Light 1</b>"
                    input "enableReadingLight1_${i}", "bool", title: "Enable Reading Light 1?", submitOnChange: true
                    if (settings["enableReadingLight1_${i}"]) {
                        input "readingLight1_${i}", "capability.switchLevel", title: "Reading Light 1 (Dimmer)", required: false
                        input "readingButton1_${i}", "capability.pushableButton", title: "Button for Light 1", required: false
                        input "readingButtonNum1_${i}", "number", title: "Button Number", required: false, defaultValue: 1
                        input "readingLevel1_${i}", "number", title: "Dim Level (%)", required: false, defaultValue: 30
                        input "readingTimeout1_${i}", "number", title: "Timeout (Minutes)", required: false, defaultValue: 60
                        input "readingModes1_${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    }

                    paragraph "<b>Reading Light 2</b>"
                    input "enableReadingLight2_${i}", "bool", title: "Enable Reading Light 2?", submitOnChange: true
                    if (settings["enableReadingLight2_${i}"]) {
                        input "readingLight2_${i}", "capability.switchLevel", title: "Reading Light 2 (Dimmer)", required: false
                        input "readingButton2_${i}", "capability.pushableButton", title: "Button for Light 2", required: false
                        input "readingButtonNum2_${i}", "number", title: "Button Number", required: false, defaultValue: 1
                        input "readingLevel2_${i}", "number", title: "Dim Level (%)", required: false, defaultValue: 30
                        input "readingTimeout2_${i}", "number", title: "Timeout (Minutes)", required: false, defaultValue: 60
                        input "readingModes2_${i}", "mode", title: "Only Allow in These Modes", multiple: true, required: false
                    }
                    
                    paragraph "<b>3. Sonos Audio Polish</b>"
                    input "roomSpeakerPower${i}", "capability.switch", title: "Sonos Speaker Power Plug (Optional)", required: false
                    input "roomSpeaker${i}", "capability.musicPlayer", title: "Sonos Speaker", required: false
                    input "audioStartDelay${i}", "number", title: "Audio Start Delay (Minutes)", required: false, defaultValue: 8, description: "Delays the music to allow Voice Butler TTS to finish."
                    
                    paragraph "<b>Audio Track Configuration</b>"
                    input "audioSourceType${i}", "enum", title: "Audio Source Type", options: ["uri":"Direct Audio URIs", "switch":"Sonos Favorite Virtual Switches"], defaultValue: "uri", submitOnChange: true
                    
                    if ((settings["audioSourceType${i}"] ?: "uri") == "uri") {
                        input "audioUri${i}_1", "text", title: "Audio URI 1", required: false
                        input "audioUri${i}_2", "text", title: "Audio URI 2", required: false
                        input "audioUri${i}_3", "text", title: "Audio URI 3", required: false
                        input "audioUri${i}_4", "text", title: "Audio URI 4", required: false
                        input "audioUri${i}_5", "text", title: "Audio URI 5", required: false
                    } else {
                        input "audioSwitch${i}_1", "capability.switch", title: "Favorite Switch 1", required: false
                        input "audioSwitch${i}_2", "capability.switch", title: "Favorite Switch 2", required: false
                        input "audioSwitch${i}_3", "capability.switch", title: "Favorite Switch 3", required: false
                        input "audioSwitch${i}_4", "capability.switch", title: "Favorite Switch 4", required: false
                        input "audioSwitch${i}_5", "capability.switch", title: "Favorite Switch 5", required: false
                    }
                    
                    paragraph "<b>Audio Volume & Fade-In (Start of Sleep)</b>"
                    input "audioVolume${i}", "number", title: "Target Nighttime Volume (1-100)", required: false, defaultValue: 30
                    
                    input "enableAudioFadeIn${i}", "bool", title: "<b>Enable Smooth Audio Fade-In</b>", submitOnChange: true
                    if (settings["enableAudioFadeIn${i}"]) {
                        input "audioFadeInDuration${i}", "number", title: "Fade-In Duration (Minutes)", defaultValue: 5, required: true
                        input "audioFadeInStartVol${i}", "number", title: "Starting Volume (%)", defaultValue: 1, required: true
                        input "audioFadeInUri${i}", "text", title: "Optional Override URI (Plays this specific URI instead of standard audio source)", required: false
                    }

                    paragraph "<b>Audio Timer & Fade-Out</b>"
                    input "audioTimer${i}", "number", title: "Total Sleep Timer: Stop audio after X minutes", required: false, submitOnChange: true
                    
                    if (settings["audioTimer${i}"]) {
                        input "enableAudioFade${i}", "bool", title: "<b>Enable Smooth Fade-Out</b>", submitOnChange: true
                        if (settings["enableAudioFade${i}"]) {
                            input "audioFadeDuration${i}", "number", title: "Fade-Out Duration (Minutes)", defaultValue: 15, required: true, description: "The volume will slowly step down to your target minimum volume over the last X minutes of the Sleep Timer to prevent abrupt waking."
                            input "audioFadeMinVol${i}", "number", title: "Minimum Fade Volume (%)", defaultValue: 1, required: true, description: "Set to 1 or higher to prevent the Sonos Green Mute LED from turning on at night."
                        }
                    }

                    paragraph "<b>Morning Audio & Ramp-Up (Wake Up)</b>"
                    input "enableMorningAudio${i}", "bool", title: "<b>Enable Morning Audio Ramp-Up</b>", submitOnChange: true
                    if (settings["enableMorningAudio${i}"]) {
                        input "morningAudioUri${i}", "text", title: "Morning Audio URI (Optional)", required: false
                        input "morningAudioSwitch${i}", "capability.switch", title: "Morning Sonos Favorite Virtual Switch (Optional)", required: false
                        input "morningFadeInDuration${i}", "number", title: "Ramp-Up Duration (Minutes)", defaultValue: 5, required: true
                        input "morningStartVol${i}", "number", title: "Starting Volume (%)", defaultValue: 1, required: true
                        input "morningTargetVol${i}", "number", title: "Target Daytime Volume (%)", defaultValue: 30, required: true
                    }
                }
            }
        }
    }
}

// ==============================================================================
// INTERNAL LOGIC ENGINE
// ==============================================================================

def appButtonHandler(btn) {
    if (btn == "refreshDataBtn") {
        logInfo("Manual UI Data Refresh Triggered.")
    } else if (btn == "clearLogBtn") {
        state.eventLog = []
        logInfo("User manually cleared the command history log.")
    } else if (btn == "forceEvalBtn") {
        logInfo("User manually forced a Global Mode Sync re-evaluation.")
        evaluateGlobalMode(null)
    }
}

def getSpeedLevels() { return ["off": 0, "low": 1, "medium-low": 2, "medium": 3, "medium-high": 4, "high": 5] }
def getLevelSpeeds() { return [0: "off", 1: "low", 2: "medium-low", 3: "medium", 4: "medium-high", 5: "high"] }

def calculateTargetSpeed(currentTemp, setpoint, delta, fanType) {
    def diff = currentTemp - setpoint
    if (fanType == "3_speed") {
        if (diff >= (delta * 2)) return "high"
        if (diff >= delta) return "medium"
        return "low"
    } else if (fanType == "6_speed") {
        if (diff >= (delta * 4)) return "high"
        if (diff >= (delta * 3)) return "medium-high"
        if (diff >= (delta * 2)) return "medium"
        if (diff >= delta) return "medium-low"
        return "low"
    }
    return "low" 
}

def getDropSpeed(current, fanType) {
    if (fanType == "3_speed") {
        if (current == "high") return "medium"
        if (current == "medium") return "low"
        if (current == "low") return "off"
    } else if (fanType == "6_speed") {
        if (current == "high") return "medium-high"
        if (current == "medium-high") return "medium"
        if (current == "medium") return "medium-low"
        if (current == "medium-low") return "low"
        if (current == "low") return "off"
    }
    return null
}

def installed() {
    logInfo("Installed and initialized.")
    initialize()
}

def updated() {
    logInfo("Updated. Re-initializing.")
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (!state.eventLog) state.eventLog = []
    
    subscribe(location, "systemStart", hubRebootHandler)
    
    if (enablePeriodicEnforcement) {
        runEvery10Minutes("periodicEnforcementHandler")
    }

    if (settings.enableWiggle) {
        runEvery1Hour("doHourlyWiggle")
    }
    
    if (emergencyAlarms) {
        subscribe(emergencyAlarms, "smoke.detected", emergencyHandler)
    }
    if (emergencyCO) {
        subscribe(emergencyCO, "carbonMonoxide.detected", emergencyHandler)
    }
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"]) {
            if (settings["roomSwitch${i}"]) {
                subscribe(settings["roomSwitch${i}"], "switch", roomSwitchHandler)
            }
            if (settings["enableDualOccupant${i}"] && settings["partnerSwitch${i}"]) {
                subscribe(settings["partnerSwitch${i}"], "switch", roomSwitchHandler)
            }
            if (settings["tempSensor${i}"]) {
                subscribe(settings["tempSensor${i}"], "temperature", tempHandler)
            }
            if (settings["gnButton${i}"]) {
                def action = settings["gnButtonAction${i}"] ?: "pushed"
                subscribe(settings["gnButton${i}"], action, goodNightButtonHandler)
            }
            
            if (settings["enableReadingLight1_${i}"] && settings["readingButton1_${i}"]) {
                subscribe(settings["readingButton1_${i}"], "pushed", readingButtonHandler)
            }
            if (settings["enableReadingLight2_${i}"] && settings["readingButton2_${i}"]) {
                subscribe(settings["readingButton2_${i}"], "pushed", readingButtonHandler)
            }
            
            prepNextAudio(i)
        }
    }
    
    if (enableGlobalMode) {
        if (syncMotion) {
            subscribe(syncMotion, "motion", globalMotionHandler)
        }
        if (blockingSwitches) {
            subscribe(blockingSwitches, "switch", blockingSwitchHandler)
        }
    }
}

// --- LIFE SAFETY EMERGENCY OVERRIDE ---
def emergencyHandler(evt) {
    logInfo("🚨 EMERGENCY: ${evt.name.toUpperCase()} DETECTED BY ${evt.device.displayName}! INITIATING LIFE-SAFETY OVERRIDE 🚨")
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"]) {
            def rName = settings["roomName${i}"] ?: "Room ${i}"
            
            // 1. Cancel all sleep timers and fade loops
            state.remove("fadeActive${i}")
            state.remove("fadeInActive${i}")
            state.remove("morningFadeInActive${i}")
            state.remove("lightFadeActive${i}")
            unschedule("lightFadeCompleteRoom${i}")
            unschedule("startAudioFadeInRoom${i}")
            unschedule("startMorningAudioFadeInRoom${i}")
            unschedule("startAudioFadeRoom${i}")
            unschedule("playDelayedAudioRoom${i}")
            unschedule("stopAudioRoom${i}")
            unschedule("applyDelayedFanSpeedRoom${i}")
            unschedule("applyDelayedVolumeRoom${i}")
            unschedule("delayedOffRoom${i}")
            
            // 2. Kill all fans to stop smoke circulation
            settings["roomFans${i}"]?.off()
            
            def cFanSpeed = settings["ceilingFanSpeed${i}"]
            def fType = settings["fanType${i}"] ?: "3_speed"
            if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") {
                cFanSpeed.setSpeed("off")
            }
            settings["ceilingFanSwitch${i}"]?.off()
            
            // 3. Stop audio to allow alarms to be heard
            settings["roomSpeaker${i}"]?.stop()
            
            // 4. Egress Lighting (Snap to 100%)
            def lights = settings["roomLights${i}"]
            if (lights) {
                lights.each { lgt ->
                    if (lgt.hasCommand("setLevel")) {
                        lgt.setLevel(100)
                    } else {
                        lgt.on()
                    }
                }
            }
            
            def lightsOn = settings["roomLightsOn${i}"]
            if (lightsOn) {
                lightsOn.each { lgt ->
                    if (lgt.hasCommand("setLevel")) {
                        lgt.setLevel(100)
                    } else {
                        lgt.on()
                    }
                }
            }
            
            logInfo("${rName}: Emergency Protocol Executed - Fans OFF, Audio STOPPED, Lights 100%.")
        }
    }
}

// --- GOOD NIGHT BUTTON TOGGLE ENGINE ---
def goodNightButtonHandler(evt) {
    def btnId = evt.device.id
    def btnNum = evt.value

    for (int i = 1; i <= 4; i++) {
        if (!settings["enableRoom${i}"]) continue

        def confBtn = settings["gnButton${i}"]
        def confNum = settings["gnButtonNum${i}"]?.toString() ?: "1"
        def confAction = settings["gnButtonAction${i}"] ?: "pushed"
        def rName = settings["roomName${i}"] ?: "Room ${i}"

        if (confBtn && confBtn.id == btnId && evt.name == confAction && btnNum == confNum) {
            
            // --- HARDWARE DEADBAND (ANTI-BOUNCE) ---
            def lastPress = state."lastBtnPress${i}" ?: 0
            if (now() - lastPress < 3000) { // 3000 milliseconds = 3 seconds
                logInfo("${rName}: Hardware button bounce/stutter detected. Ignoring duplicate signal.")
                return 
            }
            state."lastBtnPress${i}" = now()
            // ---------------------------------------

            def rModes = settings["gnButtonModes${i}"]
            if (rModes && !(rModes as List).contains(location.mode)) {
                logInfo("${rName}: Good Night button pressed, but not in an allowed mode.")
                return
            }

            def sw = settings["roomSwitch${i}"]
            if (sw) {
                if (sw.currentValue("switch") == "on") {
                    logInfo("${rName}: Toggle button pressed. Turning Primary Good Night OFF.")
                    sw.off()
                } else {
                    logInfo("${rName}: Toggle button pressed. Turning Primary Good Night ON.")
                    sw.on()
                }
            }
        }
    }
}

// --- READING LIGHT BUTTON ENGINE ---
def readingButtonHandler(evt) {
    def btnId = evt.device.id
    def btnNum = evt.value

    for (int i = 1; i <= 4; i++) {
        if (!settings["enableRoom${i}"]) continue

        for (int l = 1; l <= 2; l++) {
            if (!settings["enableReadingLight${l}_${i}"]) continue
            
            def confBtn = settings["readingButton${l}_${i}"]
            def confNum = settings["readingButtonNum${l}_${i}"]?.toString() ?: "1"

            if (confBtn && confBtn.id == btnId && btnNum == confNum) {
                toggleReadingMode(i, l)
                return
            }
        }
    }
}

def toggleReadingMode(roomNum, lightNum) {
    if (!settings["enableReadingLight${lightNum}_${roomNum}"]) return

    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def rLight = settings["readingLight${lightNum}_${roomNum}"]
    def rLevel = settings["readingLevel${lightNum}_${roomNum}"] ?: 30
    def rTimeout = settings["readingTimeout${lightNum}_${roomNum}"] ?: 60
    def rModes = settings["readingModes${lightNum}_${roomNum}"]

    if (!rLight) return

    if (rModes && !(rModes as List).contains(location.mode)) {
        logInfo("${rName}: Reading button pushed, but not in an allowed mode.")
        return
    }

    def isActive = state."readingModeActive_${roomNum}_${lightNum}"

    if (isActive) {
        logInfo("${rName}: Reading Light ${lightNum} OFF (Toggled manually).")
        endReadingMode(roomNum, lightNum)
    } else {
        logInfo("${rName}: Reading Light ${lightNum} ON. Level: ${rLevel}%, Timer: ${rTimeout}m.")
        state."readingModeActive_${roomNum}_${lightNum}" = true
        rLight.setLevel(rLevel)
        runIn(rTimeout * 60, "readingTimeoutRoom${roomNum}Light${lightNum}")
    }
}

def readingTimeoutRoom1Light1() { endReadingMode(1, 1) }
def readingTimeoutRoom1Light2() { endReadingMode(1, 2) }
def readingTimeoutRoom2Light1() { endReadingMode(2, 1) }
def readingTimeoutRoom2Light2() { endReadingMode(2, 2) }
def readingTimeoutRoom3Light1() { endReadingMode(3, 1) }
def readingTimeoutRoom3Light2() { endReadingMode(3, 2) }
def readingTimeoutRoom4Light1() { endReadingMode(4, 1) }
def readingTimeoutRoom4Light2() { endReadingMode(4, 2) }

def endReadingMode(roomNum, lightNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def rLight = settings["readingLight${lightNum}_${roomNum}"]
    
    logInfo("${rName}: Reading Mode for Light ${lightNum} ended. Turning off.")
    state."readingModeActive_${roomNum}_${lightNum}" = false
    unschedule("readingTimeoutRoom${roomNum}Light${lightNum}")
    
    if (rLight) rLight.off()
}

def isReadingLightActive(roomNum, lightDeviceId) {
    for (int l = 1; l <= 2; l++) {
        if (!settings["enableReadingLight${l}_${roomNum}"]) continue
        
        if (state."readingModeActive_${roomNum}_${l}") {
            def rLight = settings["readingLight${l}_${roomNum}"]
            if (rLight && rLight.id == lightDeviceId) return true
        }
    }
    return false
}

// --- HOURLY FAN WIGGLE ---
def doHourlyWiggle() {
    if (!settings.enableWiggle) return 
    
    logInfo("Executing Hourly RF Fan Wiggle to actively enforce target speeds...")
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && state."roomAsleepStatus${i}" && settings["enableWiggle${i}"]) {
            def cFanSpeed = settings["ceilingFanSpeed${i}"]
            def rName = settings["roomName${i}"] ?: "Room ${i}"
            def fType = settings["fanType${i}"] ?: "3_speed"
            
            if (cFanSpeed && fType != "on_off") {
                def sensor = settings["tempSensor${i}"]
                def currentTemp = sensor ? sensor.currentValue("temperature") : null
                def cFanSetpoint = settings["ceilingFanSetpoint${i}"]
                def delta = settings["fanSpeedDelta${i}"] ?: 1.0
                
                def targetSpeed = "off"
                if (cFanSetpoint && currentTemp != null) {
                    targetSpeed = calculateTargetSpeed(currentTemp, cFanSetpoint, delta, fType)
                }
                
                if (targetSpeed != "off") {
                    def dropSpeed = getDropSpeed(targetSpeed, fType)
                    if (dropSpeed) {
                        logInfo("${rName}: Wiggle - Target speed is ${targetSpeed.toUpperCase()}. Dropping to ${dropSpeed.toUpperCase()} temporarily to force resync.")
                        cFanSpeed.setSpeed(dropSpeed)
                    }
                } else {
                    logInfo("${rName}: Wiggle - Target speed is OFF. Bumping to LOW to force Bond Bridge reset.")
                    cFanSpeed.setSpeed("low")
                }
            }
        }
    }
    runIn(10, "evaluateAllSleepingFans")
}

def evaluateAllSleepingFans() {
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && state."roomAsleepStatus${i}") {
            evaluateFans(i)
        }
    }
}

// --- PERIODIC STATE ENFORCEMENT ---
def periodicEnforcementHandler() {
    def anyoneAsleep = false
    for (int i = 1; i <= 4; i++) {
        if (state."roomAsleepStatus${i}") {
             anyoneAsleep = true
            break
        }
    }
    
    if (anyoneAsleep) {
        if (txtLogEnable) log.debug "PERIODIC ENFORCEMENT: Waking up to verify system state..."
        
        evaluateGlobalMode(null)
        
        for (int i = 1; i <= 4; i++) {
            if (settings["enableRoom${i}"]) {
                def rName = settings["roomName${i}"] ?: "Room ${i}"
                
                if (state."roomAsleepStatus${i}") {
                    
                    def pauseEnforce = settings["pauseLightingEnforcement${i}"]
                    def romanceSw = settings["romanceSwitch${i}"]
                    
                    def isPaused = (pauseEnforce?.currentValue("switch") == "on") || (romanceSw?.currentValue("switch") == "on")
                    
                    if (state."lightFadeActive${i}") {
                        if (txtLogEnable) log.debug "ENFORCEMENT: Skipping ${rName} light checks (Smooth Light Fade currently active)."
                    } else if (!isPaused) {
                        if (txtLogEnable) log.debug "ENFORCEMENT: No overrides active. Proceeding with forced light shutdown for ${rName}."
                        
                        def lights = settings["roomLights${i}"]
                        if (lights) {
                             lights.each { lgt -> 
                                if (lgt.currentValue("switch") == "on") {
                                    if (isReadingLightActive(i, lgt.id)) {
                                         if (txtLogEnable) log.debug "ENFORCEMENT: Skipping [${lgt.displayName}] as Reading Mode is active."
                                         return 
                                    }
                                    if (lgt.hasCommand("setLevel")) {
                                         lgt.setLevel(1)
                                        pauseExecution(400)
                                    }
                                     lgt.off()
                                    logInfo("ENFORCEMENT: ${rName} is asleep but light [${lgt.displayName}] was ON. Forced OFF.")
                                 }
                            }
                        }
                        
                         def lightsOn = settings["roomLightsOn${i}"]
                        if (lightsOn) {
                            lightsOn.each { lgt ->
                                if (lgt.currentValue("switch") == "off") {
                                    lgt.on()
                                    logInfo("ENFORCEMENT: ${rName} is asleep but bedtime light/plug [${lgt.displayName}] was OFF. Forced ON.")
                                 }
                            }
                        }
                        
                     } else {
                        if (txtLogEnable) log.debug "ENFORCEMENT: Lighting checks successfully paused for ${rName} (Sunrise or Romance Active)."
                    }
                    
                    def shadeContact = settings["shadeContact${i}"]
                    def shade = settings["roomShade${i}"]
                    if (shadeContact && shade && shadeContact.currentValue("contact") == "open") {
                         shade.close()
                        logInfo("ENFORCEMENT: ${rName} is asleep but shade contact was OPEN. Forced CLOSE.")
                    }

                    evaluateFans(i)
                }
            }
        }
    }
}

def hubRebootHandler(evt) {
    logInfo("SYSTEM BOOT: Hub reboot or power failure detected. Running nighttime recovery scan...")
    
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"]) {
             if (state."roomAsleepStatus${i}") {
                def rName = settings["roomName${i}"] ?: "Room ${i}"
                logInfo("RECOVERY: ${rName} is still ASLEEP. Re-applying Good Night environment...")
                executeRoomGoodNight(i)
            }
        }
    }
    evaluateGlobalMode(null)
}

def calculateSleepSuitability(cTemp, cHum) {
    if (cTemp == null) return "<span style='color:gray;'>Awaiting Sensor Data...</span>"
    
    def tempStatus = ""
    def humStatus = ""
    def color = "green"
    
    if (cTemp < 60.0) { tempStatus = "Too Cold"; color = "blue" }
    else if (cTemp >= 60.0 && cTemp <= 69.0) { tempStatus = "Optimal Temp" }
    else { tempStatus = "Too Warm"; color = "red" }
    
    if (cHum != null) {
        if (cHum < 30.0) humStatus = " & Dry"
        else if (cHum >= 30.0 && cHum <= 50.0) humStatus = " & Ideal Humidity"
        else { humStatus = " & Humid"; color = "orange" }
    }
    
    def finalStatus = tempStatus + humStatus
    if (finalStatus.contains("Optimal Temp") && (humStatus == "" || humStatus.contains("Ideal"))) {
        return "<span style='color:green; font-weight:bold;'>Perfect 🌙</span>"
    }
    return "<span style='color:${color};'>${finalStatus}</span>"
}

def areRequiredRoomsAsleep() {
    if (!settings.syncRooms) return false
    def allAsleep = true
    def roomsChecked = 0
    for (int i = 1; i <= 4; i++) {
        if (settings.syncRooms.contains(i.toString())) {
            roomsChecked++
            if (!state."roomAsleepStatus${i}") {
                allAsleep = false
                break
            }
        }
    }
    return (roomsChecked > 0 && allAsleep)
}

def areAllLeadRoomsAsleep() {
    if (!settings.leadRooms) return false
    def allAsleep = true
    def roomsList = settings.leadRooms as List
    roomsList.each { rNum ->
        if (!state."roomAsleepStatus${rNum}") {
            allAsleep = false
        }
    }
    return allAsleep
}

def globalMotionHandler(evt) {
    logInfo("GLOBAL SYNC: Motion state changed to [${evt.value}] on ${evt.device.displayName}. Re-evaluating...")
    evaluateGlobalMode(evt)
}

def blockingSwitchHandler(evt) {
    logInfo("GLOBAL SYNC: Blocking device state changed to [${evt.value}] on ${evt.device.displayName}. Re-evaluating...")
    evaluateGlobalMode(evt)
}

// --- SWITCH DEBOUNCE (ABSORB METHOD) ---
def roomSwitchHandler(evt) {
    def roomNum = null
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"]) {
            if (settings["roomSwitch${i}"]?.id == evt.device.id || (settings["enableDualOccupant${i}"] && settings["partnerSwitch${i}"]?.id == evt.device.id)) {
                roomNum = i
                break
            }
        }
    }
    if (!roomNum) return
    
    def debounceSecs = settings.switchDebounceDelay != null ? settings.switchDebounceDelay.toInteger() : 3

    if (debounceSecs > 0) {
        if (txtLogEnable) log.debug "Debounce active: Delaying evaluation for Room ${roomNum} by ${debounceSecs}s to absorb rapid toggles."
        // We use dedicated wrappers so simultaneous room changes don't overwrite each other
        runIn(debounceSecs, "commitRoomSwitch${roomNum}")
    } else {
        commitRoomSwitch(roomNum)
    }
}

// --- DEBOUNCE WRAPPERS ---
def commitRoomSwitch1() { commitRoomSwitch(1) }
def commitRoomSwitch2() { commitRoomSwitch(2) }
def commitRoomSwitch3() { commitRoomSwitch(3) }
def commitRoomSwitch4() { commitRoomSwitch(4) }

def commitRoomSwitch(roomNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def sw1 = settings["roomSwitch${roomNum}"]
    def sw2 = settings["partnerSwitch${roomNum}"]
    
    def isNowAsleep = false
    if (settings["enableDualOccupant${roomNum}"]) {
        isNowAsleep = (sw1?.currentValue("switch") == "on" && sw2?.currentValue("switch") == "on")
    } else {
        isNowAsleep = (sw1?.currentValue("switch") == "on")
    }
    
    def wasAsleep = state."roomAsleepStatus${roomNum}" ?: false
    
    // Evaluate the final settled state
    if (isNowAsleep && !wasAsleep) {
        state."roomAsleepStatus${roomNum}" = true
        
        logInfo("${rName}: Good Night Triggered (Multi-Occupant Sync Met if enabled). Engaging Routine.")
        executeRoomGoodNight(roomNum)
        
        if (settings.enableLeadRoomOverride && settings.leadRooms?.contains(roomNum.toString())) {
            if (areAllLeadRoomsAsleep()) {
                def delaySecs = (settings.leadRoomTimeout ?: 30) * 60
                state.overrideScheduledTime = now() + (delaySecs * 1000)
                logInfo("GOOD NIGHT OVERRIDE: All selected Lead Rooms are asleep. Waiting ${settings.leadRoomTimeout} minutes for other rooms.")
                runIn(delaySecs, "evaluateLeadRoomOverride")
            }
        }
        
    } else if (!isNowAsleep && wasAsleep) {
        state."roomAsleepStatus${roomNum}" = false
        logInfo("${rName}: Good Night Wake Up Triggered.")
        
        endRoomGoodNight(roomNum)
        
        if (settings.enableLeadRoomOverride && settings.leadRooms?.contains(roomNum.toString())) {
            unschedule("evaluateLeadRoomOverride")
            state.remove("overrideScheduledTime")
            logInfo("GOOD NIGHT OVERRIDE: A Lead Room (${roomNum}) woke up. Canceled evaluation.")
        }
    } else {
        // If the state reverted to its original position during the delay window
        logInfo("${rName}: Switch evaluation completed with no net state change (Accidental flip completely absorbed).")
    }
    
    evaluateGlobalMode(null)
}

def evaluateLeadRoomOverride() {
    state.remove("overrideScheduledTime") 
    
    if (!settings.enableLeadRoomOverride || !settings.targetOverrideMode) return

    logInfo("GOOD NIGHT OVERRIDE: Timeout reached. Checking other rooms and motion.")

    def otherRoomAwake = false
    def lRooms = settings.leadRooms as List
    
    for (int i = 1; i <= 4; i++) {
        if (!lRooms.contains(i.toString()) && settings["enableRoom${i}"]) {
            if (!state."roomAsleepStatus${i}") {
                otherRoomAwake = true
                break
            }
        }
    }

    if (otherRoomAwake) {
        def motionActive = false
        if (settings.leadRoomMotionSensors) {
            settings.leadRoomMotionSensors.each { mSens ->
                if (mSens.currentValue("motion") == "active") {
                    motionActive = true
                }
            }
         }

        if (!motionActive) {
            logInfo("GOOD NIGHT OVERRIDE: Other rooms are awake but no motion detected. Forcing mode to ${settings.targetOverrideMode}.")
            setLocationMode(settings.targetOverrideMode)
        } else {
            logInfo("GOOD NIGHT OVERRIDE: Other rooms awake and motion detected. Skipping forced mode change, falling back to standard rules.")
        }
    } else {
        logInfo("GOOD NIGHT OVERRIDE: All enabled rooms are actually asleep. Letting standard Night mode logic handle it.")
    }
}

def evaluateGlobalMode(evt = null) {
    if (!enableGlobalMode) return
    def now = new Date()
    def currentMode = location.mode
    
    if (nightStartTime && nightEndTime && targetNightMode) {
        def nightStart = timeToday(nightStartTime, location.timeZone)
        def nightEnd = timeToday(nightEndTime, location.timeZone)
        
        def isNightWindow = false
        if (nightStart.time <= nightEnd.time) {
            isNightWindow = (now.time >= nightStart.time && now.time <= nightEnd.time)
        } else {
            isNightWindow = (now.time >= nightStart.time || now.time <= nightEnd.time)
        }
        
        if (debugLogEnable) log.debug "EVAL DEBUG: isNightWindow=${isNightWindow} | currentMode=${currentMode} | targetMode=${targetNightMode}"
        
        if (isNightWindow && currentMode != targetNightMode) {
            def isAllowedMode = true
            if (allowedNightModes) isAllowedMode = (allowedNightModes as List).contains(currentMode)
            
            if (debugLogEnable) log.debug "EVAL DEBUG: isAllowedMode=${isAllowedMode}"
            
            if (isAllowedMode) {
                def allAsleep = true
                def roomsChecked = 0
                for (int i = 1; i <= 4; i++) {
                    if (syncRooms && syncRooms.contains(i.toString())) {
                        roomsChecked++
                        if (!state."roomAsleepStatus${i}") allAsleep = false
                    }
                }
                if (roomsChecked == 0) allAsleep = false
                
                if (debugLogEnable) log.debug "EVAL DEBUG: allAsleep=${allAsleep} (Rooms Checked: ${roomsChecked})"
                
                if (allAsleep) {
                    def nDelay = settings.nightModeDelay != null ? settings.nightModeDelay.toInteger() : 60
                    if (!state.nightModeScheduledTime) {
                        state.nightModeScheduledTime = now.time + (nDelay * 1000)
                        logInfo("GLOBAL SYNC: Required rooms asleep. Scheduling Night Mode evaluation in ${nDelay} seconds.")
                        runIn(nDelay, "executeNightModeChange")
                    }
                } else {
                    if (state.nightModeScheduledTime) {
                        logInfo("GLOBAL SYNC: Timer Canceled. A required room is awake.")
                        unschedule("executeNightModeChange")
                        state.remove("nightModeScheduledTime")
                    }
                }
            } else {
                if (state.nightModeScheduledTime) {
                    logInfo("GLOBAL SYNC: Timer Canceled. Hub mode shifted out of the Safety Whitelist.")
                    unschedule("executeNightModeChange")
                    state.remove("nightModeScheduledTime")
                }
            }
        } else {
            if (state.nightModeScheduledTime) {
                unschedule("executeNightModeChange")
                state.remove("nightModeScheduledTime")
            }
        }
    }
    
    if (wakeStartTime && wakeEndTime && requireNightMode && targetWakeMode) {
         def wakeStart = timeToday(wakeStartTime, location.timeZone)
        def wakeEnd = timeToday(wakeEndTime, location.timeZone)
        
        def isWakeWindow = false
        if (wakeStart.time <= wakeEnd.time) {
            isWakeWindow = (now.time >= wakeStart.time && now.time <= wakeEnd.time)
        } else {
            isWakeWindow = (now.time >= wakeStart.time || now.time <= wakeEnd.time)
        }
        
        if (isWakeWindow && currentMode == requireNightMode) {
            def allAwake = true
            def anyRoomConfigured = false
            for (int i = 1; i <= 4; i++) {
                if (settings["enableRoom${i}"]) {
                    anyRoomConfigured = true
                    if (state."roomAsleepStatus${i}") allAwake = false
                }
            }
            if (anyRoomConfigured && allAwake) {
                 def wDelay = settings.wakeModeDelay != null ? settings.wakeModeDelay.toInteger() : 60
                if (!state.wakeModeScheduledTime) {
                    logInfo("GLOBAL SYNC: All Good Night switches are OFF. Scheduling Wake Mode (${targetWakeMode}) in ${wDelay} seconds.")
                    state.wakeModeScheduledTime = now.time + (wDelay * 1000)
                    runIn(wDelay, "executeWakeModeChange")
                }
            } else {
                unschedule("executeWakeModeChange")
                state.remove("wakeModeScheduledTime")
            }
        } else {
             unschedule("executeWakeModeChange")
             state.remove("wakeModeScheduledTime")
        }
     }
}

def executeRoomGoodNight(roomNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    state.remove("morningFadeInActive${roomNum}")
    unschedule("startMorningAudioFadeInRoom${roomNum}")
    unschedule("lightFadeCompleteRoom${roomNum}")
    unschedule("turnOffHoldReleaseRoom${roomNum}")
    unschedule("fanOffTwoRoom${roomNum}")
    unschedule("fanOffThreeRoom${roomNum}")
    unschedule("fanPowerOffRoom${roomNum}")
    unschedule("delayedOffRoom${roomNum}")

    def audioDelayMins = settings["audioStartDelay${roomNum}"] != null ? settings["audioStartDelay${roomNum}"].toInteger() : 8
    def audioDelaySecs = audioDelayMins * 60
    def lights = settings["roomLights${roomNum}"]
    
    // --- SCHEDULE DELAYED SHUT-OFF ---
    def delaySwitches = settings["delayedOffSwitches${roomNum}"]
    def delayMins = settings["delayedOffTime${roomNum}"]
    if (delaySwitches && delayMins) {
        logInfo("${rName}: Scheduled delayed shut-off for specific switches in ${delayMins} minutes.")
        runIn(delayMins * 60, "delayedOffRoom${roomNum}")
    }

    // --- SMOOTH LIGHT FADE (START OF SLEEP) ---
    def doLightFade = settings["enableLightFade${roomNum}"]
    def lightFadeMins = settings["lightFadeDuration${roomNum}"]

    if (doLightFade && lightFadeMins && lights) {
        logInfo("${rName}: Initiating Smooth Light Fade-Out for ${lightFadeMins} minutes.")
        state."lightFadeActive${roomNum}" = true
        
        lights.each { lgt ->
            if (isReadingLightActive(roomNum, lgt.id)) {
                logInfo("${rName}: Skipping light [${lgt.displayName}] (Reading Mode active).")
                return 
            }
            if (lgt.hasCommand("setLevel")) {
                lgt.setLevel(0, (lightFadeMins * 60).toInteger())
            } else {
                lgt.off()
            }
        }
        runIn((lightFadeMins * 60) + 5, "lightFadeCompleteRoom${roomNum}")
    } else if (lights) { 
        lights.each { lgt ->
            if (isReadingLightActive(roomNum, lgt.id)) {
                logInfo("${rName}: Skipping light [${lgt.displayName}] (Reading Mode active).")
                return 
            }
            if (lgt.hasCommand("setLevel")) {
                lgt.setLevel(1)
                pauseExecution(400)
            }
             lgt.off()
        }
        logInfo("${rName}: Lights turned OFF (w/ 1% flashbang protection if applicable).") 
    }
    
    def lightsOn = settings["roomLightsOn${roomNum}"]
    if (lightsOn) {
        lightsOn.each { lgt ->
            if (lgt.currentValue("switch") != "on") lgt.on()
        }
        logInfo("${rName}: Bedtime Lights/Plugs turned ON.")
    }
    
    def shadeContact = settings["shadeContact${roomNum}"]
    def shade = settings["roomShade${roomNum}"]
    if (shadeContact && shade && shadeContact.currentValue("contact") == "open") {
        shade.close()
        logInfo("${rName}: Shade contact is open. Closing shade.")
    }
    
    // --- AUDIO STARTUP ---
    def speakerPower = settings["roomSpeakerPower${roomNum}"]
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    
    if (settings["roomSpeaker${roomNum}"] || audioType == "switch") {
        if (speakerPower) {
            if (speakerPower.hasCommand("refresh")) {
                speakerPower.refresh()
                logInfo("${rName}: Refreshed speaker power plug state.")
                pauseExecution(1000) 
            }
            if (speakerPower.currentValue("switch") == "off") {
                speakerPower.on()
                def finalWait = Math.max(audioDelaySecs, 120)
                logInfo("${rName}: Speaker power plug is OFF. Turning ON and waiting ${finalWait}s before initiating audio play.")
                runIn(finalWait, "playDelayedAudioRoom${roomNum}")
            } else {
                if (audioDelaySecs > 0) {
                    logInfo("${rName}: Delaying audio start by ${audioDelayMins} minutes to allow Voice Butler TTS to finish.")
                    runIn(audioDelaySecs, "playDelayedAudioRoom${roomNum}")
                } else {
                    executeAudioPlay(roomNum)
                }
            }
        } else {
            if (audioDelaySecs > 0) {
                logInfo("${rName}: Delaying audio start by ${audioDelayMins} minutes to allow Voice Butler TTS to finish.")
                runIn(audioDelaySecs, "playDelayedAudioRoom${roomNum}")
            } else {
                executeAudioPlay(roomNum)
            }
         }
    }
    
    evaluateFans(roomNum)
}

// --- LIGHT FADE COMPLETE WRAPPERS ---
def lightFadeCompleteRoom1() { executeLightFadeComplete(1) }
def lightFadeCompleteRoom2() { executeLightFadeComplete(2) }
def lightFadeCompleteRoom3() { executeLightFadeComplete(3) }
def lightFadeCompleteRoom4() { executeLightFadeComplete(4) }

def executeLightFadeComplete(roomNum) {
    state.remove("lightFadeActive${roomNum}")
    def lights = settings["roomLights${roomNum}"]
    if (lights) {
        lights.each { lgt ->
            if (!isReadingLightActive(roomNum, lgt.id) && lgt.currentValue("switch") != "off") {
                lgt.off()
            }
        }
    }
    logInfo("${settings["roomName${roomNum}"] ?: "Room " + roomNum}: Smooth Light Fade-Out complete. Final OFF sent to ensure states.")
}

// --- DELAYED SHUT-OFF WRAPPERS ---
def delayedOffRoom1() { executeDelayedOff(1) }
def delayedOffRoom2() { executeDelayedOff(2) }
def delayedOffRoom3() { executeDelayedOff(3) }
def delayedOffRoom4() { executeDelayedOff(4) }

def executeDelayedOff(roomNum) {
    def delaySwitches = settings["delayedOffSwitches${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (delaySwitches) {
        delaySwitches.each { sw ->
            if (sw.currentValue("switch") != "off") {
                sw.off()
            }
        }
        logInfo("${rName}: Executed delayed shut-off for configured specific lights/plugs.")
    }
}

// --- AUDIO PLAY & INTERLOCKED FADE LOGIC ---
def playDelayedAudioRoom1() { executeAudioPlay(1) }
def playDelayedAudioRoom2() { executeAudioPlay(2) }
def playDelayedAudioRoom3() { executeAudioPlay(3) }
def playDelayedAudioRoom4() { executeAudioPlay(4) }

def executeAudioPlay(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"

    def doFadeIn = settings["enableAudioFadeIn${roomNum}"]
    def fadeInMins = settings["audioFadeInDuration${roomNum}"] ?: 0
    def fadeInStartVol = settings["audioFadeInStartVol${roomNum}"] ?: 1
    def overrideFadeInUri = settings["audioFadeInUri${roomNum}"]
    def setVol = settings["audioVolume${roomNum}"]

    if (speaker) {
        
        // Handle Target Start Volumes 
        if (doFadeIn && fadeInMins > 0) {
            speaker.setVolume(fadeInStartVol)
            logInfo("${rName}: Speaker volume forced to ${fadeInStartVol}% for Fade-In start.")
        } else if (setVol != null) {
            speaker.setVolume(setVol)
            logInfo("${rName}: Speaker volume forced to ${setVol}%.")
        }

        // Initialize Audio Track or Switch
        if (overrideFadeInUri) {
            speaker.playTrack(overrideFadeInUri)
            logInfo("${rName}: Playing Override Fade-In URI (${overrideFadeInUri}).")
        } else if (audioType == "uri") {
            def trackToPlay = state."nextUri${roomNum}"
            if (trackToPlay) {
                speaker.playTrack(trackToPlay)
                logInfo("${rName}: Playing tonight's Sonos URI (${trackToPlay}).")
                state."lastUri${roomNum}" = trackToPlay
            }
        } else if (audioType == "switch") {
            def switchToTurnOnId = state."nextSwitchId${roomNum}"
            if (switchToTurnOnId) {
                def targetSw = null
                for(int u = 1; u <= 5; u++) {
                    def sw = settings["audioSwitch${roomNum}_${u}"]
                    if (sw?.id == switchToTurnOnId) {
                        targetSw = sw
                        break
                    }
                }
                
                if (targetSw) {
                    targetSw.on()
                    logInfo("${rName}: Triggered Sonos Favorite Virtual Switch (${targetSw.displayName}).")
                    state."lastSwitchId${roomNum}" = switchToTurnOnId
                    
                    if (!doFadeIn && setVol != null) {
                        runIn(30, "applyDelayedVolumeRoom${roomNum}")
                    }
                }
            }
        }
        
        // Initialize Fade In Loop
        if (doFadeIn && fadeInMins > 0) {
            state."fadeInActive${roomNum}" = true
            state."fadeInStep${roomNum}" = 0
            state."fadeInMaxSteps${roomNum}" = fadeInMins
            state."fadeInStartVol${roomNum}" = fadeInStartVol
            state."fadeInTargetVol${roomNum}" = setVol ?: 30
            
            logInfo("${rName}: Started Audio Fade-In from ${fadeInStartVol}% to ${state."fadeInTargetVol${roomNum}"}% over ${fadeInMins} minutes.")
            runIn(60, "startAudioFadeInRoom${roomNum}")
        }
        
        // Initialize Sleep Timer & Fade Out
        def sTimer = settings["audioTimer${roomNum}"]
        if (sTimer) {
            def timerSecs = sTimer * 60
            def fadeEnabled = settings["enableAudioFade${roomNum}"]
            def fadeOutMins = settings["audioFadeDuration${roomNum}"] ?: 0
            
            if (fadeEnabled && fadeOutMins > 0) {
                if (fadeOutMins > sTimer) fadeOutMins = sTimer
                def fadeStartDelaySecs = (sTimer - fadeOutMins) * 60
                
                state."fadeActive${roomNum}" = true
                state."fadeStep${roomNum}" = 0
                state."fadeMaxSteps${roomNum}" = fadeOutMins
                state."fadeInitialVol${roomNum}" = setVol ?: 30
                
                def minVol = settings["audioFadeMinVol${roomNum}"] != null ? settings["audioFadeMinVol${roomNum}"] : 0
                
                if (fadeStartDelaySecs > 0) {
                    logInfo("${rName}: Sleep timer set for ${sTimer} mins. Fade-out to ${minVol}% will begin in ${sTimer - fadeOutMins} mins.")
                    runIn(fadeStartDelaySecs, "startAudioFadeRoom${roomNum}")
                } else {
                    logInfo("${rName}: Sleep timer set for ${sTimer} mins. Fade-out to ${minVol}% starting immediately.")
                    startAudioFadeRoom(roomNum)
                }
            } else {
                 logInfo("${rName}: Sleep timer set for ${sTimer} minutes (No fade-out scheduled).")
            }
            runIn(timerSecs, "stopAudioRoom${roomNum}") 
        }
    }
}

// --- FADE IN STEP LOGIC ---
def startAudioFadeInRoom1() { audioFadeInStepHandler(1) }
def startAudioFadeInRoom2() { audioFadeInStepHandler(2) }
def startAudioFadeInRoom3() { audioFadeInStepHandler(3) }
def startAudioFadeInRoom4() { audioFadeInStepHandler(4) }

def audioFadeInStepHandler(roomNum) {
    if (!state."fadeInActive${roomNum}") return
    
    def step = state."fadeInStep${roomNum}" + 1
    def maxSteps = state."fadeInMaxSteps${roomNum}" ?: 5
    def startVol = state."fadeInStartVol${roomNum}" ?: 1
    def targetVol = state."fadeInTargetVol${roomNum}" ?: 30
    def speaker = settings["roomSpeaker${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (step >= maxSteps) {
        state.remove("fadeInActive${roomNum}")
        if (speaker) speaker.setVolume(targetVol)
        logInfo("${rName}: Audio fade-in to ${targetVol}% complete.")
        return
    }
    
    state."fadeInStep${roomNum}" = step
    
    def volRange = targetVol - startVol
    def currentVol = Math.round(startVol + ((volRange.toDouble() / maxSteps) * step)).toInteger()
    
    if (speaker) speaker.setVolume(currentVol)
    runIn(60, "startAudioFadeInRoom${roomNum}")
}

// --- MORNING FADE IN STEP LOGIC ---
def startMorningAudioFadeInRoom1() { morningAudioFadeInStepHandler(1) }
def startMorningAudioFadeInRoom2() { morningAudioFadeInStepHandler(2) }
def startMorningAudioFadeInRoom3() { morningAudioFadeInStepHandler(3) }
def startMorningAudioFadeInRoom4() { morningAudioFadeInStepHandler(4) }

def morningAudioFadeInStepHandler(roomNum) {
    if (!state."morningFadeInActive${roomNum}") return
    
    def step = state."morningFadeInStep${roomNum}" + 1
    def maxSteps = state."morningFadeInMaxSteps${roomNum}" ?: 5
    def startVol = state."morningStartVol${roomNum}" ?: 1
    def targetVol = state."morningTargetVol${roomNum}" ?: 30
    def speaker = settings["roomSpeaker${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (step >= maxSteps) {
        state.remove("morningFadeInActive${roomNum}")
        if (speaker && speaker.hasCommand("setVolume")) speaker.setVolume(targetVol)
        logInfo("${rName}: Morning audio ramp-up to ${targetVol}% complete.")
        return
    }
    
    state."morningFadeInStep${roomNum}" = step
    
    def volRange = targetVol - startVol
    def currentVol = Math.round(startVol + ((volRange.toDouble() / maxSteps) * step)).toInteger()
    
    if (speaker && speaker.hasCommand("setVolume")) speaker.setVolume(currentVol)
    runIn(60, "startMorningAudioFadeInRoom${roomNum}")
}

// --- FADE OUT STEP LOGIC ---
def startAudioFadeRoom1() { audioFadeStepHandler(1) }
def startAudioFadeRoom2() { audioFadeStepHandler(2) }
def startAudioFadeRoom3() { audioFadeStepHandler(3) }
def startAudioFadeRoom4() { audioFadeStepHandler(4) }

def audioFadeStepHandler(roomNum) {
    if (!state."fadeActive${roomNum}") return
    
    def step = state."fadeStep${roomNum}" + 1
    def maxSteps = state."fadeMaxSteps${roomNum}" ?: 15
    def initialVol = state."fadeInitialVol${roomNum}" ?: 30
    def minVol = settings["audioFadeMinVol${roomNum}"] != null ? settings["audioFadeMinVol${roomNum}"].toInteger() : 0
    def speaker = settings["roomSpeaker${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (step >= maxSteps) {
        state.remove("fadeActive${roomNum}")
        if (speaker) speaker.setVolume(minVol)
        logInfo("${rName}: Audio fade-out to ${minVol}% complete. Waiting for stop command.")
        return
    }
    
    state."fadeStep${roomNum}" = step
    
    // Calculates a smooth step down from initialVol to the chosen minVol
    def volRange = initialVol - minVol
    def currentVol = Math.round(initialVol - ((volRange.toDouble() / maxSteps) * step)).toInteger()
    
    if (speaker) speaker.setVolume(currentVol)
    runIn(60, "startAudioFadeRoom${roomNum}")
}

def stopAudioRoom1() { executeAudioStop(1) }
def stopAudioRoom2() { executeAudioStop(2) }
def stopAudioRoom3() { executeAudioStop(3) }
def stopAudioRoom4() { executeAudioStop(4) }

def executeAudioStop(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    state.remove("fadeActive${roomNum}")
    unschedule("startAudioFadeRoom${roomNum}")
    
    if (speaker && speaker.hasCommand("stop")) {
        speaker.stop()
        logInfo("${rName}: Sleep timer reached. Audio stopped.")
    }
}

def applyDelayedVolumeRoom1() { executeDelayedVolume(1) }
def applyDelayedVolumeRoom2() { executeDelayedVolume(2) }
def applyDelayedVolumeRoom3() { executeDelayedVolume(3) }
def applyDelayedVolumeRoom4() { executeDelayedVolume(4) }

def executeDelayedVolume(roomNum) {
    def speaker = settings["roomSpeaker${roomNum}"]
    def setVol = settings["audioVolume${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (speaker && setVol != null) {
        speaker.setVolume(setVol)
        logInfo("${rName}: Applied delayed volume enforcement (${setVol}%) 30 seconds after Sonos Favorite start.")
    }
}

def endRoomGoodNight(roomNum) {
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    logInfo("${rName}: Executing Wake-Up routine (shutting down fans and restoring audio).")
    
    state.remove("fadeActive${roomNum}")
    state.remove("fadeInActive${roomNum}")
    state.remove("morningFadeInActive${roomNum}")
    state.remove("lightFadeActive${roomNum}")
    unschedule("lightFadeCompleteRoom${roomNum}")
    unschedule("startAudioFadeInRoom${roomNum}")
    unschedule("startMorningAudioFadeInRoom${roomNum}")
    unschedule("startAudioFadeRoom${roomNum}")
    unschedule("playDelayedAudioRoom${roomNum}")
    unschedule("stopAudioRoom${roomNum}")
    unschedule("applyDelayedFanSpeedRoom${roomNum}")
    unschedule("applyDelayedVolumeRoom${roomNum}")
    unschedule("delayedOffRoom${roomNum}")
    state.remove("pendingFanSpeed${roomNum}")
    
    def stdFans = settings["roomFans${roomNum}"]
    if (stdFans) stdFans.off()
    
    def lightsOn = settings["roomLightsOn${roomNum}"]
    if (lightsOn) {
        lightsOn.each { lgt ->
            if (lgt.currentValue("switch") != "off") lgt.off()
        }
        logInfo("${rName}: Bedtime Lights/Plugs turned OFF (Wake-up).")
    }
    
    def cFanSwitch = settings["ceilingFanSwitch${roomNum}"]
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    
    if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") {
        cFanSpeed.setSpeed("low") 
         logInfo("${rName}: Applying Low-Off Wiggle fix. Initiating 3x redundant shutdown sequence before cutting power.")
        runIn(2, "fanOffTwoRoom${roomNum}") 
    } else if (cFanSwitch) {
        cFanSwitch.off() 
    }

    def speaker = settings["roomSpeaker${roomNum}"]
    def doMorningAudio = settings["enableMorningAudio${roomNum}"]

    if (speaker) {
        if (doMorningAudio) {
            def mUri = settings["morningAudioUri${roomNum}"]
            def mSwitch = settings["morningAudioSwitch${roomNum}"]
            def startVol = settings["morningStartVol${roomNum}"] ?: 1
            def targetVol = settings["morningTargetVol${roomNum}"] ?: 30
            def duration = settings["morningFadeInDuration${roomNum}"] ?: 5
            
            if (speaker.hasCommand("setVolume")) speaker.setVolume(startVol)
            
            if (mUri && speaker.hasCommand("playTrack")) {
                speaker.playTrack(mUri)
                logInfo("${rName}: Playing Morning URI (${mUri}).")
            } else if (mSwitch) {
                mSwitch.on()
                logInfo("${rName}: Triggered Morning Sonos Favorite (${mSwitch.displayName}).")
            } else if (speaker.hasCommand("play")) {
                speaker.play() // Resumes whatever was paused
            }

            if (duration > 0) {
                state."morningFadeInActive${roomNum}" = true
                state."morningFadeInStep${roomNum}" = 0
                state."morningFadeInMaxSteps${roomNum}" = duration
                state."morningStartVol${roomNum}" = startVol
                state."morningTargetVol${roomNum}" = targetVol
                
                logInfo("${rName}: Started Morning Audio Ramp-Up from ${startVol}% to ${targetVol}% over ${duration} minutes.")
                runIn(60, "startMorningAudioFadeInRoom${roomNum}")
            } else {
                if (speaker.hasCommand("setVolume")) speaker.setVolume(targetVol)
            }

        } else {
            if (speaker.hasCommand("stop")) {
                speaker.stop()
            }
            // Restore volume for daytime use if no morning ramp-up is configured
            def setVol = settings["audioVolume${roomNum}"] ?: 30
            if (speaker.hasCommand("setVolume")) {
                speaker.setVolume(setVol)
                logInfo("${rName}: Restored speaker volume to ${setVol}% for daytime use.")
            }
        }
    }
    
    def holdRelease = settings["shadeHoldRelease${roomNum}"]
    if (holdRelease) {
        holdRelease.on()
        logInfo("${rName}: Sent hold release signal to Advanced Shade Controller. Auto-reset scheduled in 30s.")
        runIn(30, "turnOffHoldReleaseRoom${roomNum}")
    }
    
    prepNextAudio(roomNum) 
}

def turnOffHoldReleaseRoom1() { executeHoldReleaseOff(1) }
def turnOffHoldReleaseRoom2() { executeHoldReleaseOff(2) }
def turnOffHoldReleaseRoom3() { executeHoldReleaseOff(3) }
def turnOffHoldReleaseRoom4() { executeHoldReleaseOff(4) }

def executeHoldReleaseOff(roomNum) {
    def holdRelease = settings["shadeHoldRelease${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    if (holdRelease && holdRelease.currentValue("switch") != "off") {
        holdRelease.off()
        logInfo("${rName}: Hold release signal automatically reset to OFF.")
    }
}

def fanOffTwoRoom1() { executeFanOffTwo(1) }
def fanOffTwoRoom2() { executeFanOffTwo(2) }
def fanOffTwoRoom3() { executeFanOffTwo(3) }
def fanOffTwoRoom4() { executeFanOffTwo(4) }

def executeFanOffTwo(roomNum) {
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") cFanSpeed.setSpeed("off")
    runIn(2, "fanOffThreeRoom${roomNum}")
}

def fanOffThreeRoom1() { executeFanOffThree(1) }
def fanOffThreeRoom2() { executeFanOffThree(2) }
def fanOffThreeRoom3() { executeFanOffThree(3) }
def fanOffThreeRoom4() { executeFanOffThree(4) }

def executeFanOffThree(roomNum) {
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    if (cFanSpeed && cFanSpeed.hasCommand("setSpeed") && fType != "on_off") cFanSpeed.setSpeed("off") 
    runIn(2, "fanPowerOffRoom${roomNum}")
}

def fanPowerOffRoom1() { executeFanPowerOff(1) }
def fanPowerOffRoom2() { executeFanPowerOff(2) }
def fanPowerOffRoom3() { executeFanPowerOff(3) }
def fanPowerOffRoom4() { executeFanPowerOff(4) }

def executeFanPowerOff(roomNum) {
    def cFanSwitch = settings["ceilingFanSwitch${roomNum}"]
    if (cFanSwitch) {
        cFanSwitch.off()
        logInfo("${settings["roomName${roomNum}"] ?: "Room " + roomNum}: Ceiling fan power safely disconnected.")
    }
}

def tempHandler(evt) {
    for (int i = 1; i <= 4; i++) {
        if (settings["enableRoom${i}"] && settings["tempSensor${i}"]?.id == evt.device.id) {
            if (state."roomAsleepStatus${i}") {
                evaluateFans(i)
            }
        }
    }
}

def evaluateFans(roomNum) {
    def sensor = settings["tempSensor${roomNum}"]
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    def currentTemp = sensor ? sensor.currentValue("temperature") : null
    
    if (currentTemp != null) {
        def stdSetpoint = settings["fanSetpoint${roomNum}"]
        def stdFans = settings["roomFans${roomNum}"]
        if (stdSetpoint && stdFans) {
            if (currentTemp >= stdSetpoint) {
                stdFans.each { if (it.currentValue("switch") != "on") it.on() }
            } else {
                stdFans.each { if (it.currentValue("switch") != "off") it.off() }
            }
        }
    }
    
    def cFanSwitch = settings["ceilingFanSwitch${roomNum}"]
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def cFanSetpoint = settings["ceilingFanSetpoint${roomNum}"]
    def delta = settings["fanSpeedDelta${roomNum}"] ?: 1.0
    def fType = settings["fanType${roomNum}"] ?: "3_speed"
    
    if (cFanSwitch) {
        if (cFanSwitch.currentValue("switch") != "on") {
            cFanSwitch.on()
            logInfo("${rName}: Ceiling fan powered ON.")
            
            if (fType != "on_off" && cFanSpeed && cFanSetpoint && currentTemp != null) {
                def targetSpeed = calculateTargetSpeed(currentTemp, cFanSetpoint, delta, fType)
                logInfo("${rName}: Waiting 30 seconds before setting speed to ${targetSpeed.toUpperCase()}.")
                state."pendingFanSpeed${roomNum}" = targetSpeed
                runIn(30, "applyDelayedFanSpeedRoom${roomNum}")
            }
        } else {
            if (fType != "on_off" && cFanSpeed && cFanSetpoint && currentTemp != null && !state."pendingFanSpeed${roomNum}") {
                def targetSpeed = calculateTargetSpeed(currentTemp, cFanSetpoint, delta, fType)
                
                if (cFanSpeed.currentValue("speed") != targetSpeed) {
                    cFanSpeed.setSpeed(targetSpeed)
                    logInfo("${rName}: Ceiling fan dynamically adjusted to ${targetSpeed.toUpperCase()} (Temp: ${currentTemp}°, Setpoint: ${cFanSetpoint}°).")
                }
            }
        }
    }
}

def applyDelayedFanSpeedRoom1() { executeDelayedFanSpeed(1) }
def applyDelayedFanSpeedRoom2() { executeDelayedFanSpeed(2) }
def applyDelayedFanSpeedRoom3() { executeDelayedFanSpeed(3) }
def applyDelayedFanSpeedRoom4() { executeDelayedFanSpeed(4) }

def executeDelayedFanSpeed(roomNum) {
    def cFanSpeed = settings["ceilingFanSpeed${roomNum}"]
    def targetSpeed = state."pendingFanSpeed${roomNum}"
    def rName = settings["roomName${roomNum}"] ?: "Room ${roomNum}"
    
    if (cFanSpeed && targetSpeed) {
        cFanSpeed.setSpeed(targetSpeed)
        logInfo("${rName}: 30-second hardware warm-up complete. Ceiling fan speed safely set to ${targetSpeed.toUpperCase()}.")
        state.remove("pendingFanSpeed${roomNum}")
    }
}

def prepNextAudio(roomNum) {
    def audioType = settings["audioSourceType${roomNum}"] ?: "uri"
    
    if (audioType == "uri") {
        state.remove("nextSwitchId${roomNum}")
        
        def uris = []
        for(int u = 1; u <= 5; u++) {
            def uri = settings["audioUri${roomNum}_${u}"]
            if (uri) uris << uri
        }
        
        if (uris.size() > 0) {
            if (uris.size() == 1) {
                state."nextUri${roomNum}" = uris[0]
            } else {
                 def lastPlayed = state."lastUri${roomNum}"
                def availableUris = uris.findAll { it != lastPlayed }
                if (availableUris.size() == 0) availableUris = uris 
                def chosen = availableUris[new Random().nextInt(availableUris.size())]
                state."nextUri${roomNum}" = chosen
            }
        } else {
            state.remove("nextUri${roomNum}")
        }
        
    } else if (audioType == "switch") {
        state.remove("nextUri${roomNum}")
        
        def switches = []
        for(int u = 1; u <= 5; u++) {
            def sw = settings["audioSwitch${roomNum}_${u}"]
            if (sw) switches << sw.id
        }
        
        if (switches.size() > 0) {
            if (switches.size() == 1) {
                state."nextSwitchId${roomNum}" = switches[0]
             } else {
                def lastPlayed = state."lastSwitchId${roomNum}"
                def availableSwitches = switches.findAll { it != lastPlayed }
                if (availableSwitches.size() == 0) availableSwitches = switches 
                def chosen = availableSwitches[new Random().nextInt(availableSwitches.size())]
                 state."nextSwitchId${roomNum}" = chosen
            }
        } else {
            state.remove("nextSwitchId${roomNum}")
        }
    }
}

def logInfo(msg) {
    if (txtLogEnable) log.info "${app.label}: ${msg}"
    
    def hist = state.eventLog ?: []
    def timeStamp = new Date().format("MM/dd hh:mm:ss a", location.timeZone)
    hist.add(0, "[${timeStamp}] ${msg}")
    
    if (hist.size() > 20) hist = hist.take(20)
    state.eventLog = hist
}

def executeNightModeChange() {
    state.remove("nightModeScheduledTime")
    if (!targetNightMode) return

    def noMotion = true
    if (syncMotion) {
        syncMotion.each { mSens ->
            if (mSens.currentValue("motion") == "active") noMotion = false
        }
    }
    
    def noBlockingDevices = true
    if (blockingSwitches) {
        blockingSwitches.each { bSw ->
            if (bSw.currentValue("switch") == "on") noBlockingDevices = false
        }
    }

    if (noMotion && noBlockingDevices) {
        
        // --- SAFETY SWEEP HANDSHAKE ---
        if (enableSafetySweep) {
            def failedDevs = []
            if (sweepContacts) {
                sweepContacts.each { if (it.currentValue("contact") == "open") failedDevs << it.displayName }
            }
            if (sweepLocks) {
                sweepLocks.each { if (it.currentValue("lock") == "unlocked") failedDevs << it.displayName }
            }
            
            if (failedDevs.size() > 0) {
                def msg = "Night mode paused. Please check the ${failedDevs.join(', ')}."
                logInfo("SAFETY SWEEP FAILED: ${msg}")
                if (sweepSpeaker) sweepSpeaker.speak(msg)
                return 
            }
        }
        // ------------------------------
        
        logInfo("GLOBAL SYNC: Countdown complete. House is quiet and ready. Changing mode to ${targetNightMode}.")
        setLocationMode(targetNightMode)
    } else {
        logInfo("GLOBAL SYNC: Countdown complete, but Motion or Blockers are active. Waiting for them to clear...")
    }
}

def executeWakeModeChange() {
    state.remove("wakeModeScheduledTime")
    if (targetWakeMode) {
        logInfo("GLOBAL SYNC: Delay complete. Changing mode to ${targetWakeMode}.")
        setLocationMode(targetWakeMode)
    }
}
