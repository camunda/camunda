/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.backup;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.testhelpers.StatefulRestTemplate;

public class BackupRestoreTestContext extends TestContext<BackupRestoreTestContext> {

  private ElasticsearchClient esClient;
  private StatefulRestTemplate operateRestClient;

  public ElasticsearchClient getEsClient() {
    return esClient;
  }

  public BackupRestoreTestContext setEsClient(final ElasticsearchClient esClient) {
    this.esClient = esClient;
    return this;
  }

  public StatefulRestTemplate getOperateRestClient() {
    return operateRestClient;
  }

  public BackupRestoreTestContext setOperateRestClient(
      final StatefulRestTemplate operateRestClient) {
    this.operateRestClient = operateRestClient;
    return this;
  }
}
