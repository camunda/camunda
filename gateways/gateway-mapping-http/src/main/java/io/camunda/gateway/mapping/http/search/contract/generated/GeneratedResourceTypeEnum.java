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
public enum GeneratedResourceTypeEnum {
  AUDIT_LOG("AUDIT_LOG"),

  AUTHORIZATION("AUTHORIZATION"),

  BATCH("BATCH"),

  CLUSTER_VARIABLE("CLUSTER_VARIABLE"),

  COMPONENT("COMPONENT"),

  DECISION_DEFINITION("DECISION_DEFINITION"),

  DECISION_REQUIREMENTS_DEFINITION("DECISION_REQUIREMENTS_DEFINITION"),

  DOCUMENT("DOCUMENT"),

  EXPRESSION("EXPRESSION"),

  GLOBAL_LISTENER("GLOBAL_LISTENER"),

  GROUP("GROUP"),

  MAPPING_RULE("MAPPING_RULE"),

  MESSAGE("MESSAGE"),

  PROCESS_DEFINITION("PROCESS_DEFINITION"),

  RESOURCE("RESOURCE"),

  ROLE("ROLE"),

  SYSTEM("SYSTEM"),

  TENANT("TENANT"),

  USER("USER"),

  USER_TASK("USER_TASK");

  private final String value;

  GeneratedResourceTypeEnum(String value) {
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
  public static GeneratedResourceTypeEnum fromValue(String value) {
    for (GeneratedResourceTypeEnum b : GeneratedResourceTypeEnum.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
