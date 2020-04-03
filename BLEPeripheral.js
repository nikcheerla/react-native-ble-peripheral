/**
 * @providesModule BLEPeripheral
 */

'use strict';

var { NativeModules } = require('react-native');
NativeModules.BLEPeripheral.onWrite = async function(callback) {
  for (;;) {
    try {
      write = await NativeModules.BLEPeripheral.getWrite();
      callback(write);
    }
    catch(err) {
      // Broadcasting stopped
      break;
    }
  }
}
module.exports = NativeModules.BLEPeripheral;