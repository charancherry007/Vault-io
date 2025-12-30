# ProGuard rules for the Vault app
# Add any specific rules for libraries like Retrofit or Moshi if needed

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.vault.android.data.local.entity.** { *; }
