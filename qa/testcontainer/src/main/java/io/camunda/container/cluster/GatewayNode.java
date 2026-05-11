/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container.cluster;

import io.camunda.container.ZeebeTopologyWaitStrategy;
import java.net.URI;
import org.testcontainers.containers.GenericContainer;

public interface GatewayNode<T extends GenericContainer<T> & GatewayNode<T>>
    extends ClusterNode<T> {

  /**
   * Override the default topology check to change the number of expected partitions or the gateway
   * port if necessary. Overwrites previously set wait strategies with the container's default wait
   * strategy and the given topology check.
   *
   * <p>For example, to change the number of expected partitions for a complete topology, you can
   * do:
   *
   * <pre>{@code
   * gateway.withTopologyCheck(new TopologyWaitStrategy().forExpectedPartitionsCount(3));
   * }</pre>
   *
   * <p>NOTE: this may mutate the underlying wait strategy, so if you are configured a specific
   * startup timeout, it will need to be applied again after a call to this method.
   *
   * @param topologyCheck the new topology check
   * @return this container, for chaining
   */
  T withTopologyCheck(final ZeebeTopologyWaitStrategy topologyCheck);

  /**
   * Convenience method to disable the topology check. Overwrites previously set wait strategies
   * with the container's default wait strategy, without the topology check.
   *
   * @return this container, for chaining
   */
  T withoutTopologyCheck();

  /**
   * Returns an address accessible from within the container's network for the REST API. Primarily
   * meant to be used by clients.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getInternalRestUrl())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return internally accessible REST API address
   */
  default URI getInternalRestAddress() {
    return getInternalRestAddress("http");
  }

  /**
   * Returns an address accessible from within the container's network for the REST API. Primarily
   * meant to be used by clients.
   *
   * <p>Use this variant if you need to specify a different scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getInternalRestUrl("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return internally accessible REST API address
   */
  default URI getInternalRestAddress(final String scheme) {
    final int port = CamundaPort.GATEWAY_REST.getPort();
    return URI.create(String.format("%s://%s:%d", scheme, getInternalHost(), port));
  }

  /**
   * Returns the address of the REST API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use *
   * {@link #getInternalRestAddress()}
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getRestAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return externally accessible REST API address
   */
  default URI getRestAddress() {
    return getRestAddress("http");
  }

  /**
   * Returns the address of the REST API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use
   * {@link #getInternalRestAddress(String)}.
   *
   * <p>Use this method if you need to specify a different connection scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .restAddress(container.getExternalRestAddress("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return externally accessible REST API address
   */
  default URI getRestAddress(final String scheme) {
    final int port = getMappedPort(CamundaPort.GATEWAY_REST.getPort());
    return URI.create(String.format("%s://%s:%d", scheme, getExternalHost(), port));
  }

  /**
   * Returns an address accessible from within the container's network for the gRPC API. Primarily
   * meant to be used by clients.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getInternalGrpcAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return internally accessible gRPC API address
   */
  default URI getInternalGrpcAddress() {
    return getInternalGrpcAddress("http");
  }

  /**
   * Returns an address accessible from within the container's network for the gRPC API. Primarily
   * meant to be used by clients.
   *
   * <p>Use this variant if you need to specify a different scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getInternalGrpcAddress("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return internally accessible gRPC API address
   */
  default URI getInternalGrpcAddress(final String scheme) {
    final int port = CamundaPort.GATEWAY_GRPC.getPort();
    return URI.create(String.format("%s://%s:%d", scheme, getInternalHost(), port));
  }

  /**
   * Returns the address of the gRPC API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use
   * {@link #getInternalGrpcAddress()}.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getGrpcAddress())
   *     .usePlaintext()
   *     .build();
   * }</pre>
   *
   * @return externally accessible gRPC API address
   */
  default URI getGrpcAddress() {
    return getGrpcAddress("http");
  }

  /**
   * Returns the address of the gRPC API a client which is not part of the container's network
   * should use. If you want an address accessible from within the container's own network, use
   * {@link #getInternalGrpcAddress(String)}.
   *
   * <p>Use this method if you need to specify a different connection scheme, e.g. HTTPS.
   *
   * <p>You can build your client like this:
   *
   * <pre>@{code
   *   CamundaClient.newClientBuilder()
   *     .grpcAddress(container.getGrpcAddress("https"))
   *     .build();
   * }</pre>
   *
   * @param scheme the expected scheme (e.g. HTTP, HTTPS)
   * @return externally accessible gRPC API address
   */
  default URI getGrpcAddress(final String scheme) {
    final int port = getMappedPort(CamundaPort.GATEWAY_GRPC.getPort());
    return URI.create(String.format("%s://%s:%d", scheme, getExternalHost(), port));
  }
}
