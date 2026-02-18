Pod::Spec.new do |s|
  s.name           = 'ExpoSalesForceMIAW'
  s.version        = "0.1.0"
  s.summary        = "test"
  s.description    = "test"
  s.license        = "MIT"
  s.author         = "Jórdan Luiz Bisato"
  s.homepage       = "https://github.com/jordanbisato/expo-salesforce-miaw"
  s.platform       = :ios, '15.1'
  s.swift_version  = '5.9'
  s.source         = { git: 'https://github.com/jordanbisato/expo-salesforce-miaw' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'Messaging-InApp-UI'

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES'
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
