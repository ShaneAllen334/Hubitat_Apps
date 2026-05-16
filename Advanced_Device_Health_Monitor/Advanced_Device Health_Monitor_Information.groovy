/**
 * Advanced Device Health Monitor Information (Driver)
 *
 * Author: ShaneAllen
 *
 */
metadata {
    definition (name: "Advanced Device Health Monitor Information", namespace: "ShaneAllen", author: "ShaneAllen") {
        capability "Sensor"
        
        attribute "issueCount", "number"
        attribute "htmlDashboard", "string"
        attribute "issueList", "string"
    }
}

def installed() {
    log.info "Advanced Device Health Monitor Information Driver Installed"
    sendEvent(name: "issueCount", value: 0)
    sendEvent(name: "htmlDashboard", value: "<div style='padding:10px;'>Awaiting initial health scan...</div>")
}

def updated() {
    log.info "Advanced Device Health Monitor Information Driver Updated"
}
