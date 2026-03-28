/**
 * Advanced Meteorologist Report Device
 *
 * Author: ShaneAllen
 */

metadata {
    definition (name: "Advanced Meteorologist Report Device", namespace: "ShaneAllen", author: "ShaneAllen") {
        capability "Sensor"
        capability "Actuator"
        
        // Granular Attributes for Rule Machine / Automations
        attribute "meteorologistScript", "string"
        attribute "currentTemp", "number"
        attribute "currentConditions", "string"
        attribute "todayHigh", "number"
        attribute "todayLow", "number"
        
        // Dashboard HTML Tiles
        attribute "htmlTile_Compact", "string"
        attribute "htmlTile_Extended", "string"
    }
}

def updateTile(script, currentTemp, currentConditions, dates, highs, lows, conditions) {
    // 1. Update Standard Attributes
    sendEvent(name: "meteorologistScript", value: script)
    sendEvent(name: "currentTemp", value: currentTemp)
    sendEvent(name: "currentConditions", value: currentConditions)
    
    def tHigh = highs ? highs[0] : "--"
    def tLow = lows ? lows[0] : "--"
    sendEvent(name: "todayHigh", value: tHigh)
    sendEvent(name: "todayLow", value: tLow)
    
    // 2. Build Compact HTML Tile (Best for 1x1 or 2x1 standard grids)
    def compactHtml = """
    <div style='background-color:#1e1e1e; color:#ffffff; padding:10px; border-radius:8px; font-family:sans-serif; height:100%; box-sizing:border-box;'>
        <div style='font-size:15px; font-weight:bold; border-bottom:1px solid #444; padding-bottom:5px; margin-bottom:8px;'>
            🌦️ Weather Anchor
        </div>
        <div style='font-size:13px; margin-bottom:8px; color:#4dabf7;'>
            <b>${currentConditions?.capitalize()}</b> | ${currentTemp}° (H: ${tHigh}° L: ${tLow}°)
        </div>
        <div style='font-size:12px; font-style:italic; line-height:1.4; color:#cccccc; overflow:hidden;'>
            "${script}"
        </div>
    </div>
    """
    sendEvent(name: "htmlTile_Compact", value: compactHtml)
    
    // 3. Build Extended HTML Tile (Best for larger grids, includes multi-day forecast)
    def forecastRow = ""
    if (dates && dates.size() >= 5) {
        // Loop through the next 4 days (Index 1 to 4) so it fits beautifully on a tile
        for (int i = 1; i <= 4; i++) {
            def dayName = getDayOfWeek(dates[i])
            forecastRow += "<td style='padding:4px; border-left:1px solid #333;'><b style='color:#ccc;'>${dayName}</b><br><span style='color:#ff6b6b;'>${highs[i]}°</span><br><span style='color:#4dabf7;'>${lows[i]}°</span></td>"
        }
    }
    
    def extendedHtml = """
    <div style='background-color:#1e1e1e; color:#ffffff; padding:10px; border-radius:8px; font-family:sans-serif; height:100%; box-sizing:border-box; display:flex; flex-direction:column; justify-content:space-between;'>
        <div>
            <div style='font-size:15px; font-weight:bold; border-bottom:1px solid #444; padding-bottom:5px; margin-bottom:8px;'>
                🌦️ Weather Anchor
            </div>
            <div style='font-size:12px; font-style:italic; line-height:1.3; color:#cccccc; margin-bottom:10px;'>
                "${script}"
            </div>
        </div>
        <table style='width:100%; text-align:center; font-size:11px; border-top:1px solid #444; padding-top:5px; table-layout:fixed; border-collapse:collapse;'>
            <tr>
                <td style='padding:4px;'><b style='color:#ccc;'>Today</b><br><span style='color:#ff6b6b;'>${tHigh}°</span><br><span style='color:#4dabf7;'>${tLow}°</span></td>
                ${forecastRow}
            </tr>
        </table>
    </div>
    """
    sendEvent(name: "htmlTile_Extended", value: extendedHtml)
}

// Helper to convert date strings to Short Day Names (Mon, Tue, Wed) for the dashboard
def getDayOfWeek(dateString) {
    try {
        def date = Date.parse("yyyy-MM-dd", dateString)
        return date.format("EEE")
    } catch (e) {
        return "N/A"
    }
}
