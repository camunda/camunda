/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel.MappingRuleDbModelBuilder;
import io.camunda.db.rdbms.write.service.MappingRuleWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import java.util.Set;

public class MappingRuleExportHandler implements RdbmsExportHandler<MappingRuleRecordValue> {

  private static final Set<Intent> MAPPING_INTENT =
      Set.of(MappingRuleIntent.CREATED, MappingRuleIntent.DELETED);

  private final MappingRuleWriter mappingRuleWriter;

  public MappingRuleExportHandler(final MappingRuleWriter mappingRuleWriter) {
    this.mappingRuleWriter = mappingRuleWriter;
  }

  @Override
  public boolean canExport(final Record<MappingRuleRecordValue> record) {
    return MAPPING_INTENT.contains(record.getIntent());
  }

  @Override
  public void export(final Record<MappingRuleRecordValue> record) {
    if (record.getIntent().equals(MappingRuleIntent.CREATED)) {
      mappingRuleWriter.create(map(record));
    } else if (record.getIntent().equals(MappingRuleIntent.DELETED)) {
      mappingRuleWriter.delete(record.getValue().getMappingRuleId());
    }
  }

  private MappingRuleDbModel map(final Record<MappingRuleRecordValue> record) {
    final var value = record.getValue();
    return new MappingRuleDbModelBuilder()
        .mappingRuleId(value.getMappingRuleId())
        .mappingRuleKey(value.getMappingRuleKey())
        .claimName(value.getClaimName())
        .claimValue(value.getClaimValue())
        .name(value.getName())
        .build();
  }
}
