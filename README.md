# IoT Gateway Application for Samsung SmartThings

Connect your Samsung SmartThings to Watson IoT

- [IBM Watson IoT](https://internetofthings.ibmcloud.com)
- [Samsung SmartThings](https://www.smartthings.com)


## Product Withdrawal Notice
Per the September 8, 2020 [announcement](https://www-01.ibm.com/common/ssi/cgi-bin/ssialias?subtype=ca&infotype=an&appname=iSource&supplier=897&letternum=ENUS920-136#rprodnx) IBM Watson IoT Platform (5900-A0N) has been withdrawn from marketing effective **December 9, 2020**.


## Setup

- Log in to your account on [SmartThings/dev](http://developer.smartthings.com/)
- Create a new SmartApp
- Click ``From Code`` and paste in the content of ``watsoniot.groovy`` from this repository
- Click ``Create``
- Click ``Save``
- Click ``Publish``

## Configuration

Using the SmartThings mobile application navigate to ``Marketplace > SmartApps > My Apps``, here you will find the application ``IBM Watson IoT Bridge`` that we created previously.

### Configure Target Organization

You will need to have generated an API key from your Watson IoT dashboard (or have been provided one by whoever oversees the Watson IoT organization you wish to connect to).  

The API key requires the following permissions:
- Create Devices
- Update Devices
- View Devices
- Publish/send an event

Of the default roles, both `Standard Application` and `Backend Trusted Application` are suitable for use with this application, providing the abilty to register devices and submit events on behalf of devices.

![SmartApp Configuration 1](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/app_cfg_1.jpg "SmartApp Configuration 1")

### Configure Device Access

You also need to explicitly grant access to each individual device capability that you want to Watson IoT to work with
![SmartApp Configuration 2](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/app_cfg_2.jpg "SmartApp Configuration 2")

### Verification
After the setup is complete you should be able to see the application listed when you navigate to ``My Home > SmartApps``

![SmartApp](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/app.jpg "SmartApp")

## Metadata and Device Registration

- Devices are automatically registered in Watson IoT
  - All SmartThings are created with a ``typeId`` of ``smartthings``
  - The unique identifier of each device is used as the ``deviceId``
  - The class of SmartThing is recorded in ``deviceInfo.deviceClass`` (e.g. "Motion Sensor", "Multipurpose Sensor")
- Device metadata is synced to Watson IoT:
  - The custom label you applied to each device is recorded in ``deviceInfo.description``
  - The name of your location is recorded in ``deviceInfo.descriptiveLocation`` (e.g. "My Home")
  - The version of the bridge is recorded in ``metadata.smartthings.bridge.version``
  - The capabilities of each device are recorded as an array in ``metadata.smartthings.capabilities``
  - The longitude and latitude for the center of your geofence is recorded in each device's location record
  - Device metadata updates occur when the application is installed or reconfigured

### Sample
  
```json
  {
    "clientId": "d:abc123:smartthings:a0c9872a-afad-49e4-8201-3adf98a09c5e",
    "typeId": "smartthings",
    "deviceId": "a0c9872a-afad-49e4-8201-3adf98a09c5e",
    "deviceInfo": {
      "deviceClass": "Water Leak Sensor",
      "description": "Bathroom Window",
      "descriptiveLocation": "Home"
    },
    "metadata": {
      "smartthings": {
        "bridge": {
          "version": "0.2.0"
        },
        "capabilities": [
          "temperature",
          "battery"
        ]
      }
    }
  }
```

![IBM Watson IoT Dashboard](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/dashboard.jpg "IBM Watson IoT Dashboard")

## Events

- Device events are streamed in real time into Watson IoT

State updates are published to Watson IoT in real time.  The events sent by any device are defined by it's capabilities.  Watson IoT eventId's match 1:1 with SmartThings capabilities, all events are sent in JSON format.  

SmartThings often represents boolean (true or false) states as a string (e.g. on|off, active|inactive). The Watson IoT bridge converts these into simple boolean values to ease analytics and rule generation:

- SmartThings acceleration state: ``active`` is converted to an acceleration event with value of ``true``
- SmartThings contact state: ``closed`` is converted to a contact event with value of ``true``
- SmartThings motion state: ``active`` is converted to a motion event with value of ``true``
- SmartThings presence state: ``present`` is converted to a presence event with value of ``true``
- SmartThings switch state: ``on`` is converted to a switch event with value of ``true``
- SmartThings water state: ``wet`` is converted to a water event with value of ``true``

If a SmartThings device has multiple capabilities it will emit multiple events into Watson IoT.  For example, a multipurpose sensor will emit up to four different events based on the level of access granted to the bridge application: acceleration, battery, contact & temperature

### Acceleration Sensor Event
- [SmartThings Capabilities Reference: Acceleration Sensor](http://docs.smartthings.com/en/latest/capabilities-reference.html#acceleration-sensor)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "acceleration": true
}
```

### Battery Event
- [SmartThings Capabilities Reference: Battery](http://docs.smartthings.com/en/latest/capabilities-reference.html#battery)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "battery": 0.83
}
```

### Contact Sensor Event
- [SmartThings Capabilities Reference: Contact Sensor](http://docs.smartthings.com/en/latest/capabilities-reference.html#contact-sensor)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "contact": true
}
```

### Motion Sensor Event
- [SmartThings Capabilities Reference: Motion Sensor](http://docs.smartthings.com/en/latest/capabilities-reference.html#motion-sensor)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "motion": true
}
```

### Power Meter Event
- [SmartThings Capabilities Reference: Power Meter](http://docs.smartthings.com/en/latest/capabilities-reference.html#power-meter)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "power": 3.0
}
```

### Presence Sensor Event
- [SmartThings Capabilities Reference: Presence Sensor](http://docs.smartthings.com/en/latest/capabilities-reference.html#presence-sensor)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "presence": true
}
```

### Switch Event
- [SmartThings Capabilities Reference: Switch](http://docs.smartthings.com/en/latest/capabilities-reference.html#switch)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "switch": true
}
```

### Temperature Measurement Event
- [SmartThings Capabilities Reference: Temperature Measurement](http://docs.smartthings.com/en/latest/capabilities-reference.html#temperature-measurement)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "temperature": 15
}
```

### Three Axis Event
- [SmartThings Capabilities Reference: Three Axis](http://docs.smartthings.com/en/latest/capabilities-reference.html#three-axis)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "x": 1,
  "y": 50,
  "z": 62
}
```
### Water Leak Event
- [SmartThings Capabilities Reference: Three Axis](http://docs.smartthings.com/en/latest/capabilities-reference.html#water-sensor)
```json
{
  "timestamp": 	"2016-02-03T14:56:13+00:00", 
  "water": true
}
```

## Command Control

The bridge only works one way right now, commands can not be sent to SmartThings through the Watson IoT Bridge yet
