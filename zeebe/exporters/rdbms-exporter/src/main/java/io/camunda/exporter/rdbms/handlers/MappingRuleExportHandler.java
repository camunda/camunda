/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.zeebe.protocol.record.intent.MappingRuleIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.MappingRuleIntent.DELETED;
import static io.camunda.zeebe.protocol.record.intent.MappingRuleIntent.UPDATED;

import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel.MappingRuleDbModelBuilder;
import io.camunda.db.rdbms.write.service.MappingRuleWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingRuleExportHandler implements RdbmsExportHandler<MappingRuleRecordValue> {
  private static final Logger LOGGER = LoggerFactory.getLogger(MappingRuleExportHandler.class);

  private static final Set<Intent> MAPPING_INTENT = Set.of(CREATED, DELETED, UPDATED);

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
    final var intent = record.getIntent();
    switch (intent) {
      case CREATED -> mappingRuleWriter.create(map(record));
      case DELETED -> mappingRuleWriter.delete(record.getValue().getMappingRuleId());
      case UPDATED -> mappingRuleWriter.update(map(record));
      default ->
          LOGGER.trace(
              "Skip exporting {} for mapping rule, no known writing operation for it", intent);
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
