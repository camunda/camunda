/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.record;

import java.util.Collections;
import java.util.List;

public record CamundaUser(String username, boolean enabled, List<String> Roles) {

  public CamundaUser(final String username) {
    this(username, true, Collections.emptyList());
  }

  public CamundaUser(final String username, final List<String> roles) {
    this(username, true, roles);
  }

  public CamundaUser(final String username, final boolean enabled) {
    this(username, enabled, Collections.emptyList());
  }
}
