/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.configuration.Api;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Cluster;
import io.camunda.configuration.Data;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.Monitoring;
import io.camunda.configuration.Processing;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.Security;
import io.camunda.configuration.Webapps;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.util.unit.DataSize;

public class ExtendedConfigurationBuilder {

  public static final String CONFIG_FILE_NAME = "config.yaml";
  private static final ObjectMapper YAML_MAPPER = createYamlMapper();
  private static final String CAMUNDA_HEADER = "camunda";

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
      new TypeReference<>() {};

  private final Camunda unifiedConfig;
  private final Map<String, Object> additionalConfigs = new LinkedHashMap<>();

  public ExtendedConfigurationBuilder() {
    unifiedConfig = new Camunda();
    initializeUnifiedConfigDefaults();
  }

  private static ObjectMapper createYamlMapper() {
    final var springTypesModule = new SimpleModule("extendedTypes");
    springTypesModule.addSerializer(new DurationSerializer());
    springTypesModule.addSerializer(new DataSizeSerializer());

    return new ObjectMapper(new YAMLFactory().disable(Feature.WRITE_DOC_START_MARKER))
        .registerModule(new JavaTimeModule())
        .registerModule(new Jdk8Module())
        .registerModule(springTypesModule)
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY)
        .setDefaultPropertyInclusion(Include.NON_DEFAULT)
        .setVisibility(PropertyAccessor.ALL, Visibility.NONE)
        .setVisibility(PropertyAccessor.GETTER, Visibility.ANY)
        .setVisibility(PropertyAccessor.IS_GETTER, Visibility.ANY);
  }

  public Camunda getUnifiedConfig() {
    return unifiedConfig;
  }

  public Map<String, Object> getAdditionalConfigs() {
    return additionalConfigs;
  }

  public ExtendedConfigurationBuilder withSecurityConfig(final Consumer<Security> securityConfig) {
    securityConfig.accept(unifiedConfig.getSecurity());
    return this;
  }

  public ExtendedConfigurationBuilder withClusterConfig(final Consumer<Cluster> clusterConfig) {
    clusterConfig.accept(unifiedConfig.getCluster());
    return this;
  }

  public ExtendedConfigurationBuilder withDataConfig(final Consumer<Data> dataConfig) {
    dataConfig.accept(unifiedConfig.getData());
    return this;
  }

  public ExtendedConfigurationBuilder withProcessingConfig(
      final Consumer<Processing> processingConfig) {
    processingConfig.accept(unifiedConfig.getProcessing());
    return this;
  }

  public ExtendedConfigurationBuilder withApiConfig(final Consumer<Api> apiConfig) {
    apiConfig.accept(unifiedConfig.getApi());
    return this;
  }

  public ExtendedConfigurationBuilder withSystemConfig(
      final Consumer<io.camunda.configuration.System> systemConfig) {
    systemConfig.accept(unifiedConfig.getSystem());
    return this;
  }

  public ExtendedConfigurationBuilder withMonitoringConfig(
      final Consumer<Monitoring> monitoringConfig) {
    monitoringConfig.accept(unifiedConfig.getMonitoring());
    return this;
  }

  public ExtendedConfigurationBuilder withWebAppsConfig(final Consumer<Webapps> webappConfig) {
    webappConfig.accept(unifiedConfig.getWebapps());
    return this;
  }

  public ExtendedConfigurationBuilder withCamundaExporter(final String elasticSearchUrl) {
    return withCamundaExporter(elasticSearchUrl, null);
  }

  public ExtendedConfigurationBuilder withExporter(
      final String id, final Consumer<ExporterCfg> modifier) {
    // Create a temporary ExporterCfg to accept the configuration
    final var tempExporterCfg = new ExporterCfg();
    modifier.accept(tempExporterCfg);

    // Transfer to unified config exporter
    return withDataConfig(
        data -> {
          final var unifiedExporter =
              data.getExporters().computeIfAbsent(id, ignored -> new Exporter());
          unifiedExporter.setClassName(tempExporterCfg.getClassName());
          unifiedExporter.setJarPath(tempExporterCfg.getJarPath());
          unifiedExporter.setArgs(tempExporterCfg.getArgs());
        });
  }

  public ExtendedConfigurationBuilder withCamundaExporter(
      final String databaseUrl, final String retentionPolicyName) {
    final var exporterConfigArgs =
        new HashMap<String, Object>(
            Map.of("connect", Map.of("url", databaseUrl), "bulk", Map.of("size", 1)));
    if (retentionPolicyName != null) {
      exporterConfigArgs.put("retention", Map.of("enabled", true, "policyName", "test-policy"));
    }
    withExporter(
        "CamundaExporter",
        cfg -> {
          cfg.setClassName("io.camunda.exporter.CamundaExporter");
          cfg.setArgs(exporterConfigArgs);
        });

    // enable schema creation as ES is used in the current tests
    return withAdditionalConfig("camunda.database.schema-manager.createSchema", true);
  }

  /**
   * Adds a {@link BrokerCfg} configuration that will be serialized under the {@code zeebe.broker}
   * key in the exported YAML. Use this to include legacy broker properties (gateway, network,
   * threads, etc.) that are not yet part of the unified {@link Camunda} configuration.
   */
  public ExtendedConfigurationBuilder withBrokerConfig(final Consumer<BrokerCfg> brokerConfig) {
    // Retrieve or create the holder stored under the "zeebe" key.
    // On first call this will create a new LinkedHashMap; subsequent calls reuse it.
    final var holder = additionalConfigs.computeIfAbsent("zeebe", k -> new LinkedHashMap<>());
    if (holder instanceof final LinkedHashMap<?, ?> zeebeMap) {
      @SuppressWarnings("unchecked")
      final var typedMap = (Map<String, Object>) zeebeMap;
      final var cfg = (BrokerCfg) typedMap.computeIfAbsent("broker", k -> new BrokerCfg());
      brokerConfig.accept(cfg);
    }
    return this;
  }

  /**
   * Adds an arbitrary configuration object that will be serialized under the given dot-separated
   * property prefix in the exported YAML. For example, {@code withAdditionalConfig("server",
   * serverConfig)} produces a top-level {@code server:} key, and {@code
   * withAdditionalConfig("zeebe.broker", brokerCfg)} produces {@code zeebe: broker: ...}.
   *
   * @param prefix dot-separated property path (e.g. "zeebe.broker", "server")
   * @param config the configuration object to serialize
   */
  @SuppressWarnings("unchecked")
  public ExtendedConfigurationBuilder withAdditionalConfig(
      final String prefix, final Object config) {
    final String[] keys = prefix.split("\\.");
    if (keys.length == 1) {
      additionalConfigs.put(keys[0], config);
    } else {
      // Build nested maps for multi-segment prefixes, e.g. "zeebe.broker" -> {zeebe: {broker: …}}
      final String rootKey = keys[0];
      var currentMap =
          (Map<String, Object>)
              additionalConfigs.computeIfAbsent(rootKey, k -> new LinkedHashMap<>());
      for (int i = 1; i < keys.length - 1; i++) {
        final var nextMap =
            (Map<String, Object>) currentMap.computeIfAbsent(keys[i], k -> new LinkedHashMap<>());
        currentMap = nextMap;
      }
      currentMap.put(keys[keys.length - 1], config);
    }
    return this;
  }

  public ExtendedConfigurationBuilder withSecondaryStorageType(final SecondaryStorageType type) {
    unifiedConfig.getData().getSecondaryStorage().setType(type);
    return this;
  }

  private void initializeUnifiedConfigDefaults() {
    // Set cluster defaults
    unifiedConfig.getCluster().setSize(1);
    unifiedConfig.getCluster().setPartitionCount(1);
    unifiedConfig.getCluster().setReplicationFactor(1);
    unifiedConfig.getCluster().setCompressionAlgorithm(Cluster.CompressionAlgorithm.NONE);

    // Set membership defaults for fast test execution
    final var membership = unifiedConfig.getCluster().getMembership();
    membership.setFailureTimeout(Duration.ofSeconds(5));
    membership.setProbeInterval(Duration.ofMillis(100));
    membership.setSyncInterval(Duration.ofMillis(500));

    final var metadata = unifiedConfig.getCluster().getMetadata();
    metadata.setSyncInitializerDelay(Duration.ofMillis(500));
    metadata.setSyncDelay(Duration.ofMillis(500));

    // Set raft defaults - disable flushing for faster tests
    unifiedConfig.getCluster().getRaft().setFlushEnabled(false);
    unifiedConfig.getCluster().getRaft().setFlushDelay(Duration.ZERO);

    // Set data defaults - smaller segments for tests
    unifiedConfig.getData().setSnapshotPeriod(Duration.ofMinutes(5));
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getLogStream()
        .setLogSegmentSize(DataSize.ofMegabytes(16));
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getDisk()
        .getFreeSpace()
        .setProcessing(DataSize.ofMegabytes(128));
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getDisk()
        .getFreeSpace()
        .setReplication(DataSize.ofMegabytes(64));

    // set default default size for rocks db
    unifiedConfig
        .getData()
        .getPrimaryStorage()
        .getRocksDb()
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);

    // Set processing defaults - enable consistency checks
    unifiedConfig.getProcessing().setEnablePreconditionsCheck(true);
    unifiedConfig.getProcessing().setEnableForeignKeyChecks(true);

    // Set dynamic ports via properties (these aren't in unified config yet)
    /*    unifiedConfig
        .getCluster()
        .getNetwork()
        .getCommandApi()
        .setPort(SocketUtil.getNextAddress().getPort());
    unifiedConfig
        .getCluster()
        .getNetwork()
        .getInternalApi()
        .setPort(SocketUtil.getNextAddress().getPort());
    unifiedConfig.getApi().getGrpc().setPort(SocketUtil.getNextAddress().getPort());*/

    // By default, disable web apps
    unifiedConfig.getWebapps().getIdentity().setEnabled(false);
    unifiedConfig.getWebapps().getOperate().setEnabled(false);
    unifiedConfig.getWebapps().getTasklist().setEnabled(false);
  }

  public Path exportConfig(final Path exportPath) {
    final var filePath = exportPath.resolve(CONFIG_FILE_NAME);
    try {
      // Convert the unified config bean into a plain map so it can be deep-merged
      final Map<String, Object> fullConfig = new LinkedHashMap<>();
      fullConfig.put(CAMUNDA_HEADER, flatten(unifiedConfig));
      mergeConfigs(fullConfig, flatten(additionalConfigs));

      final String yaml =
          YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(fullConfig);
      Files.writeString(filePath, yaml);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }

  /**
   * Recursively deep-merges {@code source} into {@code target}. When both maps contain the same key
   * and both values are themselves {@link Map}, the merge recurses. Otherwise the value from {@code
   * source} wins (overwrite semantics).
   */
  @SuppressWarnings("unchecked")
  private static void mergeConfigs(
      final Map<String, Object> target, final Map<String, Object> source) {
    for (final var entry : source.entrySet()) {
      final String key = entry.getKey();
      final Object sourceValue = entry.getValue();
      final Object targetValue = target.get(key);

      if (targetValue instanceof Map && sourceValue instanceof Map) {
        mergeConfigs((Map<String, Object>) targetValue, (Map<String, Object>) sourceValue);
      } else {
        target.put(key, sourceValue);
      }
    }
  }

  private static Map<String, Object> flatten(final Object value) {
    return YAML_MAPPER.convertValue(value, MAP_TYPE);
  }

  /** Serializes {@link Duration} as an ISO-8601 string (e.g. {@code PT5M}, {@code PT0.1S}). */
  private static final class DurationSerializer extends StdSerializer<Duration> {
    private DurationSerializer() {
      super(Duration.class);
    }

    @Override
    public void serialize(
        final Duration value, final JsonGenerator gen, final SerializerProvider provider)
        throws IOException {
      gen.writeString(value.toString());
    }
  }

  /**
   * Serializes {@link DataSize} as a human-readable string (e.g. {@code 128MB}, {@code 2GB}). Falls
   * back to byte count for non-round values.
   */
  private static final class DataSizeSerializer extends StdSerializer<DataSize> {
    private DataSizeSerializer() {
      super(DataSize.class);
    }

    @Override
    public void serialize(
        final DataSize value, final JsonGenerator gen, final SerializerProvider provider)
        throws IOException {
      final long bytes = value.toBytes();
      final String text;
      if (bytes % DataSize.ofGigabytes(1).toBytes() == 0) {
        text = (bytes / DataSize.ofGigabytes(1).toBytes()) + "GB";
      } else if (bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
        text = (bytes / DataSize.ofMegabytes(1).toBytes()) + "MB";
      } else if (bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
        text = (bytes / DataSize.ofKilobytes(1).toBytes()) + "KB";
      } else {
        text = bytes + "B";
      }
      gen.writeString(text);
    }
  }
}
