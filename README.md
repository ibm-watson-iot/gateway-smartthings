# IoT Gateway Application for Samsung SmartThings

Connect your Samsung SmartThings to Watson IoT.

- [IBM Watson IoT](https://internetofthings.ibmcloud.com)
- [Samsung SmartThings](https://www.smartthings.com)

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

You will need to have generated an API key from your Watson IoT dashboard (or have been provided one by whoever oversees the Watson IoT organization you wish to connect to)

![SmartApp Configuration 1](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/app_cfg_1.jpg "SmartApp Configuration 1")

### Configure Device Access

You also need to explicitly grant access to each individual device capability that you want to Watson IoT to work with
![SmartApp Configuration 2](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/app_cfg_2.jpg "SmartApp Configuration 2")

### Verification
After the setup is complete you should be able to see the application listed when you navigate to ``My Home > SmartApps``

![SmartApp](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/app.jpg "SmartApp")

## What it Does

- Devices are automatically registered in Watson IoT
  - All SmartThings are created with a ``typeId`` of ``smartthings``
  - The unique identifier of each device is used as the ``deviceId``
  - The class of SmartThing is recorded in ``deviceInfo.deviceClass`` (e.g. "Motion Sensor", "Multipurpose Sensor")
- Device metadata is synced to Watson IoT:
  - The custom label you applied to each device is recorded in ``deviceInfo.description``
  - The name of your location is recorded in ``deviceInfo.descriptiveLocation`` (e.g. "My Home")
  - The longitude and latitude for the center of your geofence is recorded in each device's location record
  - Device metadata updates occur when the application is installed, updated and on a 30 minute interval while the application is running
- Device events are streamed in real time into Watson IoT
- In addition to the real time event pass-through, every 30 minutes the current state of all devices is captured and reported to Watson IoT.

![IBM Watson IoT Dashboard](https://raw.githubusercontent.com/ibm-watson-iot/gateway-smartthings/master/docs/dashboard.jpg "IBM Watson IoT Dashboard")

## What it doesn't do (Yet!)

- Command control - The bridge only works one way right now, commands can not be sent to SmartThings through the Watson IoT Bridge yet
