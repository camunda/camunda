/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
import org.springframework.web.filter.DelegatingFilterProxy;

import javax.servlet.DispatcherType;
import java.net.URL;
import java.util.EnumSet;
import java.util.concurrent.Callable;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static org.camunda.optimize.rest.IngestionRestService.EVENT_BATCH_SUB_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;

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
  private static final String CONTEXT_CLASS_PARAMETER_NAME = "contextClass";
  private static final String CONTEXT_CLASS_PARAMETER_VALUE =
    "org.springframework.web.context.support.AnnotationConfigWebApplicationContext";

  private final ContextLoaderListener contextLoaderListener = new ContextLoaderListener();

  private String springContextLocation = "org.camunda.optimize.Main";
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
    context.setInitParameter(CONTEXT_CLASS_PARAMETER_NAME, CONTEXT_CLASS_PARAMETER_VALUE);
    context.setInitParameter(CONTEXT_CONFIG_LOCATION, springContextLocation);
    context.addLifeCycleListener(new SpringContextInterceptingListener(this));

    addStaticResources(context);
    addGzipHandler(context);
    context.setErrorHandler(new NotFoundErrorHandler());

    addJavaScriptLicenseEnricher(context);
    addLicenseFilter(context);
    addNoCachingFilter(context);
    addEventIngestionQoSFilter(context);
    addEventIngestionRequestLimitFilter(context);
    addExternalVariableIngestionQoSFilter(context);
    addExternalVariableIngestionRequestLimitFilter(context);

    context.addFilter(
      new FilterHolder(new DelegatingFilterProxy("springSecurityFilterChain")),
      "/*",
      EnumSet.allOf(DispatcherType.class)
    );

    return context;
  }

  private void addStaticResources(ServletContextHandler context) {
    final URL webappURL = this.getClass().getClassLoader().getResource("webapp");
    if (webappURL != null) {
      context.setResourceBase(webappURL.toExternalForm());
    }
    final ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
    holderPwd.setInitParameter("dirAllowed", "false");
    ServletHolder externalHome = new ServletHolder("external-home", DefaultServlet.class);
    if (webappURL != null) {
      externalHome.setInitParameter("resourceBase", webappURL.toExternalForm());
    }
    externalHome.setInitParameter("dirAllowed","true");
    // Use request pathInfo, don't calculate from contextPath
    externalHome.setInitParameter("pathInfoOnly","true");
    context.addServlet(externalHome,"/external/*"); // must end in "/*" for pathInfo to work
    // Root path needs to be added last, otherwise it won't work
    context.addServlet(holderPwd, "/");
  }

  private void addEventIngestionRequestLimitFilter(final ServletContextHandler context) {
    addIngestionRequestLimitFilter(
      context,
      () -> getApplicationContext()
        .getBean(ConfigurationService.class)
        .getEventIngestionConfiguration()
        .getMaxBatchRequestBytes(),
      EVENT_BATCH_SUB_PATH
    );
  }

  private void addExternalVariableIngestionRequestLimitFilter(final ServletContextHandler context) {
    addIngestionRequestLimitFilter(
      context,
      () -> getApplicationContext()
        .getBean(ConfigurationService.class)
        .getVariableIngestionConfiguration()
        .getMaxBatchRequestBytes(),
      VARIABLE_SUB_PATH
    );
  }

  private void addIngestionRequestLimitFilter(ServletContextHandler context,
                                              final Callable<Long> maxBatchRequestBytesGetter,
                                              final String ingestionSubPath) {
    FilterHolder ingestionRequestLimitFilter = new FilterHolder();
    ingestionRequestLimitFilter.setFilter(new MaxRequestSizeFilter(
      () -> getApplicationContext().getBean(ObjectMapper.class),
      maxBatchRequestBytesGetter
    ));
    context.addFilter(
      ingestionRequestLimitFilter,
      REST_API_PATH + INGESTION_PATH + ingestionSubPath,
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)
    );
  }

  private void addEventIngestionQoSFilter(ServletContextHandler context) {
    addIngestionQosFilter(
      context,
      () -> getApplicationContext()
        .getBean(ConfigurationService.class)
        .getEventIngestionConfiguration()
        .getMaxRequests(),
      EVENT_BATCH_SUB_PATH
    );
  }

  private void addExternalVariableIngestionQoSFilter(ServletContextHandler context) {
    addIngestionQosFilter(
      context,
      () -> getApplicationContext()
        .getBean(ConfigurationService.class)
        .getVariableIngestionConfiguration()
        .getMaxRequests(),
      VARIABLE_SUB_PATH
    );
  }

  private void addIngestionQosFilter(ServletContextHandler context,
                                     final Callable<Integer> maxRequestGetter,
                                     final String ingestionSubPath) {
    FilterHolder eventIngestionQoSFilterHolder = new FilterHolder();
    IngestionQoSFilter ingestionQoSFilter = new IngestionQoSFilter(maxRequestGetter);
    eventIngestionQoSFilterHolder.setFilter(ingestionQoSFilter);
    context.addFilter(
      eventIngestionQoSFilterHolder,
      REST_API_PATH + INGESTION_PATH + ingestionSubPath,
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)
    );
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

  private void addJavaScriptLicenseEnricher(final ServletContextHandler context) {
    FilterHolder licenseEnricherFilterHolder = new FilterHolder();
    licenseEnricherFilterHolder.setFilter(new JavaScriptMainLicenseEnricherFilter());
    context.addFilter(
      licenseEnricherFilterHolder,
      STATIC_RESOURCE_PATH + "/*",
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
