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
        if (authorization.getPermissions().contains(PROCESS_PERMISSION_START)) {
          if (authorization.getResourceKey().equals(IdentityProperties.ALL_RESOURCES)) {
            processesAllowedToStart.clear();
            processesAllowedToStart.add(IdentityProperties.ALL_RESOURCES);
          } else {
            processesAllowedToStart.add(authorization.getResourceKey());
          }
        }

        if (authorization.getPermissions().contains(PROCESS_PERMISSION_READ)) {
          if (authorization.getResourceKey().equals(IdentityProperties.ALL_RESOURCES)) {
            processesAllowedToRead.clear();
            processesAllowedToRead.add(IdentityProperties.ALL_RESOURCES);
          } else {
            processesAllowedToRead.add(authorization.getResourceKey());
          }
        }
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
