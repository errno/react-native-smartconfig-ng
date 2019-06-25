#import "ESPTouchTask.h"

#if __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#else
#import <React/RCTBridgeModule.h>
#endif

@interface SmartconfigDelegateImpl : NSObject<ESPTouchDelegate>

@end

@interface SmartconfigHelper : NSObject
@property NSString * ssid;
@property NSString * bssid;
@property (atomic, strong) ESPTouchTask *_smartconfigTask;
@property (nonatomic, strong) NSCondition *_condition;
@property (nonatomic, strong) SmartconfigDelegateImpl *_smartconfigDelegate;
@end

@interface RNSmartconfigNg : NSObject <RCTBridgeModule>
@property SmartconfigHelper *helper;
@end
  
