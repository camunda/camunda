/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import org.jspecify.annotations.NullMarked;

/**
 * Writes a new {@link WaitStateEntity} to the wait-state index when a process element enters a
 * waiting state for the first time.
 *
 * @param <R> the record value type handled by the injected transformer
 */
@NullMarked
public class WaitStateAddHandler<R extends RecordValue & WaitStateRelated>
    extends AbstractWaitStateHandler<R> {

  public WaitStateAddHandler(
      final String indexName,
      final WaitStateTransformer<R> transformer,
      final ObjectMapper objectMapper) {
    super(indexName, transformer, objectMapper);
  }

  @Override
  public boolean handlesRecord(final Record<R> record) {
    return transformer.triggersAdd(record);
  }

  @Override
  public void flush(final WaitStateEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }
}
