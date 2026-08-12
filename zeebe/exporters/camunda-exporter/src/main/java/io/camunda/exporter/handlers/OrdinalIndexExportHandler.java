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
import io.camunda.zeebe.protocol.record.value.StorageOrdinalKeyRelated;
import java.util.List;

public interface OrdinalIndexExportHandler<
        T extends ExporterEntity<T>, R extends RecordValue & StorageOrdinalKeyRelated>
    extends ExportHandler<T, R> {

  @Override
  default List<IdAndIndex> extractIdAndIndexes(
      final TargetIndexLocator indexLocator, final Record<R> record) {
    final var indexName = getIndexName();
    final var index = indexLocator.locateOrdinalIndex(indexName, record.getValue());
    return generateIds(record).stream().map(id -> new IdAndIndex(id, index)).toList();
  }
}
