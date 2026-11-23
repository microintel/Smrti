# Room generates its code at build time; keep the entities and DAOs it reflects on.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Reminder / widget receivers are referenced from the manifest (kept automatically); keep the names readable in crash logs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coil / OkHttp optional dependencies that are not bundled
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
