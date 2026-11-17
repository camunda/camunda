/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import io.camunda.authentication.config.AuthenticationProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Where and how the cluster-wide REST API ({@code /cluster/v2/**}) is served, bound from the
 * environment in one place so the security chains, the port gate and the extra connector cannot
 * disagree.
 *
 * <p>By default the cluster API shares {@code server.port} with the rest of the REST API and its
 * cluster-admin chain follows the global authentication method. Setting {@value #PORT_PROPERTY}
 * moves it to a dedicated listener; {@value #AUTHENTICATION_METHOD_PROPERTY} picks the
 * cluster-admin authentication independently of the global method.
 *
 * @param port the dedicated port, or {@code null} when the cluster API shares {@code server.port}
 * @param address the dedicated port's resolved bind address, or {@code null} to bind like {@code
 *     server.address}
 * @param authenticationMethod the effective cluster-admin authentication method
 */
@NullMarked
public record ClusterApiSettings(
    @Nullable Integer port,
    @Nullable String address,
    ClusterAdminAuthenticationMethod authenticationMethod) {

  public static final String PORT_PROPERTY = "camunda.api.cluster.port";
  public static final String ADDRESS_PROPERTY = "camunda.api.cluster.address";
  public static final String AUTHENTICATION_METHOD_PROPERTY =
      "camunda.security.cluster-admin.authentication.method";

  public static final String CLUSTER_API_PREFIX = "/cluster/v2";
  public static final String CLUSTER_API_PATTERN = CLUSTER_API_PREFIX + "/**";

  /**
   * Cluster API paths served without authentication. They stay reachable on the main port when the
   * cluster API moves to a dedicated port, so monitoring that probes the main port keeps working.
   */
  public static final Set<String> PUBLIC_CLUSTER_API_PATHS =
      Set.of(CLUSTER_API_PREFIX + "/status", CLUSTER_API_PREFIX + "/status/upgrade");

  /**
   * Names of the cluster-admin {@code SecurityFilterChain} beans. The dedicated port only opens
   * when one of them is defined, so it can never serve the cluster API without its security chain.
   */
  public static final String BASIC_CHAIN_BEAN = "clusterAdminSecurityFilterChain";

  public static final String OIDC_CHAIN_BEAN = "clusterAdminOidcSecurityFilterChain";
  public static final String NO_AUTH_CHAIN_BEAN = "clusterAdminNoAuthSecurityFilterChain";
  public static final Set<String> CHAIN_BEANS =
      Set.of(BASIC_CHAIN_BEAN, OIDC_CHAIN_BEAN, NO_AUTH_CHAIN_BEAN);

  private static final Set<String> COLLIDING_PORT_PROPERTIES =
      Set.of("server.port", "management.server.port", "camunda.api.grpc.port");

  /**
   * Binds and validates the settings.
   *
   * @throws IllegalStateException on an invalid port or authentication method, a port colliding
   *     with another listener, or {@code none} authentication without a dedicated port
   */
  public static ClusterApiSettings load(final Environment environment) {
    final Integer port = parsePort(environment);
    final String address = parseAddress(environment);
    final ClusterAdminAuthenticationMethod method = parseMethod(environment);
    if (method == ClusterAdminAuthenticationMethod.NONE) {
      // On the shared REST port, disabling authentication would expose cluster-wide destructive
      // operations to every caller that can reach the public API.
      if (port == null) {
        throw new IllegalStateException(
            "%s=none requires the cluster API on a dedicated port; set %s"
                .formatted(AUTHENTICATION_METHOD_PROPERTY, PORT_PROPERTY));
      }
      // Without an explicit address the port would bind wherever server.address does — usually
      // every interface — exposing unauthenticated destructive operations to the whole network.
      if (address == null) {
        throw new IllegalStateException(
            "%s=none requires an explicit bind address for the dedicated port; set %s (e.g. 127.0.0.1)"
                .formatted(AUTHENTICATION_METHOD_PROPERTY, ADDRESS_PROPERTY));
      }
    }
    return new ClusterApiSettings(port, address, method);
  }

  public boolean hasDedicatedPort() {
    return port != null;
  }

  /** Matches the cluster API on every port. */
  public RequestMatcher clusterApiMatcher() {
    return PathPatternRequestMatcher.withDefaults().matcher(CLUSTER_API_PATTERN);
  }

  /**
   * Matches the cluster API only on the dedicated port. The local port is the socket the connection
   * was accepted on, unlike {@code getServerPort()}, which comes from the client-controlled {@code
   * Host} header.
   */
  public RequestMatcher dedicatedPortMatcher() {
    final int dedicated = requireDedicatedPort();
    return new AndRequestMatcher(
        clusterApiMatcher(), request -> request.getLocalPort() == dedicated);
  }

  /** Matches the cluster API on every port except the dedicated one. */
  public RequestMatcher otherPortsMatcher() {
    final int dedicated = requireDedicatedPort();
    return new AndRequestMatcher(
        clusterApiMatcher(), request -> request.getLocalPort() != dedicated);
  }

  /**
   * @return the dedicated port
   * @throws IllegalStateException if the cluster API shares the main port
   */
  public int requireDedicatedPort() {
    if (port == null) {
      throw new IllegalStateException("%s is not configured".formatted(PORT_PROPERTY));
    }
    return port;
  }

  private static @Nullable Integer parsePort(final Environment environment) {
    final String raw = trimToNull(environment.getProperty(PORT_PROPERTY));
    if (raw == null) {
      return null;
    }
    final int port;
    try {
      port = Integer.parseInt(raw);
    } catch (final NumberFormatException e) {
      throw new IllegalStateException(
          "%s must be a port number, but was '%s'".formatted(PORT_PROPERTY, raw), e);
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalStateException(
          "%s must be a port between 1 and 65535, but was %d".formatted(PORT_PROPERTY, port));
    }
    for (final String other : COLLIDING_PORT_PROPERTIES) {
      final String otherValue = trimToNull(environment.getProperty(other));
      if (otherValue != null && otherValue.equals(Integer.toString(port))) {
        throw new IllegalStateException(
            "%s (%d) must differ from %s".formatted(PORT_PROPERTY, port, other));
      }
    }
    return port;
  }

  /**
   * Resolves the bind address once at startup. Tomcat silently keeps its previous (wildcard)
   * address when it cannot resolve the value, so a typo would otherwise widen the exposure.
   *
   * @return the resolved IP literal, or {@code null} when no address is configured
   */
  private static @Nullable String parseAddress(final Environment environment) {
    final String raw = trimToNull(environment.getProperty(ADDRESS_PROPERTY));
    if (raw == null) {
      return null;
    }
    try {
      return InetAddress.getByName(raw).getHostAddress();
    } catch (final UnknownHostException e) {
      throw new IllegalStateException(
          "%s '%s' cannot be resolved to an address".formatted(ADDRESS_PROPERTY, raw), e);
    }
  }

  private static ClusterAdminAuthenticationMethod parseMethod(final Environment environment) {
    final String explicit = trimToNull(environment.getProperty(AUTHENTICATION_METHOD_PROPERTY));
    if (explicit != null) {
      return parseMethod(explicit, AUTHENTICATION_METHOD_PROPERTY);
    }
    final String global = trimToNull(environment.getProperty(AuthenticationProperties.METHOD));
    return parseMethod(global == null ? "basic" : global, AuthenticationProperties.METHOD);
  }

  private static ClusterAdminAuthenticationMethod parseMethod(
      final String value, final String property) {
    try {
      return ClusterAdminAuthenticationMethod.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      throw new IllegalStateException(
          "%s has unsupported value '%s'; expected one of basic, oidc, none"
              .formatted(property, value),
          e);
    }
  }

  private static @Nullable String trimToNull(final @Nullable String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public enum ClusterAdminAuthenticationMethod {
    BASIC,
    OIDC,
    NONE
  }

  /**
   * Matches when a dedicated cluster API port is configured. A blank value (e.g. an empty
   * environment variable) counts as not configured, consistently with {@link #load}.
   */
  public static final class DedicatedPortCondition implements Condition {
    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return load(context.getEnvironment()).hasDedicatedPort();
    }
  }
}
