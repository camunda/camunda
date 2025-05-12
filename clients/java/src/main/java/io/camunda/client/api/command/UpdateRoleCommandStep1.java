/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.api.command;

import io.camunda.client.api.response.UpdateRoleResponse;

public interface UpdateRoleCommandStep1 {
  /**
   * Set the ID to create role with.
   *
   * @param roleId the role ID
   * @return the builder for this command.
   */
  UpdateRoleCommandStep1.UpdateRoleCommandStep2 roleId(String roleId);

  interface UpdateRoleCommandStep2 extends FinalCommandStep<UpdateRoleResponse> {
    /**
     * Set the name for the role to be updated.
     *
     * @param name the role name
     * @return the builder for this command
     */
    UpdateRoleCommandStep1.UpdateRoleCommandStep2 name(String name);

    /**
     * Set the description for the role to be updated.
     *
     * @param description the role description
     * @return the builder for this command
     */
    UpdateRoleCommandStep1.UpdateRoleCommandStep2 description(String description);
  }
}
