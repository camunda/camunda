/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.group;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserGroupService {

  private final CamundaAuthenticationProvider authenticationProvider;

  public UserGroupService(final CamundaAuthenticationProvider authenticationProvider) {
    this.authenticationProvider = authenticationProvider;
  }

  public List<String> getUserGroups() {
    return authenticationProvider.getCamundaAuthentication().authenticatedGroupIds();
  }
}
