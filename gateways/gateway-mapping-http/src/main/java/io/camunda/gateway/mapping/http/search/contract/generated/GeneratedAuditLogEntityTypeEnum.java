/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/audit-logs.yaml#/components/schemas/AuditLogEntityTypeEnum
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public enum GeneratedAuditLogEntityTypeEnum {
  AUTHORIZATION("AUTHORIZATION"),

  BATCH("BATCH"),

  DECISION("DECISION"),

  GROUP("GROUP"),

  INCIDENT("INCIDENT"),

  JOB("JOB"),

  MAPPING_RULE("MAPPING_RULE"),

  PROCESS_INSTANCE("PROCESS_INSTANCE"),

  RESOURCE("RESOURCE"),

  ROLE("ROLE"),

  TENANT("TENANT"),

  USER("USER"),

  USER_TASK("USER_TASK"),

  VARIABLE("VARIABLE"),

  CLIENT("CLIENT");

  private final String value;

  GeneratedAuditLogEntityTypeEnum(String value) {
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
  public static GeneratedAuditLogEntityTypeEnum fromValue(String value) {
    for (GeneratedAuditLogEntityTypeEnum b : GeneratedAuditLogEntityTypeEnum.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
