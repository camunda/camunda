/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.cache.FlowNodeInstanceTreePathCache;
import io.camunda.operate.zeebeimport.cache.TreePathCacheMetricsImpl;
import io.camunda.operate.zeebeimport.v8_7.processors.processors.fni.FNITransformer;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
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
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.ConcurrentInitializer;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.apache.commons.lang3.function.FailableSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceZeebeRecordProcessor {

  public static final Set<String> AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());
  private static final Logger LOGGER =
      LoggerFactory.getLogger(FlowNodeInstanceZeebeRecordProcessor.class);
  private static final Set<String> AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());
  private final ConcurrentInitializer<FNITransformer> fniTransformerLazy;
  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public FlowNodeInstanceZeebeRecordProcessor(
      final FlowNodeStore flowNodeStore,
      final @Qualifier("operateFlowNodeInstanceTemplate") FlowNodeInstanceTemplate
              flowNodeInstanceTemplate,
      final OperateProperties operateProperties,
      final PartitionHolder partitionHolder,
      final Metrics metrics) {

    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
    final var flowNodeTreeCacheSize = operateProperties.getImporter().getFlowNodeTreeCacheSize();
    fniTransformerLazy =
        LazyInitializer.<FNITransformer>builder()
            .setInitializer(
                createFNITransformerSupplier(
                    flowNodeStore, partitionHolder, metrics, flowNodeTreeCacheSize))
            .get();
  }

  private static FailableSupplier<FNITransformer, Exception> createFNITransformerSupplier(
      final FlowNodeStore flowNodeStore,
      final PartitionHolder partitionHolder,
      final Metrics metrics,
      final int flowNodeTreeCacheSize) {
    return () -> {
      // We create the FNITransformer lazy, as the partition holder doesn't hold the
      // partitionIds right on start.
      //
      // When first accessed on importing, we can be sure that we can also request
      // the partitions.
      final var partitionIds = partitionHolder.getPartitionIds();

      // treePath by flowNodeInstanceKey caches
      final FlowNodeInstanceTreePathCache treePathCache =
          new FlowNodeInstanceTreePathCache(
              partitionIds,
              flowNodeTreeCacheSize,
              flowNodeStore::findParentTreePathFor,
              new TreePathCacheMetricsImpl(partitionIds, metrics));
      return new FNITransformer(treePathCache);
    };
  }

  private FNITransformer getFNITransformer() {
    try {
      return fniTransformerLazy.get();
    } catch (final ConcurrentException e) {
      // we do not expect any exception as our instance supplier defined is not throwing any
      // exception. If we catch this here there is something weird going on, so passing it on
      throw new RuntimeException(
          "Expected to retrieve FNITransformer without an error, but caught one.", e);
    }
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
          fniEntity = getFNITransformer().toFlowNodeInstanceEntity(record, fniEntity);
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
