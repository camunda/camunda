/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
