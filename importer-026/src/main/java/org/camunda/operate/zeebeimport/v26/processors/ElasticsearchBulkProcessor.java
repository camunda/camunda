/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v26.processors;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebe.ImportValueType;
import org.camunda.operate.zeebeimport.v26.record.value.DeploymentRecordValueImpl;
import org.camunda.operate.zeebeimport.v26.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.v26.record.value.VariableDocumentRecordImpl;
import org.camunda.operate.zeebeimport.v26.record.value.VariableRecordValueImpl;
import org.camunda.operate.zeebeimport.AbstractImportBatchProcessor;
import org.camunda.operate.zeebeimport.ImportBatch;
import org.camunda.operate.zeebeimport.v26.record.RecordImpl;
import org.camunda.operate.zeebeimport.v26.record.value.JobRecordValueImpl;
import org.camunda.operate.zeebeimport.v26.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValue;

@Component
public class ElasticsearchBulkProcessor extends AbstractImportBatchProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;

  @Autowired
  private ActivityInstanceZeebeRecordProcessor activityInstanceZeebeRecordProcessor;

  @Autowired(required = false)
  private FlowNodeInstanceZeebeRecordProcessor flowNodeInstanceZeebeRecordProcessor;

  @Autowired
  private VariableZeebeRecordProcessor variableZeebeRecordProcessor;

  @Autowired
  private OperationZeebeRecordProcessor operationZeebeRecordProcessor;

  @Autowired
  private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;

  @Autowired
  private WorkflowZeebeRecordProcessor workflowZeebeRecordProcessor;

  @Autowired
  private EventZeebeRecordProcessor eventZeebeRecordProcessor;

  @Autowired
  private SequenceFlowZeebeRecordProcessor sequenceFlowZeebeRecordProcessor;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  protected void processZeebeRecords(ImportBatch importBatch, BulkRequest bulkRequest) throws PersistenceException {

    JavaType valueType = objectMapper.getTypeFactory().constructParametricType(RecordImpl.class, getRecordValueClass(importBatch.getImportValueType()));
    final List<Record> zeebeRecords = ElasticsearchUtil.mapSearchHits(importBatch.getHits(), objectMapper, valueType);

    ImportValueType importValueType = importBatch.getImportValueType();

    logger.debug("Writing [{}] Zeebe records to Elasticsearch", zeebeRecords.size());

    switch (importValueType) {
    case WORKFLOW_INSTANCE:
      Map<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> groupedWIRecords = zeebeRecords.stream()
          .map(obj -> (RecordImpl<WorkflowInstanceRecordValueImpl>) obj).collect(Collectors.groupingBy(obj -> obj.getValue().getWorkflowInstanceKey()));
      listViewZeebeRecordProcessor.processWorkflowInstanceRecord(groupedWIRecords, bulkRequest, importBatch);
      Map<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> groupedWIRecordsPerActivityInst = zeebeRecords.stream()
          .map(obj -> (RecordImpl<WorkflowInstanceRecordValueImpl>) obj).collect(Collectors.groupingBy(obj -> obj.getKey()));
      activityInstanceZeebeRecordProcessor.processWorkflowInstanceRecord(groupedWIRecordsPerActivityInst, bulkRequest);
      List<Long> flowNodeInstanceKeysOrdered = zeebeRecords.stream()
          .map(Record::getKey)
          .collect(Collectors.toList());
      if (flowNodeInstanceZeebeRecordProcessor != null) {
        flowNodeInstanceZeebeRecordProcessor
            .processWorkflowInstanceRecord(groupedWIRecordsPerActivityInst,
                flowNodeInstanceKeysOrdered, bulkRequest);
      }
      eventZeebeRecordProcessor.processWorkflowInstanceRecords(groupedWIRecordsPerActivityInst, bulkRequest);
      for (Record record : zeebeRecords) {
        sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord(record, bulkRequest);
      }
      break;
    case INCIDENT:
      // old style
      for (Record record : zeebeRecords) {
        listViewZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
        activityInstanceZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
        if (flowNodeInstanceZeebeRecordProcessor != null) {
          flowNodeInstanceZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
        }
        incidentZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
      }
      Map<Long, List<RecordImpl<IncidentRecordValueImpl>>> groupedIncidentRecordsPerActivityInst = zeebeRecords.stream()
          .map(obj -> (RecordImpl<IncidentRecordValueImpl>) obj).collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
      eventZeebeRecordProcessor.processIncidentRecords(groupedIncidentRecordsPerActivityInst, bulkRequest);
      break;
    case VARIABLE:
      // old style
      for (Record record : zeebeRecords) {
        listViewZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
        variableZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
      }
      break;
    case VARIABLE_DOCUMENT:
      for (Record record : zeebeRecords) {
        operationZeebeRecordProcessor.processVariableDocumentRecords(record, bulkRequest);
      }
      break;
    case DEPLOYMENT:
      for (Record record : zeebeRecords) {
        // deployment records can be processed one by one
        workflowZeebeRecordProcessor.processDeploymentRecord(record, bulkRequest);
      }
      break;
    case JOB:
      // per activity
      Map<Long, List<RecordImpl<JobRecordValueImpl>>> groupedJobRecordsPerActivityInst = zeebeRecords.stream().map(obj -> (RecordImpl<JobRecordValueImpl>) obj)
          .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
      eventZeebeRecordProcessor.processJobRecords(groupedJobRecordsPerActivityInst, bulkRequest);
      break;
    default:
      logger.debug("Default case triggered for type {}", importValueType);
      break;
    }

  }

  protected Class<? extends RecordValue> getRecordValueClass(ImportValueType importValueType) {
    switch (importValueType) {
    case DEPLOYMENT:
      return DeploymentRecordValueImpl.class;
    case WORKFLOW_INSTANCE:
      return WorkflowInstanceRecordValueImpl.class;
    case JOB:
      return JobRecordValueImpl.class;
    case INCIDENT:
      return IncidentRecordValueImpl.class;
    case VARIABLE:
      return VariableRecordValueImpl.class;
    case VARIABLE_DOCUMENT:
      return VariableDocumentRecordImpl.class;
    default:
      throw new OperateRuntimeException(String.format("No value type class found for: %s", importValueType));
    }
  }

  @Override
  public String getZeebeVersion() {
    return "0.26";
  }
}
