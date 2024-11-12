/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post.opensearch;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.ThreadUtil;
import java.util.HashMap;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;

class OpensearchPostImporterRequests {
  private HashMap<String, UpdateOperation> listViewRequests = new HashMap<>();
  private HashMap<String, UpdateOperation> flowNodeInstanceRequests = new HashMap<>();
  private HashMap<String, UpdateOperation> incidentRequests = new HashMap<>();

  public HashMap<String, UpdateOperation> getListViewRequests() {
    return listViewRequests;
  }

  public OpensearchPostImporterRequests setListViewRequests(
      HashMap<String, UpdateOperation> listViewRequests) {
    this.listViewRequests = listViewRequests;
    return this;
  }

  public HashMap<String, UpdateOperation> getFlowNodeInstanceRequests() {
    return flowNodeInstanceRequests;
  }

  public OpensearchPostImporterRequests setFlowNodeInstanceRequests(
      HashMap<String, UpdateOperation> flowNodeInstanceRequests) {
    this.flowNodeInstanceRequests = flowNodeInstanceRequests;
    return this;
  }

  public HashMap<String, UpdateOperation> getIncidentRequests() {
    return incidentRequests;
  }

  public OpensearchPostImporterRequests setIncidentRequests(
      HashMap<String, UpdateOperation> incidentRequests) {
    this.incidentRequests = incidentRequests;
    return this;
  }

  public boolean isEmpty() {
    return listViewRequests.isEmpty()
        && flowNodeInstanceRequests.isEmpty()
        && incidentRequests.isEmpty();
  }

  public boolean execute(
      RichOpenSearchClient richOpenSearchClient, OperateProperties operateProperties)
      throws PersistenceException {

    final BulkRequest bulkRequest =
        BulkRequest.of(
            b -> {
              listViewRequests.values().forEach(u -> b.operations(o -> o.update(u)));
              flowNodeInstanceRequests.values().forEach(u -> b.operations(o -> o.update(u)));
              incidentRequests.values().stream().forEach(u -> b.operations(o -> o.update(u)));
              return b;
            });
    richOpenSearchClient.batch().bulk(bulkRequest);
    ThreadUtil.sleepFor(3000L);

    return true;
  }
}
