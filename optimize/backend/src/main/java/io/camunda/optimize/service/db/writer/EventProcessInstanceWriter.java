/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.importing.EventProcessGatewayDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.repository.Repository;
import io.camunda.optimize.service.db.repository.TaskRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class EventProcessInstanceWriter {
  private final ProcessInstanceRepository processInstanceRepository;
  private final TaskRepository taskRepository;
  private final Repository repository;

  public void importProcessInstances(
      final String index,
      final List<EventProcessInstanceDto> eventProcessInstanceDtos,
      final List<EventProcessGatewayDto> gatewayLookup) {
    final String importItemName = "event process instances";
    log.debug("Writing [{}] {} to Database.", eventProcessInstanceDtos.size(), importItemName);
    processInstanceRepository.bulkImportEvents(
        index, importItemName, eventProcessInstanceDtos, gatewayLookup);
  }

  public void deleteInstancesThatEndedBefore(final String index, final OffsetDateTime endDate) {
    final String deletedItemIdentifier =
        String.format("event process instances in index %s that ended before %s", index, endDate);
    log.info("Performing cleanup on {}", deletedItemIdentifier);

    taskRepository.executeWithTaskMonitoring(
        repository.getDeleteByQueryActionName(),
        () -> processInstanceRepository.deleteEndedBefore(index, endDate, deletedItemIdentifier),
        log);

    log.info("Finished cleanup on {}", deletedItemIdentifier);
  }

  public void deleteVariablesOfInstancesThatEndedBefore(
      final String index, final OffsetDateTime endDate) {
    final String updateItem =
        String.format("event process variables in index %s that ended before %s", index, endDate);
    log.info("Performing cleanup on {}", updateItem);

    taskRepository.executeWithTaskMonitoring(
        repository.getUpdateByQueryActionName(),
        () ->
            processInstanceRepository.deleteVariablesOfInstancesThatEndedBefore(
                index, endDate, updateItem),
        log);

    log.info("Finished cleanup on {}", updateItem);
  }

  public void deleteEventsWithIdsInFromAllInstances(
      final String index, final List<String> eventIdsToDelete) {
    final String updateItem =
        String.format("%d event process instance events by ID", eventIdsToDelete.size());
    processInstanceRepository.deleteEventsWithIdsInFromAllInstances(
        index, eventIdsToDelete, updateItem);
  }
}
