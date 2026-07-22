/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("restore")
@SpringBootTest(
    classes = RestoreApp.class,
    properties = {
      "camunda.data.backup.store=filesystem",
      "camunda.data.backup.filesystem.basepath=/tmp",
      "camunda.cluster.node-id=26",
      "backupId=27"
    })
public class RestoreAppTest {

  private static final int PARTITION_ID = 1;
  private static final long BACKUP_ID = 27L;

  @Autowired private BrokerBasedProperties brokerBasedProperties;

  /**
   * The RestoreApp now validates the backup existence before proceeding. We stub a completed backup
   * in the expected repository to avoid the app failing to start.
   */
  @BeforeAll
  static void createCompletedBackup() {
    final var config = new FilesystemBackupConfig.Builder().withBasePath("/tmp").build();
    final var store = FilesystemBackupStore.of(config);
    final var id = new BackupIdentifierImpl(26, PARTITION_ID, BACKUP_ID);
    if (store.getStatus(id).join().statusCode() == BackupStatusCode.COMPLETED) {
      return;
    }
    final var descriptor =
        new BackupDescriptorImpl(1L, 1, "test", Instant.now(), CheckpointType.MANUAL_BACKUP);
    final var backup =
        new BackupImpl(
            id, descriptor, new NamedFileSetImpl(Map.of()), new NamedFileSetImpl(Map.of()));
    store.save(backup).join();
  }

  @Test
  void testUnifiedConfigurationClassesLoadSuccessfully() {
    assertThat(brokerBasedProperties).isNotNull();
    assertThat(brokerBasedProperties.getCluster()).isNotNull();
    assertThat(brokerBasedProperties.getCluster().getNodeId()).isEqualTo(26);
  }
}
