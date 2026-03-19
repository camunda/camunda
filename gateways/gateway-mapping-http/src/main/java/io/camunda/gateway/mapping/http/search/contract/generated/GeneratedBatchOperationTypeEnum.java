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
public enum GeneratedBatchOperationTypeEnum {
  ADD_VARIABLE("ADD_VARIABLE"),

  CANCEL_PROCESS_INSTANCE("CANCEL_PROCESS_INSTANCE"),

  DELETE_DECISION_DEFINITION("DELETE_DECISION_DEFINITION"),

  DELETE_DECISION_INSTANCE("DELETE_DECISION_INSTANCE"),

  DELETE_PROCESS_DEFINITION("DELETE_PROCESS_DEFINITION"),

  DELETE_PROCESS_INSTANCE("DELETE_PROCESS_INSTANCE"),

  MIGRATE_PROCESS_INSTANCE("MIGRATE_PROCESS_INSTANCE"),

  MODIFY_PROCESS_INSTANCE("MODIFY_PROCESS_INSTANCE"),

  RESOLVE_INCIDENT("RESOLVE_INCIDENT"),

  UPDATE_VARIABLE("UPDATE_VARIABLE");

  private final String value;

  GeneratedBatchOperationTypeEnum(String value) {
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
  public static GeneratedBatchOperationTypeEnum fromValue(String value) {
    for (GeneratedBatchOperationTypeEnum b : GeneratedBatchOperationTypeEnum.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
