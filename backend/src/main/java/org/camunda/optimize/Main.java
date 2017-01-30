package org.camunda.optimize;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Properties;

/**
 * @author Askar Akhmerov
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String CONFIG_LOCATION = "org.camunda.optimize";
  private static final String DEFAULT_PROFILE = "dev";

  public static void main(String[] args) throws Exception {

    Properties properties = getProperties();

    // Create JAX-RS application.
    final ResourceConfig application = new ResourceConfig()
        .packages("org.camunda.optimize.rest")
        .register(JacksonFeature.class);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    URL webappURL = Main.class.getClassLoader().getResource("webapp");
    if (webappURL != null) {
      context.setResourceBase(webappURL.toExternalForm());
    }

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
    if (webappURL != null) {
      ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
      holderPwd.setInitParameter("dirAllowed","true");
      context.addServlet(holderPwd,"/");
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

  private static Properties getProperties() {
    Properties result = new Properties();
    try {
      result.load(Main.class.getClassLoader().getResourceAsStream("service.properties"));
    } catch (IOException e) {
      logger.error("cant read properties", e);
    }
    return result;
  }
}
