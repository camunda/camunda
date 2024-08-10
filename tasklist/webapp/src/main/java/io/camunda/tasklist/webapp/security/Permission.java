/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

public enum Permission {
  @JsonProperty("read")
  READ,
  @JsonProperty("write")
  WRITE;

  public static Permission fromString(final String permissionAsString) {
    for (final Permission permission : values()) {
      if (permission.name().equalsIgnoreCase(permissionAsString)) {
        return permission;
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "%s does not exists as Permission in %s",
            permissionAsString, Arrays.toString(values())));
  }
}
