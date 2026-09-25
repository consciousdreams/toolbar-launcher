package it.consciousdreams;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

/** Remembers which toolbar button opened IntelliJ's native toolbar context menu. */
@Service(Service.Level.APP)
public final class ToolbarButtonContextMenu implements Disposable {

    private final AWTEventListener listener = this::recordPopupButton;
    private Context context;

    public ToolbarButtonContextMenu() {
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void recordPopupButton(AWTEvent event) {
        if (!(event instanceof MouseEvent mouse)
                || (mouse.getID() != MouseEvent.MOUSE_PRESSED && mouse.getID() != MouseEvent.MOUSE_RELEASED)
                || (!mouse.isPopupTrigger()
                    && !(mouse.getID() == MouseEvent.MOUSE_PRESSED && mouse.getButton() == MouseEvent.BUTTON3))) return;

        context = null;
        Component source = mouse.getComponent();
        Window window = source == null ? null : SwingUtilities.getWindowAncestor(source);
        if (window == null) return;
        Point point = SwingUtilities.convertPoint(source, mouse.getPoint(), window);
        ActionButton button = source instanceof ActionButton b ? b
                : (ActionButton) SwingUtilities.getAncestorOfClass(ActionButton.class, source);
        if (button == null) {
            Component hit = SwingUtilities.getDeepestComponentAt(window, point.x, point.y);
            button = hit instanceof ActionButton b ? b
                    : (ActionButton) SwingUtilities.getAncestorOfClass(ActionButton.class, hit);
        }
        if (button == null || !(button.getAction() instanceof ToolbarAction action)) return;

        ActionToolbar toolbar = ActionToolbar.findToolbarBy(button);
        Project project = DataManager.getInstance().getDataContext(button).getData(CommonDataKeys.PROJECT);
        if (toolbar != null && project != null && !project.isDisposed()) {
            context = new Context(button, action.getConfig().getId(), project,
                    ActionPlaces.getPopupPlace(toolbar.getPlace()));
        }
    }

    Context getContext(AnActionEvent event) {
        Context current = context;
        return current != null && current.button().isShowing() && current.popupPlace().equals(event.getPlace())
                ? current : null;
    }

    @Override
    public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        context = null;
    }

    record Context(ActionButton button, String configId, Project project, String popupPlace) {}
}
