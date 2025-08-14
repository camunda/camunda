/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import java.util.List;

public record TestClient(String clientId, List<Permissions> permissions) {

  public TestClient(final String clientId) {
    this(clientId, List.of());
  }
}
