/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.backup.OperateSnapshotNameProvider;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchBackupRepository
    extends io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository {

  public ElasticsearchBackupRepository(
      final RestHighLevelClient esClient,
      final ObjectMapper objectMapper,
      final OperateProperties operateProperties) {
    super(
        esClient,
        objectMapper,
        props(operateProperties.getBackup()),
        new OperateSnapshotNameProvider());
  }

  private static BackupRepositoryProps props(final BackupProperties operateProperties) {
    return new BackupRepositoryProps() {
      @Override
      public int snapshotTimeout() {
        return operateProperties.getSnapshotTimeout();
      }

      @Override
      public Long incompleteCheckTimeoutInSeconds() {
        return operateProperties.getIncompleteCheckTimeoutInSeconds();
      }
    };
  }
}
