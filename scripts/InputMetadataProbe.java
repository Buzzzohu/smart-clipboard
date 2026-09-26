import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/** Read-only probe: never requests node text or content descriptions. */
public class InputMetadataProbe extends UiAutomatorTestCase {
    public void testInputMetadata() throws Exception {
        Method bridgeMethod = UiDevice.class.getDeclaredMethod("getAutomatorBridge");
        bridgeMethod.setAccessible(true);
        Object bridge = bridgeMethod.invoke(getUiDevice());
        UiAutomation automation = null;
        for (Class<?> type = bridge.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == UiAutomation.class) {
                    field.setAccessible(true);
                    automation = (UiAutomation) field.get(bridge);
                }
            }
        }
        if (automation == null) throw new IllegalStateException("UiAutomation unavailable");
        AccessibilityServiceInfo info = automation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        automation.setServiceInfo(info);
        sleep(400);
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            String pkg = String.valueOf(root.getPackageName());
            if (!pkg.equals("tv.danmaku.bili") && !pkg.equals("com.ss.android.ugc.aweme")) continue;
            System.out.println("METADATA window=" + window.getId() + " package=" + pkg
                    + " focused=" + window.isFocused());
            visit(root, "", 0);
        }
    }

    private void visit(AccessibilityNodeInfo node, String ancestors, int depth) {
        if (depth > 40) return;
        String path = ancestors + "/" + node.getClassName() + "[" + node.getViewIdResourceName() + "]";
        if (node.isEditable() || node.isFocused()) {
            boolean setText = node.getActionList().contains(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT);
            System.out.println("METADATA editable=" + node.isEditable() + " focused=" + node.isFocused()
                    + " password=" + node.isPassword() + " setText=" + setText + " path=" + path);
            String hint = String.valueOf(node.getHintText());
            for (String marker : new String[]{"评论", "回复", "私信", "消息", "聊天", "弹幕", "搜索", "说点什么"}) {
                if (hint.contains(marker)) System.out.println("METADATA hintMarker=" + marker);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) visit(child, path, depth + 1);
        }
    }
}
