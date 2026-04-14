/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.rest.StatefulRestTemplate;
import org.opensearch.client.opensearch.OpenSearchClient;

public class BackupRestoreTestContext extends TestContext<BackupRestoreTestContext> {

  private ElasticsearchClient esClient;
  private OpenSearchClient osClient;
  private StatefulRestTemplate tasklistRestClient;
  private CamundaClient camundaClient;

  public ElasticsearchClient getEsClient() {
    return esClient;
  }

  public BackupRestoreTestContext setEsClient(final ElasticsearchClient esClient) {
    this.esClient = esClient;
    return this;
  }

  public OpenSearchClient getOsClient() {
    return osClient;
  }

  public BackupRestoreTestContext setOsClient(final OpenSearchClient osClient) {
    this.osClient = osClient;
    return this;
  }

  public CamundaClient getCamundaClient() {
    if (camundaClient == null) {
      return createCamundaClient();
    }
    return camundaClient;
  }

  public BackupRestoreTestContext setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
    return this;
  }

  public CamundaClient createCamundaClient() {
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .grpcAddress(getZeebeGrpcAddress())
            .preferRestOverGrpc(false)
            .defaultJobWorkerMaxJobsActive(5);
    camundaClient = builder.build();
    return camundaClient;
  }
}
