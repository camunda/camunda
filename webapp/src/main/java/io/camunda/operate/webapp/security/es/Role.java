/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.es;

import java.util.Arrays;

public enum Role {
  OWNER, OPERATOR, USER;

  public static Role fromString(final String roleAsString) {
    final String roleName = roleAsString.replaceAll("\\s+", "_");
    for(Role role : values()) {
      if(role.name().equalsIgnoreCase(roleName))
        return role;
    }
    throw new IllegalArgumentException(
        String.format("%s does not exists as Role in %s", roleAsString, Arrays.toString(values())));
  }
}
