#import <React/RCTLog.h>

#import "RNSmartconfigNg.h"

#import "ESPTouchResult.h"
#import "ESP_NetUtil.h"

#import <SystemConfiguration/CaptiveNetwork.h>

@implementation SmartconfigDelegateImpl

-(void) onEsptouchResultAddedWithResult: (ESPTouchResult *) result
{
    RCTLog(@"SmartconifDelegateImpl onEsptouchResultAddedWithResult bssid: %@", result.bssid);
}

@end


@implementation SmartconfigHelper

-(id)init {
    self = [super init];
    self._condition = [[NSCondition alloc]init];
    self._smartconfigDelegate = [[SmartconfigDelegateImpl alloc]init];
    return self;
}


- (void) startSmartConfig: (NSString *)apPwd broadcastType:(NSNumber *)type
                 resolver: (RCTPromiseResolveBlock)resolve
                 rejecter: (RCTPromiseRejectBlock)reject
{
    NSDictionary *netInfo = [self fetchNetInfo];
    NSString *apSsid = [netInfo objectForKey:@"SSID"];
    NSString *apBssid = [netInfo objectForKey:@"BSSID"];
    int taskCount = 1;
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
            ESPTouchResult *firstResult = [esptouchResultArray objectAtIndex:0];
            // check whether the task is cancelled and no results received
            if (!firstResult.isCancelled)
            {
                if ([firstResult isSuc])
                {   // 配网成功
                    RCTLog(@"======>ESPTouch success");
                    NSDictionary *res = @{@"code":@"200",@"msg":@"ESPTouch success"};
                    resolve(res);
                }
                
                else
                {   // 配网失败
                    RCTLog(@"======>ESPTouch fail");
                    NSDictionary *res = @{@"code":@"0",@"msg":@"ESPTouch fail"};
                    resolve(res);
                }
            }
            
        });
    });
}

// 取消配置任务
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

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

RCT_EXPORT_MODULE(Smartconfig);

RCT_EXPORT_METHOD(init)
{
    [ESP_NetUtil tryOpenNetworkPermission];
    if (self.helper == nil) {
        self.helper = [[SmartconfigHelper alloc] init];
    }
}

RCT_REMAP_METHOD(startSmartConfig,
                 password: (NSString *)pwd
                 broadcastType: (nonnull NSNumber *) type
                 resolver: (RCTPromiseResolveBlock)resolve
                 rejecter:(RCTPromiseRejectBlock)reject)
{
    if (self.helper == nil) {
        self.helper = [[SmartconfigHelper alloc] init];
    }
    [self.helper startSmartConfig:pwd broadcastType:type resolver: resolve rejecter: reject];
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

RCT_EXPORT_METHOD(finish)
{
    
}

@end

