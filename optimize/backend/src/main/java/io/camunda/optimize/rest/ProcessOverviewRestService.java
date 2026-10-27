/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto.SORT_BY;
import static io.camunda.optimize.dto.optimize.rest.sorting.SortRequestDto.SORT_ORDER;
import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.processoverview.InitialProcessOwnerDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.rest.sorting.ProcessOverviewSorter;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.ProcessOverviewService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + ProcessOverviewRestService.PROCESS_PATH)
public class ProcessOverviewRestService {
  public static final String PROCESS_PATH = "/process";

  private final ProcessOverviewService processOverviewService;
  private final SessionService sessionService;

  public ProcessOverviewRestService(
      final ProcessOverviewService processOverviewService, final SessionService sessionService) {
    this.processOverviewService = processOverviewService;
    this.sessionService = sessionService;
  }

  @GetMapping("/overview")
  public List<ProcessOverviewResponseDto> getProcessOverviews(
      final @RequestParam(name = SORT_BY, required = false) String sortBy,
      final @RequestParam(name = SORT_ORDER, required = false) SortOrder sortOrder,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<ProcessOverviewResponseDto> processOverviewResponseDtos =
        processOverviewService.getAllProcessOverviews(
            userId, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
    final ProcessOverviewSorter processOverviewSorter =
        new ProcessOverviewSorter(sortBy, sortOrder);
    return processOverviewSorter.applySort(processOverviewResponseDtos);
  }

  @PutMapping(path = "/{processDefinitionKey}")
  public void updateProcess(
      @PathVariable("processDefinitionKey") final String processDefKey,
      @NotNull @Valid @RequestBody final ProcessUpdateDto processUpdateDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    processOverviewService.updateProcess(userId, processDefKey, processUpdateDto);
  }

  @PostMapping(path = "/initial-owner")
  public void setInitialProcessOwner(
      @NotNull @Valid @RequestBody final InitialProcessOwnerDto ownerDto,
      final HttpServletRequest request) {
    Optional<String> userId;
    try {
      userId = Optional.ofNullable(sessionService.getRequestUserOrFailNotAuthorized(request));
    } catch (final NotAuthorizedException e) {
      // If we are using a CloudSaaS Token
      userId = Optional.ofNullable(request.getUserPrincipal().getName());
    }
    userId.ifPresentOrElse(
        id ->
            processOverviewService.updateProcessOwnerIfNotSet(
                id, ownerDto.getProcessDefinitionKey(), ownerDto.getOwner()),
        () -> {
          throw new NotAuthorizedException("Could not resolve user for this request");
        });
  }
}
