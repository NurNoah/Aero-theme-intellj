package dev.weissenba.aerovista;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.IllegalComponentStateException;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Applies uniform translucency for secondary windows. Uniform opacity avoids
 * the decorated-frame crash caused
 * by alpha-valued Panel.background colors on macOS.
 */
public final class AeroVistaApplicationInitializer implements ApplicationInitializedListener, Disposable {
    private static final Logger LOG = Logger.getInstance(AeroVistaApplicationInitializer.class);
    private static final float DIALOG_OPACITY = 0.91f;
    private static final float FLOATING_WINDOW_OPACITY = 0.92f;

    private AWTEventListener windowListener;

    @Override
    public Object execute(Continuation<? super Unit> continuation) {
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
        return Unit.INSTANCE;
    }

    private static void applyFloatingWindowOpacity(Window window) {
        if (window instanceof IdeFrame) {
            return;
        }

        if (!(window instanceof Dialog) && !(window instanceof JWindow) && !(window instanceof Frame)) {
            return;
        }

        float opacity = window instanceof JWindow ? FLOATING_WINDOW_OPACITY : DIALOG_OPACITY;
        try {
            window.setOpacity(opacity);
            LOG.info("Applied opacity " + window.getOpacity() + " to " + window.getClass().getName());
        } catch (IllegalComponentStateException exception) {
            if (applyDecoratedWindowOpacity(window, opacity)) {
                LOG.info("Applied native opacity " + opacity + " to " + window.getClass().getName());
            } else {
                LOG.warn("Could not apply opacity to " + window.getClass().getName(), exception);
            }
        } catch (UnsupportedOperationException | SecurityException exception) {
            LOG.warn("Could not apply opacity to " + window.getClass().getName(), exception);
        }
    }

    /**
     * The public AWT API rejects opacity for decorated dialogs on macOS even
     * though JetBrains Runtime's native window peer supports it. IntelliJ
     * opens the required java.desktop packages to plugins, so the peer can be
     * updated without changing decoration or rebuilding the dialog.
     */
    private static boolean applyDecoratedWindowOpacity(Window window, float opacity) {
        try {
            Field peerField = Component.class.getDeclaredField("peer");
            peerField.setAccessible(true);
            Object peer = peerField.get(window);
            if (peer == null) {
                return false;
            }

            Method setOpacity = peer.getClass().getMethod("setOpacity", float.class);
            setOpacity.setAccessible(true);
            setOpacity.invoke(peer, opacity);

            Field opacityField = Window.class.getDeclaredField("opacity");
            opacityField.setAccessible(true);
            opacityField.setFloat(window, opacity);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOG.warn("Native opacity fallback failed for " + window.getClass().getName(), exception);
            return false;
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
