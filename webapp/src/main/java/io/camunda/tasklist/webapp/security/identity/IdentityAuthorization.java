/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.authorizations.dto.Authorization;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class IdentityAuthorization implements Serializable {

  public static final String ALL_RESOURCES = "*";
  private static final String PROCESS_DEFINITION = "process-definition";
  // TODO - the UPDATE_PROCESS_INSTANCE is being used temporarily until we have the new permission
  private static final String PROCESS_PERMISSION = "START_PROCESS_INSTANCE";
  private List<String> processesAllowedToStart;

  public IdentityAuthorization(List<Authorization> authorizations) {
    processesAllowedToStart = new ArrayList<String>();
    for (Authorization authorization : authorizations) {
      if (authorization.getResourceType().equals(PROCESS_DEFINITION)
          && authorization.getPermissions().contains(PROCESS_PERMISSION)) {
        if (authorization.getResourceKey().equals(ALL_RESOURCES)) {
          processesAllowedToStart.clear();
          processesAllowedToStart.add(ALL_RESOURCES);
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
