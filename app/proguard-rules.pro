-keep class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**

-keep class su.kirian.wearayugram.data.local.** { *; }

-keepattributes Signature
-keepattributes *Annotation*

# Firebase: ComponentRegistrar implementations are instantiated reflectively by
# ComponentDiscovery (names come from manifest metadata, invisible to R8). Full-mode
# R8 strips their no-arg constructors -> NoSuchMethodException -> FCM token fetch
# fails with SERVICE_NOT_AVAILABLE on release builds.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-dontwarn com.google.firebase.**
