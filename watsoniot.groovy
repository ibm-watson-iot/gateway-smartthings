/**
 *  watsoniot.groovy
 *
 *  David Parker
 *  2016-02-01
 *
 *  Report SmartThings status to Watson IoT
 *
 *  Work in progress
 */
 
definition(
	name: "IBM Watson IoT Bridge",
	namespace: "",
	author: "David Parker",
	description: "Bridge from SmartThings to IBM Watson IoT",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	oauth: false
)

preferences {
	page(name: "watson_cfg", title: "Configure Target Organization", nextPage: "access_cfg", install:false) {
        section("Watson IoT Configuration ...") {
        	input "watson_iot_org", title: "Organization ID", required: true
            input "watson_iot_api_key", title: "API Key", required: true
            input "watson_iot_api_token", title: "Authorization Token", required: true
        }
    }
	page(name: "access_cfg", title: "Configure Device Access", install:true) {
        section("Allow Watson IoT to Access These Things ...") {
            input "d_switch", "capability.switch", title: "Switch", required: false, multiple: true
            input "d_motion", "capability.motionSensor", title: "Motion", required: false, multiple: true
            input "d_temperature", "capability.temperatureMeasurement", title: "Temperature", required: false, multiple: true
            input "d_contact", "capability.contactSensor", title: "Contact", required: false, multiple: true
            input "d_acceleration", "capability.accelerationSensor", title: "Acceleration", required: false, multiple: true
            input "d_presence", "capability.presenceSensor", title: "Presence", required: false, multiple: true
            input "d_battery", "capability.battery", title: "Battery", required: false, multiple: true
            input "d_threeAxis", "capability.threeAxis", title: "3 Axis", required: false, multiple: true
        }
    }
}


/*
 *  This function is called once when the app is installed
 */
def installed() {
	log.debug("Application Installing ...")
	
	initialize()
	log.debug("Application Install Complete")
}

/*
 *  This function is called every time the user changes their preferences
 */
def updated() {
	log.debug("Application Updating ...")
	
	unsubscribe()
	initialize()
	log.debug("Application Update Complete")
}


/*
 *  Subscribe to all events that we are interested in passing through to Watson IoT
 */
def initialize() {
	// This will throw an error after the first time, but good enough for now...
    // TODO: Check if device type already registered
	registerDeviceType()
    
	// Send current state to kick us off
    for (type in getDeviceTypes()) {
        for (device in type.value) {
        	registerDeviceIfNotExists(device.id)
        	def data = deviceStateToJson(device, type.key)
            publishEvent(data)
        }
    }

    subscribe(d_switch, "switch", "onDeviceEvent")
	subscribe(d_motion, "motion", "onDeviceEvent")
	subscribe(d_temperature, "temperature", "onDeviceEvent")
	subscribe(d_contact, "contact", "onDeviceEvent")
	subscribe(d_acceleration, "acceleration", "onDeviceEvent")
	subscribe(d_presence, "presence", "onDeviceEvent")
	subscribe(d_battery, "battery", "onDeviceEvent")
	subscribe(d_threeAxis, "threeAxis", "onDeviceEvent")
}

/*
 *  This function is called whenever something changes.
 */
def onDeviceEvent(evt) {
	// log.debug "_on_event XXX event.id=${evt?.id} event.deviceId=${evt?.deviceId} event.isStateChange=${evt?.isStateChange} event.name=${evt?.name}"
	
	def dt = getDeviceAndTypeForEvent(evt)
	if (!dt) {
		log.debug "onEvent deviceId=${evt.deviceId} not found"
		return;
	}
	
	def event = deviceStateToJson(dt.device, dt.type)
	log.debug "onEvent deviceId=${jd}"

	publishEvent(event)
}


/*
 *  Empty device handler
 */
def deviceHandler(evt) {
}


def getAuthHeader() {
	def auth = "${watson_iot_api_key}:${watson_iot_api_token}"
	def encoded = auth.encodeAsBase64()
	def authHeader = "Basic ${encoded}"
    
    return authHeader
}


/*
 *  Register a new device type in Watson IoT
 */
def registerDeviceType() {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types"
	def headers = ["Authorization": getAuthHeader()]
	def body = [
    	id: "smartthings"
    ]
    
	def params = [
		uri: uri,
		headers: headers,
        body: body
	]

	log.debug "registerDeviceType: params=${params}"
    
	try {
    	httpPostJson(params) { resp ->
        	//resp.headers.each {
            //	log.debug "${it.name} : ${it.value}"
        	//}
        	//log.debug "registerDeviceType: response data: ${resp.data}"
        	//log.debug "registerDeviceType: response contentType: ${resp.contentType}"
    	}
	} catch (e) {
    	// It's probably OK, we've likely already registered the "smartthings" device type
        
    	//log.debug "registerDeviceType: something went wrong: $e"
        //log.debug "registerDeviceType: watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
	}
}

/*
 *  Register a new device in Watson IoT
 */
def registerDeviceIfNotExists(deviceId) {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices/${deviceId}"
	def headers = ["Authorization": getAuthHeader()]

	def params = [
		uri: uri,
		headers: headers
	]

	log.debug "registerDeviceIfNotExists: params=${params}"
    
	try {
    	httpGet(params) { resp ->
        	// The device is already registered
            
        	//resp.headers.each {
            //	log.debug "${it.name} : ${it.value}"
        	//}
        	//log.debug "registerDeviceIfNotExists: response data: ${resp.data}"
        	//log.debug "registerDeviceIfNotExists: response contentType: ${resp.contentType}"
    	}
	} catch (e) {
        // That's okay, we probably haven't registered the device yet
        // TODO: actually parse the exception and verify it's a "Not Found"
        
    	log.debug "registerDeviceIfNotExists: something went wrong: $e"
        log.debug "registerDeviceIfNotExists: watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
        
        registerDevice(deviceId)
	}
}

/*
 *  Register a new device in Watson IoT
 */
def registerDevice(deviceId) {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices"
	def headers = ["Authorization": getAuthHeader()]
	def body = [
    	deviceId: deviceId
    ]
    
	def params = [
		uri: uri,
		headers: headers,
        body: body
	]

	log.debug "registerDevice: params=${params}"
    
	try {
    	httpPostJson(params) { resp ->
        	//resp.headers.each {
            //	log.debug "${it.name} : ${it.value}"
        	//}
        	//log.debug "registerDevice: response data: ${resp.data}"
        	//log.debug "registerDevice: response contentType: ${resp.contentType}"
    	}
	} catch (e) {
    	log.debug "registerDevice: something went wrong: $e"
        log.debug "registerDevice: watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
	}
}


/*
 *  Send information to Watson IoT
 *  See: https://docs.internetofthings.ibmcloud.com/swagger/v0002.html#!/Connectivity/post_application_types_deviceType_devices_deviceId_events_eventName
 */
def publishEvent(evt) {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/application/types/smartthings/devices/${evt.deviceId}/events/${evt.typeId}"
	def headers = ["Authorization": getAuthHeader()]
	def body = evt.value

	def params = [
		uri: uri,
		headers: headers,
		body: body
	]

	log.debug "publishEvent: params=${params}"
    
	try {
    	httpPostJson(params) { resp ->
        	//resp.headers.each {
            //	log.debug "${it.name} : ${it.value}"
        	//}
        	//log.debug "publishEvent: response data: ${resp.data}"
        	//log.debug "publishEvent: response contentType: ${resp.contentType}"
    	}
	} catch (e) {
    	// Watson IoT HTTP API does not return a body so this exception is thrown on EVERY event because SmartThings
        // can't be configured to copy with a no body response as far as I can tell.
        
    	//log.debug "publishEvent: something went wrong: $e"
        //log.debug "publishEvent: watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
	}
}



/* --- internals --- */
/*
 *  Devices and Types Dictionary
 */
def getDeviceTypes(){
	[ 
		switch: d_switch, 
		motion: d_motion, 
		temperature: d_temperature, 
		contact: d_contact,
		acceleration: d_acceleration,
		presence: d_presence,
		battery: d_battery,
		threeAxis: d_threeAxis
	]
}

def getDevicesByType(type) {
	getDeviceTypes()[type]
}

def getDeviceAndTypeForEvent(evt) {
	for (dt in getDeviceTypes()) {
		if (dt.key != evt.name) {
			continue
		}
		
		def devices = dt.value
		for (device in devices) {
			if (device.id == evt.deviceId) {
				return [ device: device, type: dt.key ]
			}
		}
	}
}


/*
 *  Parsing and conversion of device events
 */
private deviceStateToJson(device, type) {
	if (!device) {
		return;
	}

	def vd = [:]
    
	if (type == "switch") {
		def s = device.currentState('switch')
		vd['timestamp'] = s?.isoDate
		vd['switch'] = s?.value == "on"
	} else if (type == "motion") {
		def s = device.currentState('motion')
		vd['timestamp'] = s?.isoDate
		vd['motion'] = s?.value == "active"
	} else if (type == "temperature") {
		def s = device.currentState('temperature')
		vd['timestamp'] = s?.isoDate
		vd['temperature'] = s?.value.toFloat()
	} else if (type == "contact") {
		def s = device.currentState('contact')
		vd['timestamp'] = s?.isoDate
		vd['contact'] = s?.value == "closed"
	} else if (type == "acceleration") {
		def s = device.currentState('acceleration')
		vd['timestamp'] = s?.isoDate
		vd['acceleration'] = s?.value == "active"
	} else if (type == "presence") {
		def s = device.currentState('presence')
		vd['timestamp'] = s?.isoDate
		vd['presence'] = s?.value == "present"
	} else if (type == "battery") {
		def s = device.currentState('battery')
		vd['timestamp'] = s?.isoDate
		vd['battery'] = s?.value.toFloat() / 100.0;
	} else if (type == "threeAxis") {
		def s = device.currentState('threeAxis')
		vd['timestamp'] = s?.isoDate
		vd['x'] = s?.xyzValue?.x
		vd['y'] = s?.xyzValue?.y
		vd['z'] = s?.xyzValue?.z
	}
	
    return [typeId: type, deviceId: device.id, description: device.label, value: vd];
}