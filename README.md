# pi4j-extensions

mvn -DskipTests=true clean install

mvn -DskipTests=false -Dtest=com.pi4j.extensions.components.SimpleButtonTest test

mvn -DskipTests=false -Dtest=com.pi4j.extensions.devices.i2c.Adafruit5880Test test
