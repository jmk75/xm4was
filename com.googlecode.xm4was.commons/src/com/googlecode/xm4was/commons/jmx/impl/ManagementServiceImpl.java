package com.googlecode.xm4was.commons.jmx.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;

import com.googlecode.xm4was.commons.jmx.Authorizer;
import com.googlecode.xm4was.commons.jmx.ManagementService;
import com.googlecode.xm4was.commons.osgi.annotations.Init;
import com.googlecode.xm4was.commons.osgi.annotations.Services;
import com.googlecode.xm4was.commons.resources.Messages;
import com.ibm.websphere.management.AdminService;
import com.ibm.websphere.management.AdminServiceFactory;
import com.ibm.ws.management.PlatformMBeanServer;
import com.ibm.ws.security.service.SecurityService;

@Services(ManagementService.class)
public class ManagementServiceImpl implements ManagementService {
    private static final Logger LOGGER = Logger.getLogger(ManagementServiceImpl.class.getName(), Messages.class.getName());
    
    private MBeanServer mbeanServer;
    private MBeanServer rawMBeanServer;
    private Authorizer authorizer;

    @Init
    public void init(SecurityService securityService) {
        MBeanServer wasMBeanServer = AdminServiceFactory.getMBeanFactory().getMBeanServer();
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "AdminServiceFactory.getMBeanFactory().getMBeanServer() returned an instance of type "
                    + wasMBeanServer.getClass().getName());
        }
        MBeanServer mbeanServer;
        MBeanServer rawMBeanServer;
        if (wasMBeanServer instanceof PlatformMBeanServer) {
            mbeanServer = wasMBeanServer;
            // The PlatformMBeanServer instance automatically adds the cell, node and
            // process as key properties. This will not work for the platform MXBeans
            // (jconsole e.g. would be unable to identify them). However,
            // PlatformMBeanServer is just a wrapper around a standard MBeanServer,
            // which can be retrieved using the getDefaultMBeanServer.
            rawMBeanServer = ((PlatformMBeanServer)wasMBeanServer).getDefaultMBeanServer();
        } else {
            LOGGER.log(Level.WARNING, Messages._0004W, new Object[] { PlatformMBeanServer.class.getName(), wasMBeanServer.getClass() });
            rawMBeanServer = wasMBeanServer;
            mbeanServer = new PlatformMBeanServer(wasMBeanServer);
        }
        
        Authorizer authorizer;
        if (securityService.isSecurityEnabled()) {
            AdminService adminService = AdminServiceFactory.getAdminService();
            // See http://publib.boulder.ibm.com/infocenter/wasinfo/v6r1/topic/com.ibm.websphere.express.doc/info/exp/ae/tjmx_admin_finegr_mbsec.html
            String resource = "/nodes/" + adminService.getNodeName() + "/servers/" + adminService.getProcessName();
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "resource = {0}", resource);
            }
            authorizer = new AuthorizerImpl(resource);
            LOGGER.log(Level.INFO, Messages._0005I);
        } else {
            authorizer = new NoAuthorizer();
            LOGGER.log(Level.INFO, Messages._0006I);
        }
        
        this.mbeanServer = mbeanServer;
        this.rawMBeanServer = rawMBeanServer;
        this.authorizer = authorizer;
    }
    
    public MBeanServer getMBeanServer() {
        return mbeanServer;
    }

    public MBeanServer getRawMBeanServer() {
        return rawMBeanServer;
    }

    public Authorizer getAuthorizer() {
        return authorizer;
    }
}
