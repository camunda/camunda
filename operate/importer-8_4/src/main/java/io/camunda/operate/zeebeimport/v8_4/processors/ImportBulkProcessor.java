/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport.v8_4.processors;

import static io.camunda.operate.util.CollectionUtil.map;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.SimpleType;
import io.camunda.operate.Metrics;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.ImportStore;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.AbstractImportBatchProcessor;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.*;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportBulkProcessor extends AbstractImportBatchProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportBulkProcessor.class);

  @Autowired private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;

  @Autowired private FlowNodeInstanceZeebeRecordProcessor flowNodeInstanceZeebeRecordProcessor;

  @Autowired private VariableZeebeRecordProcessor variableZeebeRecordProcessor;

  @Autowired private OperationZeebeRecordProcessor operationZeebeRecordProcessor;

  @Autowired private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;

  @Autowired private ProcessZeebeRecordProcessor processZeebeRecordProcessor;

  @Autowired private EventZeebeRecordProcessor eventZeebeRecordProcessor;

  @Autowired private SequenceFlowZeebeRecordProcessor sequenceFlowZeebeRecordProcessor;

  @Autowired private DecisionZeebeRecordProcessor decisionZeebeRecordProcessor;

  @Autowired
  private DecisionRequirementsZeebeRecordProcessor decisionRequirementsZeebeRecordProcessor;

  @Autowired private DecisionEvaluationZeebeRecordProcessor decisionEvaluationZeebeRecordProcessor;

  @Autowired private Metrics metrics;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ImportStore importStore;

  private ObjectMapper localObjectMapper;

  private static <T> T fromSearchHit(
      final String searchHitString, final ObjectMapper objectMapper, final JavaType valueType) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, valueType);
    } catch (final IOException e) {
      LOGGER.error(
          String.format(
              "Error while reading entity of type %s from indices!", valueType.toString()),
          e);
      throw new OperateRuntimeException(
          String.format("Error while reading entity of type %s from indices!", valueType), e);
    }
    return entity;
  }

  @Override
  protected void processZeebeRecords(final ImportBatch importBatch, final BatchRequest batchRequest)
      throws PersistenceException {
    final List<HitEntity> hits = importBatch.getHits();
    final List<Record> zeebeRecords =
        map(
            hits,
            hit ->
                fromSearchHit(
                    hit.getSourceAsString(),
                    getLocalObjectMapper(),
                    SimpleType.constructUnsafe(Record.class)));

    LOGGER.debug(
        "Writing {} Zeebe records to indices, version={}, importValueType={}, partition={}",
        zeebeRecords.size(),
        getZeebeVersion(),
        importBatch.getImportValueType(),
        importBatch.getPartitionId());

    final ImportValueType importValueType = importBatch.getImportValueType();
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
        LOGGER.debug("Default case triggered for type {}", importValueType);
        break;
    }

    recordRecordImportTime(zeebeRecords);
  }

  private void recordRecordImportTime(final List<Record> zeebeRecords) {
    final var currentTime = OffsetDateTime.now().toInstant().toEpochMilli();
    zeebeRecords.forEach(
        record ->
            metrics
                .getTimer(
                    Metrics.TIMER_NAME_IMPORT_TIME,
                    Metrics.TAG_KEY_TYPE,
                    record.getValueType().name(),
                    Metrics.TAG_KEY_PARTITION,
                    String.valueOf(record.getPartitionId()))
                .record(currentTime - record.getTimestamp(), TimeUnit.MILLISECONDS));
  }

  private void processProcessMessageSubscription(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    // per flow node instance
    final Map<Long, List<Record<ProcessMessageSubscriptionRecordValue>>>
        groupedRecordsPerFlowNodeInst =
            zeebeRecords.stream()
                .map(obj -> (Record<ProcessMessageSubscriptionRecordValue>) obj)
                .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    eventZeebeRecordProcessor.processProcessMessageSubscription(
        groupedRecordsPerFlowNodeInst, batchRequest);
  }

  private ObjectMapper getLocalObjectMapper() {
    if (localObjectMapper == null) {
      localObjectMapper = objectMapper.copy().registerModule(new ZeebeProtocolModule());
    }
    return localObjectMapper;
  }

  private void processDecisionRecords(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (final Record record : zeebeRecords) {
      // deployment records can be processed one by one
      decisionZeebeRecordProcessor.processDecisionRecord(record, batchRequest);
    }
  }

  private void processDecisionRequirementsRecord(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (final Record record : zeebeRecords) {
      // deployment records can be processed one by one
      decisionRequirementsZeebeRecordProcessor.processDecisionRequirementsRecord(
          record, batchRequest);
    }
  }

  private void processDecisionEvaluationRecords(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (final Record record : zeebeRecords) {
      decisionEvaluationZeebeRecordProcessor.processDecisionEvaluationRecord(record, batchRequest);
    }
  }

  private void processJobRecords(final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    // per activity
    final Map<Long, List<Record<JobRecordValue>>> groupedJobRecordsPerActivityInst =
        zeebeRecords.stream()
            .map(obj -> (Record<JobRecordValue>) obj)
            .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    listViewZeebeRecordProcessor.processJobRecords(groupedJobRecordsPerActivityInst, batchRequest);
    eventZeebeRecordProcessor.processJobRecords(groupedJobRecordsPerActivityInst, batchRequest);
  }

  private void processProcessRecords(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (final Record record : zeebeRecords) {
      // deployment records can be processed one by one
      processZeebeRecordProcessor.processDeploymentRecord(record, batchRequest);
    }
  }

  private void processVariableDocumentRecords(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    for (final Record record : zeebeRecords) {
      operationZeebeRecordProcessor.processVariableDocumentRecords(record, batchRequest);
    }
  }

  private void processVariableRecords(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {

    final var variablesGroupedByScopeKey =
        zeebeRecords.stream()
            .map(obj -> (Record<VariableRecordValue>) obj)
            .collect(Collectors.groupingBy(obj -> obj.getValue().getScopeKey()));

    listViewZeebeRecordProcessor.processVariableRecords(variablesGroupedByScopeKey, batchRequest);
    variableZeebeRecordProcessor.processVariableRecords(variablesGroupedByScopeKey, batchRequest);
  }

  private void processIncidentRecords(
      final BatchRequest batchRequest, final List<Record> zeebeRecords)
      throws PersistenceException {
    // old style
    incidentZeebeRecordProcessor.processIncidentRecord(zeebeRecords, batchRequest);
    for (final Record record : zeebeRecords) {
      listViewZeebeRecordProcessor.processIncidentRecord(record, batchRequest);
      flowNodeInstanceZeebeRecordProcessor.processIncidentRecord(record, batchRequest);
    }
    final Map<Long, List<Record<IncidentRecordValue>>> groupedIncidentRecordsPerActivityInst =
        zeebeRecords.stream()
            .map(obj -> (Record<IncidentRecordValue>) obj)
            .collect(Collectors.groupingBy(obj -> obj.getValue().getElementInstanceKey()));
    eventZeebeRecordProcessor.processIncidentRecords(
        groupedIncidentRecordsPerActivityInst, batchRequest);
  }

  private void processProcessInstanceRecords(
      final ImportBatch importBatch,
      final BatchRequest batchRequest,
      final List<Record> zeebeRecords)
      throws PersistenceException {
    final Map<Long, List<Record<ProcessInstanceRecordValue>>> groupedWIRecords =
        zeebeRecords.stream()
            .map(obj -> (Record<ProcessInstanceRecordValue>) obj)
            .collect(
                LinkedHashMap::new,
                (map, item) ->
                    CollectionUtil.addToMap(map, item.getValue().getProcessInstanceKey(), item),
                Map::putAll);
    listViewZeebeRecordProcessor.processProcessInstanceRecord(
        groupedWIRecords, batchRequest, importBatch);
    final Map<Long, List<Record<ProcessInstanceRecordValue>>> groupedWIRecordsPerActivityInst =
        zeebeRecords.stream()
            .map(obj -> (Record<ProcessInstanceRecordValue>) obj)
            .collect(Collectors.groupingBy(obj -> obj.getKey()));
    final List<Long> flowNodeInstanceKeysOrdered =
        zeebeRecords.stream().map(Record::getKey).distinct().collect(Collectors.toList());
    flowNodeInstanceZeebeRecordProcessor.processProcessInstanceRecord(
        groupedWIRecordsPerActivityInst, flowNodeInstanceKeysOrdered, batchRequest);
    eventZeebeRecordProcessor.processProcessInstanceRecords(
        groupedWIRecordsPerActivityInst, batchRequest);
    for (final Record record : zeebeRecords) {
      sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord(record, batchRequest);
    }
  }

  @Override
  public String getZeebeVersion() {
    return "8.4";
  }
}
