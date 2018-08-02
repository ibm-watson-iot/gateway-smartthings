/**
 *  watsoniot.groovy
 *
 *  David Parker
 *  2017-02-20
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
			input(name: "d_energy", type: "capability.energyMeter", title: "Energy Meter", required: false, multiple: true)
		}
	}
}

def getSettings() {
	return [ 
		version: "0.4.2"
	]
}

/*
 *  This function is called once when the app is installed
 */
def installed() {
	log.debug("IBM Watson IoT Bridge Installing ...")
	
	initialize()
	
	log.debug("IBM Watson IoT Bridge Install Complete")
	sendEvent(descriptionText:"IBM Watson IoT Bridge Installed", eventType:"SOLUTION_EVENT", displayed: true)
}

/*
 *  This function is called every time the user changes their preferences
 */
def updated() {
	log.debug("IBM Watson IoT Bridge Updating ...")
	unsubscribe()
	
	initialize()
	
	log.debug("IBM Watson IoT Bridge Update Complete")
	sendEvent(descriptionText:"IBM Watson IoT Bridge Updated", eventType:"SOLUTION_EVENT", displayed: true)
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
	def deviceList = [:]
	
	// Build a map of physical device model (by ID)
	// Devices with more than one capability will appear multiple times 
	for (type in getDeviceTypes()) {
		for (device in type.value) {
			if (deviceList.containsKey(device.id)) {
				deviceList[device.id].capabilities.add(type.key)
			} else {
				deviceList[device.id] = [id: device.id, name: device.name, label: device.label, capabilities: [type.key]]	
			}
		}
	}
	
	atomicState.deviceList = deviceList
	
	registerDevices()
}

def registerDevices() {
	log.info("RegisterDevices() - " + atomicState.deviceList.size() + " devices to register.")
	upsertDevices()
	
	log.info("RegisterDevices() - All devices registered, subscribing to device events")
	subscribeToAll()
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
	subscribe(d_energy, "energy", "onDeviceEvent")
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
		httpPostJson(params) //{ resp ->
			//resp.headers.each {
			//	log.debug "${it.name} : ${it.value}"
			//}
			//log.debug "registerDeviceType: response data: ${resp.data}"
			//log.debug "registerDeviceType: response contentType: ${resp.contentType}"
		//}
	} catch (e) {
		// It's probably OK, we've likely already registered the "smartthings" device type
		
		//log.debug "registerDeviceType: something went wrong: $e"
		//log.debug "registerDeviceType: watson_iot_api_key=${watson_iot_api_key},watson_iot_api_token=${watson_iot_api_token}"
	}
}

/*
 *  Register/Update all devices in Watson IoT
 */
def upsertDevices() {
	def uri = "https://${watson_iot_org}.internetofthings.ibmcloud.com/api/v0002/bulk/devices/upsert"
	def headers = ["Authorization": getAuthHeader()]
	def body = []
	
	// Build a single upsert statement 
	def deviceList = atomicState.deviceList
	
	for (deviceEntry in deviceList) {
		def device = deviceEntry.value
		def deviceUpsert = [
			deviceId: device.id,
			typeId: "smartthings",
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
		// Now add the record to our request body array
		body.push(deviceUpsert)
		log.trace("Device to upsert: " + deviceUpsert)
	}
	
	def params = [
		uri: uri,
		headers: headers,
		body: body
	]

	log.trace "upsertDevices: ${params.uri}"
	
	try {
		httpPostJson(params)
	} catch (e) {
		log.debug "upsertDevices: Something went wrong: $e"
	}
	
	log.debug("upsertDevices: Completed")
	sendEvent(descriptionText:"IBM Watson IoT Device Metadata Updated", eventType:"SOLUTION_EVENT", displayed: true)
}


/*
 *  Send information to Watson IoT
 *  See: https://docs.internetofthings.ibmcloud.com/swagger/v0002.html#!/Connectivity/post_application_types_deviceType_devices_deviceId_events_eventName
 * 
 *  Note on use of HTTP -- Unfortunately Samsung SmartThings Platform only supports TLS1.0, and Watson
 *  IoT Platform requires TLS1.1 or 1.2 .. yes, it's silly but that's the position we're in :)
 *  a) SmartThings does not support modern TLS
 *  b) Watson IoT Platform allows unencrypted, but does not allow an older version of
 *     TLS (which is still more secure than unencrypted) 
 */
def publishEvent(evt) {
	def uri = "http://${watson_iot_org}.messaging.internetofthings.ibmcloud.com/api/v0002/application/types/smartthings/devices/${evt.deviceId}/events/${evt.eventId}"
	def headers = ["Authorization": getAuthHeader()]

	def params = [
		uri: uri,
		headers: headers,
		body: evt.value
	]

	log.debug "publishEvent: ${evt}"
	
	try {
		httpPostJson(params) //{ resp ->
			//resp.headers.each {
			//	log.debug "${it.name} : ${it.value}"
			//}
			//log.debug "publishEvent: response data: ${resp.data}"
			//log.debug "publishEvent: response contentType: ${resp.contentType}"
		//}
	} catch (e) {
		log.debug "publishEvent: something went wrong: $e"
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
		water: d_waterLeak,
		energy: d_energy
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
		def s = device.currentState('power')
		vd['timestamp'] = s?.isoDate
		vd['power'] = s?.value.toDouble()
	} else if (eventName == "energy") {
		def s = device.currentState('energy')
		vd['timestamp'] = s?.isoDate
		vd['energy'] = s?.value.toDouble() 
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
