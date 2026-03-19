/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public enum GeneratedProcessInstanceStateEnum {
  ACTIVE("ACTIVE"),

  COMPLETED("COMPLETED"),

  TERMINATED("TERMINATED");

  private final String value;

  GeneratedProcessInstanceStateEnum(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static GeneratedProcessInstanceStateEnum fromValue(String value) {
    for (GeneratedProcessInstanceStateEnum b : GeneratedProcessInstanceStateEnum.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
