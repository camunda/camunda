/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Binds and validates the statically configured cluster-admin Basic Auth users from {@code
 * camunda.security.cluster-admin.basic.users}.
 *
 * <p>An empty or entirely absent configuration is a legitimate, silently-accepted state (no
 * cluster-admin Basic users provisioned yet) — only a malformed entry (duplicate name, blank name
 * or password) fails startup.
 */
public final class ClusterAdminBasicAuthProperties {

  private static final String USERS_PROPERTY = "camunda.security.cluster-admin.basic.users";

  private ClusterAdminBasicAuthProperties() {}

  /**
   * Binds the configured cluster-admin Basic Auth users and validates them.
   *
   * @param environment the Spring {@link Environment} to bind from
   * @return the validated list of configured users
   * @throws IllegalStateException if any entry has a blank/missing {@code name} or {@code
   *     password}, or if two entries share the same {@code name}
   */
  public static List<ClusterAdminUser> loadAndValidate(final Environment environment) {
    final List<ClusterAdminUser> users =
        Binder.get(environment)
            .bind(USERS_PROPERTY, Bindable.listOf(ClusterAdminUser.class))
            .orElse(List.of());
    validate(users);
    return users;
  }

  private static void validate(final List<ClusterAdminUser> users) {
    final Set<String> seenNames = new HashSet<>();
    for (final ClusterAdminUser user : users) {
      if (user.name() == null || user.name().isBlank()) {
        throw new IllegalStateException(
            "%s contains an entry with a blank or missing 'name'".formatted(USERS_PROPERTY));
      }
      if (user.password() == null || user.password().isBlank()) {
        throw new IllegalStateException(
            "%s entry '%s' has a blank or missing 'password'"
                .formatted(USERS_PROPERTY, user.name()));
      }
      if (!seenNames.add(user.name())) {
        throw new IllegalStateException(
            "%s contains a duplicate name: '%s'".formatted(USERS_PROPERTY, user.name()));
      }
    }
  }
}
