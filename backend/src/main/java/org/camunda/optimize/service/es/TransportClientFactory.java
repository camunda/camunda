package org.camunda.optimize.service.es;

import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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


public class TransportClientFactory implements FactoryBean<TransportClient>, DisposableBean {

  private final Logger logger = LoggerFactory.getLogger(TransportClientFactory.class);
  private TransportClient instance;
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
  public TransportClient getObject() {
    if (instance == null) {
      logger.info("Starting Elasticsearch client...");
      Settings defaultSettings = createDefaultSettings();

      try {
        TransportClient transportClient;
        if (configurationService.getElasticsearchSecuritySSLEnabled()) {
          transportClient = createSecuredTransportClient();
        } else {
          transportClient = createDefaultTransportClient();
        }

        waitForElasticsearch(transportClient);
        instance = transportClient;
      } catch (Exception e) {
        logger.error("Can't connect to Elasticsearch. Please check the connection!", e);
      }
      logger.info("Elasticsearch client has successfully been started");
    }
    return instance;
  }


  private void waitForElasticsearch(TransportClient internalClient) throws InterruptedException {
    while (internalClient.connectedNodes().size() == 0) {
      long sleepTime = backoffCalculator.calculateSleepTime();
      Thread.sleep(sleepTime);
      logger.info("No elasticsearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
    }
  }

  private TransportClient createDefaultTransportClient() {
    return new PreBuiltTransportClient(
      createDefaultSettings()
    )
      .addTransportAddresses(buildElasticsearchConnectionNodes(configurationService));
  }

  private TransportAddress[] buildElasticsearchConnectionNodes(ConfigurationService configurationService) {
    return configurationService.getElasticsearchConnectionNodes()
      .stream()
      .map(
        conf -> {
          try {
            return new TransportAddress(
              InetAddress.getByName(conf.getHost()),
              conf.getTcpPort()
            );
          } catch (UnknownHostException e) {
            String errorMessage =
              String.format("Could not build the transport client since the host [%s] is unknown", conf.getHost());
            throw new OptimizeRuntimeException(errorMessage, e);
          }
        }
      )
      .toArray(TransportAddress[]::new);
  }

  private TransportClient createSecuredTransportClient() {
    String xpackUser = configurationService.getElasticsearchSecurityUsername() + ":" +
      configurationService.getElasticsearchSecurityPassword();
    return new PreBuiltXPackTransportClient(
      Settings.builder()
        .put(createDefaultSettings())
        .put("xpack.security.user", xpackUser)
        .put("xpack.ssl.key", configurationService.getElasticsearchSecuritySSLKey())
        .put("xpack.ssl.certificate", configurationService.getElasticsearchSecuritySSLCertificate())
        .putList(
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
      .addTransportAddresses(buildElasticsearchConnectionNodes(configurationService));
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
    return TransportClient.class;
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
