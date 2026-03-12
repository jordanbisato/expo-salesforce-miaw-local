/**
 * Unified plugin for Salesforce Messaging In-App (Android)
 *
 * app.json:
 * {
 *   "plugins": [
 *     "./plugins/salesforce-miaw-android-53.js"
 *   ]
 * }
 */

const {
  withProjectBuildGradle,
  withAppBuildGradle,
  withGradleProperties,
  withAndroidManifest,
  withDangerousMod,
  createRunOncePlugin,
} = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

// ========================================
// 1. Project Build Gradle (android/build.gradle)
// ========================================

function withAndroidProjectBuildGradle(config) {
  return withProjectBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    // 1.1 Add repositories Maven to Salesforce
    // Fix to insert after default repositories (google, mavenCentral, jitpack)
    const salesforceRepos = `
        maven { url 'https://s3.amazonaws.com/salesforcesos.com/android/maven/release' }
        maven { url "https://s3.amazonaws.com/inapp.salesforce.com/public/android" }`;

    if (
      !contents.includes('salesforcesos.com/android/maven/release') ||
      !contents.includes('inapp.salesforce.com')
    ) {
      const jitpackRegex = /maven\s*{\s*url\s*['"]https:\/\/www\.jitpack\.io['"]\s*}/;
      const mavenCentralRegex = /mavenCentral\(\)/;

      if (contents.match(jitpackRegex)) {
        contents = contents.replace(jitpackRegex, (match) => `${match}${salesforceRepos}`);
      } else if (contents.match(mavenCentralRegex)) {
        contents = contents.replace(mavenCentralRegex, (match) => `${match}${salesforceRepos}`);
      } else {
        contents = contents.replace(
          /allprojects\s*{\s*repositories\s*{/,
          `allprojects {\n    repositories {${salesforceRepos}`,
        );
      }
    }

    // 1.2 Adicionar/Atualizar Kotlin Gradle Plugin
    const kotlinPluginLine =
      "        classpath('org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10')";
    const kotlinGradlePluginRegex =
      /classpath\(['"]org\.jetbrains\.kotlin:kotlin-gradle-plugin:.*['"]\)/;

    if (contents.match(kotlinGradlePluginRegex)) {
      console.log('🔄 Updating Kotlin Gradle Plugin to 2.2.10...');
      contents = contents.replace(kotlinGradlePluginRegex, kotlinPluginLine);
    } else {
      console.log('➕ Updating Kotlin Gradle Plugin to 2.2.10...');
      const dependenciesBlockRegex =
        /(buildscript\s*{\s*repositories\s*{[^}]*}\s*dependencies\s*{)/;
      if (contents.match(dependenciesBlockRegex)) {
        contents = contents.replace(dependenciesBlockRegex, `$1\n${kotlinPluginLine}`);
      } else {
        console.warn('Error when trying to add Kotlin Gradle Plugin.');
      }
    }

    config.modResults.contents = contents;
    return config;
  });
}

// ========================================
// 2. App Build Gradle (android/app/build.gradle)
// ========================================

function withAndroidAppBuildGradle(config) {
  return withAppBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    // 2.4 Adicionar packagingOptions
    if (!contents.includes('META-INF/versions/9/OSGI-INF/MANIFEST.MF')) {
      contents = contents.replace(/android\s*{[\s\S]*?}/m, (match) => {
        if (match.includes('packagingOptions')) {
          return match.replace(
            /packagingOptions\s*{([\s\S]*?)}/m,
            `packagingOptions {
              resources {
                excludes += ["META-INF/versions/9/OSGI-INF/MANIFEST.MF"]
              }
            }`,
          );
        }
        return match.replace(
          /android\s*{/,
          `android {
            packagingOptions {
              resources {
                excludes += ["META-INF/versions/9/OSGI-INF/MANIFEST.MF"]
              }
            }`,
        );
      });
    }

    config.modResults.contents = contents;
    return config;
  });
}

// ========================================
// 5. Plugin Principal Unificado
// ========================================

function withSalesforceAndroid(config) {
  config = withAndroidProjectBuildGradle(config);
  config = withAndroidAppBuildGradle(config);
  return config;
}

// Exportar como run-once plugin
module.exports = createRunOncePlugin(
  withSalesforceAndroid,
  'salesforce-miaw-android-53',
  '1.0.0',
);
