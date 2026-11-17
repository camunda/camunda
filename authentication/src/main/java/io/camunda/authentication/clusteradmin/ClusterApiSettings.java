/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import io.camunda.authentication.config.AuthenticationProperties;
import java.util.Locale;
import java.util.OptionalInt;
import org.springframework.core.env.Environment;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Where and how the cluster-wide REST API ({@code /cluster/v2/**}) is served.
 *
 * <p>By default the cluster API shares {@code server.port} with the rest of the REST API and its
 * cluster-admin chain follows the global authentication method. Setting {@value #PORT_PROPERTY}
 * moves it to a dedicated listener; {@value #AUTHENTICATION_METHOD_PROPERTY} picks the
 * cluster-admin authentication independently of the global method.
 */
public final class ClusterApiSettings {

  public static final String PORT_PROPERTY = "camunda.api.cluster.port";
  public static final String ADDRESS_PROPERTY = "camunda.api.cluster.address";
  public static final String AUTHENTICATION_METHOD_PROPERTY =
      "camunda.security.cluster-admin.authentication.method";

  public static final String CLUSTER_API_PREFIX = "/cluster/v2";
  public static final String CLUSTER_API_PATTERN = CLUSTER_API_PREFIX + "/**";

  private ClusterApiSettings() {}

  /**
   * @return the dedicated cluster API port, or empty when the cluster API shares {@code
   *     server.port}
   * @throws IllegalStateException if the dedicated port collides with the REST or management port
   */
  public static OptionalInt dedicatedPort(final Environment environment) {
    final Integer port = environment.getProperty(PORT_PROPERTY, Integer.class);
    if (port == null) {
      return OptionalInt.empty();
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalStateException(
          "%s must be a port between 1 and 65535, but was %d".formatted(PORT_PROPERTY, port));
    }
    rejectCollision(environment, port, "server.port");
    rejectCollision(environment, port, "management.server.port");
    return OptionalInt.of(port);
  }

  /**
   * Matches requests to the cluster API. With a dedicated port, only requests that arrived on that
   * port's connector match — the local port is the socket the connection was accepted on, unlike
   * {@code getServerPort()}, which comes from the client-controlled {@code Host} header.
   */
  public static RequestMatcher clusterApiMatcher(final Environment environment) {
    final RequestMatcher path =
        PathPatternRequestMatcher.withDefaults().matcher(CLUSTER_API_PATTERN);
    final OptionalInt port = dedicatedPort(environment);
    if (port.isEmpty()) {
      return path;
    }
    final int dedicated = port.getAsInt();
    return new AndRequestMatcher(path, request -> request.getLocalPort() == dedicated);
  }

  /**
   * @return the cluster-admin authentication method: the explicit {@value
   *     #AUTHENTICATION_METHOD_PROPERTY}, else the global authentication method (default BASIC)
   */
  public static ClusterAdminAuthenticationMethod authenticationMethod(
      final Environment environment) {
    final String explicit = environment.getProperty(AUTHENTICATION_METHOD_PROPERTY);
    if (explicit != null && !explicit.isBlank()) {
      return parse(explicit, AUTHENTICATION_METHOD_PROPERTY);
    }
    return parse(
        environment.getProperty(AuthenticationProperties.METHOD, "basic"),
        AuthenticationProperties.METHOD);
  }

  private static ClusterAdminAuthenticationMethod parse(final String value, final String property) {
    try {
      return ClusterAdminAuthenticationMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      throw new IllegalStateException(
          "%s has unsupported value '%s'; expected one of basic, oidc, none"
              .formatted(property, value),
          e);
    }
  }

  private static void rejectCollision(
      final Environment environment, final int port, final String otherProperty) {
    final Integer other = environment.getProperty(otherProperty, Integer.class);
    if (other != null && other == port) {
      throw new IllegalStateException(
          "%s (%d) must differ from %s".formatted(PORT_PROPERTY, port, otherProperty));
    }
  }

  public enum ClusterAdminAuthenticationMethod {
    BASIC,
    OIDC,
    NONE
  }
}
