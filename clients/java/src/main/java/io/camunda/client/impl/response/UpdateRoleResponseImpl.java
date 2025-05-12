/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.impl.response;

import io.camunda.client.api.response.UpdateRoleResponse;
import io.camunda.client.protocol.rest.RoleUpdateResult;

public class UpdateRoleResponseImpl implements UpdateRoleResponse {
  private long roleKey;
  private String roleId;
  private String name;
  private String description;

  @Override
  public long getRoleKey() {
    return roleKey;
  }

  @Override
  public String getRoleId() {
    return roleId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public UpdateRoleResponseImpl setResponse(final RoleUpdateResult roleUpdateResult) {
    roleKey = Long.parseLong(roleUpdateResult.getRoleKey());
    roleId = roleUpdateResult.getRoleId();
    name = roleUpdateResult.getName();
    description = roleUpdateResult.getDescription();
    return this;
  }
}
