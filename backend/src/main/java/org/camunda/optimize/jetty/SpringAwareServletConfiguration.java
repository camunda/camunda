/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.ContextLoaderListener;

import javax.servlet.DispatcherType;
import java.net.URL;
import java.util.EnumSet;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;

/**
 * Wrapper around all Camunda Optimize components, specifically JAX-RS configuration
 * which can be used to be loaded by different implementations of embedded web containers
 */
public class SpringAwareServletConfiguration implements ApplicationContextAware {

  private static final String COMPRESSED_MIME_TYPES = "application/json," +
    "text/html," +
    "application/x-font-ttf," +
    "image/svg+xml";

  private static final String CONTEXT_CONFIG_LOCATION = "contextConfigLocation";
  private static final String OPTIMIZE_REST_PACKAGE = "org.camunda.optimize.rest";

  private final ContextLoaderListener contextLoaderListener = new ContextLoaderListener();

  private String springContextLocation = "classpath:applicationContext.xml";
  private ApplicationContext applicationContext;

  public SpringAwareServletConfiguration(String contextLocation) {
    if (contextLocation != null) {
      this.springContextLocation = contextLocation;
    }
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  public ServletContextHandler initServletContextHandler() {
    final ServletHolder jerseyServlet = initJerseyServlet();
    return setupServletContextHandler(jerseyServlet);
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  private ServletHolder initJerseyServlet() {
    // Create JAX-RS application.
    final ResourceConfig application = new ResourceConfig()
      .packages(OPTIMIZE_REST_PACKAGE)
      // WADL is not used and having it not explicitly disabled causes a warn log
      .property(ServerProperties.WADL_FEATURE_DISABLE, true)
      .register(JacksonFeature.class);

    ServletHolder jerseyServlet = new ServletHolder(new ServletContainer(application));
    jerseyServlet.setInitOrder(0);
    return jerseyServlet;
  }

  private ServletContextHandler setupServletContextHandler(ServletHolder jerseyServlet) {
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    context.addServlet(jerseyServlet, "/api/*");

    //add spring
    context.addEventListener(contextLoaderListener);
    context.setInitParameter(CONTEXT_CONFIG_LOCATION, springContextLocation);
    context.addLifeCycleListener(new SpringContextInterceptingListener(this));

    addStaticResources(context);
    addGzipHandler(context);
    context.setErrorHandler(new NotFoundErrorHandler());

    addLicenseFilter(context);
    addSingleSignOnFilter(context);
    addNoCachingFilter(context);
    addEventIngestionQoSFilter(context);
    addIngestionRequestLimitFilter(context);

    return context;
  }

  private void addStaticResources(ServletContextHandler context) {
    final URL webappURL = this.getClass().getClassLoader().getResource("webapp");
    if (webappURL != null) {
      context.setResourceBase(webappURL.toExternalForm());
    }
    final ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
    holderPwd.setInitParameter("dirAllowed", "false");
    context.addServlet(holderPwd, "/");
  }

  private void addIngestionRequestLimitFilter(final ServletContextHandler context) {
    FilterHolder ingestionRequestLimitFilter = new FilterHolder();
    ingestionRequestLimitFilter.setFilter(new MaxRequestSizeFilter(
      () -> getApplicationContext().getBean(ObjectMapper.class),
      () -> getApplicationContext()
        .getBean(ConfigurationService.class)
        .getEventIngestionConfiguration()
        .getMaxBatchRequestBytes()
    ));
    context.addFilter(
      ingestionRequestLimitFilter,
      REST_API_PATH + INGESTION_PATH + EVENT_BATCH_SUB_PATH,
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)
    );
  }

  private void addEventIngestionQoSFilter(ServletContextHandler context) {
    FilterHolder eventIngestionQoSFilterHolder = new FilterHolder();
    IngestionQoSFilter ingestionQoSFilter = new IngestionQoSFilter(
      () -> getApplicationContext()
        .getBean(ConfigurationService.class)
        .getEventIngestionConfiguration()
        .getMaxRequests()
    );
    eventIngestionQoSFilterHolder.setFilter(ingestionQoSFilter);
    context.addFilter(
      eventIngestionQoSFilterHolder,
      REST_API_PATH + INGESTION_PATH + EVENT_BATCH_SUB_PATH,
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)
    );
  }

  private void addSingleSignOnFilter(ServletContextHandler context) {
    FilterHolder singleSignOnFilterHolder = new FilterHolder();
    singleSignOnFilterHolder.setFilter(new SingleSignOnFilter(this));
    context.addFilter(singleSignOnFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
  }

  private void addLicenseFilter(ServletContextHandler context) {
    FilterHolder licenseFilterHolder = new FilterHolder();
    licenseFilterHolder.setFilter(new LicenseFilter(this));
    context.addFilter(
      licenseFilterHolder,
      "/*",
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.ASYNC)
    );
  }

  private void addNoCachingFilter(ServletContextHandler context) {
    FilterHolder licenseFilterHolder = new FilterHolder();
    licenseFilterHolder.setFilter(new NoCachingFilter());
    context.addFilter(
      licenseFilterHolder,
      "/*",
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.ASYNC)
    );
  }

  private void addGzipHandler(ServletContextHandler context) {
    GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setCompressionLevel(9);
    gzipHandler.setMinGzipSize(23);
    gzipHandler.setIncludedMimeTypes(COMPRESSED_MIME_TYPES);
    gzipHandler.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
    gzipHandler.setIncludedPaths("/*");
    context.setGzipHandler(gzipHandler);
  }
}
