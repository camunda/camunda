/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.writer.variable;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.db.repository.VariableRepository;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ExternalProcessVariableWriter {
  private VariableRepository variableRepository;
  private TaskRepository taskRepository;

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
