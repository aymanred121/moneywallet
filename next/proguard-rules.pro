# Room, Compose, Glance and WorkManager ship their own consumer rules.
-keepattributes *Annotation*,Signature
# org.json is part of the platform; nothing else is reflected on at runtime except the drawable
# catalog, which looks up ic_icon_* fields by name.
-keepclassmembers class com.oriondev.moneywallet.next.R$drawable { public static int ic_icon_*; }
