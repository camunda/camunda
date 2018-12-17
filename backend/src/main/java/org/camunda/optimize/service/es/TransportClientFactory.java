package org.camunda.optimize.service.es;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.block.ClusterBlockException;
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

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.OPTIMIZE_INDEX_PREFIX;


public class TransportClientFactory implements FactoryBean<Client>, DisposableBean {

  public static final String INDEX_READ_ONLY_SETTING = "index.blocks.read_only_allow_delete";
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

        unblockIndices(internalClient);

        elasticSearchSchemaInitializer.initializeSchema();
      } catch (Exception e) {
        logger.error("Can't connect to Elasticsearch. Please check the connection!", e);
      }
      logger.info("Elasticsearch client has successfully been started");
    }
    return instance;
  }

  private void unblockIndices(TransportClient internalClient) throws InterruptedException {
    GetSettingsResponse response = internalClient.admin().indices().prepareGetSettings("_all").get();
    boolean indexBlocked = false;
    for (ObjectObjectCursor<String, Settings> cursor : response.getIndexToSettings()) {
      if (Boolean.parseBoolean(cursor.value.get(INDEX_READ_ONLY_SETTING))
        && cursor.key.contains(OPTIMIZE_INDEX_PREFIX)) {
        indexBlocked = true;
        logger.info("Found blocked Optimize Elasticsearch indices");
        break;
      }
    }

    if (indexBlocked) {
      logger.info("Unblocking Elasticsearch indices...");
      internalClient.admin().indices().prepareUpdateSettings(OPTIMIZE_INDEX_PREFIX + "*")
        .setSettings(Settings.builder().put(INDEX_READ_ONLY_SETTING, false)).get();
    }
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
