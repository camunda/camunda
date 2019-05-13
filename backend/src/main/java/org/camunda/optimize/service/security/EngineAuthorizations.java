/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.camunda.optimize.dto.engine.AuthorizationDto;

import java.util.List;

@AllArgsConstructor
@Data
public class EngineAuthorizations {
  private List<AuthorizationDto> allAuthorizations;
  private List<AuthorizationDto> groupAuthorizations;
  private List<AuthorizationDto> userAuthorizations;
}
