/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.snapshot;

import static io.camunda.optimize.dto.optimize.BackupState.INCOMPLETE;
import static io.camunda.optimize.service.util.SnapshotUtil.getSnapshotNameForImportIndices;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.rest.BackupInfoDto;
import io.camunda.optimize.service.BackupService;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.util.configuration.db.DatabaseBackup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class BackupServiceIT extends AbstractCCSMIT {
  private static final Long VALID_BACKUP_ID = 123L;
  private static final String VALID_REPOSITORY_NAME = "my_backup_1";

  @Test
  @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
  public void backupApi() {
    // given
    databaseIntegrationTestExtension.cleanSnapshots(VALID_REPOSITORY_NAME);
    databaseIntegrationTestExtension.createRepoSnapshot(VALID_REPOSITORY_NAME);
    databaseIntegrationTestExtension.createSnapshot(
        VALID_REPOSITORY_NAME,
        getSnapshotNameForImportIndices(VALID_BACKUP_ID),
        ElasticSearchSchemaManager.getAllNonDynamicMappings().stream()
            .filter(IndexMappingCreator::isImportIndex)
            .map(
                databaseIntegrationTestExtension.getIndexNameService()
                    ::getOptimizeIndexAliasForIndex)
            .toArray(String[]::new));

    final DatabaseBackup databaseBackup = new DatabaseBackup();
    databaseBackup.setSnapshotRepositoryName(VALID_REPOSITORY_NAME);
    embeddedOptimizeExtension
        .getConfigurationService()
        .getElasticSearchConfiguration()
        .setBackup(databaseBackup);

    // when
    final BackupInfoDto backupInfoDto =
        embeddedOptimizeExtension.getBean(BackupService.class).getSingleBackupInfo(VALID_BACKUP_ID);

    // then
    assertThat(backupInfoDto.getState()).isEqualTo(INCOMPLETE);
  }
}
