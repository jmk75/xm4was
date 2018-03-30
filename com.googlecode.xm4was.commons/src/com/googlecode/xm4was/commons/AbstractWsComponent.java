package com.googlecode.xm4was.commons;

import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.googlecode.xm4was.commons.resources.Messages;
import com.ibm.ws.exception.ComponentDisabledException;
import com.ibm.ws.exception.ConfigurationError;
import com.ibm.ws.exception.ConfigurationWarning;
import com.ibm.ws.exception.RuntimeError;
import com.ibm.ws.exception.RuntimeWarning;
import com.ibm.wsspi.runtime.component.WsComponent;

public abstract class AbstractWsComponent implements WsComponent {
    private static final Logger LOGGER = Logger.getLogger(AbstractWsComponent.class.getName(), Messages.class.getName());
    
    private String state;
    private final Stack<Runnable> stopActions = new Stack<Runnable>();

    public final String getName() {
        return "XM_" + getClass().getSimpleName();
    }

    public final String getState() {
        return state;
    }

    public final void initialize(Object config) throws ComponentDisabledException,
            ConfigurationWarning, ConfigurationError {
        state = INITIALIZING;
        try {
            doInitialize();
        } catch (Exception ex) {
            throw new ConfigurationError(ex);
        }
        state = INITIALIZED;
    }
    
    protected void doInitialize() throws Exception {
    }
    
    public final void start() throws RuntimeError, RuntimeWarning {
        state = STARTING;
        try {
            doStart();
        } catch (Exception ex) {
            throw new RuntimeError(ex);
        }
        state = STARTED;
    }

    protected void doStart() throws Exception {
    }
    
    protected final void addStopAction(Runnable action) {
        if (state != STARTING) {
            throw new IllegalStateException();
        }
        stopActions.push(action);
    }
    
    public final void stop() {
        state = STOPPING;
        while (!stopActions.isEmpty()) {
            try {
                stopActions.pop().run();
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, Messages._0001E, ex);
            }
        }
        state = STOPPED;
    }

    public final void destroy() {
        state = DESTROYING;
        doDestroy();
        state = DESTROYED;
    }

    protected void doDestroy() {
    }
}
