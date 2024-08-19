/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer.variable;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.db.repository.VariableRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ExternalProcessVariableWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExternalProcessVariableWriter.class);
  private final VariableRepository variableRepository;
  private final TaskRepository taskRepository;

  public ExternalProcessVariableWriter(
      final VariableRepository variableRepository, final TaskRepository taskRepository) {
    this.variableRepository = variableRepository;
    this.taskRepository = taskRepository;
  }

  public void writeExternalProcessVariables(final List<ExternalProcessVariableDto> variables) {
    final String itemName = "external process variables";
    log.debug("Writing {} {} to Database.", variables.size(), itemName);
    variableRepository.writeExternalProcessVariables(variables, itemName);
  }

  public void deleteExternalVariablesIngestedBefore(final OffsetDateTime timestamp) {
    final String deletedItemIdentifier =
        String.format("external variables with timestamp older than %s", timestamp);
    log.info("Deleting {}", deletedItemIdentifier);
    taskRepository.executeWithTaskMonitoring(
        DeleteByQueryAction.NAME,
        () ->
            variableRepository.deleteExternalVariablesIngestedBefore(
                timestamp, deletedItemIdentifier),
        log);
  }
}
