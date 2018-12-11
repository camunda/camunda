package org.camunda.optimize.service.es;

import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.elasticsearch.xpack.client.PreBuiltXPackTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;


public class TransportClientFactory implements FactoryBean<Client>, DisposableBean {

  private final Logger logger = LoggerFactory.getLogger(TransportClientFactory.class);
  private SchemaInitializingClient instance;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ElasticSearchSchemaInitializer elasticSearchSchemaInitializer;

  private BackoffCalculator backoffCalculator;

  @PostConstruct
  public void init() {
    backoffCalculator = new BackoffCalculator(
            configurationService.getMaximumBackoff(),
            configurationService.getImportHandlerWait()
    );
  }

  @Override
  public Client getObject() {
    if (instance == null) {
      logger.info("Starting Elasticsearch client...");
      Settings defaultSettings = createDefaultSettings();

      try {
        TransportClient internalClient;
        if (configurationService.getElasticsearchSecuritySSLEnabled()) {
          internalClient = createSecuredTransportClient();
        } else {
          internalClient = createDefaultTransportClient();
        }

        waitForElasticsearch(internalClient);

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

  private void waitForElasticsearch(TransportClient internalClient) throws InterruptedException {
    while(internalClient.connectedNodes().size() == 0) {
      long sleepTime = backoffCalculator.calculateSleepTime();
      Thread.sleep(sleepTime);
      logger.info("No elasticsearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
    }
  }

  private TransportClient createDefaultTransportClient() throws UnknownHostException {
    return new PreBuiltTransportClient(
      createDefaultSettings()
    )
      .addTransportAddress(new TransportAddress(
        InetAddress.getByName(configurationService.getElasticSearchHost()),
        configurationService.getElasticSearchTcpPort()
      ));
  }

  private TransportClient createSecuredTransportClient() throws UnknownHostException {
    String xpackUser = configurationService.getElasticsearchSecurityUsername() + ":" +
      configurationService.getElasticsearchSecurityPassword();
    return new PreBuiltXPackTransportClient(
      Settings.builder()
        .put(createDefaultSettings())
        .put("xpack.security.user", xpackUser)
        .put("xpack.ssl.key", configurationService.getElasticsearchSecuritySSLKey())
        .put("xpack.ssl.certificate", configurationService.getElasticsearchSecuritySSLCertificate())
        .putArray(
          "xpack.ssl.certificate_authorities",
          configurationService.getElasticsearchSecuritySSLCertificateAuthorities()
        )
        .put("xpack.security.transport.ssl.enabled", configurationService.getElasticsearchSecuritySSLEnabled())
        .put(
          "xpack.security.transport.ssl.verification_mode",
          configurationService.getElasticsearchSecuritySSLVerificationMode()
        )
        .build()
    )
      .addTransportAddress(new TransportAddress(
        InetAddress.getByName(configurationService.getElasticSearchHost()),
        configurationService.getElasticSearchTcpPort()
      ));
  }

  private Settings createDefaultSettings() {
    return Settings.builder()
      .put(
        "client.transport.ping_timeout",
        configurationService.getElasticsearchConnectionTimeout(),
        TimeUnit.MILLISECONDS
      )
      .put(
        "client.transport.nodes_sampler_interval",
        configurationService.getSamplerInterval(),
        TimeUnit.MILLISECONDS
      )
      .put("cluster.name", configurationService.getElasticSearchClusterName())
      .build();
  }

  @Override
  public Class<?> getObjectType() {
    return Client.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public void destroy() {
    if (instance != null) {
      instance.close();
    }
  }
}
