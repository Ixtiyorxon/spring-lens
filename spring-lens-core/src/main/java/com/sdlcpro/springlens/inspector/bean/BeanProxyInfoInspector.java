package com.sdlcpro.springlens.inspector.bean;

import com.sdlcpro.springlens.model.bean.instance.BeanInstanceProxyInfo;

/**
 * Inspects Spring bean instances to extract proxy metadata.
 */
public interface BeanProxyInfoInspector {

    /**
     * Inspects the specified bean and extracts its proxy metadata.
     *
     * @param contextId the id of the application context
     * @param beanName  the name of the Spring bean to inspect
     * @return proxy information for the bean, or {@code null} if the bean
     * does not exist or is not proxied
     */
    BeanInstanceProxyInfo inspectBeanInstanceProxyInfo(String contextId, String beanName);
}
