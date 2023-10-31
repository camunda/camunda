/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_4.processors;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.SimpleType;
import io.camunda.operate.Metrics;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.AbstractImportBatchProcessor;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.camunda.operate.util.CollectionUtil.map;

@Component
public class ImportBulkProcessor extends AbstractImportBatchProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ImportBulkProcessor.class);

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

  private static <T> T fromSearchHit(String searchHitString, ObjectMapper objectMapper, JavaType valueType) {
    T entity;
    try {
      entity = objectMapper.readValue(searchHitString, valueType);
    } catch (IOException e) {
      logger.error(String.format("Error while reading entity of type %s from indices!", valueType.toString()), e);
      throw new OperateRuntimeException(String.format("Error while reading entity of type %s from indices!", valueType), e);
    }
    return entity;
  }

  @Override
  protected void processZeebeRecords(ImportBatch importBatch, BatchRequest batchRequest) throws PersistenceException {
    final List<HitEntity> hits = importBatch.getHits();
    final List<Record> zeebeRecords = map(hits, hit ->
        fromSearchHit(hit.getSourceAsString(), getLocalObjectMapper() , SimpleType.constructUnsafe(Record.class))
    );

    logger.debug(
        "Writing {} Zeebe records to indices, version={}, importValueType={}, partition={}",
        zeebeRecords.size(), getZeebeVersion(), importBatch.getImportValueType(),
        importBatch.getPartitionId());

    ImportValueType importValueType = importBatch.getImportValueType();
    switch (importValueType) {
      case DECISION:
        processDecisionRecords(batchRequest, zeebeRecords);
        break;
      case DECISION_REQUIREMENTS:
        processDecisionRequirementsRecord(batchRequest, zeebeRecords);
        break;
      case DECISION_EVALUATION:
        processDecisionEvaluationRecords(batchRequest, zeebeRecords);
        break;
      case PROCESS_INSTANCE:
        processProcessInstanceRecords(importBatch, batchRequest, zeebeRecords);
        break;
      case INCIDENT:
        processIncidentRecords(batchRequest, zeebeRecords);
        break;
      case VARIABLE:
        processVariableRecords(batchRequest, zeebeRecords);
        break;
      case VARIABLE_DOCUMENT:
        processVariableDocumentRecords(batchRequest, zeebeRecords);
        break;
      case PROCESS:
        processProcessRecords(batchRequest, zeebeRecords);
        break;
      case JOB:
        processJobRecords(batchRequest, zeebeRecords);
        break;
      case PROCESS_MESSAGE_SUBSCRIPTION:
        processProcessMessageSubscription(batchRequest, zeebeRecords);
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

  private void processProcessMessageSubscription(final BatchRequest batchRequest,
      final List<Record> zeebeRecords) throws PersistenceException {
    // per flow node instance
    Map<Long, List<Record<ProcessMessageSubscriptionRecordValue>>> groupedRecordsPerFlowNodeInst
        = zeebeRecords.stream().map(obj -> (Record<ProcessMessageSubscriptionRecordValue>)obj)
        .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    eventZeebeRecordProcessor.processProcessMessageSubscription(groupedRecordsPerFlowNodeInst, batchRequest);
  }

  private ObjectMapper getLocalObjectMapper() {
    if (localObjectMapper == null) {
      localObjectMapper = objectMapper.copy().registerModule(new ZeebeProtocolModule());
    }
    return localObjectMapper;
  }

  private void processDecisionRecords(final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      // deployment records can be processed one by one
      decisionZeebeRecordProcessor.processDecisionRecord(record, batchRequest);
    }
  }

  private void processDecisionRequirementsRecord(final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      // deployment records can be processed one by one
      decisionRequirementsZeebeRecordProcessor.processDecisionRequirementsRecord(record, batchRequest);
    }
  }

  private void processDecisionEvaluationRecords(final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      decisionEvaluationZeebeRecordProcessor.processDecisionEvaluationRecord(record, batchRequest);
    }
  }

  private void processJobRecords(final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    // per activity
    Map<Long, List<Record<JobRecordValue>>> groupedJobRecordsPerActivityInst = zeebeRecords.stream().map(obj -> (Record<JobRecordValue>)obj)
        .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    listViewZeebeRecordProcessor.processJobRecords(groupedJobRecordsPerActivityInst, batchRequest);
    eventZeebeRecordProcessor.processJobRecords(groupedJobRecordsPerActivityInst, batchRequest);
  }

  private void processProcessRecords(final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (Record record : zeebeRecords) {
      // deployment records can be processed one by one
      processZeebeRecordProcessor.processDeploymentRecord(record, batchRequest);
    }
  }

  private void processVariableDocumentRecords(final BatchRequest batchRequest,
      final List<Record> zeebeRecords) throws PersistenceException {
    for (Record record : zeebeRecords) {
      operationZeebeRecordProcessor.processVariableDocumentRecords(record, batchRequest);
    }
  }

  private void processVariableRecords(final BatchRequest batchRequest,
      final List<Record> zeebeRecords) throws PersistenceException {

    final var variablesGroupedByScopeKey = zeebeRecords
        .stream()
        .map(obj -> (Record<VariableRecordValue>) obj)
        .collect(Collectors.groupingBy(obj -> obj.getValue().getScopeKey()));

    listViewZeebeRecordProcessor.processVariableRecords(variablesGroupedByScopeKey, batchRequest);
    variableZeebeRecordProcessor.processVariableRecords(variablesGroupedByScopeKey, batchRequest);
  }

  private void processIncidentRecords(final BatchRequest batchRequest,
      final List<Record> zeebeRecords) throws PersistenceException {
    // old style
    incidentZeebeRecordProcessor.processIncidentRecord(zeebeRecords, batchRequest);
    for (Record record : zeebeRecords) {
      listViewZeebeRecordProcessor.processIncidentRecord(record, batchRequest);
      flowNodeInstanceZeebeRecordProcessor.processIncidentRecord(record, batchRequest);
    }
    Map<Long, List<Record<IncidentRecordValue>>> groupedIncidentRecordsPerActivityInst = zeebeRecords
        .stream()
        .map(obj -> (Record<IncidentRecordValue>) obj).collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    eventZeebeRecordProcessor.processIncidentRecords(groupedIncidentRecordsPerActivityInst,
        batchRequest);
  }

  private void processProcessInstanceRecords(final ImportBatch importBatch,
                                             final BatchRequest batchRequest, final List<Record> zeebeRecords) throws PersistenceException {
    Map<Long, List<Record<ProcessInstanceRecordValue>>> groupedWIRecords = zeebeRecords
        .stream()
        .map(obj -> (Record<ProcessInstanceRecordValue>) obj).collect(LinkedHashMap::new,
            (map, item) -> CollectionUtil
                .addToMap(map, item.getValue().getProcessInstanceKey(), item),
            Map::putAll);
    listViewZeebeRecordProcessor.processProcessInstanceRecord(groupedWIRecords, batchRequest,
        importBatch);
    Map<Long, List<Record<ProcessInstanceRecordValue>>> groupedWIRecordsPerActivityInst = zeebeRecords
        .stream()
        .map(obj -> (Record<ProcessInstanceRecordValue>) obj).collect(Collectors.groupingBy(obj -> obj.getKey()));
    List<Long> flowNodeInstanceKeysOrdered = zeebeRecords.stream()
        .map(Record::getKey)
        .distinct()
        .collect(Collectors.toList());
    flowNodeInstanceZeebeRecordProcessor.processProcessInstanceRecord(groupedWIRecordsPerActivityInst, flowNodeInstanceKeysOrdered,
        batchRequest);
    eventZeebeRecordProcessor.processProcessInstanceRecords(groupedWIRecordsPerActivityInst,
        batchRequest);
    for (Record record : zeebeRecords) {
      sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord(record, batchRequest);
    }
  }

  @Override
  public String getZeebeVersion() {
    return "8.4";
  }
}
