package dev.weissenba.aerovista;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;

import javax.swing.JDialog;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Dialog;
import java.awt.GraphicsDevice;
import java.awt.IllegalComponentStateException;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;

/**
 * Applies uniform translucency for secondary windows. Uniform opacity avoids
 * the decorated-frame crash caused
 * by alpha-valued Panel.background colors on macOS.
 */
public final class AeroVistaApplicationInitializer implements ApplicationInitializedListener, Disposable {
    private static final float DIALOG_OPACITY = 0.94f;
    private static final float FLOATING_WINDOW_OPACITY = 0.96f;

    private AWTEventListener windowListener;

    @Override
    public void componentsInitialized() {
        Application application = ApplicationManager.getApplication();
        Disposer.register(application, this);

        windowListener = event -> {
            if (event instanceof WindowEvent windowEvent
                    && windowEvent.getID() == WindowEvent.WINDOW_OPENED) {
                applyFloatingWindowOpacity(windowEvent.getWindow());
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(windowListener, AWTEvent.WINDOW_EVENT_MASK);
        SwingUtilities.invokeLater(() -> {
            for (Window window : Window.getWindows()) {
                applyFloatingWindowOpacity(window);
            }
        });
    }

    private static void applyFloatingWindowOpacity(Window window) {
        if (!(window instanceof Dialog) && !(window instanceof JWindow)) {
            return;
        }

        GraphicsDevice device = window.getGraphicsConfiguration().getDevice();
        if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            return;
        }

        float opacity = window instanceof JDialog ? DIALOG_OPACITY : FLOATING_WINDOW_OPACITY;
        try {
            window.setOpacity(opacity);
        } catch (IllegalComponentStateException | UnsupportedOperationException | SecurityException ignored) {
            // Some native helper windows reject translucency; leaving them opaque is the safe fallback.
        }
    }

    @Override
    public void dispose() {
        if (windowListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(windowListener);
            windowListener = null;
        }
    }
}
