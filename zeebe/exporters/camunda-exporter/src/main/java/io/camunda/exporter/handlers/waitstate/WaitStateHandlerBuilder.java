/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import java.util.HashSet;
import java.util.Set;

/**
 * Builds matched pairs of {@link WaitStateAddHandler} and {@link WaitStateRemoveHandler} from a set
 * of {@link WaitStateTransformer}s, so that handler registration stays concise and DRY.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * WaitStateHandlerBuilder.of(indexName, objectMapper)
 *     .addTransformer(new JobBasedWaitStateTransformer())
 *     .build();
 * }</pre>
 */
public final class WaitStateHandlerBuilder {

  private final String indexName;
  private final ObjectMapper objectMapper;
  private final Set<ExportHandler<WaitStateEntity, ?>> handlers = new HashSet<>();

  private WaitStateHandlerBuilder(final String indexName, final ObjectMapper objectMapper) {
    this.indexName = indexName;
    this.objectMapper = objectMapper;
  }

  public static WaitStateHandlerBuilder of(
      final String indexName, final ObjectMapper objectMapper) {
    return new WaitStateHandlerBuilder(indexName, objectMapper);
  }

  public <R extends RecordValue & WaitStateRelated> WaitStateHandlerBuilder addTransformer(
      final WaitStateTransformer<R> transformer) {
    handlers.add(new WaitStateAddHandler<>(indexName, transformer, objectMapper));
    handlers.add(new WaitStateRemoveHandler<>(indexName, transformer));
    return this;
  }

  public Set<ExportHandler<WaitStateEntity, ?>> build() {
    return new HashSet<>(handlers);
  }
}
