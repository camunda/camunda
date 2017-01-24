package org.camunda.optimize.test;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.SpringLifecycleListener;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.glassfish.jersey.test.JerseyTest;

import javax.ws.rs.core.Application;

/**
 * Abstract class that configures Jersey Application to use spring
 * configuration in order to make it available in test environment.
 *
 * @author Askar Akhmerov
 */
public abstract class AbstractJerseyTest extends JerseyTest {

  @Override
  protected Application configure() {
    final ResourceConfig application = new ResourceConfig()
        .packages("org.camunda.optimize.rest")
        .register(JacksonFeature.class);

    application.register(SpringLifecycleListener.class);
    application.register(RequestContextFilter.class);

    application.property("contextConfigLocation", "classpath:applicationContext.xml");

    //this is a bit dirty, unit test will be executed in container, which will allow spring autowiring
    //of beans declared in spring context of container
    application.register(this);
    return application;
  }
}
