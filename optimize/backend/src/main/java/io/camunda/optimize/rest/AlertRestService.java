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
import io.camunda.optimize.service.security.SecurityContextUtils;
import io.camunda.optimize.service.util.ValidationHelper;
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

  public AlertRestService(final AlertService alertService) {
    this.alertService = alertService;
  }

  @PostMapping()
  public IdResponseDto createAlert(@RequestBody final AlertCreationRequestDto toCreate) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    final String user = SecurityContextUtils.getAuthenticatedUser();
    return alertService.createAlert(toCreate, user);
  }

  @PutMapping("/{id}")
  public void updateAlert(
      @PathVariable("id") final String alertId,
      @RequestBody final AlertCreationRequestDto toCreate) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    final String user = SecurityContextUtils.getAuthenticatedUser();
    alertService.updateAlert(alertId, toCreate, user);
  }

  @DeleteMapping("/{id}")
  public void deleteAlert(@PathVariable("id") final String alertId) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    alertService.deleteAlert(alertId, userId);
  }

  @PostMapping("/delete")
  public void deleteAlerts(@NotNull @RequestBody final List<String> alertIds) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    alertService.deleteAlerts(alertIds, userId);
  }
}
