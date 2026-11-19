// SPDX-License-Identifier: GPL-3.0-or-later
#ifndef PAPER_SETTINGS_H
#define PAPER_SETTINGS_H
#include <gutenprint/gutenprint.h>

/* Shared by the Android JNI bridge and the offline native encoder smoke test. */
static stp_vars_t *paper_settings(const char *paper,int gray,int quality,int photo,
                                 stp_outfunc_t error_func,void *error_data) {
 const stp_printer_t*p=stp_get_printer_by_driver("bjc-PIXMA-G2100");
 if(!p)return NULL;
 stp_vars_t*v=stp_vars_create();stp_set_printer_defaults(v,p);
 stp_set_errfunc(v,error_func);stp_set_errdata(v,error_data);
 stp_set_string_parameter(v,"PageSize",paper);
 stp_set_string_parameter(v,"MediaType",photo?"PhotoPlusGloss2":"Plain");
 stp_set_string_parameter(v,"PrintingMode",gray?"BW":"Color");
 stp_set_string_parameter(v,"InputImageType","RGB");
 stp_set_string_parameter(v,"ChannelBitDepth","8");
 stp_set_string_parameter(v,"ImageType",photo?"Photo":"TextGraphics");
 stp_set_string_parameter(v,"JobMode","Job");
 stp_set_string_parameter(v,"Duplex","None");
 stp_set_string_parameter(v,"Resolution",photo?(quality==2?"600x600dpi_photohigh2":"600x600dpi_photo"):(quality==0?"300x300dpi":quality==2?"600x600dpi_high":"600x600dpi"));
 stp_dimension_t left,right,bottom,top;stp_get_imageable_area(v,&left,&right,&bottom,&top);
 stp_set_left(v,left);stp_set_top(v,top);stp_set_width(v,right-left);stp_set_height(v,bottom-top);
 return v;
}
#endif
