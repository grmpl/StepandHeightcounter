# StepandHeightcounter
This Android app counts footsteps and elevation gain during walking. It should work on all devices with a step counter sensor and a pressure sensor.
Since Android 10+ access permission to "Physical Activity" is necessary for step sensor. Thanks to duggalsu the app has been reworked and current release is now working again. I haven't tested, if reworked app is working on older devices. Please use release v1.21-fdroid if not.

A pressure or barometric sensor is mandatory, steps will not be counted without pressure values. Please note: The step counter sensor is a special sensor which counts the steps by itself without additional software and is not to be mistaken with a step detector sensor.

I've written the app, because I was interested in my elevation gain when I use the stairs the whole day instead of using the elevator. I couldn't find a small app with just this feature. And it's useful when doing hiking in the mountains.

Elevation gain is counted only when walking, so driving and elevators will not count.
But riding a bycicle will give a step counting and therefore an elevation gain. I can't think of a way to stop this, so you have to pause measurement if you don't want it.
You should also shut off measurement when walking in subways or trains. Measurement of elevation gain would be disturbed as air pressure will change significantly during driving.

The app will count only when measurement ist activated. You get a display of current height, current steps and current elevation gain, the latter can be reset to zero at any time. If you know your actual height, you can calibrate the height, but this will not change the elevation gain.
There is also a display of the daily values for steps and elevation gain, which are independent and will not be resetted.

You can save a marker with the floating action button and you can switch on automatic saving of detailed, regular or daily statistics.

There is no function for displaying the statistical values. Please use a text or csv-viewer of your choice. And there is no function to share data with anybody or anything (that's why I don't use one of the big activity apps).

Needed permissions:

1. Keep devices awake: App is running in background, but will not keep the device awake for the whole time! I inserted regular alarms, wakeup-triggers and limited wakelocks to be sure that data is processed when necessary and device can sleep the other time.
2. Write to external storage: All data will be saved to "internal" SD-card. App will run without permission, but saving data will not be possible.
3. Access to physical activity: Access to step sensor needs permission to access physical activity since Android 10+. The app will not work without this permission.

It's my first and only android application, so the code may be a little bit clumsy at some points. ;-)
