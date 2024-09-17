# pi4j-extensions

mvn -DskipTests=true clean install

mvn -DskipTests=false -Dtest=com.pi4j.extensions.components.SimpleButtonTest test

mvn -DskipTests=false -Dtest=com.pi4j.extensions.devices.i2c.Adafruit5880Test test

apt install xvfb
sudo -i
nohup Xvfb :1 -screen 0 1152x900x8 &
export DISPLAY=":1"
xhost +
mvn -DskipTests=false -Dtest=com.pi4j.extensions.devices.spi.Adafruit3787Test test
