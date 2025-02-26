/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import io.camunda.security.configuration.InitializationConfiguration;
import java.util.List;

public record User(String username, String password, List<Permissions> permissions) {
  public static final User DEFAULT =
      new User(
          InitializationConfiguration.DEFAULT_USER_USERNAME,
          InitializationConfiguration.DEFAULT_USER_PASSWORD,
          List.of());
}
