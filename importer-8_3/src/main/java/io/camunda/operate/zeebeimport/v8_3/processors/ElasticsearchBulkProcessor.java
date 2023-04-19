/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_3.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.SimpleType;

import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.AbstractImportBatchProcessor;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
  private DecisionZeebeRecordProcessor decisionZeebeRecordProcessor;

  @Autowired
  private DecisionRequirementsZeebeRecordProcessor decisionRequirementsZeebeRecordProcessor;

  @Autowired
  private DecisionEvaluationZeebeRecordProcessor decisionEvaluationZeebeRecordProcessor;

  @Autowired
  private Metrics metrics;

  @Autowired
  private ObjectMapper objectMapper;

  private ObjectMapper localObjectMapper;

  @Override
  protected void processZeebeRecords(ImportBatch importBatch, BulkRequest bulkRequest) throws PersistenceException {
    final List<Record> zeebeRecords = ElasticsearchUtil
        .mapSearchHits(importBatch.getHits(), getLocalObjectMapper(), SimpleType.constructUnsafe(Record.class));

    logger.debug(
        "Writing {} Zeebe records to Elasticsearch, version={}, importValueType={}, partition={}",
        zeebeRecords.size(), getZeebeVersion(), importBatch.getImportValueType(),
        importBatch.getPartitionId());

    ImportValueType importValueType = importBatch.getImportValueType();
    switch (importValueType) {
      case DECISION:
        processDecisionRecords(bulkRequest, zeebeRecords);
        break;
      case DECISION_REQUIREMENTS:
        processDecisionRequirementsRecord(bulkRequest, zeebeRecords);
        break;
      case DECISION_EVALUATION:
        processDecisionEvaluationRecords(bulkRequest, zeebeRecords);
        break;
      case PROCESS_INSTANCE:
        processProcessInstanceRecords(importBatch, bulkRequest, zeebeRecords);
        break;
      case INCIDENT:
        processIncidentRecords(bulkRequest, zeebeRecords);
        break;
      case VARIABLE:
        processVariableRecords(bulkRequest, zeebeRecords);
        break;
      case VARIABLE_DOCUMENT:
        processVariableDocumentRecords(bulkRequest, zeebeRecords);
        break;
      case PROCESS:
        processProcessRecords(bulkRequest, zeebeRecords);
        break;
      case JOB:
        processJobRecords(bulkRequest, zeebeRecords);
        break;
      case PROCESS_MESSAGE_SUBSCRIPTION:
        processProcessMessageSubscription(bulkRequest, zeebeRecords);
        break;
      default:
        logger.debug("Default case triggered for type {}", importValueType);
        break;
    }

    recordRecordImportTime(zeebeRecords);
  }

  private void recordRecordImportTime(final List<Record> zeebeRecords) {
    final var currentTime = OffsetDateTime.now().toInstant().toEpochMilli();
    zeebeRecords.forEach(record -> metrics.getTimer(
      Metrics.TIMER_NAME_IMPORT_TIME,
      Metrics.TAG_KEY_TYPE, record.getValueType().name(),
      Metrics.TAG_KEY_PARTITION, String.valueOf(record.getPartitionId())
    ).record(currentTime - record.getTimestamp(), TimeUnit.MILLISECONDS));
  }

  private void processProcessMessageSubscription(final BulkRequest bulkRequest,
      final List<Record> zeebeRecords) throws PersistenceException {
    // per flow node instance
    Map<Long, List<Record<ProcessMessageSubscriptionRecordValue>>> groupedRecordsPerFlowNodeInst
        = zeebeRecords.stream().map(obj -> (Record<ProcessMessageSubscriptionRecordValue>)obj)
        .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    eventZeebeRecordProcessor.processProcessMessageSubscription(groupedRecordsPerFlowNodeInst, bulkRequest);
  }

  private ObjectMapper getLocalObjectMapper() {
    if (localObjectMapper == null) {
      localObjectMapper = objectMapper.copy().registerModule(new ZeebeProtocolModule());
    }
    return localObjectMapper;
  }

  private void processDecisionRecords(final BulkRequest bulkRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      // deployment records can be processed one by one
      decisionZeebeRecordProcessor.processDecisionRecord(record, bulkRequest);
    }
  }

  private void processDecisionRequirementsRecord(final BulkRequest bulkRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      // deployment records can be processed one by one
      decisionRequirementsZeebeRecordProcessor.processDecisionRequirementsRecord(record, bulkRequest);
    }
  }

  private void processDecisionEvaluationRecords(final BulkRequest bulkRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      decisionEvaluationZeebeRecordProcessor.processDecisionEvaluationRecord(record, bulkRequest);
    }
  }

  private void processJobRecords(final BulkRequest bulkRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    // per activity
    Map<Long, List<Record<JobRecordValue>>> groupedJobRecordsPerActivityInst = zeebeRecords.stream().map(obj -> (Record<JobRecordValue>)obj)
        .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    eventZeebeRecordProcessor.processJobRecords(groupedJobRecordsPerActivityInst, bulkRequest);
  }

  private void processProcessRecords(final BulkRequest bulkRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      // deployment records can be processed one by one
      processZeebeRecordProcessor.processDeploymentRecord(record, bulkRequest);
    }
  }

  private void processVariableDocumentRecords(final BulkRequest bulkRequest,
      final List<Record> zeebeRecords) throws PersistenceException {
    for (Record record : zeebeRecords) {
      operationZeebeRecordProcessor.processVariableDocumentRecords(record, bulkRequest);
    }
  }

  private void processVariableRecords(final BulkRequest bulkRequest,
      final List<Record> zeebeRecords) throws PersistenceException {
    // old style
    for (Record record : zeebeRecords) {
      listViewZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
      variableZeebeRecordProcessor.processVariableRecord(record, bulkRequest);
    }
  }

  private void processIncidentRecords(final BulkRequest bulkRequest,
      final List<Record> zeebeRecords) throws PersistenceException {
    // old style
    incidentZeebeRecordProcessor.processIncidentRecord(zeebeRecords, bulkRequest);
    for (Record record : zeebeRecords) {
      listViewZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
      flowNodeInstanceZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
    }
    Map<Long, List<Record<IncidentRecordValue>>> groupedIncidentRecordsPerActivityInst = zeebeRecords
        .stream()
        .map(obj -> (Record<IncidentRecordValue>) obj).collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    eventZeebeRecordProcessor.processIncidentRecords(groupedIncidentRecordsPerActivityInst,
        bulkRequest);
  }

  private void processProcessInstanceRecords(final ImportBatch importBatch,
      final BulkRequest bulkRequest, final List<Record> zeebeRecords) throws PersistenceException {
    Map<Long, List<Record<ProcessInstanceRecordValue>>> groupedWIRecords = zeebeRecords
        .stream()
        .map(obj -> (Record<ProcessInstanceRecordValue>) obj).collect(LinkedHashMap::new,
            (map, item) -> CollectionUtil
                .addToMap(map, item.getValue().getProcessInstanceKey(), item),
            Map::putAll);
    listViewZeebeRecordProcessor.processProcessInstanceRecord(groupedWIRecords, bulkRequest,
        importBatch);
    Map<Long, List<Record<ProcessInstanceRecordValue>>> groupedWIRecordsPerActivityInst = zeebeRecords
        .stream()
        .map(obj -> (Record<ProcessInstanceRecordValue>) obj).collect(Collectors.groupingBy(obj -> obj.getKey()));
    List<Long> flowNodeInstanceKeysOrdered = zeebeRecords.stream()
        .map(Record::getKey)
        .distinct()
        .collect(Collectors.toList());
    flowNodeInstanceZeebeRecordProcessor.processProcessInstanceRecord(groupedWIRecordsPerActivityInst, flowNodeInstanceKeysOrdered,
        bulkRequest);
    eventZeebeRecordProcessor.processProcessInstanceRecords(groupedWIRecordsPerActivityInst,
        bulkRequest);
    for (Record record : zeebeRecords) {
      sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord(record, bulkRequest);
    }
  }

  @Override
  public String getZeebeVersion() {
    return "8.3";
  }
}
