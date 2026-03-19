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
public enum GeneratedPermissionTypeEnum {
  ACCESS("ACCESS"),

  CANCEL_PROCESS_INSTANCE("CANCEL_PROCESS_INSTANCE"),

  CLAIM("CLAIM"),

  CLAIM_USER_TASK("CLAIM_USER_TASK"),

  COMPLETE("COMPLETE"),

  COMPLETE_USER_TASK("COMPLETE_USER_TASK"),

  CREATE("CREATE"),

  CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE("CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE"),

  CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION(
      "CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION"),

  CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE(
      "CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE"),

  CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION(
      "CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION"),

  CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE("CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE"),

  CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE(
      "CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE"),

  CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE("CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE"),

  CREATE_BATCH_OPERATION_RESOLVE_INCIDENT("CREATE_BATCH_OPERATION_RESOLVE_INCIDENT"),

  CREATE_DECISION_INSTANCE("CREATE_DECISION_INSTANCE"),

  CREATE_PROCESS_INSTANCE("CREATE_PROCESS_INSTANCE"),

  CREATE_TASK_LISTENER("CREATE_TASK_LISTENER"),

  DELETE("DELETE"),

  DELETE_DECISION_INSTANCE("DELETE_DECISION_INSTANCE"),

  DELETE_DRD("DELETE_DRD"),

  DELETE_FORM("DELETE_FORM"),

  DELETE_PROCESS("DELETE_PROCESS"),

  DELETE_PROCESS_INSTANCE("DELETE_PROCESS_INSTANCE"),

  DELETE_RESOURCE("DELETE_RESOURCE"),

  DELETE_TASK_LISTENER("DELETE_TASK_LISTENER"),

  EVALUATE("EVALUATE"),

  MODIFY_PROCESS_INSTANCE("MODIFY_PROCESS_INSTANCE"),

  READ("READ"),

  READ_DECISION_DEFINITION("READ_DECISION_DEFINITION"),

  READ_DECISION_INSTANCE("READ_DECISION_INSTANCE"),

  READ_JOB_METRIC("READ_JOB_METRIC"),

  READ_PROCESS_DEFINITION("READ_PROCESS_DEFINITION"),

  READ_PROCESS_INSTANCE("READ_PROCESS_INSTANCE"),

  READ_USAGE_METRIC("READ_USAGE_METRIC"),

  READ_USER_TASK("READ_USER_TASK"),

  READ_TASK_LISTENER("READ_TASK_LISTENER"),

  UPDATE("UPDATE"),

  UPDATE_PROCESS_INSTANCE("UPDATE_PROCESS_INSTANCE"),

  UPDATE_USER_TASK("UPDATE_USER_TASK"),

  UPDATE_TASK_LISTENER("UPDATE_TASK_LISTENER");

  private final String value;

  GeneratedPermissionTypeEnum(String value) {
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
  public static GeneratedPermissionTypeEnum fromValue(String value) {
    for (GeneratedPermissionTypeEnum b : GeneratedPermissionTypeEnum.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}
