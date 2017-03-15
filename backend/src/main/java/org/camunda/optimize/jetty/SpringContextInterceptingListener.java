package org.camunda.optimize.jetty;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.LifeCycle;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * @author Askar Akhmerov
 */
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
