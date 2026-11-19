// SPDX-License-Identifier: GPL-3.0-or-later
/* Offline test: produces files only; has no USB or printer access. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "paper_settings.h"

static void error(void *ctx,const char *s,size_t n){(void)ctx;fwrite(s,1,n,stderr);}
static void output(void *ctx,const char *s,size_t n){if(fwrite(s,1,n,(FILE*)ctx)!=n)exit(8);}
static void noop(stp_image_t*i){(void)i;}
static int width(stp_image_t*i){(void)i;return 180;}
static int height(stp_image_t*i){(void)i;return 240;}
static const char *name(stp_image_t*i){(void)i;return "Paper offline test";}
static stp_image_status_t row(stp_image_t*i,unsigned char*p,size_t limit,int y){
 (void)i;if(limit<540)return STP_IMAGE_STATUS_ABORT;memset(p,255,540);
 for(int x=15;x<150;x++)if(y>=15&&y<30){p[x*3]=0;p[x*3+1]=(x<70)?0:120;p[x*3+2]=(x<120)?0:220;}
 return STP_IMAGE_STATUS_OK;
}
int main(int argc,char**argv){
 if(argc!=3)return 2;
 _putenv_s("STP_DATA_PATH",argv[1]);
 if(stp_init()!=0)return 3;
 const char*papers[]={"A4","A5","Letter","Legal","w288h432"};int verified=0;
 for(int p=0;p<5;p++)for(int gray=0;gray<2;gray++)for(int quality=0;quality<3;quality++)for(int photo=0;photo<2;photo++){
  stp_vars_t*v=paper_settings(papers[p],gray,quality,photo,error,NULL);
  if(!v||!stp_verify(v)){fprintf(stderr,"Rejected: %s gray=%d quality=%d photo=%d\n",papers[p],gray,quality,photo);return 4;}
  stp_vars_destroy(v);verified++;
 }
 printf("Verified %d paper/colour/quality/media combinations\n",verified);fflush(stdout);
 stp_image_t image={noop,noop,width,height,row,name,noop,NULL};
 for(int test=0;test<5;test++){
  char path[2048];snprintf(path,sizeof(path),"%s/case-%d.prn",argv[2],test);FILE*f=fopen(path,"wb");if(!f)return 5;
  for(int page=0;page<(test==4?2:1);page++){
   stp_vars_t*v=paper_settings(test==3?"w288h432":"A4",test!=2,test<3?test:1,test==3,error,NULL);
   stp_set_int_parameter(v,"PageNumber",page);stp_set_outfunc(v,output);stp_set_outdata(v,f);
   if(!stp_verify(v)||(!page&&stp_start_job(v,&image)!=1)||stp_print(v,&image)!=1||((test!=4||page==1)&&stp_end_job(v,&image)!=1))return 6;
   stp_vars_destroy(v);
  }
  if(fclose(f)!=0)return 7;printf("Encoded offline case %d\n",test);fflush(stdout);
 }
 return 0;
}
