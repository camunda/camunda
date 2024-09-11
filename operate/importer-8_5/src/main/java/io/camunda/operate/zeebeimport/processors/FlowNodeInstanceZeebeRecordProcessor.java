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
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.operate.Metrics;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.cache.FlowNodeInstanceTreePathCache;
import io.camunda.operate.zeebeimport.cache.TreePathCacheMetricsImpl;
import io.camunda.operate.zeebeimport.processors.fni.FNITransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceZeebeRecordProcessor {

  public static final Set<String> AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());
  private static final Logger LOGGER =
      LoggerFactory.getLogger(FlowNodeInstanceZeebeRecordProcessor.class);
  private static final Set<String> AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());

  private final FlowNodeStore flowNodeStore;
  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  private final FNITransformer fniTransformer;

  public FlowNodeInstanceZeebeRecordProcessor(
      final FlowNodeStore flowNodeStore,
      final FlowNodeInstanceTemplate flowNodeInstanceTemplate,
      final OperateProperties operateProperties,
      final PartitionHolder partitionHolder,
      final Metrics metrics) {
    this.flowNodeStore = flowNodeStore;
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
    final var flowNodeTreeCacheSize = operateProperties.getImporter().getFlowNodeTreeCacheSize();
    final var partitionIds = partitionHolder.getPartitionIds();
    // treePath by flowNodeInstanceKey caches
    final FlowNodeInstanceTreePathCache treePathCache =
        new FlowNodeInstanceTreePathCache(
            partitionIds,
            flowNodeTreeCacheSize,
            flowNodeStore::findParentTreePathFor,
            new TreePathCacheMetricsImpl(partitionIds, metrics));
    fniTransformer = new FNITransformer(treePathCache::resolveTreePath);
  }

  public void processIncidentRecord(final Record record, final BatchRequest batchRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final IncidentRecordValue recordValue = (IncidentRecordValue) record.getValue();

    // update activity instance
    final FlowNodeInstanceEntity entity =
        new FlowNodeInstanceEntity()
            .setId(ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()))
            .setKey(recordValue.getElementInstanceKey())
            .setPartitionId(record.getPartitionId())
            .setFlowNodeId(recordValue.getElementId())
            .setProcessInstanceKey(recordValue.getProcessInstanceKey())
            .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
            .setBpmnProcessId(recordValue.getBpmnProcessId())
            .setTenantId(tenantOrDefault(recordValue.getTenantId()));
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setIncidentKey(record.getKey());
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setIncidentKey(null);
    }

    LOGGER.debug("Flow node instance: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(FlowNodeInstanceTemplate.INCIDENT_KEY, entity.getIncidentKey());
    batchRequest.upsert(
        flowNodeInstanceTemplate.getFullQualifiedName(), entity.getId(), entity, updateFields);
  }

  public void processProcessInstanceRecord(
      final Map<Long, List<Record<ProcessInstanceRecordValue>>> records,
      final List<Long> flowNodeInstanceKeysOrdered,
      final BatchRequest batchRequest)
      throws PersistenceException {

    for (final Long key : flowNodeInstanceKeysOrdered) {
      final List<Record<ProcessInstanceRecordValue>> wiRecords = records.get(key);
      FlowNodeInstanceEntity fniEntity = null;
      for (final Record<ProcessInstanceRecordValue> record : wiRecords) {

        if (shouldProcessProcessInstanceRecord(record)) {
          fniEntity = fniTransformer.toFlowNodeInstanceEntity(record, fniEntity);
        }
      }
      if (fniEntity != null) {
        LOGGER.debug("Flow node instance: id {}", fniEntity.getId());
        if (canOptimizeFlowNodeInstanceIndexing(fniEntity)) {
          batchRequest.add(flowNodeInstanceTemplate.getFullQualifiedName(), fniEntity);
        } else {
          final Map<String, Object> updateFields = new HashMap<>();
          updateFields.put(FlowNodeInstanceTemplate.ID, fniEntity.getId());
          updateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, fniEntity.getPartitionId());
          updateFields.put(FlowNodeInstanceTemplate.TYPE, fniEntity.getType());
          updateFields.put(FlowNodeInstanceTemplate.STATE, fniEntity.getState());
          updateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, fniEntity.getFlowNodeId());
          updateFields.put(
              FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY, fniEntity.getProcessDefinitionKey());
          updateFields.put(FlowNodeInstanceTemplate.BPMN_PROCESS_ID, fniEntity.getBpmnProcessId());

          if (fniEntity.getTreePath() != null) {
            updateFields.put(FlowNodeInstanceTemplate.TREE_PATH, fniEntity.getTreePath());
            updateFields.put(FlowNodeInstanceTemplate.LEVEL, fniEntity.getLevel());
          }
          if (fniEntity.getStartDate() != null) {
            updateFields.put(FlowNodeInstanceTemplate.START_DATE, fniEntity.getStartDate());
          }
          if (fniEntity.getEndDate() != null) {
            updateFields.put(FlowNodeInstanceTemplate.END_DATE, fniEntity.getEndDate());
          }
          if (fniEntity.getPosition() != null) {
            updateFields.put(FlowNodeInstanceTemplate.POSITION, fniEntity.getPosition());
          }
          batchRequest.upsert(
              flowNodeInstanceTemplate.getFullQualifiedName(),
              fniEntity.getId(),
              fniEntity,
              updateFields);
        }
      }
    }
  }

  private boolean shouldProcessProcessInstanceRecord(
      final Record<ProcessInstanceRecordValue> processInstanceRecord) {
    final var processInstanceRecordValue = processInstanceRecord.getValue();
    final var intent = processInstanceRecord.getIntent().name();
    return !isProcessEvent(processInstanceRecordValue)
        && (AI_START_STATES.contains(intent)
            || AI_FINISH_STATES.contains(intent)
            || ELEMENT_MIGRATED.name().equals(intent));
  }

  private boolean canOptimizeFlowNodeInstanceIndexing(final FlowNodeInstanceEntity entity) {
    final var startDate = entity.getStartDate();
    final var endDate = entity.getEndDate();

    if (startDate != null && endDate != null) {
      // When the activating and completed/terminated events
      // for a flow node instance is part of the same batch
      // to import, then we can try to optimize the request
      // by submitting an IndexRequest instead of a UpdateRequest.
      // In such case, the following is assumed:
      // * When the duration between start and end time is lower than
      //   (or equal to) 2 seconds, then it can "safely" be assumed
      //   that there was no incident in between.
      // * The 2s duration is chosen arbitrarily. However, it should
      //   not be too short but not too long to avoid any negative
      //   side effects with incidents.
      final var duration = Duration.between(startDate, endDate);
      return duration.getSeconds() <= 2L;
    }

    return false;
  }

  private boolean isProcessEvent(final ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(
      final ProcessInstanceRecordValue recordValue, final BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }
}
