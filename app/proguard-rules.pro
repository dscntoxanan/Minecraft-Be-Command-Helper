# 命令助手 release 混淆规则

# 保留入口四大组件（反射由系统调用，混淆后会找不到类）。
-keep class com.xanan.cmdhelper.ui.MainActivity { *; }
-keep class com.xanan.cmdhelper.service.** { *; }
# 外部唤起通道的落点：AndroidManifest 里按全限定名指向它（android:name），
# 混淆掉名字 = manifest 指向一个不存在的类 = 启动即崩。
-keep class com.xanan.cmdhelper.ui.ShowPanelActivity { *; }

# 无障碍服务由系统按 XML 里的全限定名反射实例化。
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
