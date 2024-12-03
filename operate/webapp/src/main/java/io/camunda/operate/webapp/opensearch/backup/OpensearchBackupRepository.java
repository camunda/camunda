/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.backup;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.backup.OperateSnapshotNameProvider;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBackupRepository
    extends io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository {

  public OpensearchBackupRepository(
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final BackupRepositoryProps backupProps) {
    super(openSearchClient, openSearchAsyncClient, backupProps, new OperateSnapshotNameProvider());
  }
}
