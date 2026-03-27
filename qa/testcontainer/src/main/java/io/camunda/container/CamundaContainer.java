/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Exporter;
import io.camunda.container.cluster.BrokerNode;
import io.camunda.container.cluster.CamundaPort;
import io.camunda.container.cluster.GatewayNode;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.TestcontainersConfiguration;

public abstract sealed class CamundaContainer<SELF extends CamundaContainer<SELF>>
    extends GenericContainer<SELF> {
  public static final String CAMUNDA_CONTAINER_IMAGE_PROPERTY = "camunda.container.image";
  public static final String DEFAULT_CAMUNDA_CONTAINER_IMAGE = "camunda/camunda";
  public static final String CAMUNDA_CONTAINER_VERSION_PROPERTY = "camunda.container.version";
  public static final String DEFAULT_CAMUNDA_VERSION = "8.10-SNAPSHOT";
  public static final String DEFAULT_CAMUNDA_DATA_PATH = "/usr/local/camunda/data";
  public static final String DEFAULT_CAMUNDA_LOGS_PATH = "/usr/local/camunda/logs";
  public static final String DEFAULT_CAMUNDA_TMP_PATH = "/tmp";
  private static final String CONFIG_PATH = "/usr/local/camunda/config/application.yml";
  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);

  protected final ExtendedConfigurationBuilder configurationBuilder;

  protected CamundaContainer(final DockerImageName image) {
    super(image);
    configurationBuilder = new ExtendedConfigurationBuilder();
  }

  @Override
  public void start() {
    final var tempDir = createTempDir();
    final var configFile = configurationBuilder.exportConfig(tempDir);
    withCopyFileToContainer(MountableFile.forHostPath(configFile), CONFIG_PATH)
        .withEnv("SPRING_CONFIG_ADDITIONALLOCATION", CONFIG_PATH);
    super.start();
  }

  public boolean isStarted() {
    return getContainerId() != null;
  }

  public SELF withUnifiedConfig(final Consumer<Camunda> configurer) {
    configurer.accept(configurationBuilder.getUnifiedConfig());
    return self();
  }

  private static Path createTempDir() {
    try {
      return Files.createTempDirectory("camunda-config");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static DockerImageName getBrokerImageName() {
    return DockerImageName.parse(getDefaultImage()).withTag(getDefaultVersion());
  }

  public Camunda getConfiguration() {
    return configurationBuilder.getUnifiedConfig();
  }

  public Map<String, Object> getAdditionalConfigs() {
    return configurationBuilder.getAdditionalConfigs();
  }

  /** Returns the default Camunda docker image, without a tag */
  public static String getDefaultImage() {
    return TestcontainersConfiguration.getInstance()
        .getEnvVarOrProperty(CAMUNDA_CONTAINER_IMAGE_PROPERTY, DEFAULT_CAMUNDA_CONTAINER_IMAGE);
  }

  /** Returns the default Camunda docker image tag/version */
  public static String getDefaultVersion() {
    return TestcontainersConfiguration.getInstance()
        .getEnvVarOrProperty(CAMUNDA_CONTAINER_VERSION_PROPERTY, DEFAULT_CAMUNDA_VERSION);
  }

  public DockerImageName getDefaultDockerImage() {
    return DockerImageName.parse(getDefaultImage()).withTag(getDefaultVersion());
  }

  abstract void applyDefaultConfiguration();

  /**
   * Supply a configuration property outside the Unified Config scope. This is useful for properties
   * that are not part of the unified config exposed model or internal overrides. Properties
   * included are merged with the Unified Configuration ones.
   */
  public SELF withProperty(final String name, final Object value) {
    configurationBuilder.withAdditionalConfig(name, value);
    return self();
  }

  /** Returns the default wait strategy for this container */
  protected WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(BrokerContainer.newDefaultBrokerReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  /**
   * Creates a new {@link HttpWaitStrategy} configured for the default broker ready check. Available
   * publicly to be modified as desired.
   *
   * @return the default broker ready check
   */
  public static HttpWaitStrategy newDefaultBrokerReadyCheck() {
    return new HttpWaitStrategy()
        .forPath("/ready")
        .forPort(CamundaPort.MONITORING.getPort())
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(Duration.ofSeconds(10));
  }

  /**
   * Resolves the JAR file containing the given class. If the class is loaded from a {@code
   * target/classes} directory (typical for local module builds), the method looks for the
   * corresponding JAR in the {@code target/} directory. If the class is already loaded from a JAR,
   * its path is returned directly.
   */
  private static Path resolveJar(final Class<?> clazz) {
    final var codeSource =
        Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().getPath());

    if (Files.isRegularFile(codeSource) && codeSource.toString().endsWith(".jar")) {
      return codeSource;
    }

    // codeSource is a directory like <module>/target/classes – look for the JAR in target/
    final var targetDir = codeSource.getParent();
    try (final var jars = Files.list(targetDir)) {
      return jars.filter(p -> p.toString().endsWith(".jar"))
          .filter(p -> !p.toString().endsWith("-sources.jar"))
          .filter(p -> !p.toString().endsWith("-javadoc.jar"))
          .filter(p -> !p.toString().endsWith("-tests.jar"))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Could not find JAR for " + clazz.getName() + " in " + targetDir));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static final class BrokerContainer extends CamundaContainer<BrokerContainer>
      implements BrokerNode<BrokerContainer>, GatewayNode<BrokerContainer> {

    public BrokerContainer(final DockerImageName image) {
      super(image);
      applyDefaultConfiguration();
    }

    @Override
    public BrokerContainer withTopologyCheck(final ZeebeTopologyWaitStrategy topologyCheck) {
      return waitingFor(newDefaultWaitStrategy().withStrategy(topologyCheck));
    }

    @Override
    public BrokerContainer withoutTopologyCheck() {
      return waitingFor(newDefaultWaitStrategy());
    }

    @Override
    void applyDefaultConfiguration() {
      withNetwork(Network.SHARED)
          .withTopologyCheck(ZeebeTopologyWaitStrategy.newDefaultTopologyCheck())
          .withEnv("SPRING_PROFILES_ACTIVE", "broker")
          .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
          .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
          .addExposedPorts(
              CamundaPort.GATEWAY_REST.getPort(),
              CamundaPort.GATEWAY_GRPC.getPort(),
              CamundaPort.COMMAND.getPort(),
              CamundaPort.INTERNAL.getPort(),
              CamundaPort.MONITORING.getPort());
    }

    public BrokerContainer withEmbeddedGateway() {
      configurationBuilder.withAdditionalConfig("zeebe.broker.gateway.enable", true);
      return this;
    }

    public BrokerContainer withRecordingExporter() {
      final var jarPath = resolveJar(RecordingExporter.class);

      return withCopyToContainer(MountableFile.forHostPath(jarPath), "/tmp/recording-exporter.jar")
          .withUnifiedConfig(
              cfg -> {
                final var exporter =
                    cfg.getData()
                        .getExporters()
                        .computeIfAbsent("recordingExporter", ignored -> new Exporter());
                exporter.setClassName(RecordingExporter.class.getName());
                exporter.setJarPath("/tmp/recording-exporter.jar");
              });
    }
  }

  public static final class GatewayContainer extends CamundaContainer<GatewayContainer>
      implements GatewayNode<GatewayContainer> {

    public GatewayContainer(final DockerImageName image) {
      super(image);
      applyDefaultConfiguration();
    }

    @Override
    void applyDefaultConfiguration() {
      withNetwork(Network.SHARED)
          .withEnv("SPRING_PROFILES_ACTIVE", "gateway, standalone")
          .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
          .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
          .addExposedPorts(
              CamundaPort.GATEWAY_REST.getPort(),
              CamundaPort.GATEWAY_GRPC.getPort(),
              CamundaPort.INTERNAL.getPort(),
              CamundaPort.MONITORING.getPort());
    }

    @Override
    public GatewayContainer withTopologyCheck(final ZeebeTopologyWaitStrategy topologyCheck) {
      return waitingFor(newDefaultWaitStrategy(topologyCheck));
    }

    @Override
    public GatewayContainer withoutTopologyCheck() {
      return waitingFor(new HostPortWaitStrategy().withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));
    }

    private WaitAllStrategy newDefaultWaitStrategy(final ZeebeTopologyWaitStrategy topologyCheck) {
      return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
          .withStrategy(new HostPortWaitStrategy())
          .withStrategy(topologyCheck)
          .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
    }
  }

  public static final class WebAppContainer extends CamundaContainer<WebAppContainer> {

    private final WebApp webApp;

    public WebAppContainer(final DockerImageName image, final WebApp webApp) {
      super(image);
      this.webApp = webApp;
      applyDefaultConfiguration();
    }

    @Override
    void applyDefaultConfiguration() {
      switch (webApp) {
        case ALL ->
            withEnv(
                "SPRING_PROFILES_ACTIVE", "operate, tasklist, identity, admin,consolidated-auth");
        case OPERATE -> withEnv("SPRING_PROFILES_ACTIVE", "operate, standalone,consolidated-auth");
        case TASKLIST ->
            withEnv("SPRING_PROFILES_ACTIVE", "tasklist, standalone,consolidated-auth");
        default -> throw new IllegalStateException("Unexpected value: " + webApp);
      }
    }

    public enum WebApp {
      ALL,
      OPERATE,
      TASKLIST,
    }
  }
}
