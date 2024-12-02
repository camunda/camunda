/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.backup.OperateSnapshotNameProvider;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(
    prefix = "camunda.database",
    name = "type",
    havingValue = "elasticsearch",
    matchIfMissing = true)
@Component
@Profile("operate")
public class ElasticsearchOperateBackupRepository
    extends io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository {

  @Autowired
  public ElasticsearchOperateBackupRepository(
      @Qualifier("esClient") final RestHighLevelClient esClient,
      final ObjectMapper objectMapper,
      final BackupRepositoryProps backupRepositoryProps) {
    super(esClient, objectMapper, backupRepositoryProps, new OperateSnapshotNameProvider());
  }
}
