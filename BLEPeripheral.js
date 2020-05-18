/**
 * @providesModule BLEPeripheral
 */
import { NativeEventEmitter, NativeModules } from 'react-native';

if (Platform.OS === 'android') { 
  let oldGetWrite = NativeModules.BLEPeripheral.getWrite;
  NativeModules.BLEPeripheral.getWrite = null;

  NativeModules.BLEPeripheral.getWrite = async () => {
    let write = await oldGetWrite();
    return String.fromCharCode.apply(String, write.data);
  }

  let oldAddCharacteristic = NativeModules.BLEPeripheral.addCharacteristicToService;
  NativeModules.BLEPeripheral.addCharacteristicToService = null;

  NativeModules.BLEPeripheral.addCharacteristicToService = async (ServiceUUID: string, UUID: string, permissions: number, properties: number, data: string) => {
    var byteData = [];
    for (var i = 0; i < data.length; i++){  
        byteData.push(data.charCodeAt(i));
    }
    console.log("Byte data: ", byteData);
    oldAddCharacteristic(ServiceUUID, UUID, permissions, properties, byteData)
  }
}


NativeModules.BLEPeripheral.onWrite = async function(callback) {
  for (;;) {
    try {
      let write = await NativeModules.BLEPeripheral.getWrite();
      callback(write);
    }
    catch(err) {
      break;
    }
  }
}

let emitter = new NativeEventEmitter( NativeModules.BLEPeripheral );
emitter.addListener(
      'RestoreState',
      name => {
        console.log('[emitter] Restoring state');
        NativeModules.BLEPeripheral.stop();
      }
  );

NativeModules.BLEPeripheral.registerRestoreStateFunction = async function(callback) {
  console.log("Registering restore state function");
  emitter.addListener(
      'RestoreState',
      name => callback()
  );
}

module.exports = NativeModules.BLEPeripheral;