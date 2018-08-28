/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport.impl;

import io.zeebe.transport.EndpointRegistry;
import io.zeebe.transport.Loggers;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RemoteAddressList;
import io.zeebe.transport.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Thread safe implementation of {@link EndpointRegistry} using {@link ConcurrentHashMap}s.
 *
 * <p>The implementation takes care of registering and deactivating remote addresses using the
 * provided {@link RemoteAddressList}.
 */
public class EndpointRegistryImpl implements EndpointRegistry {

  public static final Logger LOG = Loggers.TRANSPORT_ENDPOINT_LOGGER;

  private final String name;
  private final RemoteAddressList remoteAddressList;

  private final Map<Integer, RemoteAddress> endpoints = new ConcurrentHashMap<>();

  public EndpointRegistryImpl(String name, RemoteAddressList remoteAddressList) {
    this.name = name;
    this.remoteAddressList = remoteAddressList;
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * Get the current remote address for endpoint of the given node
   *
   * @param nodeId the id of the node of the endpoint to get
   * @return the remote address for the endpoint of the node or null if non is set
   */
  @Override
  public RemoteAddress getEndpoint(final Integer nodeId) {
    return nodeId != null ? endpoints.get(nodeId) : null;
  }

  /**
   * Set the current endpoint address for the node id. It will also register the socket address so a
   * transport channel will be managed for this endpoint.
   *
   * @param nodeId the node id of the endpoint
   * @param socketAddress the socket address of the endpoint
   * @return the previous socket address if it has changed, otherwise null
   */
  @Override
  public SocketAddress setEndpoint(final int nodeId, final SocketAddress socketAddress) {
    LOG.info(
        "Registering endpoint for node '{}' with address '{}' on transport '{}'",
        nodeId,
        socketAddress,
        name);
    final RemoteAddress remoteAddress = remoteAddressList.register(socketAddress);
    final RemoteAddress lastRemoteAddress = endpoints.put(nodeId, remoteAddress);
    if (lastRemoteAddress != null
        && lastRemoteAddress.getStreamId() != remoteAddress.getStreamId()) {
      return deactivateRemote(nodeId, lastRemoteAddress);
    } else {
      return null;
    }
  }

  /**
   * Remove the endpoint for the given node id. If an endpoint is set it will be deactivated on the
   * transport so the channel can be closed.
   *
   * @param nodeId the id of the node for this endpoint
   * @return the previous set socket address or null if non was set
   */
  @Override
  public SocketAddress removeEndpoint(final int nodeId) {
    final RemoteAddress remoteAddress = endpoints.remove(nodeId);
    if (remoteAddress != null) {
      return deactivateRemote(nodeId, remoteAddress);
    } else {
      return null;
    }
  }

  /**
   * Remove the endpoint for the given node id. If an endpoint is set it will be retired on the
   * transport so the channel can be closed. And the internal remote address become invalid.
   *
   * @param nodeId the id of the node for this endpoint
   * @return the previous set socket address or null if non was set
   */
  @Override
  public SocketAddress retire(int nodeId) {
    final RemoteAddress remoteAddress = endpoints.remove(nodeId);
    if (remoteAddress != null) {
      return retireRemote(nodeId, remoteAddress);
    } else {
      return null;
    }
  }

  private SocketAddress deactivateRemote(int nodeId, RemoteAddress remoteAddress) {
    LOG.info(
        "Deactivating endpoint for node '{}' with address '{}' on transport '{}'",
        nodeId,
        remoteAddress,
        name);
    remoteAddressList.deactivate(remoteAddress);
    return remoteAddress.getAddress();
  }

  private SocketAddress retireRemote(int nodeId, RemoteAddress remoteAddress) {
    LOG.info(
        "Retiring endpoint for node '{}' with address '{}' on transport '{}'",
        nodeId,
        remoteAddress,
        name);
    remoteAddressList.retire(remoteAddress);
    return remoteAddress.getAddress();
  }
}
