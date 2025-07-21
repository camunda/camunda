/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import java.util.List;

public record TestMappingRule(
    String id, String claimName, String claimValue, List<Permissions> permissions) {

  public TestMappingRule(final String id, final String claimName, final String claimValue) {
    this(id, claimName, claimValue, List.of());
  }
}
