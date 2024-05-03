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

  // TODO - the UPDATE_PROCESS_INSTANCE is being used temporarily until we have the new permission
  public static final String PROCESS_PERMISSION_START = "START_PROCESS_INSTANCE";
  public static final String PROCESS_PERMISSION_READ = "READ";
  private static final String PROCESS_DEFINITION = "process-definition";
  private List<String> processesAllowedToStart;
  private final List<String> processesAllowedToRead;

  public IdentityAuthorization(final List<Authorization> authorizations) {
    processesAllowedToStart = new ArrayList<String>();
    processesAllowedToRead = new ArrayList<String>();
    for (final Authorization authorization : authorizations) {
      if (authorization.getResourceType().equals(PROCESS_DEFINITION)) {
        addPermissions(authorization, processesAllowedToStart, PROCESS_PERMISSION_START);
        addPermissions(authorization, processesAllowedToRead, PROCESS_PERMISSION_READ);
      }
    }
  }

  private void addPermissions(
      final Authorization authorization,
      final List<String> processesAllowed,
      final String expectedPermission) {
    if (!processesAllowed.contains(IdentityProperties.ALL_RESOURCES)
        && authorization.getPermissions().contains(expectedPermission)) {
      if (authorization.getResourceKey().equals(IdentityProperties.ALL_RESOURCES)) {
        processesAllowed.clear();
        processesAllowed.add(IdentityProperties.ALL_RESOURCES);
      } else {
        processesAllowed.add(authorization.getResourceKey());
      }
    }
  }

  public List<String> getProcessesAllowedToStart() {
    return processesAllowedToStart;
  }

  public IdentityAuthorization setProcessesAllowedToStart(
      final List<String> processesAllowedToStart) {
    this.processesAllowedToStart = processesAllowedToStart;
    return this;
  }

  public List<String> getProcessesAllowedToRead() {
    return processesAllowedToRead;
  }
}
