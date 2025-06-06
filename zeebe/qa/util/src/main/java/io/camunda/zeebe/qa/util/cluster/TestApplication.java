/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.common.collect.ObjectArrays;
import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;

@SuppressWarnings("UnusedReturnValue")
public interface TestApplication<T extends TestApplication<T>> extends AutoCloseable {

  /** Starts the node in a blocking fashion. */
  T start();

  /** Attempts to stop the container gracefully in a blocking fashion. */
  T stop();

  /** Returns whether the underlying application is started yet; does not include any probes */
  boolean isStarted();

  @Override
  default void close() {
    //noinspection resource
    stop();
  }

  /** Convenience method to return the appropriate concrete type */
  T self();

  /**
   * When the underlying application is started, all beans of the given type will resolve to the
   * given value. The qualifier is useful for cases where more than one beans of the same type are
   * defined with different qualifiers.
   *
   * @param qualifier the bean name/qualifier
   * @param bean the object to inject as the bean value
   * @param type the type to be resolved/autowired
   * @return itself for chaining
   * @param <V> the bean type
   */
  @SuppressWarnings("UnusedReturnValue")
  <V> T withBean(final String qualifier, final V bean, final Class<V> type);

  /** Returns this node's unique cluster ID */
  MemberId nodeId();

  /**
   * Returns the hostname of this node, such that it is visible to hosts from the outside of the
   * Docker network.
   *
   * @return the hostname of this node
   */
  default String host() {
    return "localhost";
  }

  /** Returns the actual port for the given logical port. */
  default int mappedPort(final TestZeebePort port) {
    return port.port();
  }

  /**
   * Returns the address of this node for the given port.
   *
   * @param port the target port
   * @return externally accessible address for {@code port}
   */
  default String address(final int port) {
    return host() + ":" + port;
  }

  /**
   * Returns the address of this node for the given port.
   *
   * @param port the target port
   * @return externally accessible address for {@code port}
   */
  default String address(final TestZeebePort port) {
    return address(mappedPort(port));
  }

  /**
   * Returns the address of this node for the given port and scheme as a URI.
   *
   * @param scheme the URI scheme, e.g. http, or https
   * @param port the target port
   * @return externally accessible address for {@code port}
   */
  default URI uri(final String scheme, final TestZeebePort port, final String... paths) {
    return uri(scheme, port, List.of(paths));
  }

  /**
   * Returns the address of this node for the given port and scheme as a URI.
   *
   * @param scheme the URI scheme, e.g. http, or https
   * @param port the target port
   * @return externally accessible address for {@code port}
   */
  default URI uri(
      final String scheme, final TestZeebePort port, final SequencedCollection<String> paths) {
    try {
      final var path =
          paths.stream()
              .filter(Predicate.not(Objects::isNull))
              .map(String::trim)
              .map(p -> p.replaceFirst("^\\/", ""))
              .filter(Predicate.not(String::isBlank))
              .collect(Collectors.joining("/"));
      return new URI(scheme + "://" + address(port) + "/" + path);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("Failed to parse URI", e);
    }
  }

  /**
   * Returns the address to access the monitoring API of this node from outside the container
   * network of this node. This method returns a URI which is scheme and context path aware.
   *
   * @return the external monitoring address
   */
  default URI monitoringUri(final String... paths) {
    final var serverBasePath = property("management.server.base-path", String.class, "");
    final var sslEnabled = property("management.server.ssl.enabled", Boolean.class, false);
    return uri(
        sslEnabled ? "https" : "http",
        TestZeebePort.MONITORING,
        ObjectArrays.concat(serverBasePath, paths));
  }

  /**
   * Returns the address to access the actuators of this node from outside the container network of
   * this node. This method returns a URI which is scheme and context-path aware, as well as
   * actuator path aware.
   *
   * @return the external actuator address
   */
  default URI actuatorUri(final String... paths) {
    final var actuatorBasePath =
        property("management.endpoints.web.base-path", String.class, "/actuator");
    return monitoringUri(ObjectArrays.concat(actuatorBasePath, paths));
  }

  /** Returns the default health actuator for this application. */
  HealthActuator healthActuator();

  /** Probes for the given health probe; throws an exception on failure. */
  default void probe(final TestHealthProbe probe) {
    switch (probe) {
      case LIVE -> healthActuator().live();
      case READY -> healthActuator().ready();
      case STARTED -> healthActuator().startup();
      default -> throw new IllegalStateException("Unexpected value: " + probe);
    }
  }

  /** Returns true if this node can act as a gateway (e.g. broker with embedded gateway) */
  boolean isGateway();

  /**
   * Blocks and waits until the given health probe succeeds, or the given timeout is reached.
   *
   * @param probe the type of probe to await
   */
  default T await(final TestHealthProbe probe, final Duration timeout) {
    Awaitility.await("until broker '%s' is '%s'".formatted(nodeId(), probe))
        .atMost(timeout)
        .untilAsserted(() -> assertThatCode(() -> probe(probe)).doesNotThrowAnyException());

    return self();
  }

  /** Convenience method to wait for a probe for a default timeout of 30 seconds. */
  default T await(final TestHealthProbe probe) {
    return await(probe, Duration.ofSeconds(30));
  }

  /**
   * If the application is started (e.g. {@link #isStarted()}, resolves and returns (i.e.
   * auto-wires) the first bean of the given type.
   *
   * @param type the expected bean type
   * @return the bean (if any was resolved), or null
   * @param <V> the expected bean type
   */
  <V> V bean(final Class<V> type);

  /**
   * If the application is started (e.g. {@link #isStarted()}, resolves and returns the value for
   * this property, or a given fallback if there was none set. If the application is not started, it
   * will look it up only in the property overrides (e.g. {@link #withProperty(String, Object)}.
   *
   * @param property the key identifying this property
   * @param type the expected type of the property value
   * @param fallback a default value if the property is not set
   * @return the value of this (if any was resolved), or the fallback value
   * @param <V> the expected property type type
   */
  <V> V property(final String property, final Class<V> type, final V fallback);

  /**
   * Configures Spring via properties. This does not work with properties that would be applied to
   * injected beans (e.g. via {@link #withBean(String, Object, Class)}), since there will be
   * property resolution for these beans.
   *
   * @param key the property key
   * @param value the new value
   * @return itself for chaining
   */
  T withProperty(final String key, final Object value);

  default T withAdditionalProperties(final Map<String, Object> properties) {
    properties.forEach(this::withProperty);
    return self();
  }

  /**
   * Configures additional active Spring profiles.
   *
   * @param profile the profile ID
   * @return itself for chaining
   */
  T withAdditionalProfile(final String profile);

  /**
   * Configures additional active Spring profiles.
   *
   * @return itself for chaining
   */
  default T withAdditionalProfiles(final Collection<Profile> profiles) {
    profiles.forEach(this::withAdditionalProfile);
    return self();
  }

  /**
   * Configures additional active Spring profiles.
   *
   * @return itself for chaining
   */
  default T withAdditionalProfiles(final Profile... profiles) {
    return withAdditionalProfiles(List.of(profiles));
  }

  /**
   * @see #withAdditionalProfile(String)
   */
  default T withAdditionalProfile(final Profile profile) {
    return withAdditionalProfile(profile.getId());
  }
}
