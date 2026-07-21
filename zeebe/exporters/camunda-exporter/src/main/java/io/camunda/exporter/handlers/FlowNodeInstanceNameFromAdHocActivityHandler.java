/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;

/**
 * Names the inner instance of an ad-hoc subprocess after the element that was initially activated
 * inside it (its entry element).
 *
 * <p>Activating an element inside an ad-hoc subprocess creates a synthetic <em>inner instance</em>:
 * a scope that owns the activated element together with everything reachable from it (e.g. the
 * elements targeted by its outgoing sequence flows), so variables and state persist across those
 * elements for the lifetime of the activation. That inner instance has no name of its own — its
 * element id is the synthetic {@code <ahspId>#innerInstance}, which is not present in the deployed
 * BPMN — so in the instance history it surfaces with a generic label.
 *
 * <p>This handler reacts to the <em>entry child's</em> {@code ELEMENT_ACTIVATING} record and writes
 * the child's resolved name onto the parent inner-instance document, so the inner instance reads as
 * the element that was activated (e.g. {@code listUsers} → "List users").
 *
 * <p>NOTE: no-op stub; the naming behaviour is implemented in the green phase.
 */
public class FlowNodeInstanceNameFromAdHocActivityHandler
    implements ExportHandler<FlowNodeInstanceEntity, ProcessInstanceRecordValue> {

  /**
   * Painless script that sets {@code flowNodeName} only when it is not already set, so that the
   * first-activated child (the entry element) wins and later activations in the same inner instance
   * cannot clobber the name.
   */
  public static final String SET_IF_NULL_NAME_SCRIPT =
      """
      if (ctx._source.flowNodeName == null) {
          ctx._source.flowNodeName = params.flowNodeName;
      }
      """;

  private final String indexName;
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;

  public FlowNodeInstanceNameFromAdHocActivityHandler(
      final String indexName, final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.indexName = indexName;
    this.processCache = processCache;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityType() {
    return FlowNodeInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    // stub
    return false;
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    // stub
    return List.of();
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(final String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final FlowNodeInstanceEntity entity) {
    // stub
  }

  @Override
  public void flush(final FlowNodeInstanceEntity entity, final BatchRequest batchRequest) {
    // stub
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
