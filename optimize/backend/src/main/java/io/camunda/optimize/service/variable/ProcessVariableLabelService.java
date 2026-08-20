/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.variable;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.writer.VariableLabelWriter;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProcessVariableLabelService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessVariableLabelService.class);
  private final VariableLabelWriter variableLabelWriter;
  private final DefinitionService definitionService;

  public ProcessVariableLabelService(
      final VariableLabelWriter variableLabelWriter, final DefinitionService definitionService) {
    this.variableLabelWriter = variableLabelWriter;
    this.definitionService = definitionService;
  }

  public void storeVariableLabels(final DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    definitionVariableLabelsDto.getLabels().stream()
        .collect(
            Collectors.groupingBy(
                label -> label.getVariableName() + label.getVariableType(), Collectors.counting()))
        .values()
        .forEach(
            count -> {
              if (count > 1) {
                throw new BadRequestException("Each variable can only have a single label!");
              }
            });

    if (definitionService.definitionExists(
        DefinitionType.PROCESS, definitionVariableLabelsDto.getDefinitionKey())) {
      variableLabelWriter.createVariableLabelUpsertRequest(definitionVariableLabelsDto);
    } else {
      throw new NotFoundException(
          "The process definition with id "
              + definitionVariableLabelsDto.getDefinitionKey()
              + " has not yet been "
              + "imported to Optimize");
    }
  }

  public void deleteVariableLabelsForDefinition(final String processDefinitionKey) {
    variableLabelWriter.deleteVariableLabelsForDefinition(processDefinitionKey);
  }
}
