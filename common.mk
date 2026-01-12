TARGET_FACE_UNLOCK_SUPPORTED ?= true
ifneq ($(TARGET_FACE_UNLOCK_SUPPORTED),false)
ifeq ($(TARGET_SUPPORTS_GFU),true)
$(call inherit-product-if-exists, vendor/google/faceunlock/config.mk)
else
PRODUCT_PACKAGES += \
    FaceUnlock \
    libtensorflowlite_jni \
    libtensorflowlite_gpu_jni \
    libimage_processing_util_jni \
    libsurface_util_jni

PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.face.sense_service=true

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.biometrics.face.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/android.hardware.biometrics.face.xml
    
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    system/lib/libimage_processing_util_jni.so \
    system/lib/libsurface_util_jni.so \
    system/lib/libtensorflowlite_jni.so \
	system/lib64/libtensorflowlite_jni.so \
	system/lib64/libimage_processing_util_jni.so \
	system/lib64/libsurface_util_jni.so \
    system/lib64/libtensorflowlite_gpu_jni.so \
    system/lib/libtensorflowlite_gpu_jni.so
endif
endif
