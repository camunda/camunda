/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.qa.util.actuator.GatewayHealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import org.awaitility.Awaitility;

/**
 * Represents a Zeebe gateway, either standalone or embedded.
 *
 * @param <T> the concrete type of the implementation
 */
public interface TestGateway<T extends TestGateway<T>> extends TestApplication<T> {

  /**
   * Returns the address used by clients to interact with the gateway.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .gatewayAddress(gateway.gatewayAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return the address for the gRPC gateway
   */
  default URI grpcAddress() {
    final var scheme = unifiedConfig().getApi().getGrpc().getSsl().isEnabled() ? "https" : "http";
    return uri(scheme, TestZeebePort.GATEWAY);
  }

  /**
   * Returns the address used by clients to interact with the gateway.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *    CamundaClient.newClientBuilder()
   *      .restAddress(gateway.restAddress())
   *      .usePlaintext()
   *      .build();
   *  }</pre>
   *
   * @return the REST gateway address
   */
  default URI restAddress() {
    final var basePath = property("server.servlet.context-path", String.class, "");
    final var sslEnabled = property("server.ssl.enabled", Boolean.class, false);
    return uri(sslEnabled ? "https" : "http", TestZeebePort.REST, basePath);
  }

  default URI actuatorAddress(final String path) {
    final var basePath = property("server.servlet.context-path", String.class, "");
    return uri("http", TestZeebePort.MONITORING, basePath, "actuator", path);
  }

  /**
   * Returns the health actuator for this gateway. You can use this to check for liveness,
   * readiness, and startup.
   */
  default GatewayHealthActuator gatewayHealth() {
    return GatewayHealthActuator.of(this);
  }

  @Override
  default HealthActuator healthActuator() {
    return gatewayHealth();
  }

  @Override
  default boolean isGateway() {
    return true;
  }

  /**
   * Modifies the unified configuration (camunda.* properties). This is the recommended way to
   * configure test gateways going forward.
   *
   * @param modifier a configuration function that accepts the Camunda configuration object
   * @return itself for chaining
   */
  @Override
  T withUnifiedConfig(final Consumer<Camunda> modifier);

  /** Returns the unified configuration for this node */
  Camunda unifiedConfig();

  /** Returns a new pre-configured client builder for this gateway */
  default CamundaClientBuilder newClientBuilder() {
    final var builder =
        CamundaClient.newClientBuilder()
            .grpcAddress(grpcAddress())
            .restAddress(restAddress())
            .preferRestOverGrpc(false);
    final var security = unifiedConfig().getApi().getGrpc().getSsl();
    final var restSSL = property("server.ssl.enabled", Boolean.class, false);
    if (security.isEnabled() || restSSL) {
      builder.caCertificatePath(security.getCertificate().getAbsolutePath());
    }

    return builder;
  }

  /**
   * Blocks until the topology is complete. See {@link TopologyAssert#isComplete(int, int, int)} for
   * semantics.
   *
   * @return itself for chaining
   * @see TopologyAssert#isComplete(int, int, int)
   */
  default T awaitCompleteTopology(
      final int clusterSize,
      final int partitionCount,
      final int replicationFactor,
      final Duration timeout) {
    try (final var client = newClientBuilder().build()) {
      awaitCompleteTopology(clusterSize, partitionCount, replicationFactor, timeout, client);
    }

    return self();
  }

  /**
   * Blocks until the topology is complete. See {@link TopologyAssert#isComplete(int, int, int)} for
   * semantics.
   *
   * @return itself for chaining
   * @see TopologyAssert#isComplete(int, int, int)
   */
  default T awaitCompleteTopology(
      final int clusterSize,
      final int partitionCount,
      final int replicationFactor,
      final Duration timeout,
      final CamundaClient client) {
    Awaitility.await("until cluster topology is complete")
        .atMost(timeout)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .isComplete(clusterSize, partitionCount, replicationFactor));
    return self();
  }

  /**
   * Convenience method to await complete topology of single node clusters.
   *
   * @return itself for chaining
   */
  default T awaitCompleteTopology() {
    return awaitCompleteTopology(1, 1, 1, Duration.ofSeconds(30));
  }

  /**
   * Method to await the complete topology of a cluster with the given unified configuration.
   *
   * @param unifiedConfig the unified configuration containing cluster settings
   * @return itself for chaining
   */
  default T awaitCompleteTopology(final Camunda unifiedConfig) {
    final var clusterCfg = unifiedConfig.getCluster();
    return awaitCompleteTopology(
        clusterCfg.getSize(),
        clusterCfg.getPartitionCount(),
        clusterCfg.getReplicationFactor(),
        Duration.ofSeconds(30));
  }

  /**
   * Method to await the complete topology of a cluster with the given unified configuration.
   *
   * @param unifiedConfig the unified configuration containing cluster settings
   * @param camundaClient the client to use for checking topology
   * @return itself for chaining
   */
  default T awaitCompleteTopology(final Camunda unifiedConfig, final CamundaClient camundaClient) {
    final var clusterCfg = unifiedConfig.getCluster();
    return awaitCompleteTopology(
        clusterCfg.getSize(),
        clusterCfg.getPartitionCount(),
        clusterCfg.getReplicationFactor(),
        Duration.ofSeconds(30),
        camundaClient);
  }
}
