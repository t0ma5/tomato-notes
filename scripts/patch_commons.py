#!/usr/bin/env python3
"""Apply fork-only patches to the CI checkout of Simple-Commons."""
from pathlib import Path
import re
import sys

ROOT = Path("Simple-Commons")
DOMAIN_RE = re.compile(
    r"(?:https?://(?:www\.)?simplemobiletools\.com[^\s\"'<>]*)"
    r"|(?:mailto:hello@simplemobiletools\.com)"
    r"|(?:hello@simplemobiletools\.com)"
    r"|(?:www\.simplemobiletools\.com)"
    r"|(?<![\w.])simplemobiletools\.com",
    re.IGNORECASE,
)


def patch(rel: str, replacements: list[tuple[str, str]]) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    for old, new in replacements:
        if old not in text:
            raise SystemExit(f"patch failed in {rel}: {old[:90]!r} not found")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")
    print(f"patched {rel}")


def patch_commons_versions() -> None:
    """AGP/Kotlin/KSP/SDK so the pinned Commons checkout configures under Gradle 9.1 / JDK 25."""
    patch(
        "gradle/libs.versions.toml",
        [
            ('gradlePlugins-agp = "8.1.1"', 'gradlePlugins-agp = "8.13.2"'),
            ('kotlin = "1.9.10"', 'kotlin = "2.2.10"'),
            ('ksp = "1.9.10-1.0.13"', 'ksp = "2.2.10-2.0.2"'),
            ('kotlinxSerializationJson = "1.5.1"', 'kotlinxSerializationJson = "1.8.1"'),
            ('room = "2.6.0-beta01"', 'room = "2.8.4"'),
            ('app-build-compileSDKVersion = "34"', 'app-build-compileSDKVersion = "36"'),
            ('app-build-targetSDK = "34"', 'app-build-targetSDK = "36"'),
            ('rtlViewpager = "940f12724f"', 'rtlViewpager = "2.0.2"'),
            (
                'rtl-viewpager = { module = "com.github.duolingo:rtl-viewpager", version.ref = "rtlViewpager" }',
                'rtl-viewpager = { module = "com.github.naveensingh:rtl-viewpager", version.ref = "rtlViewpager" }',
            ),
            (
                'kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }\n',
                'kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }\n'
                'kotlinCompose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }\n',
            ),
        ],
    )


def patch_gradle_for_gradle9() -> None:
    compose_options = (
        "    composeOptions {\n"
        "        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()\n"
        "    }\n\n"
    )
    kotlin_options_compose = (
        "    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {\n"
        "        kotlinOptions.jvmTarget = project.libs.versions.app.build.kotlinJVMTarget.get()\n"
        "        kotlinOptions.freeCompilerArgs = listOf(\n"
        "            \"-opt-in=kotlin.RequiresOptIn\",\n"
        "            \"-opt-in=androidx.compose.material3.ExperimentalMaterial3Api\",\n"
        "            \"-opt-in=androidx.compose.material.ExperimentalMaterialApi\",\n"
        "            \"-opt-in=androidx.compose.foundation.ExperimentalFoundationApi\",\n"
        "            \"-Xcontext-receivers\"\n"
        "        )\n"
        "    }\n"
    )
    kotlin_options_samples_short = (
        "    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {\n"
        "        kotlinOptions.jvmTarget = project.libs.versions.app.build.kotlinJVMTarget.get()\n"
        "        kotlinOptions.freeCompilerArgs = listOf(\n"
        "            \"-opt-in=kotlin.RequiresOptIn\",\n"
        "            \"-Xcontext-receivers\"\n"
        "        )\n"
        "    }\n"
    )
    compiler_options_compose = (
        "    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {\n"
        "        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)\n"
        "        compilerOptions.freeCompilerArgs.addAll(\n"
        "            \"-opt-in=kotlin.RequiresOptIn\",\n"
        "            \"-opt-in=androidx.compose.material3.ExperimentalMaterial3Api\",\n"
        "            \"-opt-in=androidx.compose.material.ExperimentalMaterialApi\",\n"
        "            \"-opt-in=androidx.compose.foundation.ExperimentalFoundationApi\",\n"
        "            \"-Xcontext-receivers\"\n"
        "        )\n"
        "    }\n"
    )
    compiler_options_samples_short = (
        "    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {\n"
        "        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)\n"
        "        compilerOptions.freeCompilerArgs.addAll(\n"
        "            \"-opt-in=kotlin.RequiresOptIn\",\n"
        "            \"-Xcontext-receivers\"\n"
        "        )\n"
        "    }\n"
    )
    patch(
        "build.gradle.kts",
        [
            (
                "    alias(libs.plugins.kotlinAndroid).apply(false)\n",
                "    alias(libs.plugins.kotlinAndroid).apply(false)\n"
                "    alias(libs.plugins.kotlinCompose).apply(false)\n",
            ),
        ],
    )
    patch(
        "commons/build.gradle.kts",
        [
            (
                "    alias(libs.plugins.kotlinAndroid)\n",
                "    alias(libs.plugins.kotlinAndroid)\n"
                "    alias(libs.plugins.kotlinCompose)\n",
            ),
            (compose_options, ""),
            (kotlin_options_compose, compiler_options_compose),
        ],
    )
    patch(
        "samples/build.gradle.kts",
        [
            (kotlin_options_samples_short, compiler_options_samples_short),
        ],
    )


def patch_kotlin2_nullability() -> None:
    """Kotlin 2.2 treats PackageManager applicationInfo as nullable."""
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/adapters/FilepickerItemsAdapter.kt",
        [
            (
                "                    if (packageInfo != null) {\n"
                "                        val appInfo = packageInfo.applicationInfo\n"
                "                        appInfo.sourceDir = path\n"
                "                        appInfo.publicSourceDir = path\n"
                "                        appInfo.loadIcon(root.context.packageManager)\n"
                "                    } else {\n",
                "                    val appInfo = packageInfo?.applicationInfo\n"
                "                    if (appInfo != null) {\n"
                "                        appInfo.sourceDir = path\n"
                "                        appInfo.publicSourceDir = path\n"
                "                        appInfo.loadIcon(root.context.packageManager)\n"
                "                    } else {\n",
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/extensions/Activity.kt",
        [
            (
                "fun Activity.isAppInstalledOnSDCard(): Boolean = try {\n"
                "    val applicationInfo = packageManager.getPackageInfo(packageName, 0).applicationInfo\n"
                "    (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE\n"
                "} catch (e: Exception) {\n"
                "    false\n"
                "}\n",
                "fun Activity.isAppInstalledOnSDCard(): Boolean = try {\n"
                "    val applicationInfo = packageManager.getPackageInfo(packageName, 0).applicationInfo\n"
                "    applicationInfo != null && (applicationInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE\n"
                "} catch (e: Exception) {\n"
                "    false\n"
                "}\n",
            ),
        ],
    )


def rewrite_deprecated_string_apis() -> None:
    """Kotlin 2.2 treats String.toLowerCase/toUpperCase as errors."""
    count = 0
    for path in ROOT.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        new = text.replace(".toLowerCase(", ".lowercase(").replace(
            ".toUpperCase(", ".uppercase("
        )
        if new == text:
            continue
        path.write_text(new, encoding="utf-8")
        count += 1
        print(f"rewrote string case APIs in {path.relative_to(ROOT)}")
    print(f"rewrote string case APIs in {count} files")


def scrub_simplemobiletools_com() -> None:
    """Strip leftover website/email mentions from Commons sources and strings."""
    count = 0
    for path in ROOT.rglob("*"):
        if path.suffix.lower() not in {".kt", ".xml", ".java"}:
            continue
        text = path.read_text(encoding="utf-8")
        # Package names like com.simplemobiletools.commons contain the
        # substring "simplemobiletools.com"; only rewrite actual website/email hits.
        if DOMAIN_RE.search(text) is None:
            continue
        new = DOMAIN_RE.sub("", text)
        if DOMAIN_RE.search(new):
            raise SystemExit(f"domain still present after scrub in {path}")
        path.write_text(new, encoding="utf-8")
        count += 1
        print(f"scrubbed simplemobiletools.com from {path.relative_to(ROOT)}")
    print(f"scrubbed {count} Commons files")


def main() -> None:
    if not ROOT.exists():
        raise SystemExit("Simple-Commons/ is missing")

    patch_commons_versions()
    patch_gradle_for_gradle9()
    rewrite_deprecated_string_apis()
    patch_kotlin2_nullability()

    fake = (
        "You are using a fake version of the app. For your own safety download the original "
        "one from www.simplemobiletools.com. Thanks"
    )

    # Notes pins Commons eceb48949e; fake-version still uses a raw Play Store URL,
    # not DEVELOPER_PLAY_STORE_URL (that landed in a later Commons commit).
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/activities/BaseSimpleActivity.kt",
        [
            (
                '        if (!packageName.startsWith("com.simplemobiletools.", true)) {\n'
                "            if ((0..50).random() == 10 || baseConfig.appRunCount % 100 == 0) {\n"
                f'                val label = "{fake}"\n'
                "                ConfirmationDialog(this, label, positive = R.string.ok, negative = 0) {\n"
                '                    launchViewIntent("https://play.google.com/store/apps/dev?id=9070296388022589266")\n'
                "                }\n"
                "            }\n"
                "        }\n",
                "",
            ),
            (
                '        if (!packageName.contains("slootelibomelpmis".reversed(), true)) {\n'
                "            if (baseConfig.appRunCount > 100) {\n"
                f'                val label = "{fake}"\n'
                "                ConfirmationDialog(this, label, positive = R.string.ok, negative = 0) {\n"
                '                    launchViewIntent("https://play.google.com/store/apps/dev?id=9070296388022589266")\n'
                "                }\n"
                "                return\n"
                "            }\n"
                "        }\n\n",
                "",
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/activities/BaseSimpleActivity.kt",
        [
            (
                "            } else {\n"
                "                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility.removeBit(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)\n"
                "                updateTopBottomInsets(0, 0)\n"
                "            }\n"
                "        }\n"
                "    }\n",
                "            } else {\n"
                "                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility.removeBit(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)\n"
                "                updateTopBottomInsets(statusBarHeight, 0)\n"
                "                onApplyWindowInsets {\n"
                "                    val insets = it.getInsets(WindowInsetsCompat.Type.systemBars())\n"
                "                    updateTopBottomInsets(insets.top, 0)\n"
                "                }\n"
                "            }\n"
                "        } else {\n"
                "            updateTopBottomInsets(statusBarHeight, 0)\n"
                "            onApplyWindowInsets {\n"
                "                val insets = it.getInsets(WindowInsetsCompat.Type.systemBars())\n"
                "                updateTopBottomInsets(insets.top, 0)\n"
                "            }\n"
                "        }\n"
                "    }\n",
            ),
        ],
    )

    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/extensions/Activity.kt",
        [
            (
                "fun Activity.checkAppSideloading(): Boolean {\n"
                "    val isSideloaded = when (baseConfig.appSideloadingStatus) {\n"
                "        SIDELOADING_TRUE -> true\n"
                "        SIDELOADING_FALSE -> false\n"
                "        else -> isAppSideloaded()\n"
                "    }\n"
                "\n"
                "    baseConfig.appSideloadingStatus = if (isSideloaded) SIDELOADING_TRUE else SIDELOADING_FALSE\n"
                "    if (isSideloaded) {\n"
                "        showSideloadingDialog()\n"
                "    }\n"
                "\n"
                "    return isSideloaded\n"
                "}\n",
                "fun Activity.checkAppSideloading(): Boolean {\n"
                "    return false\n"
                "}\n",
            ),
        ],
    )

    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/activities/AboutActivity.kt",
        [
            (
                'launchViewIntent("https://github.com/SimpleMobileTools")',
                'launchViewIntent("https://github.com/t0ma5/tomato-notes")',
            ),
            (
                """    private fun onVersionClick() {
        if (firstVersionClickTS == 0L) {
            firstVersionClickTS = System.currentTimeMillis()
            Handler(Looper.getMainLooper()).postDelayed({
                firstVersionClickTS = 0L
                clicksSinceFirstClick = 0
            }, EASTER_EGG_TIME_LIMIT)
        }

        clicksSinceFirstClick++
        if (clicksSinceFirstClick >= EASTER_EGG_REQUIRED_CLICKS) {
            toast(R.string.hello)
            firstVersionClickTS = 0L
            clicksSinceFirstClick = 0
        }
    }
""",
                """    private fun onVersionClick() {
        launchViewIntent("https://github.com/t0ma5/tomato-notes/releases")
    }
""",
            ),
            (
                "showPrivacyPolicy = showExternalLinks,",
                "showPrivacyPolicy = false,",
            ),
            (
                "                    helpUsSection = {\n"
                "                        val showHelpUsSection =\n"
                "                            remember { showGoogleRelations || !showExternalLinks }\n"
                "                        HelpUsSection(\n"
                "                            onRateUsClick = ::onRateUsClick,\n"
                "                            onInviteClick = ::onInviteClick,\n"
                "                            onContributorsClick = ::onContributorsClick,\n"
                "                            showDonate = resources.getBoolean(R.bool.show_donate_in_about) && showExternalLinks,\n"
                "                            onDonateClick = ::onDonateClick,\n"
                "                            showInvite = showHelpUsSection,\n"
                "                            showRateUs = showHelpUsSection\n"
                "                        )\n"
                "                    },\n",
                "                    helpUsSection = {},\n",
            ),
            (
                "                    aboutSection = {\n"
                "                        val setupFAQ = remember { !(intent.getSerializableExtra(APP_FAQ) as? ArrayList<FAQItem>).isNullOrEmpty() }\n"
                "                        if (!showExternalLinks || setupFAQ) {\n"
                "                            AboutSection(setupFAQ = setupFAQ, onFAQClick = ::launchFAQActivity, onEmailClick = ::onEmailClick)\n"
                "                        }\n"
                "                    },\n",
                "                    aboutSection = {},\n",
            ),
        ],
    )

    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/compose/screens/AboutScreen.kt",
        [
            (
                "import androidx.compose.ui.res.stringResource\n",
                "import androidx.compose.ui.res.colorResource\n"
                "import androidx.compose.ui.res.stringResource\n",
            ),
            (
                "    SettingsScaffold(title = stringResource(id = R.string.about), goBack = goBack) {\n"
                "        aboutSection()\n"
                "        helpUsSection()\n"
                "        socialSection()\n"
                "        otherSection()\n"
                "        SettingsListItem(text = stringResource(id = R.string.about_footer))\n"
                "    }\n",
                "    SettingsScaffold(title = stringResource(id = R.string.about), goBack = goBack) {\n"
                "        HistorySection()\n"
                "        aboutSection()\n"
                "        helpUsSection()\n"
                "        socialSection()\n"
                "        otherSection()\n"
                "    }\n",
            ),
            (
                "}\n\n@Composable\ninternal fun HelpUsSection(\n",
                "}\n\n@Composable\ninternal fun HistorySection() {\n"
                "    SettingsGroup(title = {\n"
                "        SettingsTitleTextComponent(\n"
                "            text = stringResource(id = R.string.history),\n"
                "            modifier = startingTitlePadding,\n"
                "            color = colorResource(id = R.color.color_primary)\n"
                "        )\n"
                "    }) {\n"
                "        SettingsListItem(\n"
                "            tint = MaterialTheme.colorScheme.onSurface,\n"
                "            text = stringResource(id = R.string.about_history_text),\n"
                "        )\n"
                "        SettingsHorizontalDivider()\n"
                "    }\n"
                "}\n\n@Composable\ninternal fun HelpUsSection(\n",
            ),
            (
                "        SettingsTitleTextComponent(text = stringResource(id = R.string.other), modifier = startingTitlePadding)",
                "        SettingsTitleTextComponent(\n"
                "            text = stringResource(id = R.string.other),\n"
                "            modifier = startingTitlePadding,\n"
                "            color = colorResource(id = R.color.color_primary)\n"
                "        )",
            ),
            (
                "        SettingsTitleTextComponent(text = stringResource(id = R.string.social), modifier = startingTitlePadding)",
                "        SettingsTitleTextComponent(\n"
                "            text = stringResource(id = R.string.website),\n"
                "            modifier = startingTitlePadding,\n"
                "            color = colorResource(id = R.color.color_primary)\n"
                "        )",
            ),
            (
                "        SocialText(\n"
                "            click = onFacebookClick,\n"
                "            text = stringResource(id = R.string.facebook),\n"
                "            icon = R.drawable.ic_facebook_vector,\n"
                "        )\n"
                "        SocialText(\n"
                "            click = onGithubClick,\n"
                "            text = stringResource(id = R.string.github),\n"
                "            icon = R.drawable.ic_github_vector,\n"
                "            tint = MaterialTheme.colorScheme.onSurface\n"
                "        )\n"
                "        SocialText(\n"
                "            click = onRedditClick,\n"
                "            text = stringResource(id = R.string.reddit),\n"
                "            icon = R.drawable.ic_reddit_vector,\n"
                "        )\n"
                "        SocialText(\n"
                "            click = onTelegramClick,\n"
                "            text = stringResource(id = R.string.telegram),\n"
                "            icon = R.drawable.ic_telegram_vector,\n"
                "        )\n",
                "        SocialText(\n"
                "            click = onGithubClick,\n"
                "            text = stringResource(id = R.string.github),\n"
                "            icon = R.drawable.ic_github_vector,\n"
                "            tint = MaterialTheme.colorScheme.onSurface\n"
                "        )\n",
            ),
        ],
    )

    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/helpers/Constants.kt",
        [
            (
                "const val LICENSE_ZIP4J = 8589934592L\n",
                "const val LICENSE_ZIP4J = 8589934592L\n"
                "const val LICENSE_MARKWON = 17179869184L\n"
                "const val LICENSE_KOTLINX_SERIALIZATION = 34359738368L\n",
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/activities/LicenseActivity.kt",
        [
            (
                "        License(LICENSE_ZIP4J, R.string.zip4j_title, R.string.zip4j_text, R.string.zip4j_url)\n",
                "        License(LICENSE_ZIP4J, R.string.zip4j_title, R.string.zip4j_text, R.string.zip4j_url),\n"
                "        License(LICENSE_MARKWON, R.string.markwon_title, R.string.markwon_text, R.string.markwon_url),\n"
                "        License(LICENSE_KOTLINX_SERIALIZATION, R.string.kotlinx_serialization_title, R.string.kotlinx_serialization_text, R.string.kotlinx_serialization_url)\n",
            ),
        ],
    )
    patch(
        "commons/src/main/res/values/strings.xml",
        [
            (
                '    <string name="zip4j_title">Zip4j (ZIP compression and decompression)</string>\n',
                '    <string name="zip4j_title">Zip4j (ZIP compression and decompression)</string>\n'
                '    <string name="markwon_title">Markwon (markdown rendering)</string>\n'
                '    <string name="kotlinx_serialization_title">Kotlinx Serialization (JSON)</string>\n',
            ),
            (
                '    <string name="disclaimer">Disclaimer</string>\n',
                '    <string name="disclaimer">Disclaimer</string>\n'
                '    <string name="history">History</string>\n'
                '    <string name="about_history_text">Simple-Notes (GPL-3.0) was my favorite FOSS notes app until the project was sold to a shady Israeli company named ZipoApps in 2023. I forked it to keep it alive, FOSS and updated. I will release new versions as long as I have time and energy. Code contributions on Github are very welcome :)</string>\n',
            ),
        ],
    )
    patch(
        "commons/src/main/res/values/donottranslate.xml",
        [
            (
                '    <string name="zip4j_url">https://github.com/srikanth-lingala/zip4j</string>\n',
                '    <string name="zip4j_url">https://github.com/srikanth-lingala/zip4j</string>\n'
                '    <string name="markwon_text">Copyright 2019 Dimitry Ivanov\\n\\nLicensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\\n\\nhttps://www.apache.org/licenses/LICENSE-2.0\\n\\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.</string>\n'
                '    <string name="markwon_url">https://github.com/noties/Markwon</string>\n'
                '    <string name="kotlinx_serialization_text">Copyright 2017-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.\\n\\nLicensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at\\n\\nhttps://www.apache.org/licenses/LICENSE-2.0\\n\\nUnless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.</string>\n'
                '    <string name="kotlinx_serialization_url">https://github.com/Kotlin/kotlinx.serialization</string>\n',
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/dialogs/LineColorPickerDialog.kt",
        [
            (
                "    private val DEFAULT_PRIMARY_COLOR_INDEX = 14\n",
                "    private val DEFAULT_PRIMARY_COLOR_INDEX = 0\n",
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/compose/theme/Colors.kt",
        [
            (
                "val color_primary = Color(0xFFF57C00)\n"
                "val color_primary_dark = Color(0xFFD76D00)\n",
                "val color_primary = Color(0xFFD32F2F)\n"
                "val color_primary_dark = Color(0xFFB71C1C)\n",
            ),
        ],
    )
    patch(
        "commons/src/main/res/values/colors.xml",
        [
            (
                '    <color name="color_primary">#FFF57C00</color>\n'
                '    <color name="color_primary_dark">#FFD76D00</color>\n',
                '    <color name="color_primary">#FFD32F2F</color>\n'
                '    <color name="color_primary_dark">#FFB71C1C</color>\n',
            ),
            (
                '    <color name="theme_dark_red_primary_color">@color/md_red_700</color>\n',
                '    <color name="theme_dark_red_primary_color">@color/md_red_700</color>\n'
                '    <color name="theme_dark_red_text_color">#FFF5F5F5</color>\n'
                '    <color name="theme_dark_red_background_color">#FF121212</color>\n',
            ),
            (
                '    <color name="default_text_color">@color/theme_dark_text_color</color>\n'
                '    <color name="default_background_color">@color/theme_dark_background_color</color>\n',
                '    <color name="default_text_color">#FFF5F5F5</color>\n'
                '    <color name="default_background_color">#FF121212</color>\n',
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/extensions/Activity-themes.kt",
        [
            (
                "            else -> R.style.AppTheme_Orange_700_core\n",
                "            else -> R.style.AppTheme_Red_700_core\n",
            ),
            (
                "            else -> R.style.AppTheme_Orange_700\n",
                "            else -> R.style.AppTheme_Red_700\n",
            ),
        ],
    )

    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/helpers/BaseConfig.kt",
        [
            (
                '        get() = prefs.getBoolean(IS_USING_SYSTEM_THEME, isSPlus())\n',
                '        get() = prefs.getBoolean(IS_USING_SYSTEM_THEME, false)\n',
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/activities/CustomizationActivity.kt",
        [
            (
                '                MyTheme(\n'
                '                    getString(R.string.dark_red),\n'
                '                    R.color.theme_dark_text_color,\n'
                '                    R.color.theme_dark_background_color,\n'
                '                    R.color.theme_dark_red_primary_color,\n'
                '                    R.color.md_red_700\n'
                '                )\n',
                '                MyTheme(\n'
                '                    getString(R.string.dark_red),\n'
                '                    R.color.theme_dark_red_text_color,\n'
                '                    R.color.theme_dark_red_background_color,\n'
                '                    R.color.theme_dark_red_primary_color,\n'
                '                    R.color.md_red_700\n'
                '                )\n',
            ),
        ],
    )
    patch(
        "commons/src/main/kotlin/com/simplemobiletools/commons/activities/BaseSimpleActivity.kt",
        [
            (
                '        val backgroundColor = getProperBackgroundColor()\n'
                '        updateStatusbarColor(backgroundColor)\n'
                '        updateActionbarColor(backgroundColor)\n',
                '        val topColor = getProperPrimaryColor()\n'
                '        updateStatusbarColor(topColor)\n'
                '        updateActionbarColor(topColor)\n',
            ),
            (
                '    fun getRequiredStatusBarColor(): Int {\n'
                '        return if ((scrollingView is RecyclerView || scrollingView is NestedScrollView) && scrollingView?.computeVerticalScrollOffset() == 0) {\n'
                '            getProperBackgroundColor()\n'
                '        } else {\n'
                '            getColoredMaterialStatusBarColor()\n'
                '        }\n'
                '    }\n',
                '    fun getRequiredStatusBarColor(): Int {\n'
                '        return getProperPrimaryColor()\n'
                '    }\n',
            ),
        ],
    )
    scrub_simplemobiletools_com()
    print("commons fork patches applied")


if __name__ == "__main__":
    try:
        main()
    except SystemExit as e:
        print(e, file=sys.stderr)
        raise
