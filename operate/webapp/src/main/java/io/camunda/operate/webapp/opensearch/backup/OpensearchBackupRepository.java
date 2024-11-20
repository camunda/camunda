/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.backup;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.backup.OperateSnapshotNameProvider;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsImpl;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBackupRepository
    extends io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository {
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "snapshot_missing_exception";
  public static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "repository_missing_exception";

  public OpensearchBackupRepository(
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final OperateProperties operateProperties) {
    super(
        openSearchClient,
        openSearchAsyncClient,
        props(operateProperties.getBackup()),
        new OperateSnapshotNameProvider());
  }

  private static BackupRepositoryProps props(final BackupProperties backup) {
    return new BackupRepositoryPropsImpl(
        backup.getSnapshotTimeout(), backup.getIncompleteCheckTimeoutInSeconds());
  }
}
