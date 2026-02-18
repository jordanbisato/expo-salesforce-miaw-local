const {
  withInfoPlist,
  withAppBuildGradle,
  withProjectBuildGradle,
  withDangerousMod,
  createRunOncePlugin,
} = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

/**
 * Configurações para a plataforma iOS usando CocoaPods (Podfile)
 */
const withSalesforceMIAWiOS = (config, props) => {
  // 1. Adiciona descrições de privacidade no Info.plist
  config = withInfoPlist(config, (config) => {
    const infoPlist = config.modResults;

    infoPlist.NSCameraUsageDescription =
      infoPlist.NSCameraUsageDescription ||
      'Este app precisa de acesso à câmera para enviar fotos no chat.';
    
    infoPlist.NSPhotoLibraryUsageDescription =
      infoPlist.NSPhotoLibraryUsageDescription ||
      'Este app precisa de acesso à biblioteca de fotos para enviar imagens no chat.';

    infoPlist.LSSupportsOpeningDocumentsInPlace = true;
    infoPlist.UIFileSharingEnabled = true;

    return config;
  });

  // 2. Modificações no Podfile: Fontes de Specs e Dependência Nativa
  config = withDangerousMod(config, [
    'ios',
    async (config) => {
      const podfilePath = path.join(config.modRequest.projectRoot, 'ios', 'Podfile');
      
      if (!fs.existsSync(podfilePath)) {
        return config;
      }

      let podfileContent = fs.readFileSync(podfilePath, 'utf8');

      // Fontes otimizadas: Usamos a CDN do CocoaPods em vez do repositório Git padrão
      // A CDN é muito mais rápida e evita o travamento em "Installing CocoaPods"
      const sfSource = "source 'https://github.com/salesforce/service-sdk-ios-specs.git'";
      const cocoapodsCDN = "source 'https://cdn.cocoapods.org/'";
      
      // Adiciona as fontes no topo do arquivo se não existirem
      if (!podfileContent.includes('service-sdk-ios-specs.git')) {
        // Removemos qualquer referência a fontes Git antigas do CocoaPods se existirem para forçar CDN
        podfileContent = podfileContent.replace("source 'https://github.com/CocoaPods/Specs.git'", "");
        
        // Injetamos a fonte do Salesforce e a CDN do CocoaPods no topo
        if (!podfileContent.includes('source ')) {
          podfileContent = `${sfSource}\n${cocoapodsCDN}\n\n${podfileContent}`;
        } else {
          podfileContent = `${sfSource}\n${podfileContent}`;
        }
      }

      // Configuração da dependência do SDK
      const podName = 'Messaging-InApp-UI';
      const podVersion = props.iosVersion ? `, '~> ${props.iosVersion}'` : '';
      const podLine = `  pod '${podName}'${podVersion}`;

      // Injeta o pod se ele ainda não estiver presente
      if (!podfileContent.includes(podName)) {
        const searchString = 'use_expo_modules!';
        if (podfileContent.includes(searchString)) {
          podfileContent = podfileContent.replace(
            searchString,
            `${searchString}\n${podLine}`
          );
        } else {
          const lastEndIndex = podfileContent.lastIndexOf('end');
          if (lastEndIndex !== -1) {
            podfileContent = 
              podfileContent.slice(0, lastEndIndex) + 
              `${podLine}\n` + 
              podfileContent.slice(lastEndIndex);
          }
        }
      }
      
      fs.writeFileSync(podfilePath, podfileContent);
      return config;
    },
  ]);

  return config;
};

/**
 * Configurações para a plataforma Android
 */
const withSalesforceMIAWAndroid = (config, props) => {
  config = withProjectBuildGradle(config, (config) => {
    const buildGradle = config.modResults.contents;

    if (!buildGradle.includes('s3.amazonaws.com/inapp.salesforce.com')) {
      config.modResults.contents = buildGradle.replace(
        /allprojects\s*{\s*repositories\s*{/,
        `allprojects {
    repositories {
        maven {
            url "https://s3.amazonaws.com/inapp.salesforce.com/public/android/"
        }`
      );
    }

    return config;
  });

  config = withAppBuildGradle(config, (config) => {
    const buildGradle = config.modResults.contents;

    if (!buildGradle.includes('dataBinding')) {
      config.modResults.contents = buildGradle.replace(
        /android\s*{/,
        `android {
    buildFeatures {
        dataBinding true
    }`
      );
    }

    return config;
  });

  return config;
};

const withSalesforceMIAW = (config, props = {}) => {
  config = withSalesforceMIAWiOS(config, props);
  config = withSalesforceMIAWAndroid(config, props);
  return config;
};

module.exports = createRunOncePlugin(
  withSalesforceMIAW,
  'expo-salesforce-miaw',
  '1.3.2'
);
