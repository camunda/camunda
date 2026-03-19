/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml#/components/schemas/JobStateEnum
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.annotation.Generated;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public enum GeneratedJobStateEnum {
  CANCELED("CANCELED"),

  COMPLETED("COMPLETED"),

  CREATED("CREATED"),

  ERROR_THROWN("ERROR_THROWN"),

  FAILED("FAILED"),

  MIGRATED("MIGRATED"),

  RETRIES_UPDATED("RETRIES_UPDATED"),

  TIMED_OUT("TIMED_OUT");

  private final String value;

  GeneratedJobStateEnum(String value) {
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
  public static GeneratedJobStateEnum fromValue(String value) {
    for (GeneratedJobStateEnum b : GeneratedJobStateEnum.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
