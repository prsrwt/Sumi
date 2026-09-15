import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sumi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.prsrwt.sumi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    /*
     * Release signing reads its keystore and passwords from outside the project,
     * in ~/.sumi-signing. Keeping them out of the repo means git can never commit
     * them, and out of this OneDrive-synced folder means they are not uploaded
     * without a deliberate choice. Without that file, release builds are simply
     * left unsigned; debug builds are unaffected.
     */
    val signingProps = Properties().apply {
        val file = File(System.getProperty("user.home"), ".sumi-signing/keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    signingConfigs {
        if (signingProps.containsKey("storeFile")) {
            create("release") {
                storeFile = File(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Shrinks and optimises the release build. Anything reached by name at
            // runtime needs a keep rule in proguard-rules.pro, and the release build
            // has to be exercised on a device - R8 breakages never show up in debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // Element's colours call android.graphics.Color, which throws in plain JVM
        // tests unless stubbed framework calls return defaults.
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

/*
 * Room writes each database version's layout to app/schemas. Those files are
 * committed, so every future change to the tables can be checked against the
 * exact layout that is already on people's phones.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/*
 * The study guide lives once, in docs/ at the top of the repository, where GitHub
 * Pages can serve it. This copies it into the app's assets on every build, so the
 * guide inside Sumi and the one on the web are always the same files.
 */
abstract class SyncStudyGuide : DefaultTask() {
    @get:InputFiles
    abstract val guideFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("guide")
        target.deleteRecursively()
        target.mkdirs()
        guideFiles.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

val syncStudyGuide = tasks.register<SyncStudyGuide>("syncStudyGuide") {
    guideFiles.from(rootProject.file("docs/index.html"), rootProject.file("docs/content.js"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncStudyGuide, SyncStudyGuide::outputDir)
    }
}

/*
 * One house rule for all prose in this project, from code comments to the README:
 * no em dashes. Every build scans the project's text files first and stops, naming
 * each line, if one has crept in. Third-party licence text is left as written.
 */
abstract class CheckNoEmDashes : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun check() {
        val emDash = Char(0x2014)
        val root = projectRoot.get().asFile
        val found = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (emDash in line) "${file.relativeTo(root)}:${index + 1}" else null
            }
        }
        if (found.isNotEmpty()) {
            throw GradleException(
                "This project does not use em dashes. Replace them with a comma, colon, " +
                    "full stop or parentheses in:\n" + found.joinToString("\n")
            )
        }
    }
}

val checkNoEmDashes = tasks.register<CheckNoEmDashes>("checkNoEmDashes") {
    projectRoot.set(rootProject.layout.projectDirectory)
    sources.from(
        rootProject.fileTree(rootProject.projectDir) {
            include(
                "**/*.kt", "**/*.kts", "**/*.java", "**/*.xml", "**/*.md", "**/*.html", "**/*.js",
                "**/*.css", "**/*.json", "**/*.pro", "**/*.toml", "**/*.yml", "**/*.yaml", "**/*.txt"
            )
            exclude("**/build/**", "**/.gradle/**", "**/.git/**", "**/.idea/**", "**/assets/licenses/**")
        }
    )
}

tasks.named("preBuild") { dependsOn(checkNoEmDashes) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // Android's own org.json is only a stub in plain JVM tests; this is the real one.
    testImplementation(libs.org.json)

    // Asking for permission to the user's Drive. Everything after that is plain
    // HTTPS, so none of Google's heavy API client libraries are needed.
    implementation(libs.play.services.auth)
    // Background sync that waits for a network and survives restarts.
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
