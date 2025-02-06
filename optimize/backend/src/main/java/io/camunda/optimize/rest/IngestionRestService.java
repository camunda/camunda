/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto.toExternalProcessVariableDtos;
import static io.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.VariableHelper;
import io.camunda.optimize.service.variable.ExternalVariableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + INGESTION_PATH)
public class IngestionRestService {

  public static final String INGESTION_PATH = "/ingestion";
  public static final String VARIABLE_SUB_PATH = "/variable";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(IngestionRestService.class);

  private final ExternalVariableService externalVariableService;

  public IngestionRestService(final ExternalVariableService externalVariableService) {
    this.externalVariableService = externalVariableService;
  }

  @PostMapping(VARIABLE_SUB_PATH)
  public void ingestVariables(
      final @NotNull @Valid @RequestBody List<ExternalProcessVariableRequestDto> variableDtos) {
    validateVariableType(variableDtos);
    externalVariableService.storeExternalProcessVariables(
        toExternalProcessVariableDtos(
            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(), variableDtos));
  }

  private void validateVariableType(final List<ExternalProcessVariableRequestDto> variables) {
    if (variables.stream()
        .anyMatch(variable -> !VariableHelper.isProcessVariableTypeSupported(variable.getType()))) {
      throw new BadRequestException(
          String.format(
              "A given variable type is not supported. The type must always be one of: %s",
              ReportConstants.ALL_SUPPORTED_PROCESS_VARIABLE_TYPES));
    }
  }
}
