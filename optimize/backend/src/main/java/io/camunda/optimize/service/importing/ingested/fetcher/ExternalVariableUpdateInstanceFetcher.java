/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.fetcher;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.db.reader.ExternalVariableReader;
import io.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateInstanceFetcher {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExternalVariableUpdateInstanceFetcher.class);
  private final ExternalVariableReader variableReader;
  private final ConfigurationService configurationService;

  public ExternalVariableUpdateInstanceFetcher(
      final ExternalVariableReader variableReader,
      final ConfigurationService configurationService) {
    this.variableReader = variableReader;
    this.configurationService = configurationService;
  }

  public List<ExternalProcessVariableDto> fetchVariableInstanceUpdates(
      final TimestampBasedImportPage page) {
    return variableReader.getVariableUpdatesIngestedAfter(
        page.getTimestampOfLastEntity().toInstant().toEpochMilli(),
        configurationService
            .getExternalVariableConfiguration()
            .getImportConfiguration()
            .getMaxPageSize());
  }

  public List<ExternalProcessVariableDto> fetchVariableInstanceUpdates(
      final OffsetDateTime endTimeOfLastInstance) {
    return variableReader.getVariableUpdatesIngestedAt(
        endTimeOfLastInstance.toInstant().toEpochMilli());
  }
}
