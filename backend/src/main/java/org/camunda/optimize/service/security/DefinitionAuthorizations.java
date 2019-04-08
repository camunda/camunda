/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthorizationDto;

import java.util.List;

public class DefinitionAuthorizations {

  private List<AuthorizationDto> allDefinitionAuthorizations;
  private List<AuthorizationDto> groupAuthorizations;
  private List<AuthorizationDto> userAuthorizations;

  public DefinitionAuthorizations(List<AuthorizationDto> allDefinitionAuthorizations,
                                  List<AuthorizationDto> groupAuthorizations,
                                  List<AuthorizationDto> userAuthorizations) {
    this.allDefinitionAuthorizations = allDefinitionAuthorizations;
    this.groupAuthorizations = groupAuthorizations;
    this.userAuthorizations = userAuthorizations;
  }

  public List<AuthorizationDto> getAllDefinitionAuthorizations() {
    return allDefinitionAuthorizations;
  }

  public List<AuthorizationDto> getGroupAuthorizations() {
    return groupAuthorizations;
  }

  public List<AuthorizationDto> getUserAuthorizations() {
    return userAuthorizations;
  }
}
