/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db.index;

import static io.camunda.optimize.service.db.DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import org.springframework.stereotype.Component;

@Component
public abstract class UpdateLogEntryIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {
  public static final String INDEX_NAME = "update-log";
  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return UPDATE_LOG_ENTRY_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(UpgradeStepLogEntryDto.Fields.indexName, p -> p.keyword(k -> k))
        .properties(UpgradeStepLogEntryDto.Fields.optimizeVersion, p -> p.keyword(k -> k))
        .properties(UpgradeStepLogEntryDto.Fields.stepType, p -> p.keyword(k -> k))
        .properties(UpgradeStepLogEntryDto.Fields.stepNumber, p -> p.long_(k -> k))
        .properties(UpgradeStepLogEntryDto.Fields.appliedDate, p -> p.date(k -> k));
  }
}
