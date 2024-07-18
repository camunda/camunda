/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.automation.permissions;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.stream.Stream;

public enum PermissionEnum {
  CREATE_ALL("*:create"),
  READ_ALL("*:read"),
  UPDATE_ALL("*:update"),
  DELETE_ALL("*:delete");

  private final String value;

  PermissionEnum(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static PermissionEnum fromString(final String value) {
    return Stream.of(PermissionEnum.values())
        .filter(
            permissionEnum ->
                permissionEnum.getValue().equalsIgnoreCase(value)
                    || permissionEnum.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    """
                    Invalid Enum value "%s" for Permission, expected one of %s"""
                        .formatted(value, Arrays.toString(PermissionEnum.values()))));
  }
}
