/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.operate.zeebeimport.processors.fni;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.zeebeimport.cache.FNITreePathCacheCompositeKey;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.util.Set;
import java.util.function.Function;

/**
 * Transfomer to transform a given Zeebe flow node instance record to a {@link
 * FlowNodeInstanceEntity}.
 */
public class FNITransformer {

  private static final Set<String> FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());
  private static final Set<String> START_STATES = Set.of(ELEMENT_ACTIVATING.name());
  private final Function<FNITreePathCacheCompositeKey, String> treePathResolver;

  public FNITransformer(final Function<FNITreePathCacheCompositeKey, String> treePathResolver) {
    this.treePathResolver = treePathResolver;
  }

  private static FNITreePathCacheCompositeKey toCompositeKey(
      final Record<?> record, final ProcessInstanceRecordValue recordValue) {
    return new FNITreePathCacheCompositeKey(
        record.getPartitionId(),
        record.getKey(),
        recordValue.getFlowScopeKey(),
        recordValue.getProcessInstanceKey());
  }

  /**
   * Transform the given Zeebe flow node instance record into a {@link FlowNodeInstanceEntity}.
   *
   * <p>If the given entity is not-null the entity will be updated/extended.
   *
   * @param record the Zeebe flow node instance record
   * @param entity the entity which should be updated, if null a new entity is created
   * @return the newly created or updated entity
   */
  public FlowNodeInstanceEntity toFlowNodeInstanceEntity(
      final Record<ProcessInstanceRecordValue> record, FlowNodeInstanceEntity entity) {
    if (entity == null) {
      entity = new FlowNodeInstanceEntity();
    }

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setFlowNodeId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(FlowNodeState.TERMINATED);
      } else {
        entity.setState(FlowNodeState.COMPLETED);
      }
      entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      entity.setState(FlowNodeState.ACTIVE);
      if (START_STATES.contains(intentStr)) {
        entity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
        entity.setPosition(record.getPosition());
      }

      // We resolve the treePath only when necessary, for example when not done earlier
      // and when in an active state, for completed and terminated we expect the treePath
      // already be resolved before so we skip this to reduce the load on our cache / backend
      // storage
      if (entity.getTreePath() == null) {
        final String parentTreePath = treePathResolver.apply(toCompositeKey(record, recordValue));
        entity.setTreePath(
            String.join("/", parentTreePath, ConversionUtils.toStringOrNull(record.getKey())));
        entity.setLevel(parentTreePath.split("/").length);
      }
    }

    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));

    return entity;
  }
}
