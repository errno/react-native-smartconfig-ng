
Pod::Spec.new do |s|
  s.name         = "RNSmartconfigNg"
  s.version      = "1.0.0"
  s.summary      = "RNSmartconfigNg"
  s.description  = <<-DESC
                  RNSmartconfigNg
                   DESC
  s.homepage     = "https://github.com/errno/react-native-smartconfig-ng"
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author             = { "author" => "artem.hodlevskyy@gmail.com" }
  s.platform     = :ios, "8.0"
  s.source       = { :git => "https://github.com/errno/react-native-smartconfig-ng.git", :tag => "master" }
  s.source_files  = "**/*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end

  
