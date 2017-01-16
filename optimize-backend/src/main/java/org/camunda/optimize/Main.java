package org.camunda.optimize;

import org.camunda.optimize.service.configuration.PropertiesProvider;
import org.camunda.optimize.service.es.TransportClientFactory;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.security.TokenService;
import org.camunda.optimize.service.security.impl.AuthenticationProviderImpl;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.elasticsearch.client.transport.TransportClient;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Properties;

/**
 * @author Askar Akhmerov
 */
public class Main {

  public static void main(String[] args) throws Exception {

    Properties properties = new PropertiesProvider().provide();

    // Create JAX-RS application.
    final ResourceConfig application = new ResourceConfig()
        .packages("org.camunda.optimize.rest")
        .register(JacksonFeature.class)
        .register(new AbstractBinder() {
          @Override
          protected void configure() {
            //TODO:dynamically iterate all factories and register them?
            bindFactory(TransportClientFactory.class).to(TransportClient.class);
            bind(properties).to(Properties.class);
            bind(AuthenticationProviderImpl.class).to(AuthenticationProvider.class).in(Singleton.class);
            bind(TokenService.class).to(TokenService.class).in(Singleton.class);
          }
        });

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
}
