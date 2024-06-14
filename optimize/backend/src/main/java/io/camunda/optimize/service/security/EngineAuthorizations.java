/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.dto.engine.AuthorizationDto;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
@Data
public class EngineAuthorizations {
  private final String engine;
  private List<AuthorizationDto> globalAuthorizations = new ArrayList<>();
  private List<AuthorizationDto> groupAuthorizations = new ArrayList<>();
  private List<AuthorizationDto> userAuthorizations = new ArrayList<>();
  ;
}
