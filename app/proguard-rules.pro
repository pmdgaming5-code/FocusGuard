# FocusGuard does not use reflection-based serializers.
# Keep Android framework components discoverable by the manifest.
-keep public class com.copperbars.focusguard.monitor.UsageMonitorService
-keep public class com.copperbars.focusguard.admin.FocusGuardAdminReceiver
