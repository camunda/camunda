/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post.opensearch;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.ThreadUtil;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;

import java.util.HashMap;

class OpensearchPostImporterRequests {
    private HashMap<String, UpdateOperation> listViewRequests = new HashMap<>();
    private HashMap<String, UpdateOperation> flowNodeInstanceRequests = new HashMap<>();
    private HashMap<String, UpdateOperation> incidentRequests = new HashMap<>();

    public HashMap<String, UpdateOperation> getListViewRequests() {
        return listViewRequests;
    }

    public OpensearchPostImporterRequests setListViewRequests(HashMap<String, UpdateOperation> listViewRequests) {
        this.listViewRequests = listViewRequests;
        return this;
    }

    public HashMap<String, UpdateOperation> getFlowNodeInstanceRequests() {
        return flowNodeInstanceRequests;
    }

    public OpensearchPostImporterRequests setFlowNodeInstanceRequests(HashMap<String, UpdateOperation> flowNodeInstanceRequests) {
        this.flowNodeInstanceRequests = flowNodeInstanceRequests;
        return this;
    }

    public HashMap<String, UpdateOperation> getIncidentRequests() {
        return incidentRequests;
    }

    public OpensearchPostImporterRequests setIncidentRequests(HashMap<String, UpdateOperation> incidentRequests) {
        this.incidentRequests = incidentRequests;
        return this;
    }

    public boolean isEmpty() {
        return listViewRequests.isEmpty() && flowNodeInstanceRequests.isEmpty() && incidentRequests.isEmpty();
    }

    public boolean execute(RichOpenSearchClient richOpenSearchClient, OperateProperties operateProperties)
            throws PersistenceException {

        BulkRequest bulkRequest = BulkRequest.of(b -> {
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
