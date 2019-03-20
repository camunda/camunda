/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.List;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.processors.WorkflowZeebeRecordProcessor;
import org.camunda.operate.zeebeimport.processors.ActivityInstanceZeebeRecordProcessor;
import org.camunda.operate.zeebeimport.processors.EventZeebeRecordProcessor;
import org.camunda.operate.zeebeimport.processors.IncidentZeebeRecordProcessor;
import org.camunda.operate.zeebeimport.processors.ListViewZeebeRecordProcessor;
import org.camunda.operate.zeebeimport.processors.VariableZeebeRecordProcessor;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchBulkProcessor extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;

  @Autowired
  private ActivityInstanceZeebeRecordProcessor activityInstanceZeebeRecordProcessor;

  @Autowired
  private VariableZeebeRecordProcessor variableZeebeRecordProcessor;

  @Autowired
  private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;

  @Autowired
  private WorkflowZeebeRecordProcessor workflowZeebeRecordProcessor;

  @Autowired
  private EventZeebeRecordProcessor eventZeebeRecordProcessor;

  public void persistZeebeRecords(List<? extends RecordImpl> zeebeRecords) throws PersistenceException {

      logger.debug("Writing [{}] Zeebe records to Elasticsearch", zeebeRecords.size());

      BulkRequestBuilder bulkRequest = esClient.prepareBulk();
      for (RecordImpl record : zeebeRecords) {
        switch (record.getMetadata().getValueType()) {
        case WORKFLOW_INSTANCE:
          listViewZeebeRecordProcessor.processWorkflowInstanceRecord(record, bulkRequest);
          activityInstanceZeebeRecordProcessor.processWorkflowInstanceRecord(record, bulkRequest);
          eventZeebeRecordProcessor.processWorkflowInstanceRecord(record, bulkRequest);
          break;
        case INCIDENT:
          listViewZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
          activityInstanceZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
          incidentZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
          eventZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
          break;
        case VARIABLE:
          listViewZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
          variableZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
          break;
        case DEPLOYMENT:
          workflowZeebeRecordProcessor.processDeploymentRecord(record, bulkRequest);
          break;
        case JOB:
          eventZeebeRecordProcessor.processJobRecord(record, bulkRequest);
          break;
        }
      }
      ElasticsearchUtil.processBulkRequest(bulkRequest, true);

  }

}
