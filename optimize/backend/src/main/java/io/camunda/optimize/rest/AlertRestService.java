/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.service.alert.AlertService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.ValidationHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + AlertRestService.ALERT_PATH)
public class AlertRestService {

  public static final String ALERT_PATH = "/alert";

  private final AlertService alertService;
  private final SessionService sessionService;

  public AlertRestService(final AlertService alertService, final SessionService sessionService) {
    this.alertService = alertService;
    this.sessionService = sessionService;
  }

  @PostMapping()
  public IdResponseDto createAlert(
      @RequestBody final AlertCreationRequestDto toCreate, final HttpServletRequest request) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    final String user = sessionService.getRequestUserOrFailNotAuthorized(request);
    return alertService.createAlert(toCreate, user);
  }

  @PutMapping("/{id}")
  public void updateAlert(
      @PathVariable("id") final String alertId,
      @RequestBody final AlertCreationRequestDto toCreate,
      final HttpServletRequest request) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    final String user = sessionService.getRequestUserOrFailNotAuthorized(request);
    alertService.updateAlert(alertId, toCreate, user);
  }

  @DeleteMapping("/{id}")
  public void deleteAlert(
      @PathVariable("id") final String alertId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    alertService.deleteAlert(alertId, userId);
  }

  @PostMapping("/delete")
  public void deleteAlerts(
      @NotNull @RequestBody final List<String> alertIds, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    alertService.deleteAlerts(alertIds, userId);
  }
}
