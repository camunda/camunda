/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

/**
 * Hides the agentic Phase 2 endpoints while the feature is switched off. Each Phase 2 endpoint
 * calls {@link #requireEnabled()} first, so callers get a 404 as if the endpoint did not exist.
 */
@Component
public class AgenticPhase2Guard {

  private final ConfigurationService configurationService;

  public AgenticPhase2Guard(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public void requireEnabled() {
    if (!configurationService.getAgenticPhase2Configuration().isEnabled()) {
      throw new NotFoundException("Agentic Phase 2 is not enabled.");
    }
  }
}
