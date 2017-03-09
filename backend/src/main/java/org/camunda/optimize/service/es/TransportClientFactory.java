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
import java.util.concurrent.TimeUnit;

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
      logger.info("Starting Elasticsearch client...");
      try {
        instance =
          new PreBuiltTransportClient(
            Settings.builder()
              .put("client.transport.ping_timeout", configurationService.getElasticsearchConnectionTimeout(), TimeUnit.MILLISECONDS)
              .build())
            .addTransportAddress(new InetSocketTransportAddress(
                InetAddress.getByName(configurationService.getElasticSearchHost()),
                configurationService.getElasticSearchPort()
                ));
      } catch (UnknownHostException e) {
        logger.error("Can't connect to Elasticsearch. Please check the connection!", e);
      }
      logger.info("Elasticsearch client has successfully been started");
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
