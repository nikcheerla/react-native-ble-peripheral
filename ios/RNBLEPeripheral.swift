//  Created by Eskel on 12/12/2018

import Foundation
import CoreBluetooth

@objc(BLEPeripheral)
class BLEPeripheral: RCTEventEmitter, CBPeripheralManagerDelegate {
    var advertising: Bool = false
    var hasListeners: Bool = false
    var name: String = "RN_BLE"
    var servicesMap = Dictionary<String, CBMutableService>()
    var manager: CBPeripheralManager!
    var restoreStateIdentifier: String = "com.nikcheerla.tracetogether"
    var startPromiseResolve: RCTPromiseResolveBlock?
    var startPromiseReject: RCTPromiseRejectBlock?
    var getWritePromiseResolve: RCTPromiseResolveBlock?
    var getWritePromiseReject: RCTPromiseRejectBlock?
    var storedValue: Data?
    
    override init() {
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: nil, options: [CBPeripheralManagerOptionRestoreIdentifierKey: restoreStateIdentifier])
        print("BLEPeripheral initialized, advertising: \(advertising)")
    }
    
    // Returns an array of your named events
    override func supportedEvents() -> [String]! {
      return ["RestoreState", "onWarning"]
    }
    
    //// PUBLIC METHODS

    @objc func setName(_ name: String) {
        self.name = name
        print("name set to \(name)")
    }
    
    @objc func isAdvertising(_ resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
        resolve(advertising)
        print("called isAdvertising")
    }
    
    @objc(addService:primary:)
    func addService(_ uuid: String, primary: Bool) {
        let serviceUUID = CBUUID(string: uuid)
        let service = CBMutableService(type: serviceUUID, primary: primary)
        if(servicesMap.keys.contains(uuid) != true){
            servicesMap[uuid] = service
            manager.add(service)
            print("added service \(uuid)")
        }
        else {
            alertJS("service \(uuid) already there")
        }
    }
    
    @objc(addCharacteristicToService:uuid:permissions:properties:data:)
    func addCharacteristicToService(_ serviceUUID: String, uuid: String, permissions: UInt, properties: UInt, data: String) {
        
        let characteristicUUID = CBUUID(string: uuid)
        let propertyValue = CBCharacteristicProperties(rawValue: properties)
        let permissionValue = CBAttributePermissions(rawValue: permissions)
        let byteData: Data = data.data(using: .utf8)!
        let characteristic = CBMutableCharacteristic(
            type: characteristicUUID,
            properties: [.read, .write, .writeWithoutResponse],
            value: nil,
            permissions: [.readable, .writeable]
        )
        if let service = servicesMap[serviceUUID] {
            service.characteristics = [characteristic]
            manager.removeAllServices()
            manager.add(service)
            print("added characteristic to service")
            characteristic.value = byteData
            let success = manager.updateValue( byteData, for: characteristic, onSubscribedCentrals: nil)
        }
        else {
            print("seervice doesn't exist")
        }
    }
    
    @objc func start(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        
        startPromiseResolve = resolve
        startPromiseReject = reject
        
        if (manager.state != .poweredOn) {
            alertJS("Bluetooth turned off")
            startPromiseReject!("ST_PWR_ERR", "power off", nil);
            startPromiseResolve = nil;
            startPromiseReject = nil;
            return;
        }
        
        let advertisementData = [
            CBAdvertisementDataLocalNameKey: name,
            CBAdvertisementDataServiceUUIDsKey: getServiceUUIDArray()
            ] as [String : Any]
        manager.startAdvertising(advertisementData)
    }
    
    @objc func stop() {
//        if (!advertising) {
//            return;
//        }
        manager.stopAdvertising()
        advertising = false
        getWritePromiseResolve = nil;
        getWritePromiseReject?("WR_ERR", "stopped broadcasting before received any writes", nil);
        getWritePromiseReject = nil;
        print("called stop")
    }
    
    @objc func getWrite(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        if (manager.state != .poweredOn) {
            alertJS("Bluetooth turned off")
            reject("WR_PWR_ERR", "power off", nil)
        }
        
        getWritePromiseResolve = resolve
        getWritePromiseReject = reject
    }

    @objc(sendNotificationToDevices:characteristicUUID:data:)
    func sendNotificationToDevices(_ serviceUUID: String, characteristicUUID: String, data: Data) {
        if(servicesMap.keys.contains(serviceUUID) == true){
            let service = servicesMap[serviceUUID]!
            let characteristic = getCharacteristicForService(service, characteristicUUID)
            if (characteristic == nil) { alertJS("service \(serviceUUID) does NOT have characteristic \(characteristicUUID)") }

            let char = characteristic as! CBMutableCharacteristic
            char.value = data
            let success = manager.updateValue( data, for: char, onSubscribedCentrals: nil)
            if (success){
                print("changed data for characteristic \(characteristicUUID)")
            } else {
                alertJS("failed to send changed data for characteristic \(characteristicUUID)")
            }

        } else {
            alertJS("service \(serviceUUID) does not exist")
        }
    }
    
    //// EVENTS

    // Respond to Read request
    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest)
    {
        print("did receive read")
        let characteristic = getCharacteristic(request.characteristic.uuid)
        if (characteristic != nil){
            if (request.offset > (characteristic?.value)!.count) {
                request.value = nil;
            }
            else {
                request.value = (characteristic?.value)![request.offset...]
            }
            manager.respond(to: request, withResult: .success)
        } else {
            print("did not read \(request)")
            alertJS("cannot read, characteristic not found")
        }
    }
    

    // Respond to Write request
    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest])
    {
//        print("did receive write \(requests.count)")
        for request in requests
        {
//            print("req \(request)")
//            print("req \(request.offset)")
            let characteristic = getCharacteristic(request.characteristic.uuid)
            if (characteristic == nil) { alertJS("characteristic for writing not found") }
            if request.characteristic.uuid.isEqual(characteristic?.uuid)
            {
//                let char = characteristic as! CBMutableCharacteristic
//                char.value = request.value
                let data = String(decoding: request.value!, as: UTF8.self)
                
                getWritePromiseResolve?(data);
                getWritePromiseResolve = nil;
                getWritePromiseReject = nil;
            } else {
                alertJS("characteristic you are trying to access doesn't match")
            }
        }
        manager.respond(to: requests[0], withResult: .success)
    }

    // Respond to Subscription to Notification events
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        print("subscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Respond to Unsubscribe events
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        let char = characteristic as! CBMutableCharacteristic
        print("unsubscribed centrals: \(String(describing: char.subscribedCentrals))")
    }

    // Service added
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            alertJS("error: \(error)")
            return
        }
        print("service: \(service)")
    }

    // Bluetooth status changed
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        var state: Any
        if #available(iOS 10.0, *) {
            state = peripheral.state.description
        } else {
            state = peripheral.state
        }
        alertJS("BT state change: \(state)")
    }

    // Advertising started
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            alertJS("advertising failed. error: \(error)")
            advertising = false
            startPromiseReject!("AD_ERR", "advertising failed", error)
            startPromiseResolve = nil;
            startPromiseReject = nil;
            return
        }
        advertising = true
        startPromiseResolve!(advertising)
        startPromiseResolve = nil;
        startPromiseReject = nil;
        print("advertising succeeded!")
    }
    
    //// HELPERS

    func getCharacteristic(_ characteristicUUID: CBUUID) -> CBCharacteristic? {
        for (uuid, service) in servicesMap {
            for characteristic in service.characteristics ?? [] {
                if (characteristic.uuid.isEqual(characteristicUUID) ) {
                    print("service \(uuid) does have characteristic \(characteristicUUID)")
                    if (characteristic is CBMutableCharacteristic) {
                        return characteristic
                    }
                    print("but it is not mutable")
                } else {
                    alertJS("characteristic you are trying to access doesn't match")
                }
            }
        }
        return nil
    }

    func getCharacteristicForService(_ service: CBMutableService, _ characteristicUUID: String) -> CBCharacteristic? {
        for characteristic in service.characteristics ?? [] {
            if (characteristic.uuid.isEqual(characteristicUUID) ) {
                print("service \(service.uuid) does have characteristic \(characteristicUUID)")
                if (characteristic is CBMutableCharacteristic) {
                    return characteristic
                }
                print("but it is not mutable")
            } else {
                alertJS("characteristic you are trying to access doesn't match")
            }
        }
        return nil
    }

    func getServiceUUIDArray() -> Array<CBUUID> {
        var serviceArray = [CBUUID]()
        for (_, service) in servicesMap {
            serviceArray.append(service.uuid)
        }
        return serviceArray
    }

    func alertJS(_ message: Any) {
        print(message)
        if(hasListeners) {
            sendEvent(withName: "onWarning", body: message)
        }
    }
    
    public func peripheralManager(_ peripheral: CBPeripheralManager,
                                  willRestoreState dict: [String : Any]) {
        print("Peripheral manager: restore state");
        stop();
        sendEvent(withName: "RestoreState", body: dict);
    }

    override func startObserving() { hasListeners = true }
    override func stopObserving() { hasListeners = false }
    @objc override static func requiresMainQueueSetup() -> Bool { return false }
    
}

@available(iOS 10.0, *)
extension CBManagerState: CustomStringConvertible {
    public var description: String {
        switch self {
        case .poweredOff: return ".poweredOff"
        case .poweredOn: return ".poweredOn"
        case .resetting: return ".resetting"
        case .unauthorized: return ".unauthorized"
        case .unknown: return ".unknown"
        case .unsupported: return ".unsupported"
        }
    }
}
