package org.camunda.optimize.service.es;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * @author Askar Akhmerov
 */
public class TransportClientFactory implements FactoryBean <Client> {
  private final Logger logger = LoggerFactory.getLogger(TransportClientFactory.class);
  private SchemaInitializingClient instance;
  private TransportClient internalClient;

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ElasticSearchSchemaInitializer elasticSearchSchemaInitializer;

  @Override
  public Client getObject() throws Exception {
    if (instance == null) {
      logger.info("Starting Elasticsearch client...");
      try {
        internalClient =
          new PreBuiltTransportClient(
            Settings.builder()
              .put("client.transport.ping_timeout", configurationService.getElasticsearchConnectionTimeout(), TimeUnit.MILLISECONDS)
              .put("client.transport.nodes_sampler_interval", configurationService.getSamplerInterval(), TimeUnit.MILLISECONDS)
              .build())
            .addTransportAddress(new TransportAddress(
                InetAddress.getByName(configurationService.getElasticSearchHost()),
                configurationService.getElasticSearchPort()
                ));
        instance = new SchemaInitializingClient(internalClient);
        elasticSearchSchemaInitializer.useClient(internalClient, configurationService);
        instance.setElasticSearchSchemaInitializer(elasticSearchSchemaInitializer);
        //
        elasticSearchSchemaInitializer.initializeSchema();
      } catch (Exception e) {
        logger.error("Can't connect to Elasticsearch. Please check the connection!", e);
      }
      logger.info("Elasticsearch client has successfully been started");
    }
    return instance;
  }

  @Override
  public Class<?> getObjectType() {
    return Client.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
