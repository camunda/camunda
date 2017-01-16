package org.camunda.optimize.service.es;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.glassfish.hk2.api.Factory;

import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * @author Askar Akhmerov
 */
public class TransportClientFactory implements Factory<TransportClient> {
  private static TransportClient instance;

  private Properties applicationProperties;

  @Inject
  public TransportClientFactory(Properties applicationProperties) {
    this.applicationProperties = applicationProperties;
  }

  @Override
  public TransportClient provide() {
    if (instance == null) {
      try {
        instance = new PreBuiltTransportClient(Settings.EMPTY)
            //TODO: port and host should come from properties
            .addTransportAddress(new InetSocketTransportAddress(
                InetAddress.getByName(applicationProperties.getProperty("camunda.optimize.es.host")),
                Integer.parseInt(applicationProperties.getProperty("camunda.optimize.es.port")))
            );
      } catch (UnknownHostException e) {
        e.printStackTrace();
      }
    }
    return instance;
  }

  @Override
  public void dispose(TransportClient transportClient) {

  }
}
