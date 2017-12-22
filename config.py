
def can_build(plat):
    return (plat == "android")

def configure(env):
    if env["platform"] == "android":
        env.android_add_maven_repository("url 'https://maven.google.com'")
        env.android_add_maven_repository("url 'https://oss.sonatype.org/content/repositories/snapshots'")

        env.android_add_gradle_classpath("com.google.gms:google-services:3.1.1")
        env.android_add_gradle_plugin("com.google.gms.google-services")

        env.android_add_dependency("compile 'com.google.android.gms:play-services-auth:11.8.0'")
        env.android_add_dependency("compile 'com.google.android.gms:play-services-games:11.8.0'")

	env.android_add_dependency("compile 'com.google.firebase:firebase-invites:11.8.0'")

        env.android_add_java_dir("android");
        env.android_add_res_dir("res");
        env.android_add_to_manifest("android/AndroidManifestChunk.xml");
        env.android_add_to_permissions("android/AndroidPermissionsChunk.xml");
        env.android_add_default_config("applicationId 'com.smartcoon.twentyoneonline'")
        env.disable_module()
