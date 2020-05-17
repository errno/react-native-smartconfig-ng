#import "ESPTouchTask.h"

#import <React/RCTEventEmitter.h>

#import <CoreLocation/CoreLocation.h>

@protocol SmartconfigResultDelegate <NSObject>

- (void)resultAddedReceived:(NSString *) bssid;
- (void)wifiChangedReceived:(NSString *) ssid;

@end

@interface SmartconfigDelegateImpl : NSObject<ESPTouchDelegate>

@property(nonatomic, weak)id <SmartconfigResultDelegate> delegate;

@end

@interface SmartconfigHelper : NSObject
@property NSString * ssid;
@property NSString * bssid;
@property (atomic, strong) ESPTouchTask *_smartconfigTask;
@property (nonatomic, strong) NSCondition *_condition;
@property (nonatomic, strong) SmartconfigDelegateImpl *_smartconfigDelegate;
@end

@interface RNSmartconfigNg : RCTEventEmitter <RCTBridgeModule, CLLocationManagerDelegate, SmartconfigResultDelegate>
@property SmartconfigHelper *helper;
@end
  
