/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
