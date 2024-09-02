/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.client.indices.IndexTemplateMetadata;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgradeEsSchemaIT {
  private static final Logger log = LoggerFactory.getLogger(UpgradeEsSchemaIT.class);

  private final String previousVersion = System.getProperties().getProperty("previousVersion");
  private final String currentVersion = System.getProperties().getProperty("currentVersion");
  private final String buildDirectory = System.getProperties().getProperty("buildDirectory");
  private final Integer oldElasticPort =
      Integer.valueOf(System.getProperties().getProperty("oldElasticPort"));
  private final Integer newElasticPort =
      Integer.valueOf(System.getProperties().getProperty("newElasticPort"));
  private FileWriter oldOptimizeOutputWriter;
  private FileWriter newOptimizeOutputWriter;
  private FileWriter upgradeOutputWriter;

  @BeforeEach
  public void logFilePrepare() throws IOException {
    oldOptimizeOutputWriter = new FileWriter(getOldOptimizeOutputLogPath());
    newOptimizeOutputWriter = new FileWriter(getNewOptimizeOutputLogPath());
    upgradeOutputWriter = new FileWriter(getOptimizeUpdateLogPath());
  }

  @AfterEach
  public void logFileFlush() throws IOException {
    oldOptimizeOutputWriter.flush();
    oldOptimizeOutputWriter.close();
    newOptimizeOutputWriter.flush();
    newOptimizeOutputWriter.close();
    upgradeOutputWriter.flush();
    upgradeOutputWriter.close();
  }

  @Test
  public void upgradeAndVerifySchemaIntegrityTest() throws Exception {
    // clean new elastic and clean old elastic
    final ElasticClient oldElasticClient = new ElasticClient("old", oldElasticPort);
    oldElasticClient.cleanIndicesAndTemplates();
    final ElasticClient newElasticClient = new ElasticClient("new", newElasticPort);
    newElasticClient.cleanIndicesAndTemplates();
    final OptimizeWrapper oldOptimize =
        new OptimizeWrapper(previousVersion, buildDirectory, oldElasticPort);
    final OptimizeWrapper newOptimize =
        new OptimizeWrapper(currentVersion, buildDirectory, newElasticPort);

    try {
      // start new optimize to obtain expected schema and settings
      newOptimize.start(getNewOptimizeOutputLogPath());
      newElasticClient.refreshAll();
      final var expectedSettings = newElasticClient.getSettings();
      final var expectedMappings = newElasticClient.getMappings();
      final var expectedAliases = newElasticClient.getAliases();
      final var expectedTemplates = newElasticClient.getTemplates();
      newOptimize.stop();
      newElasticClient.cleanIndicesAndTemplates();

      // start old optimize to prepare for upgrade
      oldOptimize.start(getOldOptimizeOutputLogPath());
      oldElasticClient.refreshAll();
      oldOptimize.stop();

      // perform snapshot and restore on new es
      oldElasticClient.createSnapshotRepository();
      oldElasticClient.createSnapshotOfOptimizeIndices();
      oldElasticClient.deleteSnapshotRepository();

      newElasticClient.createSnapshotRepository();
      newElasticClient.restoreSnapshot();
      newElasticClient.deleteSnapshot();

      // start a new async snapshot operation to ensure the upgrade is resilient to concurrently
      // running snapshots
      newElasticClient.createAsyncSnapshot();

      // run the upgrade
      newOptimize.startUpgrade(getOptimizeUpdateLogPath());
      newOptimize.waitForUpgradeToFinish(360);

      // stop/delete async snapshot operation as upgrade completed already
      newElasticClient.deleteAsyncSnapshot();

      // start new optimize
      newOptimize.start(getNewOptimizeOutputLogPath());
      newOptimize.stop();

      log.info("Asserting expected index metadata...");
      // Indices
      log.info(
          "Expected settings size: {}, keys: {}",
          expectedSettings.size(),
          expectedSettings.keySet());
      final ImmutableOpenMap<String, Settings> newSettings = newElasticClient.getSettings();
      log.info("Actual settings size: {}, keys: {}", newSettings.size(), newSettings.keySet());
      assertThat(newSettings).isEqualTo(expectedSettings);
      assertThat(newElasticClient.getMappings()).isEqualTo(expectedMappings);

      // Aliases
      log.info(
          "Expected aliases size: {}, keys: {}", expectedAliases.size(), expectedAliases.keySet());
      final Map<String, Set<AliasMetadata>> newAliases = newElasticClient.getAliases();
      log.info("Actual aliases size: {}, keys: {}", newAliases.size(), newAliases.keySet());
      assertThat(newAliases).isEqualTo(expectedAliases);

      // Templates
      log.info(
          "Expected templates size: {}, names: {}",
          expectedTemplates.size(),
          expectedTemplates.stream().map(IndexTemplateMetadata::name).toList());
      final List<IndexTemplateMetadata> newTemplates = newElasticClient.getTemplates();
      log.info(
          "Actual templates size: {}, names: {}",
          newTemplates.size(),
          newTemplates.stream().map(IndexTemplateMetadata::name).toList());
      assertThat(newTemplates).containsExactlyInAnyOrderElementsOf(expectedTemplates);

      log.info("Finished asserting expected index metadata!");
    } finally {
      oldElasticClient.close();
      newElasticClient.close();
      oldOptimize.stop();
      newOptimize.stop();
    }
  }

  private String getOptimizeUpdateLogPath() {
    return buildDirectory + "/update-schema-optimize-upgrade.log";
  }

  private String getNewOptimizeOutputLogPath() {
    return buildDirectory + "/update-schema-new-optimize-startup.log";
  }

  private String getOldOptimizeOutputLogPath() {
    return buildDirectory + "/update-schema-old-optimize-startup.log";
  }
}
