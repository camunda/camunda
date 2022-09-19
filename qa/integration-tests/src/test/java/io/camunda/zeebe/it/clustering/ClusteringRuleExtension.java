/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This is a wrapper over {@link ClusteringRule}. NOTE: {@link
 * io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher} is not available when using this
 * extension.
 */
public class ClusteringRuleExtension extends ClusteringRule
    implements BeforeEachCallback, AfterEachCallback {

  private Path tempDir;

  public ClusteringRuleExtension(
      final int partitionCount,
      final int replicationFactor,
      final int clusterSize,
      final Consumer<BrokerCfg> configurator) {
    super(partitionCount, replicationFactor, clusterSize, configurator);
  }

  @Override
  public void afterEach(final ExtensionContext context) throws Exception {
    FileUtil.deleteFolderIfExists(tempDir);
    super.after();
  }

  @Override
  public void beforeEach(final ExtensionContext context) throws Exception {
    tempDir = Files.createTempDirectory("clustered-tests");
    super.before();
  }

  @Override
  protected File getBrokerBase(final int nodeId) {
    final Path base;
    try {
      base = Files.createDirectory(tempDir.resolve(String.valueOf(nodeId)));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    return base.toFile();
  }

  public ClusteringRule getCluster() {
    return this;
  }
}
