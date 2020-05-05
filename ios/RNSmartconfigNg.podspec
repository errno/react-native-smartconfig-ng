
Pod::Spec.new do |s|
  s.name         = "RNSmartconfigNg"
  s.version      = "1.0.0"
  s.summary      = "RNSmartconfigNg"
  s.description  = <<-DESC
                  RNSmartconfigNg
                   DESC
  s.homepage     = ""
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author             = { "author" => "artem.hodlevskyy@gmail.com" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/errno/react-native-smartconfig-ng", :tag => "master" }
  s.source_files  = "RNSmartconfigNg/**/*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end

  