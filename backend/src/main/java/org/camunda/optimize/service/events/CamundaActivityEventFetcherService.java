/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.events;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceConfigDto;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.es.reader.importindex.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.camunda.optimize.service.importing.engine.handler.CompletedProcessInstanceImportIndexHandler.COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler.RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.service.importing.engine.handler.VariableUpdateInstanceImportIndexHandler.VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;

@AllArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class CamundaActivityEventFetcherService implements EventFetcherService<CamundaActivityEventDto> {

  private final CamundaEventSourceConfigDto eventSourceConfig;

  private final CamundaActivityEventReader camundaActivityEventReader;
  private final TimestampBasedImportIndexReader timestampBasedImportIndexReader;

  @Override
  public List<CamundaActivityEventDto> getEventsIngestedAfter(final Long eventTimestamp, final int limit) {
    return camundaActivityEventReader
      .getCamundaActivityEventsForDefinitionBetween(
        eventSourceConfig.getProcessDefinitionKey(),
        eventTimestamp,
        getMaxTimestampForEventRetrieval(),
        limit
      );
  }

  @Override
  public List<CamundaActivityEventDto> getEventsIngestedAt(final Long eventTimestamp) {
    return camundaActivityEventReader.getCamundaActivityEventsForDefinitionAt(
      eventSourceConfig.getProcessDefinitionKey(), eventTimestamp);
  }

  private long getMaxTimestampForEventRetrieval() {
    return timestampBasedImportIndexReader.getAllImportIndicesForTypes(getImportIndicesToSearch())
      .stream()
      .filter(importIndex -> Objects.nonNull(importIndex.getLastImportExecutionTimestamp()))
      .min(Comparator.comparing(TimestampBasedImportIndexDto::getLastImportExecutionTimestamp))
      .map(importIndex -> {
        log.debug(
          "Searching using the max timestamp {} from import index type {}",
          importIndex.getLastImportExecutionTimestamp(),
          importIndex.getEsTypeIndexRefersTo()
        );
        return importIndex.getLastImportExecutionTimestamp().toInstant().toEpochMilli();
      })
      .orElseThrow(() -> new OptimizeRuntimeException("Could not find the maximum timestamp to search for"));
  }

  private List<String> getImportIndicesToSearch() {
    if (!eventSourceConfig.isTracedByBusinessKey()) {
      return Arrays.asList(
        COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID,
        RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID,
        VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID
      );
    } else {
      return Arrays.asList(
        COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID,
        RUNNING_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID
      );
    }
  }

}
