/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement;

public record CamundaUser(Integer id, String username, String email, boolean enabled) {

  public CamundaUser(final String username) {
    this(username, null);
  }

  public CamundaUser(final String username, final String email) {
    this(username, email, true);
  }

  public CamundaUser(final String username, final String email, final boolean enabled) {
    this(null, username, email, enabled);
  }
}
