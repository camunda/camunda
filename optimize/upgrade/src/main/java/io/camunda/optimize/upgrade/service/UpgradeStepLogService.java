/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.service;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.db.index.UpdateLogEntryIndex;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClientES;
import io.camunda.optimize.upgrade.es.index.UpdateLogEntryIndexES;
import io.camunda.optimize.upgrade.os.SchemaUpgradeClientOS;
import io.camunda.optimize.upgrade.os.index.UpdateLogEntryIndexOS;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UpgradeStepLogService {

  public void initializeOrUpdate(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    if (schemaUpgradeClient instanceof final SchemaUpgradeClientES esClient) {
      esClient.createOrUpdateIndex(new UpdateLogEntryIndexES());
    } else if (schemaUpgradeClient instanceof final SchemaUpgradeClientOS osClient) {
      osClient.createOrUpdateIndex(new UpdateLogEntryIndexOS());
    }
  }

  public void recordAppliedStep(
      final SchemaUpgradeClient<?, ?> schemaUpgradeClient,
      final UpgradeStepLogEntryDto logEntryDto) {
    logEntryDto.setAppliedDate(LocalDateUtil.getCurrentDateTime().toInstant());
    schemaUpgradeClient.upsert(UpdateLogEntryIndex.INDEX_NAME, logEntryDto.getId(), logEntryDto);
  }

  public Map<String, UpgradeStepLogEntryDto> getAllAppliedStepsForUpdateToById(
      final SchemaUpgradeClient<?, ?> schemaUpgradeClient, final String targetOptimizeVersion) {
    return schemaUpgradeClient.getAppliedUpdateStepsForTargetVersion(targetOptimizeVersion).stream()
        .collect(Collectors.toMap(UpgradeStepLogEntryDto::getId, Function.identity()));
  }
}
