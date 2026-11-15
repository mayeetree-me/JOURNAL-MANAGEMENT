# Preserve Firestore data models and reflection constructors/fields for R8/ProGuard release builds
-keepclassmembers class com.example.data.model.** {
    *;
}
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * {
    *;
}
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}

# Preserve line numbers and source file info for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
