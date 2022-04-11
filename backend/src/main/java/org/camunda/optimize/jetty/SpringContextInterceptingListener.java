/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.LifeCycle;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.support.WebApplicationContextUtils;


public class SpringContextInterceptingListener implements LifeCycle.Listener {
  private ApplicationContextAware contextAware;

  public SpringContextInterceptingListener(ApplicationContextAware awareDelegate) {
    this.contextAware = awareDelegate;
  }

  @Override
  public void lifeCycleStarted(LifeCycle event) {
    ServletContextHandler contextHandler = (ServletContextHandler) event;
    contextAware.setApplicationContext(WebApplicationContextUtils
        .getWebApplicationContext(contextHandler.getServletContext()));
  }

  @Override
  public void lifeCycleStarting(LifeCycle lifeCycle) {

  }

  @Override
  public void lifeCycleFailure(LifeCycle lifeCycle, Throwable throwable) {

  }

  @Override
  public void lifeCycleStopping(LifeCycle lifeCycle) {

  }

  @Override
  public void lifeCycleStopped(LifeCycle lifeCycle) {

  }
}
