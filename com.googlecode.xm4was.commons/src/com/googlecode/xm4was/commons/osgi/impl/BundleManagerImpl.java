package com.googlecode.xm4was.commons.osgi.impl;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.util.tracker.BundleTrackerCustomizer;

import com.googlecode.xm4was.commons.osgi.annotations.ProcessTypes;
import com.googlecode.xm4was.commons.osgi.annotations.Services;
import com.googlecode.xm4was.commons.resources.Messages;
import com.ibm.websphere.management.AdminServiceFactory;

/**
 * Manages bundles containing XM4WAS components. It looks for bundles having a manifest with an
 * <tt>XM4WAS-Components</tt> attribute and creates the components specified by that attribute.
 * <p>
 * <b>Note:</b> It is expected that bundles specifying an <tt>XM4WAS-Components</tt> attribute have
 * <tt>Eclipse-AutoStart: true</tt> set in their manifest. Earlier versions of XM4WAS attempted to
 * start the bundles explicitly, but this caused an issue on WAS 8.5 (because starting a bundle
 * explicitly modifies the state of the Equinox container and this causes an issue when the
 * WebSphere instance is restarted).
 */
final class BundleManagerImpl implements BundleManager, BundleTrackerCustomizer {
    private static final Logger LOGGER = Logger.getLogger(BundleManagerImpl.class.getName(), Messages.class.getName());

    private final String processType;
    private final List<ManagedBundle> managedBundles = new LinkedList<ManagedBundle>();
    
    BundleManagerImpl() {
        processType = AdminServiceFactory.getAdminService().getProcessType();
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Process type is {0}", processType);
        }
    }
    
    public boolean isManaged(Bundle bundle) {
        for (ManagedBundle managedBundle : managedBundles) {
            if (managedBundle.getBundle().equals(bundle)) {
                return true;
            }
        }
        return false;
    }

    public Object addingBundle(Bundle bundle, BundleEvent event) {
        String header = (String)bundle.getHeaders().get("XM4WAS-Components");
        if (header == null) {
            return null;
        } else {
            int state = bundle.getState();
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Discovered managed bundle {0}; state {1}", new Object[] { bundle.getSymbolicName(), state });
            }
            ManagedBundle managedBundle = new ManagedBundle(bundle);
            managedBundles.add(managedBundle);
            for (String className : header.trim().split("\\s*,\\s*")) {
                Class<?> clazz;
                try {
                    clazz = bundle.loadClass(className);
                } catch (ClassNotFoundException ex) {
                    LOGGER.log(Level.SEVERE, Messages._0007E, new Object[] { className, bundle.getSymbolicName(), ex });
                    continue;
                }
                ProcessTypes processTypes = clazz.getAnnotation(ProcessTypes.class);
                if (processTypes != null) {
                    boolean matches = false;
                    for (String candidate : processTypes.value()) {
                        if (candidate.equals(processType)) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        if (LOGGER.isLoggable(Level.FINEST)) {
                            LOGGER.log(Level.FINEST, "Skipping component {0} which is only valid for process types {1}",
                                    new Object[] { clazz.getName(), Arrays.asList(processTypes.value()) });
                        }
                        continue;
                    }
                }
                Object componentObject;
                try {
                    componentObject = clazz.newInstance();
                } catch (Throwable ex) {
                    LOGGER.log(Level.SEVERE, Messages._0008E, new Object[] { clazz.getName(), ex });
                    continue;
                }
                Services annotation = clazz.getAnnotation(Services.class);
                String[] serviceClassNames;
                if (annotation == null) {
                    serviceClassNames = null;
                } else {
                    Class<?>[] serviceClasses = annotation.value();
                    serviceClassNames = new String[serviceClasses.length];
                    for (int i=0; i<serviceClasses.length; i++) {
                        serviceClassNames[i] = serviceClasses[i].getName();
                    }
                }
                LifecycleManager component = new LifecycleManager(Util.getBundleContext(bundle), serviceClassNames, componentObject, null);
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.log(Level.FINEST, "Adding component {0}", component);
                }
                managedBundle.addComponent(component);
            }
            managedBundle.startComponents();
            return managedBundle;
        }
    }

    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
    }

    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
        if (object != null) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Bundle {0} no longer managed; new state is {1}", new Object[] { bundle.getSymbolicName(), bundle.getState()});
            }
            ((ManagedBundle)object).stopComponents();
            managedBundles.remove(object);
        }
    }
}
