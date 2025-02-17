/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + SettingsRestService.SETTINGS_PATH)
public class SettingsRestService {

  public static final String SETTINGS_PATH = "/settings";

  private final SessionService sessionService;
  private final SettingsService settingsService;

  public SettingsRestService(
      final SessionService sessionService, final SettingsService settingsService) {
    this.sessionService = sessionService;
    this.settingsService = settingsService;
  }

  @GetMapping
  public SettingsDto getSettings() {
    return settingsService.getSettings();
  }

  @PutMapping
  public void setSettings(
      @NotNull @RequestBody final SettingsDto settingsDto, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    settingsService.setSettings(userId, settingsDto);
  }
}
