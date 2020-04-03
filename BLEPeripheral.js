/**
 * @providesModule BLEPeripheral
 */

'use strict';

var { NativeModules } = require('react-native');
NativeModules.BLEPeripheral.onWrite = function(callback) {
  NativeModules.BLEPeripheral.getWrite().then(write => {
    callback(write);
    NativeModules.BLEPeripheral.onWrite(callback);
  })
}
module.exports = NativeModules.BLEPeripheral;