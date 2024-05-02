/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post.elasticsearch;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ThreadUtil;
import java.util.HashMap;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticsearchPostImporterRequests {
  private HashMap<String, UpdateRequest> listViewRequests = new HashMap<>();
  private HashMap<String, UpdateRequest> flowNodeInstanceRequests = new HashMap<>();
  private HashMap<String, UpdateRequest> incidentRequests = new HashMap<>();

  public HashMap<String, UpdateRequest> getListViewRequests() {
    return listViewRequests;
  }

  public ElasticsearchPostImporterRequests setListViewRequests(
      HashMap<String, UpdateRequest> listViewRequests) {
    this.listViewRequests = listViewRequests;
    return this;
  }

  public HashMap<String, UpdateRequest> getFlowNodeInstanceRequests() {
    return flowNodeInstanceRequests;
  }

  public ElasticsearchPostImporterRequests setFlowNodeInstanceRequests(
      HashMap<String, UpdateRequest> flowNodeInstanceRequests) {
    this.flowNodeInstanceRequests = flowNodeInstanceRequests;
    return this;
  }

  public HashMap<String, UpdateRequest> getIncidentRequests() {
    return incidentRequests;
  }

  public ElasticsearchPostImporterRequests setIncidentRequests(
      HashMap<String, UpdateRequest> incidentRequests) {
    this.incidentRequests = incidentRequests;
    return this;
  }

  public boolean isEmpty() {
    return listViewRequests.isEmpty()
        && flowNodeInstanceRequests.isEmpty()
        && incidentRequests.isEmpty();
  }

  public boolean execute(RestHighLevelClient esClient, OperateProperties operateProperties)
      throws PersistenceException {

    final BulkRequest bulkRequest = new BulkRequest();

    listViewRequests.values().stream().forEach(bulkRequest::add);
    flowNodeInstanceRequests.values().stream().forEach(bulkRequest::add);
    incidentRequests.values().stream().forEach(bulkRequest::add);

    ElasticsearchUtil.processBulkRequest(
        esClient, bulkRequest, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());

    ThreadUtil.sleepFor(3000L);

    return true;
  }
}
