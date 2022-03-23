/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.service;

import lombok.SneakyThrows;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;

import java.time.Instant;
import java.util.Optional;

public class UpgradeStepLogService {

  public void initializeOrUpdate(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.createOrUpdateIndex(new UpdateLogEntryIndex());
  }

  @SneakyThrows
  public void recordAppliedStep(final SchemaUpgradeClient schemaUpgradeClient,
                                final UpgradeStepLogEntryDto logEntryDto) {
    logEntryDto.setAppliedDate(LocalDateUtil.getCurrentDateTime().toInstant());
    schemaUpgradeClient.upsert(UpdateLogEntryIndex.INDEX_NAME, logEntryDto.getId(), logEntryDto);
  }

  public Optional<Instant> getStepAppliedDate(final SchemaUpgradeClient schemaUpgradeClient,
                                             final UpgradeStepLogEntryDto logEntryDto) {
    return schemaUpgradeClient.getDocumentByIdAs(
      UpdateLogEntryIndex.INDEX_NAME, logEntryDto.getId(), UpgradeStepLogEntryDto.class
    ).map(UpgradeStepLogEntryDto::getAppliedDate);
  }

}
