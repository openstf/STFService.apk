# How to generate aosp_4.4_r1_classes.jar

Check out android-4.4_r1 from the AOSP project. Then, run the following commands:

```
source build/envsetup.sh
lunch 1
make out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar
```

Then copy the file here as `aosp_4.4_r1_classes.jar`.
