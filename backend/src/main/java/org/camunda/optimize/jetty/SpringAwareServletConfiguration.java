package org.camunda.optimize.jetty;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.ContextLoaderListener;

import javax.servlet.DispatcherType;
import java.net.URL;
import java.util.EnumSet;

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
  private String springContextLocation = "classpath:applicationContext.xml";
  private String optimizeRestPackage = "org.camunda.optimize.rest";
  private ContextLoaderListener contextLoaderListener = new ContextLoaderListener();
  private ApplicationContext applicationContext;

  public SpringAwareServletConfiguration() {
    this(null);
  }

  public SpringAwareServletConfiguration(String contextLocation) {
    if (contextLocation != null) {
      this.springContextLocation = contextLocation;
    }
  }

  private ServletHolder initJerseyServlet() {
    // Create JAX-RS application.
    final ResourceConfig application = new ResourceConfig()
        .packages(optimizeRestPackage)
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
    return context;
  }

  private void addStaticResources(ServletContextHandler context) {
    //add static resources
    URL webappURL = this.getClass().getClassLoader().getResource("webapp");
    if (webappURL != null) {
      context.setResourceBase(webappURL.toExternalForm());
    }
    ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
    holderPwd.setInitParameter("dirAllowed", "true");
    context.addServlet(holderPwd, "/");

    addLicenseFilter(context);
    addSingleSignOnFilter(context);

    NotFoundErrorHandler errorMapper = new NotFoundErrorHandler();
    context.setErrorHandler(errorMapper);

    initGzipFilterHolder(context);
    //initJSRewriteHandler(context);
  }

  private void addSingleSignOnFilter(ServletContextHandler context) {
    FilterHolder singleSignOnFilterHolder = new FilterHolder();
    singleSignOnFilterHolder.setFilter(new SingleSignOnFilter(this));
    context.addFilter(singleSignOnFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
  }

  private void addLicenseFilter(ServletContextHandler context) {
    FilterHolder licenseFilterHolder = new FilterHolder();
    licenseFilterHolder.setFilter(new LicenseFilter(this));
    context.addFilter(
      licenseFilterHolder,
      "/*",
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR)
    );
  }

  private void initJSRewriteHandler(ServletContextHandler context) {

    context.addFilter(GzipForwardPatternRule.class, "/*", EnumSet.of(DispatcherType.REQUEST));
  }

  private void initGzipFilterHolder(ServletContextHandler context) {
    FilterHolder gzipFilterHolder = new FilterHolder(GzipFilter.class);
    gzipFilterHolder.setInitParameter("deflateCompressionLevel", "9");
    gzipFilterHolder.setInitParameter("minGzipSize", "0");
    gzipFilterHolder.setInitParameter("mimeTypes", COMPRESSED_MIME_TYPES);

    context.addFilter(gzipFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
  }

  public ServletContextHandler getServletContextHandler() {
    ServletHolder jerseyServlet = initJerseyServlet();

    return setupServletContextHandler(jerseyServlet);
  }

  public String getSpringContextLocation() {
    return springContextLocation;
  }

  public void setSpringContextLocation(String springContextLocation) {
    this.springContextLocation = springContextLocation;
  }

  public String getOptimizeRestPackage() {
    return optimizeRestPackage;
  }

  public void setOptimizeRestPackage(String optimizeRestPackage) {
    this.optimizeRestPackage = optimizeRestPackage;
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}
