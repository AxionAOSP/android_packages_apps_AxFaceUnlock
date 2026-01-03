TARGET_FACE_UNLOCK_SUPPORTED ?= true
ifneq ($(TARGET_FACE_UNLOCK_SUPPORTED),false)
ifeq ($(TARGET_SUPPORTS_GFU),true)
$(call inherit-product-if-exists, vendor/google/faceunlock/config.mk)
else
PRODUCT_PACKAGES += \
    FaceUnlock

PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.face.sense_service=true
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.biometrics.face.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/android.hardware.biometrics.face.xml
    
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
	system/lib64/libtensorflowlite_jni.so
endif
endif
