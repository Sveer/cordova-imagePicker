//
//  SOSPicker.m
//  SyncOnSet
//
//  Created by Christopher Sullivan on 10/25/13.
//
//

#import "SOSPicker.h"


#import "GMImagePickerController.h"
#import "GMFetchItem.h"
#import "MBProgressHUD.h"

#define CDV_PHOTO_PREFIX @"cdv_photo_"

extern NSUInteger kGMImageMaxSeletedNumber;

typedef enum : NSUInteger {
    FILE_URI = 0,
    BASE64_STRING = 1
} SOSPickerOutputType;

@interface SOSPicker () <GMImagePickerControllerDelegate>
@end

@implementation SOSPicker 

@synthesize callbackId;

- (void) getPictures:(CDVInvokedUrlCommand *)command {
    
    NSDictionary *options = [command.arguments objectAtIndex: 0];
    NSInteger maximumImagesCount = [[options objectForKey:@"maximumImagesCount"] integerValue];
    
    self.outputType = [[options objectForKey:@"outputType"] integerValue];
    BOOL allow_video = [[options objectForKey:@"allow_video" ] boolValue ];
    NSString * title = [options objectForKey:@"title"];
    NSString * message = [options objectForKey:@"message"];
    kGMImageMaxSeletedNumber = maximumImagesCount;
    if (message == (id)[NSNull null]) {
      message = nil;
    }
    self.width = [[options objectForKey:@"width"] integerValue];
    self.height = [[options objectForKey:@"height"] integerValue];
    self.quality = [[options objectForKey:@"quality"] integerValue];


    self.preSelectedAssets = [options objectForKey:@"assets"];
    
    self.callbackId = command.callbackId;
    [self launchGMImagePicker:allow_video title:title message:message];
}

- (void)launchGMImagePicker:(bool)allow_video title:(NSString *)title message:(NSString *)message
{
    GMImagePickerController *picker = [[GMImagePickerController alloc] init:allow_video withAssets: self.preSelectedAssets];
    picker.delegate = self;
    picker.title = title;
    picker.customNavigationBarPrompt = message;
    picker.colsInPortrait = 3;
    picker.colsInLandscape = 5;
    picker.minimumInteritemSpacing = 2.0;
    picker.modalPresentationStyle = UIModalPresentationPopover;
    
    UIPopoverPresentationController *popPC = picker.popoverPresentationController;
    popPC.permittedArrowDirections = UIPopoverArrowDirectionAny;
    popPC.sourceView = picker.view;
    //popPC.sourceRect = nil;
    
    [self.viewController showViewController:picker sender:nil];
}


- (UIImage*)imageByScalingNotCroppingForSize:(UIImage*)anImage toSize:(CGSize)frameSize
{
    UIImage* sourceImage = anImage;
    UIImage* newImage = nil;
    CGSize imageSize = sourceImage.size;
    CGFloat width = imageSize.width;
    CGFloat height = imageSize.height;
    CGFloat targetWidth = frameSize.width;
    CGFloat targetHeight = frameSize.height;
    CGFloat scaleFactor = 0.0;
    CGSize scaledSize = frameSize;

    if (CGSizeEqualToSize(imageSize, frameSize) == NO) {
        CGFloat widthFactor = targetWidth / width;
        CGFloat heightFactor = targetHeight / height;

        // opposite comparison to imageByScalingAndCroppingForSize in order to contain the image within the given bounds
        if (widthFactor == 0.0) {
            scaleFactor = heightFactor;
        } else if (heightFactor == 0.0) {
            scaleFactor = widthFactor;
        } else if (widthFactor > heightFactor) {
            scaleFactor = heightFactor; // scale to fit height
        } else {
            scaleFactor = widthFactor; // scale to fit width
        }
        scaledSize = CGSizeMake(floor(width * scaleFactor), floor(height * scaleFactor));
    }

    UIGraphicsBeginImageContext(scaledSize); // this will resize

    [sourceImage drawInRect:CGRectMake(0, 0, scaledSize.width, scaledSize.height)];

    newImage = UIGraphicsGetImageFromCurrentImageContext();
    if (newImage == nil) {
        NSLog(@"could not scale image");
    }

    // pop the context to get back to the default
    UIGraphicsEndImageContext();
    return newImage;
}

#pragma mark - GMImagePickerControllerDelegate

- (void)assetsPickerController:(GMImagePickerController *)picker didFinishPickingAssets:(NSArray *)fetchArray
{
    [picker.presentingViewController dismissViewControllerAnimated:YES completion:nil];
    
    NSLog(@"GMImagePicker: User finished picking assets. Number of selected items is: %lu", (unsigned long)fetchArray.count);
    
    __block NSMutableArray *preSelectedAssets = [[NSMutableArray alloc] init];
    __block NSMutableArray *fileStrings = [[NSMutableArray alloc] init];
    CGSize targetSize = CGSizeMake(self.width, self.height);
    NSString* docsPath = [NSTemporaryDirectory()stringByStandardizingPath];
    
    __block CDVPluginResult* result = nil;
    
    PHImageManager *manager = [PHImageManager defaultManager];
    PHImageRequestOptions *requestOptions;
    requestOptions = [[PHImageRequestOptions alloc] init];
    requestOptions.resizeMode   = PHImageRequestOptionsResizeModeExact;
    requestOptions.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;
    requestOptions.networkAccessAllowed = YES;
    
    // this one is key
    requestOptions.synchronous = true;
    
    dispatch_group_t dispatchGroup = dispatch_group_create();
    
    MBProgressHUD *progressHUD = [MBProgressHUD showHUDAddedTo:self.viewController.presentedViewController.view.superview
                                                      animated:YES];
    progressHUD.mode = MBProgressHUDModeDeterminate;
    progressHUD.dimBackground = YES;
    progressHUD.labelText = NSLocalizedStringFromTable(
                                                       @"picker.selection.processing",
                                                       @"GMImagePicker",
                                                       @"Loading"
                                                       );
    [progressHUD show: YES];
    dispatch_group_async(dispatchGroup, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0), ^{
        __block NSString* filePath;
        NSError* err = nil;
        __block NSData *imgData;
        // Index for tracking the current image
        int index = 0;
        // If image fetching fails then retry 3 times before giving up
        int retry = 3;
        do {
            PHAsset *asset = [fetchArray objectAtIndex:index];
            NSString *localIdentifier;
            if (asset == nil) {
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_IO_EXCEPTION messageAsString:[err localizedDescription]];
            } else {
                __block UIImage *image;
                if (retry > 0) {
                    [manager requestImageDataForAsset:asset
                                  options:requestOptions
                                resultHandler:^(NSData *imageData,
                                                    NSString *dataUTI,
                                                    UIImageOrientation orientation,
                                                    NSDictionary *info) {
                                imgData = [imageData copy];
                                NSString* fullFilePath = [info objectForKey:@"PHImageFileURLKey"];
                                NSString* fileName = [[fullFilePath lastPathComponent] stringByDeletingPathExtension];
                                filePath = [NSString stringWithFormat:@"%@/%@.%@", docsPath, fileName, @"jpg"];
                            }];
                    retry--;
                    localIdentifier = [asset localIdentifier];
                    NSLog(@"localIdentifier: %@", localIdentifier);
                    requestOptions.deliveryMode = PHImageRequestOptionsDeliveryModeFastFormat;
                } else {
                    retry = 3;
                    index++;
                    requestOptions.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;
                }
                
                if (imgData != nil) {
                    retry = 3;
                    requestOptions.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;

                    @autoreleasepool {
                        NSData* data = nil;
                        if (self.width == 0 && self.height == 0) {
                            // no scaling required
                            if (self.quality == 100) {
                                data = [imgData copy];
                            } else {
                                image = [UIImage imageWithData:imgData];
                                // resample first
                                data = UIImageJPEGRepresentation(image, self.quality/100.0f);
                            }
                        } else {
                            image = [UIImage imageWithData:imgData];
                            // scale
                            UIImage* scaledImage = [self imageByScalingNotCroppingForSize:image toSize:targetSize];
                            data = UIImageJPEGRepresentation(scaledImage, self.quality/100.0f);
                        }
                        if (![data writeToFile:filePath options:NSAtomicWrite error:&err]) {
                            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_IO_EXCEPTION messageAsString:[err localizedDescription]];
                            break;
                        } else {
                            [fileStrings addObject:[[NSURL fileURLWithPath:filePath] absoluteString]];
                            [preSelectedAssets addObject: localIdentifier];
                        }
                        data = nil;
                    }
//                    index = [NSNumber numberWithInt:[index intValue] + 1];
                    index++;
                }
            }
        } while (index < fetchArray.count);
        
        if (result == nil) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: [NSDictionary dictionaryWithObjectsAndKeys: preSelectedAssets, @"preSelectedAssets", fileStrings, @"images", nil]];
        }
    });
    
    dispatch_group_notify(dispatchGroup, dispatch_get_main_queue(), ^{
        if (nil == result) {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary: [NSDictionary dictionaryWithObjectsAndKeys: preSelectedAssets, @"preSelectedAssets", fileStrings, @"images", nil]];
        }
        
        progressHUD.progress = 1.f;
        [progressHUD hide:YES];
        [self.viewController dismissViewControllerAnimated:YES completion:nil];
        [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
    });
    
}

//Optional implementation:
-(void)assetsPickerControllerDidCancel:(GMImagePickerController *)picker
{
    CDVPluginResult* pluginResult = nil;
    NSArray* emptyArray = [NSArray array];
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:emptyArray];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    NSLog(@"GMImagePicker: User pressed cancel button");
}


@end