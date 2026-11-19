// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <gutenprint/gutenprint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include "paper_settings.h"

static pthread_mutex_t mutex=PTHREAD_MUTEX_INITIALIZER;
static int initialized=0;
typedef struct { JNIEnv *env; jobject rows; jmethodID read; jbyteArray buffer; int width,height,failed; FILE *output; char error[4096]; size_t error_size; } Context;
static void fail(JNIEnv *env,const char *text){if(!(*env)->ExceptionCheck(env)){jclass ex=(*env)->FindClass(env,"java/io/IOException");if(ex)(*env)->ThrowNew(env,ex,text);}}
static void error_text(void *opaque,const char *text,size_t length){Context *c=opaque;if(!c)return;size_t remaining=sizeof(c->error)-c->error_size-1;if(length>remaining)length=remaining;memcpy(c->error+c->error_size,text,length);c->error_size+=length;c->error[c->error_size]=0;}
static void output_bytes(void *opaque,const char *bytes,size_t length){Context*c=opaque;if(c->failed)return;if(fwrite(bytes,1,length,c->output)!=length){c->failed=1;const char*message="Could not save the native print stream";error_text(c,message,strlen(message));}}
static void image_noop(stp_image_t *i){(void)i;}
static int image_width(stp_image_t*i){return ((Context*)i->rep)->width;}
static int image_height(stp_image_t*i){return ((Context*)i->rep)->height;}
static const char *image_name(stp_image_t*i){(void)i;return "Paper";}
static stp_image_status_t image_row(stp_image_t*i,unsigned char*data,size_t limit,int row){
 Context*c=i->rep;if(c->failed||limit<(size_t)c->width*3)return STP_IMAGE_STATUS_ABORT;
 jboolean ok=(*c->env)->CallBooleanMethod(c->env,c->rows,c->read,row,c->buffer);
 if((*c->env)->ExceptionCheck(c->env)||!ok){c->failed=1;return STP_IMAGE_STATUS_ABORT;}
 (*c->env)->GetByteArrayRegion(c->env,c->buffer,0,c->width*3,(jbyte*)data);
 return (*c->env)->ExceptionCheck(c->env)?STP_IMAGE_STATUS_ABORT:STP_IMAGE_STATUS_OK;
}
static int init_driver(JNIEnv *env,const char *path){
 if(!initialized){setenv("STP_DATA_PATH",path,1);if(stp_init()!=0){fail(env,"Could not initialize the Canon raster driver");return 0;}initialized=1;}
 return 1;
}
static stp_vars_t *settings(JNIEnv*env,Context*c,const char*paper,int gray,int quality,int photo){
 stp_vars_t*v=paper_settings(paper,gray,quality,photo,error_text,c);
 if(!v)fail(env,"The Gutenprint G2100 printer definition could not be loaded");
 return v;
}
JNIEXPORT jdoubleArray JNICALL Java_app_paper_print_CanonRaster_nativeGeometry(JNIEnv*env,jobject self,jstring data,jstring paper,jboolean gray,jint quality,jboolean photo){
 (void)self;pthread_mutex_lock(&mutex);Context c={0};c.env=env;
 const char*d=(*env)->GetStringUTFChars(env,data,0),*p=(*env)->GetStringUTFChars(env,paper,0);jdoubleArray result=NULL;stp_vars_t*v=NULL;
 if(d&&p&&init_driver(env,d))v=settings(env,&c,p,gray,quality,photo);
 if(v){stp_dimension_t left,right,bottom,top,w,h;stp_get_imageable_area(v,&left,&right,&bottom,&top);stp_get_media_size(v,&w,&h);
  if(right<=left||bottom<=top||w<=0||h<=0)fail(env,"The native printer profile returned an invalid print area");
  else{jdouble values[]={left,top,right-left,bottom-top,w,h};result=(*env)->NewDoubleArray(env,6);if(result)(*env)->SetDoubleArrayRegion(env,result,0,6,values);}stp_vars_destroy(v);
 }
 if(d)(*env)->ReleaseStringUTFChars(env,data,d);if(p)(*env)->ReleaseStringUTFChars(env,paper,p);pthread_mutex_unlock(&mutex);return result;
}
JNIEXPORT void JNICALL Java_app_paper_print_CanonRaster_nativeEncode(JNIEnv*env,jobject self,jstring data,jstring paper,jboolean gray,jint quality,jboolean photo,jobject rows,jint width,jint height,jstring path,jint page_index,jboolean first,jboolean last){
 (void)self;pthread_mutex_lock(&mutex);Context c={0};c.env=env;c.rows=rows;c.width=width;c.height=height;stp_vars_t*v=NULL;
 const char*d=(*env)->GetStringUTFChars(env,data,0),*p=(*env)->GetStringUTFChars(env,paper,0),*out=(*env)->GetStringUTFChars(env,path,0);
 if(width<=0||height<=0||width>10000||height>20000){fail(env,"Invalid native input page dimensions");goto done;}
 if(!d||!p||!out||!init_driver(env,d))goto done;
 v=settings(env,&c,p,gray,quality,photo);if(!v)goto done;
 stp_set_int_parameter(v,"PageNumber",page_index);
 if(!stp_verify(v)){fail(env,c.error[0]?c.error:"The native printer settings were rejected");goto done;}
 c.output=fopen(out,first?"wb":"ab");if(!c.output){fail(env,"Could not open the native print stream");goto done;}
 jclass cls=(*env)->GetObjectClass(env,rows);c.read=(*env)->GetMethodID(env,cls,"readRow","(I[B)Z");(*env)->DeleteLocalRef(env,cls);if(!c.read)goto done;
 c.buffer=(*env)->NewByteArray(env,width*3);if(!c.buffer)goto done;
 stp_set_outfunc(v,output_bytes);stp_set_outdata(v,&c);
 stp_image_t image={image_noop,image_noop,image_width,image_height,image_row,image_name,image_noop,&c};
 int ok=1;if(first)ok=stp_start_job(v,&image);
 if(ok==1)ok=stp_print(v,&image);if(ok==1&&last)ok=stp_end_job(v,&image);
 if(ok!=1||c.failed||ferror(c.output))fail(env,c.error[0]?c.error:"The Canon raster encoder could not finish the page");
done:
 if(c.output&&fclose(c.output)!=0)fail(env,"Could not finish saving the native print stream");if(c.buffer)(*env)->DeleteLocalRef(env,c.buffer);if(v)stp_vars_destroy(v);
 if(d)(*env)->ReleaseStringUTFChars(env,data,d);if(p)(*env)->ReleaseStringUTFChars(env,paper,p);if(out)(*env)->ReleaseStringUTFChars(env,path,out);
 pthread_mutex_unlock(&mutex);
}
