package org.camunda.optimize;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.servlet.DispatcherType;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.EnumSet;
import java.util.Properties;

/**
 * @author Askar Akhmerov
 */
public class Main {

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String CONFIG_LOCATION = "org.camunda.optimize";
  private static final String DEFAULT_PROFILE = "dev";
  public static final String COMPRESSED_MIME_TYPES = "application/json," +
      "application/javascript," +
      "text/html," +
      "text/css," +
      "application/x-font-ttf," +
      "image/svg+xml";

  public static void main(String[] args) throws Exception {
    Properties properties = getProperties();

    // Create JAX-RS application.
    final ResourceConfig application = new ResourceConfig()
        .packages("org.camunda.optimize.rest")
        .register(JacksonFeature.class);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    InetSocketAddress address = new InetSocketAddress(
        properties.getProperty("camunda.optimize.container.host"),
        Integer.parseInt(properties.getProperty("camunda.optimize.container.port")));
    Server jettyServer = new Server(address);

    jettyServer.setHandler(context);

    ServletHolder jerseyServlet = new ServletHolder(new
        org.glassfish.jersey.servlet.ServletContainer(application));
    jerseyServlet.setInitOrder(0);

    context.addServlet(jerseyServlet, "/api/*");

    //add spring
    context.addEventListener(new ContextLoaderListener());
    context.setInitParameter("contextConfigLocation","classpath:applicationContext.xml");

    //add static resources
    URL webappURL = Main.class.getClassLoader().getResource("webapp");
    if (webappURL != null) {
      ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
      context.setResourceBase(webappURL.toExternalForm());
      holderPwd.setInitParameter("dirAllowed","true");
      context.addServlet(holderPwd,"/");

      ErrorPageErrorHandler errorMapper = new ErrorPageErrorHandler();
      errorMapper.addErrorPage(404,"/"); // map all 404's to root (aka /index.html)
      context.setErrorHandler(errorMapper);

      FilterHolder holder = new FilterHolder(GzipFilter.class);
      holder.setInitParameter("deflateCompressionLevel", "9");
      holder.setInitParameter("minGzipSize", "0");
      holder.setInitParameter("mimeTypes", COMPRESSED_MIME_TYPES);

      context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    try {
      jettyServer.start();
      jettyServer.join();
    } finally {
      jettyServer.destroy();
    }
  }

  private static WebApplicationContext getContext() {
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.setConfigLocation(CONFIG_LOCATION);
    context.getEnvironment().setDefaultProfiles(DEFAULT_PROFILE);
    return context;
  }

  /**
   * Since spring context is not loaded yet, just hook up properties manually.
   * @return
   */
  private static Properties getProperties() {
    Properties result = new Properties();
    try {
      result.load(Main.class.getClassLoader().getResourceAsStream("service.properties"));
      InputStream environmentProperties = Main.class.getClassLoader().getResourceAsStream("environment.properties");
      if (environmentProperties != null) {
        //overwrites previously loaded properties
        result.load(environmentProperties);
      }
    } catch (IOException e) {
      logger.error("cant read properties", e);
    }
    return result;
  }
}
