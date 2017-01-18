package org.camunda.optimize.service.es;

import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Askar Akhmerov
 */
public class TransportClientFactory implements FactoryBean <TransportClient> {
  private final Logger logger = LoggerFactory.getLogger(TransportClientFactory.class);
  private TransportClient instance;

  @Autowired
  private ConfigurationService configurationService;

  @Override
  public TransportClient getObject() throws Exception {
    if (instance == null) {
      try {
        instance = new PreBuiltTransportClient(Settings.EMPTY)
            //TODO: port and host should come from properties
            .addTransportAddress(new InetSocketTransportAddress(
                InetAddress.getByName(configurationService.getElasticSearchHost()),
                configurationService.getElasticSearchPort()
                ));
      } catch (UnknownHostException e) {
        logger.error("cant connect to elasticsearch", e);
      }
    }
    return instance;
  }

  @Override
  public Class<?> getObjectType() {
    return TransportClient.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
