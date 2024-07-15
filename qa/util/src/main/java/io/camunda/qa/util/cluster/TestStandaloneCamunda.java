package io.camunda.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.application.sources.DefaultObjectMapperConfiguration;
import io.camunda.commons.CommonsModuleConfiguration;
import io.camunda.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.actuator.BrokerHealthActuator;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/** Represents an instance of the {@link BrokerModuleConfiguration} Spring application. */
@SuppressWarnings("UnusedReturnValue")
public final class TestStandaloneCamunda extends TestSpringApplication<TestStandaloneCamunda>
    implements TestGateway<TestStandaloneCamunda> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestStandaloneCamunda.class);
  private static final DockerImageName ELASTIC_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
          .withTag(RestClient.class.getPackage().getImplementationVersion());
  private static final String RECORDING_EXPORTER_ID = "recordingExporter";
  private final ElasticsearchContainer esContainer =
      new ElasticsearchContainer(ELASTIC_IMAGE)
          .withClasspathResourceMapping(
              "elasticsearch-fast-startup.options",
              "/usr/share/elasticsearch/config/jvm.options.d/elasticsearch-fast-startup.options",
              BindMode.READ_ONLY)
          .withStartupTimeout(Duration.ofMinutes(5))
          .withEnv("action.auto_create_index", "true")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.watcher.enabled", "false")
          .withEnv("xpack.ml.enabled", "false")
          .withEnv("action.destructive_requires_name", "false");
  private final BrokerBasedProperties brokerProperties;
  private final OperateProperties operateProperties;
  private final TasklistProperties tasklistProperties;

  public TestStandaloneCamunda() {
    super(
        CommonsModuleConfiguration.class,
        // test overrides - to control data clean up; (and some components are not installed on)
        OperateModuleConfiguration.class,
        TasklistModuleConfiguration.class,
        WebappsModuleConfiguration.class,
        BrokerModuleConfiguration.class,
        DefaultObjectMapperConfiguration.class,
        TestOperateElasticsearchSchemaManager.class,
        TestTasklistElasticsearchSchemaManager.class,
        TestOperateSchemaStartup.class,
        TestTasklistSchemaStartup.class);

    brokerProperties = new BrokerBasedProperties();

    brokerProperties.getNetwork().getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getNetwork().getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());

    brokerProperties.getData().setLogSegmentSize(DataSize.ofMegabytes(16));
    brokerProperties.getData().getDisk().getFreeSpace().setProcessing(DataSize.ofMegabytes(128));
    brokerProperties.getData().getDisk().getFreeSpace().setReplication(DataSize.ofMegabytes(64));

    operateProperties = new OperateProperties();
    tasklistProperties = new TasklistProperties();

    withBean("config", brokerProperties, BrokerBasedProperties.class)
        .withBean("operate-config", operateProperties, OperateProperties.class)
        .withBean("tasklist-config", tasklistProperties, TasklistProperties.class)
        .withAdditionalProfile(Profile.BROKER)
        .withAdditionalProfile(Profile.OPERATE)
        .withAdditionalProfile(Profile.TASKLIST)
        .withAdditionalInitializer(new WebappsConfigurationInitializer());
  }

  @Override
  public TestStandaloneCamunda start() {
    esContainer.start();

    final String esURL = String.format("http://%s", esContainer.getHttpHostAddress());

    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName("io.camunda.zeebe.exporter.ElasticsearchExporter");
    exporterCfg.setArgs(Map.of("url", esURL));
    brokerProperties.getExporters().put("elasticsearch", exporterCfg);

    operateProperties.getElasticsearch().setUrl(esURL);
    operateProperties.getZeebeElasticsearch().setUrl(esURL);
    tasklistProperties.getElasticsearch().setUrl(esURL);
    tasklistProperties.getZeebeElasticsearch().setUrl(esURL);
    return super.start();
  }

  @Override
  public TestStandaloneCamunda stop() {
    LOGGER.info("Stopping standalone camunda test...");
    ((TestOperateElasticsearchSchemaManager) bean(SchemaManager.class)).deleteSchema();

    return super.stop();
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case COMMAND -> brokerProperties.getNetwork().getCommandApi().getPort();
      case GATEWAY -> brokerProperties.getGateway().getNetwork().getPort();
      case CLUSTER -> brokerProperties.getNetwork().getInternalApi().getPort();
      default -> super.mappedPort(port);
    };
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    withProperty("zeebe.broker.gateway.enable", brokerProperties.getGateway().isEnable());
    return super.createSpringBuilder();
  }

  public TestRestOperateClient newOperateClient() {
    return new TestRestOperateClient(restAddress());
  }

  @Override
  public TestStandaloneCamunda self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(String.valueOf(brokerProperties.getCluster().getNodeId()));
  }

  @Override
  public String host() {
    return brokerProperties.getNetwork().getHost();
  }

  @Override
  public HealthActuator healthActuator() {
    return BrokerHealthActuator.of(monitoringUri().toString());
  }

  @Override
  public boolean isGateway() {
    return brokerProperties.getGateway().isEnable();
  }

  @Override
  public URI grpcAddress() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Expected to get the gateway address for this broker, but the embedded gateway is not enabled");
    }

    return TestGateway.super.grpcAddress();
  }

  @Override
  public GatewayHealthActuator gatewayHealth() {
    throw new UnsupportedOperationException("Brokers do not support the gateway health indicators");
  }

  @Override
  public TestStandaloneCamunda withGatewayConfig(final Consumer<GatewayCfg> modifier) {
    modifier.accept(brokerProperties.getGateway());
    return this;
  }

  @Override
  public GatewayCfg gatewayConfig() {
    return brokerProperties.getGateway();
  }

  @Override
  public ZeebeClientBuilder newClientBuilder() {
    if (!isGateway()) {
      throw new IllegalStateException(
          "Cannot create a new client for this broker, as it does not have an embedded gateway");
    }

    return TestGateway.super.newClientBuilder();
  }

  public BrokerBasedProperties brokerConfig() {
    return brokerProperties;
  }

  public TestStandaloneCamunda withBrokerConfig(final Consumer<BrokerBasedProperties> modifier) {
    modifier.accept(brokerProperties);
    return this;
  }

  public TestStandaloneCamunda withRecordingExporter(final boolean useRecordingExporter) {
    if (!useRecordingExporter) {
      brokerProperties.getExporters().remove(RECORDING_EXPORTER_ID);
      return this;
    }

    return withExporter(
        RECORDING_EXPORTER_ID, cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  public TestStandaloneCamunda withExporter(final String id, final Consumer<ExporterCfg> modifier) {
    final var exporterConfig =
        brokerProperties.getExporters().computeIfAbsent(id, ignored -> new ExporterCfg());
    modifier.accept(exporterConfig);

    return this;
  }

  /**
   * Sets the broker's working directory, aka its data directory. If a path is given, the broker
   * will not delete it on shutdown.
   *
   * @param directory path to the broker's root data directory
   * @return itself for chaining
   */
  public TestStandaloneCamunda withWorkingDirectory(final Path directory) {
    return withBean(
        "workingDirectory", new WorkingDirectory(directory, false), WorkingDirectory.class);
  }
}
