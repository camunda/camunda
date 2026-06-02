/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.waitstate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.domain.WaitStateDbModel;
import io.camunda.db.rdbms.write.service.WaitStateWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateDetails;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import io.camunda.zeebe.util.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inserts a {@link WaitStateDbModel} when a process element enters a waiting state. The row is
 * keyed by the stable entity key (e.g. jobKey, userTaskKey).
 *
 * @param <R> the record value type handled by the injected transformer
 */
public class WaitStateAddHandler<R extends RecordValue & WaitStateRelated>
    implements RdbmsExportHandler<R> {

  private static final Logger LOG = LoggerFactory.getLogger(WaitStateAddHandler.class);

  private final WaitStateWriter waitStateWriter;
  private final WaitStateTransformer<R> transformer;
  private final ObjectMapper objectMapper;

  public WaitStateAddHandler(
      final WaitStateWriter waitStateWriter,
      final WaitStateTransformer<R> transformer,
      final ObjectMapper objectMapper) {
    this.waitStateWriter = waitStateWriter;
    this.transformer = transformer;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean canExport(final Record<R> record) {
    return transformer.triggersAdd(record);
  }

  @Override
  public void export(final Record<R> record) {
    final var entry = transformer.transform(record);
    waitStateWriter.create(map(record, entry));
  }

  @VisibleForTesting
  public WaitStateTransformer<R> getTransformer() {
    return transformer;
  }

  private WaitStateDbModel map(final Record<R> record, final WaitStateEntry entry) {
    return new WaitStateDbModel.Builder()
        .waitStateKey(record.getKey())
        .rootProcessInstanceKey(entry.getRootProcessInstanceKey())
        .processInstanceKey(entry.getProcessInstanceKey())
        .elementInstanceKey(entry.getElementInstanceKey())
        .elementId(entry.getElementId())
        .elementType(entry.getElementType() != null ? entry.getElementType().name() : null)
        .waitStateType(entry.getWaitStateType() != null ? entry.getWaitStateType().name() : null)
        .details(serializeDetails(entry.getDetails()))
        .tenantId(entry.getTenantId())
        .partitionId(record.getPartitionId())
        .build();
  }

  private String serializeDetails(final WaitStateDetails details) {
    if (details == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(details);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize wait state details: {}", e.getMessage(), e);
      return null;
    }
  }
}
