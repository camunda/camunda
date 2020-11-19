/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.service;

import lombok.SneakyThrows;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;

public class UpgradeStepLogService {

  public void initializeOrUpdate(final SchemaUpgradeClient schemaUpgradeClient) {
    schemaUpgradeClient.createIndex(new UpdateLogEntryIndex());
  }

  @SneakyThrows
  public void recordAppliedStep(final SchemaUpgradeClient schemaUpgradeClient,
                                final UpdateStepLogEntryDto logEntryDto) {
    logEntryDto.setAppliedDate(LocalDateUtil.getCurrentDateTime().toInstant());
    schemaUpgradeClient.update(
      new UpdateRequest(UpdateLogEntryIndex.INDEX_NAME, logEntryDto.getId())
        .doc(schemaUpgradeClient.getObjectMapper().writeValueAsString(logEntryDto), XContentType.JSON)
        .docAsUpsert(true)
    );
  }
}
