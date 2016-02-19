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
        	input(name: "watson_iot_org", title: "Organization ID", required: true)
            input(name: "watson_iot_api_key", title: "API Key", required: true)
            input(name: "watson_iot_api_token", title: "Authorization Token", required: true)
        }
    }
	page(name: "access_cfg", title: "Configure Device Access", install:true) {
        section("Allow Watson IoT to Access These Things ...") {
            input(name: "d_switch", type: "capability.switch", title: "Switch", required: false, multiple: true)
            input(name: "d_meter", type: "capability.powerMeter", title: "Power Meter", required: false, multiple: true)
            input(name: "d_motion", type: "capability.motionSensor", title: "Motion", required: false, multiple: true)
            input(name: "d_temperature", type: "capability.temperatureMeasurement", title: "Temperature", required: false, multiple: true)
            input(name: "d_contact", type: "capability.contactSensor", title: "Contact", required: false, multiple: true)
            input(name: "d_acceleration", type: "capability.accelerationSensor", title: "Acceleration", required: false, multiple: true)
            input(name: "d_presence", type: "capability.presenceSensor", title: "Presence", required: false, multiple: true)
            input(name: "d_battery", type: "capability.battery", title: "Battery", required: false, multiple: true)
            input(name: "d_threeAxis", type: "capability.threeAxis", title: "3 Axis", required: false, multiple: true)
        }
    }
}

def getSettings() {
    return [ 
        version: "0.2.0"
    ]
}

/*
 *  This function is called once when the app is installed
 */
def installed() {
	log.debug("Application Installing ...")
	
    // Delay the initialization to avoid holding up the install
	runIn(60, initialize)
	log.debug("Application Install Complete")
}

/*
 *  This function is called every time the user changes their preferences
 */
def updated() {
	log.debug("Application Updating ...")
	
	unsubscribe()
    // Delay the initialization to avoid holding up the update
	runIn(60, initialize)
	log.debug("Application Update Complete")
}


/*
 *  Subscribe to all events that we are interested in passing through to Watson IoT
 */
def initialize() {
	// This will throw an error after the first time, but good enough for now...
    // TODO: Check if device type already registered
	registerDeviceType()

	// Build a map of physical device model (by ID)
    // Devices with more than one capability will appear multiple times 
	def deviceList = [:]
    def deviceCapabilities = [:]
    
    for (type in getDeviceTypes()) {
        for (device in type.value) {
        	if (deviceList.containsKey(device.id)) {
            	deviceCapabilities[device.id].add(type.key)
            } else {
            	deviceList[device.id] = device	
        		deviceCapabilities[device.id] = [type.key]
            }
        }
    }
    
    for (entry in deviceList) {
    	def id = entry.key
		registerDeviceIfNotExists(deviceList[id], deviceCapabilities[id])
	}

	syncEverything()
	
    subscribe(d_switch, "switch", "onDeviceEvent")
    subscribe(d_meter, "power", "onDeviceEvent")
	subscribe(d_motion, "motion", "onDeviceEvent")
	subscribe(d_temperature, "temperature", "onDeviceEvent")
	subscribe(d_contact, "contact", "onDeviceEvent")
	subscribe(d_acceleration, "acceleration", "onDeviceEvent")
	subscribe(d_presence, "presence", "onDeviceEvent")
	subscribe(d_battery, "battery", "onDeviceEvent")
	subscribe(d_threeAxis, "threeAxis", "onDeviceEvent")
	
	// Register the syncEverything() method to run every 30 minutes
	runEvery5Minutes(syncEverything)
}

/*
 *  Push current state of everything to Watson IoT.  This will be ran every 
 *  30 minutes to supplement the data sent by the event handler.
 */
def syncEverything() {
	// Send current state for all capabilities of all devices
    for (type in getDeviceTypes()) {
        for (device in type.value) {
        	def data = deviceStateToJson(device, type.key)
            publishEvent(data)
        }
    }
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
 *  Register a new device in Watson IoT if it doesn't already exist
 */
def registerDeviceIfNotExists(device, deviceCapabilities) {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices/${device.id}"
	def headers = ["Authorization": getAuthHeader()]

	def params = [
		uri: uri,
		headers: headers
	]

	log.debug "registerDeviceIfNotExists: params=${params}"
    
	try {
    	httpGet(params) { resp ->
        	// The device is already registered
            
			// Update the location
			// TODO: Check whether an update is even needed!
			updateDevice(device, deviceCapabilities)
			
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
        
        registerDevice(device, deviceCapabilities)
	}
}

/*
 *  Register a new device in Watson IoT
 */
def registerDevice(device, deviceCapabilities) {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices"
	def headers = ["Authorization": getAuthHeader()]
	def body = [
    	deviceId: device.id,
    	deviceInfo: [
			descriptiveLocation: location.name,
			deviceClass: device.name,
			description: device.label
		],
		location: [
			latitude: location.latitude,
			longitude: location.longitude
		],
        metadata: [
        	smartthings: [
            	bridge: [
                	version: getSettings().version
                ],
                capabilities: deviceCapabilities
            ]
        ]
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
 *  Update the location details in Watson IoT.  This could/should be done more
 *  intelligently in the future ... we could easy check if there's no change
 *  instead of blindly updating the location regardless
 */
def updateDevice(device, deviceCapabilities) {
	// Update device info
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices/${device.id}"
	def headers = ["Authorization": getAuthHeader()]
	def body = [
    	deviceInfo: [
			descriptiveLocation: location.name,
			deviceClass: device.name,
			description: device.label
		],
        metadata: [
        	smartthings: [
            	bridge: [
                	version: getSettings().version
                ],
                capabilities: deviceCapabilities
            ]
        ]
    ]
    
	def params = [
		uri: uri,
		headers: headers,
        body: body
	]

	log.debug "updateDevice (1): params=${params}"
    
	try {
    	httpPutJson(params)
	} catch (e) {
    	log.debug "updateDevice (1): something went wrong: $e"
        log.debug "updateDevice (1): watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
	}
	
	// Update location
	def uri2 = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices/${device.id}/location"
	def body2 = [
    	latitude: location.latitude,
		longitude: location.longitude
    ]
    
	def params2 = [
		uri: uri2,
		headers: headers,
        body: body2
	]

	log.debug "updateDevice (2): params=${params2}"
    
	try {
    	httpPutJson(params2)
	} catch (e) {
    	log.debug "updateDevice (2): something went wrong: $e"
        log.debug "updateDevice (2): watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
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


/*
 *  Devices and Types Dictionary
 */
def getDeviceTypes(){
	[ 
		switch: d_switch, 
		meter: d_meter, 
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
	} else if (type == "meter") {
		def p = device.currentState('power')
		vd['power'] = p?.value.toDouble()
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