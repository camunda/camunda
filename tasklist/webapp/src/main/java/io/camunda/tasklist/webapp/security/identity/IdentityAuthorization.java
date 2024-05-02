/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.authorizations.dto.Authorization;
import io.camunda.tasklist.property.IdentityProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class IdentityAuthorization implements Serializable {

  private static final String PROCESS_DEFINITION = "process-definition";
  // TODO - the UPDATE_PROCESS_INSTANCE is being used temporarily until we have the new permission
  private static final String PROCESS_PERMISSION = "START_PROCESS_INSTANCE";
  private List<String> processesAllowedToStart;

  public IdentityAuthorization(List<Authorization> authorizations) {
    processesAllowedToStart = new ArrayList<String>();
    for (Authorization authorization : authorizations) {
      if (authorization.getResourceType().equals(PROCESS_DEFINITION)
          && authorization.getPermissions().contains(PROCESS_PERMISSION)) {
        if (authorization.getResourceKey().equals(IdentityProperties.ALL_RESOURCES)) {
          processesAllowedToStart.clear();
          processesAllowedToStart.add(IdentityProperties.ALL_RESOURCES);
          break;
        } else {
          processesAllowedToStart.add(authorization.getResourceKey());
        }
      }
    }
  }

  public List<String> getProcessesAllowedToStart() {
    return processesAllowedToStart;
  }

  public IdentityAuthorization setProcessesAllowedToStart(List<String> processesAllowedToStart) {
    this.processesAllowedToStart = processesAllowedToStart;
    return this;
  }
}
