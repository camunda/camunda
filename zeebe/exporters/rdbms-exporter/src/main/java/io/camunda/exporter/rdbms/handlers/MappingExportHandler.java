/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.MappingDbModel;
import io.camunda.db.rdbms.write.domain.MappingDbModel.MappingDbModelBuilder;
import io.camunda.db.rdbms.write.service.MappingWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import java.util.Set;

public class MappingExportHandler implements RdbmsExportHandler<MappingRecordValue> {

  private static final Set<Intent> MAPPING_INTENT =
      Set.of(MappingIntent.CREATED, MappingIntent.DELETED);

  private final MappingWriter mappingWriter;

  public MappingExportHandler(final MappingWriter mappingWriter) {
    this.mappingWriter = mappingWriter;
  }

  @Override
  public boolean canExport(final Record<MappingRecordValue> record) {
    return MAPPING_INTENT.contains(record.getIntent());
  }

  @Override
  public void export(final Record<MappingRecordValue> record) {
    if (record.getIntent().equals(MappingIntent.CREATED)) {
      mappingWriter.create(map(record));
    } else if (record.getIntent().equals(MappingIntent.DELETED)) {
      mappingWriter.delete(record.getValue().getMappingKey());
    }
  }

  private MappingDbModel map(final Record<MappingRecordValue> record) {
    final var value = record.getValue();
    return new MappingDbModelBuilder()
        .id(value.getId())
        .mappingKey(value.getMappingKey())
        .claimName(value.getClaimName())
        .claimValue(value.getClaimValue())
        .name(value.getName())
        .build();
  }
}
