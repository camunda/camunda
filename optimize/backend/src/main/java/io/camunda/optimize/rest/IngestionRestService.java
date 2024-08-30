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

import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.VariableHelper;
import io.camunda.optimize.service.variable.ExternalVariableService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@AllArgsConstructor
@Slf4j
@Path(INGESTION_PATH)
@Component
public class IngestionRestService {
  public static final String INGESTION_PATH = "/ingestion";
  public static final String VARIABLE_SUB_PATH = "/variable";

  private final ExternalVariableService externalVariableService;

  @POST
  @Path(VARIABLE_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  public void ingestVariables(
      final @Context ContainerRequestContext requestContext,
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

  @Data
  private static class ValidList<E> implements List<E> {

    @Delegate private List<E> list = new ArrayList<>();
  }
}
