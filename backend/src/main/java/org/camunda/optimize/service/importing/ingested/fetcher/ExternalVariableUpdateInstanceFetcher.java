/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested.fetcher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.es.reader.ExternalVariableReader;
import org.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class ExternalVariableUpdateInstanceFetcher {

  private final ExternalVariableReader variableReader;
  private final ConfigurationService configurationService;

  public List<ExternalProcessVariableDto> fetchVariableInstanceUpdates(final TimestampBasedImportPage page) {
    return variableReader.getVariableUpdatesIngestedAfter(
      page.getTimestampOfLastEntity().toInstant().toEpochMilli(),
      configurationService.getExternalVariableConfiguration().getImportConfiguration().getMaxPageSize()
    );
  }

  public List<ExternalProcessVariableDto> fetchVariableInstanceUpdates(final OffsetDateTime endTimeOfLastInstance) {
    return variableReader.getVariableUpdatesIngestedAt(endTimeOfLastInstance.toInstant().toEpochMilli());
  }

}
