/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_0.processors;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.v1_0.record.value.IncidentRecordValueImpl;
import io.camunda.operate.zeebeimport.v1_0.record.value.VariableDocumentRecordImpl;
import io.camunda.operate.zeebeimport.v1_0.record.value.VariableRecordValueImpl;
import io.camunda.operate.zeebeimport.AbstractImportBatchProcessor;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.v1_0.record.RecordImpl;
import io.camunda.operate.zeebeimport.v1_0.record.value.JobRecordValueImpl;
import io.camunda.operate.zeebeimport.v1_0.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.operate.zeebeimport.v1_0.record.value.deployment.DeployedProcessImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;

@Component
public class ElasticsearchBulkProcessor extends AbstractImportBatchProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;

  @Autowired
  private FlowNodeInstanceZeebeRecordProcessor flowNodeInstanceZeebeRecordProcessor;

  @Autowired
  private VariableZeebeRecordProcessor variableZeebeRecordProcessor;

  @Autowired
  private OperationZeebeRecordProcessor operationZeebeRecordProcessor;

  @Autowired
  private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;

  @Autowired
  private ProcessZeebeRecordProcessor processZeebeRecordProcessor;

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

    logger.debug("Writing {} Zeebe records to Elasticsearch, version={}, importValueType={}, partition={}", zeebeRecords.size(), "0.26", importBatch.getImportValueType(), importBatch.getPartitionId());

    switch (importValueType) {
    case PROCESS_INSTANCE:
      Map<Long, List<RecordImpl<ProcessInstanceRecordValueImpl>>> groupedWIRecords = zeebeRecords.stream()
          .map(obj -> (RecordImpl<ProcessInstanceRecordValueImpl>) obj).collect(Collectors.groupingBy(obj -> obj.getValue().getProcessInstanceKey()));
      listViewZeebeRecordProcessor.processProcessInstanceRecord(groupedWIRecords, bulkRequest, importBatch);
      Map<Long, List<RecordImpl<ProcessInstanceRecordValueImpl>>> groupedWIRecordsPerActivityInst = zeebeRecords.stream()
          .map(obj -> (RecordImpl<ProcessInstanceRecordValueImpl>) obj).collect(Collectors.groupingBy(obj -> obj.getKey()));
      List<Long> flowNodeInstanceKeysOrdered = zeebeRecords.stream()
          .map(Record::getKey)
          .collect(Collectors.toList());
      flowNodeInstanceZeebeRecordProcessor.processProcessInstanceRecord(groupedWIRecordsPerActivityInst, flowNodeInstanceKeysOrdered, bulkRequest);
      eventZeebeRecordProcessor.processProcessInstanceRecords(groupedWIRecordsPerActivityInst, bulkRequest);
      for (Record record : zeebeRecords) {
        sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord(record, bulkRequest);
      }
      break;
    case INCIDENT:
      // old style
      for (Record record : zeebeRecords) {
        listViewZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
        flowNodeInstanceZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
      }
      incidentZeebeRecordProcessor.processIncidentRecord(zeebeRecords, bulkRequest);
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
      case PROCESS:
      for (Record record : zeebeRecords) {
        // deployment records can be processed one by one
        processZeebeRecordProcessor.processDeploymentRecord(record, bulkRequest);
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
      case PROCESS:
      return DeployedProcessImpl.class;
    case PROCESS_INSTANCE:
      return ProcessInstanceRecordValueImpl.class;
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
    return "1.0";
  }
}
