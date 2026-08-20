/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.WaitStateRelated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts waiting-state data from a Zeebe record.
 *
 * <p>Implementations declare which record intents trigger an index write or removal via {@link
 * #config()}, and provide the extraction logic in {@link #extract(Record, WaitStateEntry)}.
 *
 * @param <R> the record value type this transformer handles
 */
public interface WaitStateTransformer<R extends RecordValue & WaitStateRelated> {

  Logger LOG = LoggerFactory.getLogger(WaitStateTransformer.class);

  WaitStateTransformerConfig config();

  /**
   * Extracts a {@link WaitStateEntry} from the given record.
   *
   * <p>Called only when {@link #triggersAdd(Record)} or {@link #triggersRemoval(Record)} returns
   * {@code true}.
   */
  void extract(final Record<R> record, final WaitStateEntry entry);

  default WaitStateEntry transform(final Record<R> record) {
    final WaitStateEntry waitStateEntry =
        WaitStateEntry.of(record).setWaitStateType(config().waitStateType());

    try {
      extract(record, waitStateEntry);
    } catch (final Exception e) {
      LOG.error(
          "Error extracting wait state entity for record with key {}: {}",
          record.getKey(),
          e.getMessage(),
          e);
    }

    return waitStateEntry;
  }

  default boolean supports(final Record<R> record) {
    return config().supports(record);
  }

  default boolean triggersAdd(final Record<R> record) {
    return config().triggersAdd(record);
  }

  default boolean triggersUpdate(final Record<R> record) {
    return config().triggersUpdate(record);
  }

  default boolean triggersRemoval(final Record<R> record) {
    return config().triggersRemoval(record);
  }
}
