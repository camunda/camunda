/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class EventProcessAuthenticationService {
  private final ConfigurationService configurationService;

  public boolean hasEventProcessManagementAccess(final String userId) {
    return configurationService.getEventBasedProcessAccessUserIds().contains(userId);
  }

}
