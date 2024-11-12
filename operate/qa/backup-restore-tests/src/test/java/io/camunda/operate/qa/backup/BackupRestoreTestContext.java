/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.backup;

import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import org.elasticsearch.client.RestHighLevelClient;

public class BackupRestoreTestContext extends TestContext<BackupRestoreTestContext> {

  private RestHighLevelClient esClient;
  private StatefulRestTemplate operateRestClient;

  public RestHighLevelClient getEsClient() {
    return esClient;
  }

  public BackupRestoreTestContext setEsClient(RestHighLevelClient esClient) {
    this.esClient = esClient;
    return this;
  }

  public StatefulRestTemplate getOperateRestClient() {
    return operateRestClient;
  }

  public BackupRestoreTestContext setOperateRestClient(StatefulRestTemplate operateRestClient) {
    this.operateRestClient = operateRestClient;
    return this;
  }
}
