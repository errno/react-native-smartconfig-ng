#import <React/RCTLog.h>

#import "RNSmartconfigNg.h"

#import "ESPTouchResult.h"
#import "ESP_NetUtil.h"

#import <CoreLocation/CoreLocation.h>
#import <SystemConfiguration/CaptiveNetwork.h>

@implementation SmartconfigDelegateImpl

-(void) onEsptouchResultAddedWithResult: (ESPTouchResult *) result
{
    RCTLog(@"SmartconifDelegateImpl onEsptouchResultAddedWithResult bssid: %@", result.bssid);
    NSString *result_bssid = result.bssid;
    if ([self.delegate respondsToSelector:@selector(resultAddedReceived:)]) {
        [self.delegate resultAddedReceived:result_bssid];
    }
}

@end

@implementation SmartconfigHelper

-(id)init {
    self = [super init];
    self._condition = [[NSCondition alloc] init];
    self._smartconfigDelegate = [[SmartconfigDelegateImpl alloc]init];
    return self;
}

- (void) startSmartConfig: (NSString *)apPwd deviceCnt: (int)deviceCnt broadcastType:(NSNumber *)type
                 resolver: (RCTPromiseResolveBlock)resolve
                 rejecter: (RCTPromiseRejectBlock)reject
{
    NSDictionary *netInfo = [self fetchNetInfo];
    NSString *apSsid = [netInfo objectForKey:@"SSID"];
    NSString *apBssid = [netInfo objectForKey:@"BSSID"];
    int taskCount = deviceCnt;
    BOOL broadcast = [type intValue] == 1 ? YES : NO; // 1: broadcast  0:  multicast
    
    RCTLog(@"ssid======>%@", apSsid);
    RCTLog(@"apBssid======>%@", apBssid);
    RCTLog(@"apPwd======>%@", apPwd);
    RCTLog(@"taskCount======>%d", taskCount);
    RCTLog(@"broadcast======>%@", broadcast ? @"broadcast" : @"multicast");
    
    if (apSsid == nil || apSsid == NULL) {
        RCTLog(@"======>no Wifi connection");
        reject(RCTErrorUnspecified, nil, RCTErrorWithMessage(@"No Wifi connection"));
        return;
    }
    
    RCTLog(@"ESPTouch smartConfig...");
    dispatch_queue_t  queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0);
    dispatch_async(queue, ^{
        RCTLog(@"ESPTouch do the execute work...");
        // execute the task
        NSArray *esptouchResultArray = [self executeForResultsWithSsid:apSsid bssid:apBssid password:apPwd taskCount:taskCount broadcast:broadcast];
        // show the result to the user in UI Main Thread
        dispatch_async(dispatch_get_main_queue(), ^{
            BOOL resolved = false;
            NSMutableArray *ret = [[NSMutableArray alloc]init];

            for (int i = 0; i < [esptouchResultArray count]; ++i)
            {
                ESPTouchResult *resultInArray = [esptouchResultArray objectAtIndex:i];
                
                if (![resultInArray isCancelled] && [resultInArray bssid] != nil) {
                    
                    unsigned char *ipBytes = (unsigned char *)[[resultInArray ipAddrData] bytes];
                    
                    NSString *ipv4String = [NSString stringWithFormat:@"%d.%d.%d.%d", ipBytes[0], ipBytes[1], ipBytes [2], ipBytes [3]];
                    
                    NSDictionary *respData = @{@"bssid": [resultInArray bssid], @"ipv4": ipv4String};
                    
                    [ret addObject: respData];
                    resolved = true;
                    if (![resultInArray isSuc])
                        break;
                }
            }
            if(resolved)
                resolve(ret);
            else
                reject(RCTErrorUnspecified, nil, RCTErrorWithMessage(@"No devices"));
        });
    });
}

- (void) cancel
{
    [self._condition lock];
    if (self._smartconfigTask != nil)
    {
        [self._smartconfigTask interrupt];
    }
    [self._condition unlock];
}

- (NSArray *) executeForResultsWithSsid:(NSString *)apSsid bssid:(NSString *)apBssid password:(NSString *)apPwd taskCount:(int)taskCount broadcast:(BOOL)broadcast
{
    [self._condition lock];
    self._smartconfigTask = [[ESPTouchTask alloc]initWithApSsid:apSsid andApBssid:apBssid andApPwd:apPwd];
    // set delegate
    [self._smartconfigTask setEsptouchDelegate:self._smartconfigDelegate];
    [self._smartconfigTask setPackageBroadcast:broadcast];
    [self._condition unlock];
    NSArray * esptouchResults = [self._smartconfigTask executeForResults:taskCount];
    RCTLog(@"ESPTouch executeForResult() result is: %@", esptouchResults);
    return esptouchResults;
}

// refer to http://stackoverflow.com/questions/5198716/iphone-get-ssid-without-private-library
- (NSDictionary *)fetchNetInfo
{
    NSArray *interfaceNames = CFBridgingRelease(CNCopySupportedInterfaces());

    NSDictionary *SSIDInfo;
    for (NSString *interfaceName in interfaceNames) {
        SSIDInfo = CFBridgingRelease(CNCopyCurrentNetworkInfo((__bridge CFStringRef)interfaceName));
        BOOL isNotEmpty = (SSIDInfo.count > 0);
        RCTLog(@"SSIDInfo.SSID -> %@", [SSIDInfo objectForKey:@"SSID"]);
        if (isNotEmpty) break;
    }
    return SSIDInfo;
}
@end


@implementation RNSmartconfigNg
{
    bool hasListeners;
    CLLocationManager *_locationManager;
}

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

RCT_EXPORT_MODULE(Smartconfig);

- (NSArray<NSString *> *)supportedEvents {
    return @[@"SmartconfigResultAdded", @"SmartconfigWifiChanged"];
}

-(void)startObserving {
    hasListeners = YES;
}

-(void)stopObserving {
    hasListeners = NO;
}

- (void)resultAddedReceived:(NSString *) bssid
{
    if (hasListeners) {
        [self sendEventWithName:@"SmartconfigResultAdded" body:@{@"bssid": bssid}];
    }
}

- (void)wifiChangedReceived:(NSString *) ssid
{
    if (hasListeners) {
        if ([ssid isEqual: @""])
            [self sendEventWithName:@"SmartconfigWifiChanged" body:@{@"ssid": ssid, @"connected": @NO, @"is5G": @NO}];
        else
            [self sendEventWithName:@"SmartconfigWifiChanged" body:@{@"ssid": ssid, @"connected": @YES, @"is5G": @NO}];
    }
}

#pragma mark - CLLocationManagerDelegate

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error
{
  NSLog(@"didFailWithError %@", error);
}

- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations
{
    CLLocation *newLocation = [locations lastObject];
    NSLog(@"didUpdateLocations %@", newLocation);
}

- (void)locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status {
    NSLog(@"%s", __PRETTY_FUNCTION__);
    switch (status) {
        case kCLAuthorizationStatusAuthorizedAlways:
        case kCLAuthorizationStatusAuthorizedWhenInUse:
            NSLog(@"=====> kCLAuthorizationStatusAuthorized");
//            [_locationManager startUpdatingLocation];
            [self updateWIFIinfo];
            break;
        case kCLAuthorizationStatusDenied:
            NSLog(@"=====> kCLAuthorizationStatusDenied");
            break;
        case kCLAuthorizationStatusNotDetermined:
            NSLog(@"=====> kCLAuthorizationStatusNotDetermined");
            break;
        case kCLAuthorizationStatusRestricted:
            NSLog(@"=====> kCLAuthorizationStatusRestricted");
            break;
    }
}

- (void)updateWIFIinfo {
    NSDictionary *netInfo = [self.helper fetchNetInfo];
    NSString *apSsid = [netInfo objectForKey:@"SSID"];
    apSsid = apSsid == nil ? @"" : apSsid;
    [self wifiChangedReceived:apSsid];
}

RCT_REMAP_METHOD(init, initESPTouch)
{
    [ESP_NetUtil tryOpenNetworkPermission];
    _locationManager = [[CLLocationManager alloc] init];
    _locationManager.delegate = self;
    _locationManager.desiredAccuracy = kCLLocationAccuracyKilometer;
    [_locationManager requestWhenInUseAuthorization];
    if (self.helper == nil) {
        self.helper = [[SmartconfigHelper alloc] init];
        self.helper._smartconfigDelegate.delegate = self;
    }
    [self updateWIFIinfo];
}

RCT_REMAP_METHOD(start,
                 password: (NSString *)pwd
                 broadcastType: (nonnull NSNumber *) type
                 devCnt: (int) devs
                 resolver: (RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    if (self.helper == nil) {
        self.helper = [[SmartconfigHelper alloc] init];
    }
    [self.helper startSmartConfig:pwd deviceCnt:devs broadcastType:type resolver: resolve rejecter: reject];
}

RCT_REMAP_METHOD(getNetInfo,
                 resolver:(RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    NSDictionary *netInfo = [self.helper fetchNetInfo];
    NSString *apSsid = [netInfo objectForKey:@"SSID"];
    NSString *apBssid = [netInfo objectForKey:@"BSSID"];
    apSsid = apSsid == nil ? @"" : apSsid;
    apBssid = apBssid == nil ? @"" : apBssid;
    NSDictionary *res = @{@"ssid":apSsid,@"bssid":apBssid};
    resolve(res);
}

RCT_EXPORT_METHOD(cancel)
{
    [self.helper cancel];
}

RCT_EXPORT_METHOD(finish)
{
    
}

@end

