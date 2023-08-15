/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.backup;

import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.camunda.tasklist.qa.util.TestContext;
import org.elasticsearch.client.RestHighLevelClient;
import org.opensearch.client.opensearch.OpenSearchClient;

public class BackupRestoreTestContext extends TestContext<BackupRestoreTestContext> {

  private RestHighLevelClient esClient;
  private OpenSearchClient osClient;
  private GraphQLTestTemplate tasklistRestClient;

  public RestHighLevelClient getEsClient() {
    return esClient;
  }

  public BackupRestoreTestContext setEsClient(RestHighLevelClient esClient) {
    this.esClient = esClient;
    return this;
  }

  public OpenSearchClient getOsClient() {
    return osClient;
  }

  public BackupRestoreTestContext setOsClient(OpenSearchClient osClient) {
    this.osClient = osClient;
    return this;
  }

  public GraphQLTestTemplate getTasklistRestClient() {
    return tasklistRestClient;
  }

  public BackupRestoreTestContext setTasklistRestClient(GraphQLTestTemplate operateRestClient) {
    this.tasklistRestClient = operateRestClient;
    return this;
  }
}
