/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.List;

/**
 * This is a special tagging interface used in cases where we have a storage ordinal related record,
 * but we expect the handler to only receive records with NOT_ORDINAL_CONTROLLED set.
 *
 * <p>e.g. this can happen with MessageStartEventSubscriptionRecordValue records as they are
 * sometimes process instance related and sometimes are "global" (trigger a new PI rather than being
 * part of a pre-existing PI)
 *
 * <p>These records will end up being routed to the "main" index.
 */
public interface NotOrdinalControlledIndexExportHandler<
        T extends ExporterEntity<T>, R extends RecordValue & StorageOrdinalRelated>
    extends OrdinalIndexExportHandler<T, R> {

  @Override
  default List<IdAndIndex> extractIdAndIndexes(
      final TargetIndexLocator indexLocator, final Record<R> record) {
    // just add some extra checking to ensure it's definitely not ordinal controlled
    if (record.getValue().getStorageOrdinal() != StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED) {
      throw new IllegalStateException(
          "Received record with key: "
              + record.getKey()
              + " on partition "
              + record.getPartitionId()
              + " that should not be ordinal controlled. Storage ordinal was: "
              + record.getValue().getStorageOrdinal());
    }
    return OrdinalIndexExportHandler.super.extractIdAndIndexes(indexLocator, record);
  }
}
