//// Write C++ code here.
////
//// Do not forget to dynamically load the C++ library into your application.
////
//// For instance,
////
//// In MainActivity.java:
////    static {
////       System.loadLibrary("fcmfix");
////    }
////
//// Or, in MainActivity.kt:
////    companion object {
////      init {
////         System.loadLibrary("fcmfix")
////      }
////    }
//#include <string.h>
//#include <jni.h>
//#include <fcntl.h>
//#include <linux/ioctl.h>
//#include <sys/ioctl.h>
//#include <linux/videodev2.h>
//
///* This is a trivial JNI example where we use a native method
// * to return a new VM String. See the corresponding Java source
// * file located at:
// *
// *   apps/samples/hello-jni/project/src/com/example/HelloJni/HelloJni.java
// */
//#define VIDIOC_S_FLASH_CAMERA      _IOW  ('V', 98, void *)
//#define VIDIOC_S_FLASH_MOVIE      _IOW  ('V', 99, void *)
//#define VIDIOC_S_CAMERA_START    _IO  ('V', BASE_VIDIOC_PRIVATE + 0)
//#define VIDIOC_S_CAMERA_STOP    _IO  ('V', BASE_VIDIOC_PRIVATE + 1)
//
//extern "C" {
//
//static int fd;
//JNIEXPORT void JNICALL
//Java_jvr_test_FlashTest_openCamera( JNIEnv* env,
//                                    jobject thiz)
//{
//    fd = open("/dev/video13", O_RDWR);
//    int r = ioctl(fd, VIDIOC_S_CAMERA_START, (unsigned long)1);
//}
//
//JNIEXPORT void JNICALL
//Java_jvr_test_FlashTest_closeCamera( JNIEnv* env,
//                                     jobject thiz)
//{
//    int r = ioctl(fd, VIDIOC_S_CAMERA_STOP, (unsigned long)1);
//    close(fd);
//}
//
//JNIEXPORT jstring JNICALL
//Java_jvr_test_FlashTest_setFlash( JNIEnv* env,
//                                  jobject thiz, int i)
//{
//    struct v4l2_control cont;
//    cont.id = 0;
//    cont.value = i;
//    //int r = ioctl(fd, VIDIOC_S_CAMERA_START, &cont);
//    int r = ioctl(fd, VIDIOC_S_FLASH_MOVIE, &cont);
//    if (i)
//        return env->NewStringUTF("Flash On");
//    else
//        return env->NewStringUTF("Flash Off");
//}
//
//JNIEXPORT void JNICALL
//Java
//
//}