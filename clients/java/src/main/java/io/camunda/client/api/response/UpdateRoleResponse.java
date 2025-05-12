/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.api.response;

public interface UpdateRoleResponse {
  /**
   * Returns the key of the updated role.
   *
   * @return the key of the updated role.
   */
  long getRoleKey();

  /**
   * Returns the ID of the updated role.
   *
   * @return the ID of the updated role.
   */
  String getRoleId();

  /**
   * Returns the name of the updated role.
   *
   * @return the name of the updated role.
   */
  String getName();

  /**
   * Returns the description of the updated role.
   *
   * @return the description of the updated role.
   */
  String getDescription();
}
