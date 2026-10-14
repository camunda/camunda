/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import io.camunda.configuration.PrimaryStorageBackup;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.io.TempDir;

/** Backup/restore tests without RDBMS dependency. */
public class ScaleUpPartitionsBackupRestoreTest extends ScaleUpPartitionsTest {
  ScaleUpPartitionsBackupRestoreTest(@TempDir final Path backupPath) {
    super(backupPath);
  }

  @Override
  protected io.camunda.zeebe.qa.util.cluster.TestCluster buildCluster(final Path backupPath) {
    return io.camunda.zeebe.qa.util.cluster.TestCluster.builder()
        .useRecordingExporter(true)
        .withBrokersCount(3)
        .withPartitionsCount(3)
        .withReplicationFactor(3)
        .withBrokerConfig(
            b -> {
              // No authentication (Basic Auth requires secondary storage)
              b.withUnifiedConfig(
                  cfg -> {
                    final var backup = cfg.getData().getPrimaryStorage().getBackup();
                    backup.setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);
                    backup.getFilesystem().setBasePath(backupPath.toString());

                    final var membership = cfg.getCluster().getMembership();
                    membership.setSyncInterval(Duration.ofSeconds(1));
                    membership.setGossipInterval(Duration.ofMillis(500));

                    final var distribution = cfg.getProcessing().getEngine().getDistribution();
                    distribution.setMaxBackoffDuration(Duration.ofSeconds(1));
                    distribution.setRedistributionInterval(Duration.ofMillis(200));
                  });
            })
        .build();
  }

  // Decision evaluation tests are auth-specific; skip for backup/restore
  @Override
  protected void verifyExistingDecisionCanBeEvaluatedOnPartition(final int partitionId) {
    // no-op
  }
}
