/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.test.util.record.RecordLogger;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * This is a wrapper over {@link ClusteringRule}. You can use it on both instance and static level
 * fields. If you register a static field, then the instance level callbacks (e.g. {@link
 * #beforeEach(ExtensionContext)}) no-ops.
 */
public class ClusteringRuleExtension extends ClusteringRule
    implements BeforeAllCallback,
        BeforeEachCallback,
        AfterAllCallback,
        AfterEachCallback,
        TestWatcher {

  private Path tempDir;
  private boolean invokedStatically;

  public ClusteringRuleExtension(
      final int partitionCount, final int replicationFactor, final int clusterSize) {
    this(partitionCount, replicationFactor, clusterSize, cfg -> {});
  }

  public ClusteringRuleExtension(
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final Consumer<BrokerCfg> configurator) {
    super(partitionCount, replicationFactor, clusterSize, configurator);
  }

  public ClusteringRuleExtension(
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final Consumer<BrokerCfg> brokerConfigurator,
      final Consumer<GatewayCfg> gatewayConfigurator) {
    super(partitionCount, replicationFactor, clusterSize, brokerConfigurator, gatewayConfigurator);
  }

  @Override
  public void afterAll(final ExtensionContext extensionContext) throws Exception {
    teardown();
  }

  @Override
  public void beforeAll(final ExtensionContext extensionContext) throws Exception {
    invokedStatically = true;
    setup();
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    if (!invokedStatically) {
      teardown();
    }
  }

  private void teardown() throws IOException {
    super.after();
    FileUtil.deleteFolderIfExists(tempDir);
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    if (!invokedStatically) {
      setup();
    }
  }

  private void setup() throws IOException {
    RecordingExporter.reset();
    tempDir = Files.createTempDirectory("clustered-tests");
    super.before();
  }

  @Override
  protected File getBrokerBase(final int nodeId) {
    final Path base;
    try {
      base = tempDir.resolve(String.valueOf(nodeId));
      FileUtil.ensureDirectoryExists(base);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    return base.toFile();
  }

  public ClusteringRule getCluster() {
    return this;
  }

  @Override
  public void testFailed(final ExtensionContext context, final Throwable cause) {
    RecordLogger.logRecords();
  }
}
