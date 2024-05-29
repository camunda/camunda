/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.upgrade.service;

import static io.camunda.optimize.service.db.DatabaseConstants.UPDATE_LOG_ENTRY_INDEX_NAME;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;

public class UpgradeStepLogService {

  public void initializeOrUpdate(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.createOrUpdateIndex(new UpdateLogEntryIndex());
  }

  @SneakyThrows
  public void recordAppliedStep(
      final SchemaUpgradeClient schemaUpgradeClient, final UpgradeStepLogEntryDto logEntryDto) {
    logEntryDto.setAppliedDate(LocalDateUtil.getCurrentDateTime().toInstant());
    schemaUpgradeClient.upsert(UPDATE_LOG_ENTRY_INDEX_NAME, logEntryDto.getId(), logEntryDto);
  }

  public Map<String, UpgradeStepLogEntryDto> getAllAppliedStepsForUpdateToById(
      final SchemaUpgradeClient schemaUpgradeClient, final String targetOptimizeVersion) {
    return schemaUpgradeClient.getAppliedUpdateStepsForTargetVersion(targetOptimizeVersion).stream()
        .collect(Collectors.toMap(UpgradeStepLogEntryDto::getId, Function.identity()));
  }
}
