/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.APPLICATION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.GROUP;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.ROLE;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.TENANT;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.USER;
import static io.camunda.zeebe.protocol.record.value.PermissionType.ACCESS;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ_USER_TASK;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.function.Function;

public record Authorization(AuthorizationResourceType resourceType, PermissionType permissionType) {

  public static final String WILDCARD = "*";

  public static Authorization of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static class Builder {
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;

    public Builder resourceType(final AuthorizationResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public Builder processDefinition() {
      return resourceType(PROCESS_DEFINITION);
    }

    public Builder decisionDefinition() {
      return resourceType(DECISION_DEFINITION);
    }

    public Builder decisionRequirementsDefinition() {
      return resourceType(DECISION_REQUIREMENTS_DEFINITION);
    }

    public Builder application() {
      return resourceType(APPLICATION);
    }

    public Builder mapping() {
      return resourceType(MAPPING_RULE);
    }

    public Builder role() {
      return resourceType(ROLE);
    }

    public Builder group() {
      return resourceType(GROUP);
    }

    public Builder tenant() {
      return resourceType(TENANT);
    }

    public Builder authorization() {
      return resourceType(AUTHORIZATION);
    }

    public Builder user() {
      return resourceType(USER);
    }

    public Builder access() {
      return permissionType(ACCESS);
    }

    public Builder read() {
      return permissionType(READ);
    }

    public Builder readProcessInstance() {
      return permissionType(READ_PROCESS_INSTANCE);
    }

    public Builder readUserTask() {
      return permissionType(READ_USER_TASK);
    }

    public Builder readDecisionInstance() {
      return permissionType(READ_DECISION_INSTANCE);
    }

    public Authorization build() {
      return new Authorization(resourceType, permissionType);
    }
  }
}
