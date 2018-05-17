//
//  BraintreePlugin.h
//
//  Copyright (c) 2016 Justin Unterreiner. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>
#import <BraintreeDataCollector/BraintreeDataCollector.h>

@interface BraintreePlugin : CDVPlugin

@property (nonatomic, strong) BTDataCollector *dataCollector;

- (void)initialize:(CDVInvokedUrlCommand *)command;
- (void)presentDropInPaymentUI:(CDVInvokedUrlCommand *)command;
- (void)payPalProcess:(CDVInvokedUrlCommand *)command;
- (void)payPalProcessVaulted:(CDVInvokedUrlCommand *)command;
@end
