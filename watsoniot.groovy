/**
 *  watsoniot.groovy
 *
 *  David Parker
 *  2016-10-18
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
			input(name: "d_waterLeak", type: "capability.waterSensor", title: "Leak", required: false, multiple: true)
		}
	}
}

def getSettings() {
	return [ 
		version: "0.3.3"
	]
}

/*
 *  This function is called once when the app is installed
 */
def installed() {
	log.debug("Application Installing ...")
	
	// Delay the initialization to avoid holding up the install
	initialize()
	
	log.debug("Application Install Complete")
}

/*
 *  This function is called every time the user changes their preferences
 */
def updated() {
	log.debug("Application Updating ...")
	
	unsubscribe()
	
	// Delay the initialization to avoid holding up the update
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

	// It may seem a bit roundabout, however: http://community.smartthings.com/t/atomicstate-not-working/27827/5
	// I believe that the atomicState is only persisted when something on the root object changes. Since this has
	// to happen immediately, and it could be expensive to watch every single child property/object. So for 
	// instance in your situation you need to reassign the property on atomicState to the value you changed it to.
	def deviceIds = []
	def deviceList = [:]
	
	// Build a map of physical device model (by ID)
	// Devices with more than one capability will appear multiple times 
	for (type in getDeviceTypes()) {
		for (device in type.value) {
			if (deviceList.containsKey(device.id)) {
				deviceList[device.id].capabilities.add(type.key)
			} else {
				deviceIds.push(device.id)
				deviceList[device.id] = [id: device.id, name: device.name, label: device.label, capabilities: [type.key]]	
			}
		}
	}
	
	atomicState.deviceIds = deviceIds
	atomicState.deviceList = deviceList
	atomicState.registeredDeviceIds = []
	
	// For better user experience delay the device registration process
	runIn(60, registerDevices)
	
	// Disable this for now, introduces timeouts due to taking > 20 seconds when lots of devices are active
	// syncEverything()
	
	// Register the syncEverything() method to run every 30 minutes
	// runEvery5Minutes(syncEverything)
}

def registerDevices() {
	def maxDeviceRegistrations = 4
	def loopCounter = 0
	def deviceIds = atomicState.deviceIds
	def registeredDeviceIds = atomicState.registeredDeviceIds

	log.info("RegisterDevices() - " + deviceIds.size() + " devices to register.  Registering batches of ${maxDeviceRegistrations}")

	while(deviceIds.size() > 0 && loopCounter < maxDeviceRegistrations) { 
		def id = deviceIds.pop()
		def device = atomicState.deviceList[id]
		
		registerDeviceIfNotExists(device)
		
		for (capability in device.capabilities) {
	        def actualDevice = getDeviceCapability(device.id, capability)
			def data = deviceStateToJson(actualDevice, capability)
			publishEvent(data)
	    }
		
		registeredDeviceIds.add(id)
		
		loopCounter ++
	}
	
	atomicState.deviceIds = deviceIds
	atomicState.registeredDeviceIds = registeredDeviceIds
	
	// Schedule registration of the next 5 devices
	if (deviceIds.size() > 0) {
		log.info("RegisterDevices() - Device registration will resume in 1 minute")
		runIn(60, registerDevices)
	}
	else {
		log.info("RegisterDevices() - All devices registered, subscribing to device events")
		runIn(60, subscribeToAll)        
	}    
}


def subscribeToAll() {
	subscribe(d_switch, "switch", "onDeviceEvent")
	subscribe(d_power, "power", "onDeviceEvent")
	subscribe(d_motion, "motion", "onDeviceEvent")
	subscribe(d_temperature, "temperature", "onDeviceEvent")
	subscribe(d_contact, "contact", "onDeviceEvent")
	subscribe(d_acceleration, "acceleration", "onDeviceEvent")
	subscribe(d_presence, "presence", "onDeviceEvent")
	subscribe(d_battery, "battery", "onDeviceEvent")
	subscribe(d_threeAxis, "threeAxis", "onDeviceEvent")
	subscribe(d_waterLeak, "water", "onDeviceEvent")
}

/*
 *  Push current state of everything to Watson IoT over time.  Iterations start every 
 *  30 minutes to supplement the data sent by the event handler.  Each iteration 
 *  is broken into stages to avoid timeouts if the bridge is working with large 
 *  numbers of devices.
 */
def syncEverything() {
	def maxDeviceSyncs = 5
	def loopCounter = 0

	def deviceIds = atomicState.deviceIds

	log.info("syncEverything() - " + deviceIds.size() + " devices to sync.  Syncing batches of ${maxDeviceSyncs}")

	while(deviceIds.size() > 0 && loopCounter < maxDeviceSyncs) { 
		def id = deviceIds.pop()
		def device = atomicState.deviceList[id]
		
		for (capability in device.capabilities) {
	        def actualDevice = getDeviceCapability(device.id, capability)
			def data = deviceStateToJson(actualDevice, capability)
			publishEvent(data)
	    }
		
		loopCounter ++
	}
	
	atomicState.deviceIds = deviceIds
	
	// Schedule sync of the next batch of devices
	if (deviceIds.size() > 0) {
		log.info("syncEverything() - Device Sync will resume in 1 minute")
		runIn(60, syncEverything)
	}
	else {
		log.info("syncEverything() - All devices synced, scheduling resync in ~ 30 minutes")
		runIn(1800, syncEverything)
	}
}


/*
 *  This function is called whenever something changes.
 */
def onDeviceEvent(evt) {
	log.debug "onDeviceEvent(${evt.name} / ${evt.device})"
	def event = deviceStateToJson(evt.device, evt.name)

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

	log.trace "registerDeviceType: params=${params}"
	
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
def registerDeviceIfNotExists(device) {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices/${device.id}"
	def headers = ["Authorization": getAuthHeader()]

	def params = [
		uri: uri,
		headers: headers
	]

	log.trace "registerDeviceIfNotExists: params=${params}"
	
	try {
		httpGet(params) { resp ->
			// The device is already registered
			
			// Update the location
			// TODO: Check whether an update is even needed!
			updateDevice(device)
			
			//resp.headers.each {
			//	log.debug "${it.name} : ${it.value}"
			//}
			//log.debug "registerDeviceIfNotExists: response data: ${resp.data}"
			//log.debug "registerDeviceIfNotExists: response contentType: ${resp.contentType}"
		}
	} catch (e) {
		// That's okay, we probably haven't registered the device yet
		// TODO: actually parse the exception and verify it's a "Not Found"
		
		registerDevice(device)
	}
}

/*
 *  Register a new device in Watson IoT
 */
def registerDevice(device) {
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
				capabilities: device.capabilities
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
def updateDevice(device) {
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
				capabilities: device.capabilities
			]
		]
	]
	
	def params = [
		uri: uri,
		headers: headers,
		body: body
	]

	log.trace "updateDevice (1): params=${params}"
	
	try {
		httpPutJson(params)
	} catch (e) {
		log.debug "updateDevice (1): something went wrong: $e"
		log.debug "updateDevice (1): watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
	}
	
	// Update location
	def uri2 = "https://${watson_iot_org}.messaging.internetofthings.ibmcloud.com/api/v0002/device/types/smartthings/devices/${device.id}/location"
	def body2 = [
		latitude: location.latitude,
		longitude: location.longitude
	]
	
	def params2 = [
		uri: uri2,
		headers: headers,
		body: body2
	]

	log.trace "updateDevice (2): params=${params2}"
	
	try {
		httpPutJson(params2)
	} catch (e) {
		log.warning "updateDevice (2): something went wrong: $e"
	}
}


/*
 *  Send information to Watson IoT
 *  See: https://docs.internetofthings.ibmcloud.com/swagger/v0002.html#!/Connectivity/post_application_types_deviceType_devices_deviceId_events_eventName
 */
def publishEvent(evt) {
	def uri = "https://${watson_iot_org}.messaging.internetofthings.ibmcloud.com/api/v0002/application/types/smartthings/devices/${evt.deviceId}/events/${evt.eventId}"
	def headers = ["Authorization": getAuthHeader()]

	def params = [
		uri: uri,
		headers: headers,
		body: evt.value
	]

	log.debug "publishEvent: ${evt}"
	
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
		// can't be configured to cope with a no body response as far as I can tell.
		
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
		power: d_power, 
		motion: d_motion, 
		temperature: d_temperature, 
		contact: d_contact,
		acceleration: d_acceleration,
		presence: d_presence,
		battery: d_battery,
		threeAxis: d_threeAxis,
		water: d_waterLeak
	]
}

def getDevicesByCapability(capability) {
	getDeviceTypes()[capability]
}

def getDeviceCapability(deviceId, capability) {
	def devices = getDevicesByCapability(capability)
	
	for (device in devices) {
		if (device.id == deviceId) {
			return device
		}
	}
}

/*
 *  Parsing and conversion of device events
 */
private deviceStateToJson(device, eventName) {
	if (!device) {
		return;
	}

	def vd = [:]
	
	if (eventName == "switch") {
		def s = device.currentState('switch')
		vd['timestamp'] = s?.isoDate
		vd['switch'] = s?.value == "on"
	} else if (eventName == "power") {
		def p = device.currentState('power')
		vd['timestamp'] = s?.isoDate
		vd['power'] = p?.value.toDouble()
	} else if (eventName == "motion") {
		def s = device.currentState('motion')
		vd['timestamp'] = s?.isoDate
		vd['motion'] = s?.value == "active"
	} else if (eventName == "temperature") {
		def s = device.currentState('temperature')
		vd['timestamp'] = s?.isoDate
		vd['temperature'] = s?.value.toFloat()
	} else if (eventName == "contact") {
		def s = device.currentState('contact')
		vd['timestamp'] = s?.isoDate
		vd['contact'] = s?.value == "closed"
	} else if (eventName == "acceleration") {
		def s = device.currentState('acceleration')
		vd['timestamp'] = s?.isoDate
		vd['acceleration'] = s?.value == "active"
	} else if (eventName == "presence") {
		def s = device.currentState('presence')
		vd['timestamp'] = s?.isoDate
		vd['presence'] = s?.value == "present"
	} else if (eventName == "battery") {
		def s = device.currentState('battery')
		vd['timestamp'] = s?.isoDate
		vd['battery'] = s?.value.toFloat() / 100.0;
	} else if (eventName == "threeAxis") {
		def s = device.currentState('threeAxis')
		vd['timestamp'] = s?.isoDate
		vd['x'] = s?.xyzValue?.x
		vd['y'] = s?.xyzValue?.y
		vd['z'] = s?.xyzValue?.z
	} else if (eventName == "water") {
		def s = device.currentState('water')
		vd['timestamp'] = s?.isoDate
		vd['water'] = s?.value == "wet"
	} 
	
	return [eventId: eventName, deviceId: device.id, value: vd];
}
