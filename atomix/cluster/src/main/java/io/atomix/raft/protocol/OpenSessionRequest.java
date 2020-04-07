/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.protocol;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.raft.ReadConsistency;
import java.util.Objects;

/** Open session request. */
public class OpenSessionRequest extends AbstractRaftRequest {

  private final String node;
  private final String name;
  private final String typeName;
  private final byte[] config;
  private final ReadConsistency readConsistency;
  private final long minTimeout;
  private final long maxTimeout;

  public OpenSessionRequest(
      final String node,
      final String name,
      final String typeName,
      final byte[] config,
      final ReadConsistency readConsistency,
      final long minTimeout,
      final long maxTimeout) {
    this.node = node;
    this.name = name;
    this.typeName = typeName;
    this.config = config;
    this.readConsistency = readConsistency;
    this.minTimeout = minTimeout;
    this.maxTimeout = maxTimeout;
  }

  /**
   * Returns a new open session request builder.
   *
   * @return A new open session request builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the client node identifier.
   *
   * @return The client node identifier.
   */
  public String node() {
    return node;
  }

  /**
   * Returns the state machine name.
   *
   * @return The state machine name.
   */
  public String serviceName() {
    return name;
  }

  /**
   * Returns the state machine type;
   *
   * @return The state machine type.
   */
  public String serviceType() {
    return typeName;
  }

  /**
   * Returns the service configuration.
   *
   * @return the service configuration
   */
  public byte[] serviceConfig() {
    return config;
  }

  /**
   * Returns the session read consistency level.
   *
   * @return The session's read consistency.
   */
  public ReadConsistency readConsistency() {
    return readConsistency;
  }

  /**
   * Returns the minimum session timeout.
   *
   * @return The minimum session timeout.
   */
  public long minTimeout() {
    return minTimeout;
  }

  /**
   * Returns the maximum session timeout.
   *
   * @return The maximum session timeout.
   */
  public long maxTimeout() {
    return maxTimeout;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), name, typeName, minTimeout, maxTimeout);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof OpenSessionRequest) {
      final OpenSessionRequest request = (OpenSessionRequest) object;
      return request.node.equals(node)
          && request.name.equals(name)
          && request.typeName.equals(typeName)
          && request.readConsistency == readConsistency
          && request.minTimeout == minTimeout
          && request.maxTimeout == maxTimeout;
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("node", node)
        .add("serviceName", name)
        .add("serviceType", typeName)
        .add("readConsistency", readConsistency)
        .add("minTimeout", minTimeout)
        .add("maxTimeout", maxTimeout)
        .toString();
  }

  /** Open session request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, OpenSessionRequest> {

    private String memberId;
    private String serviceName;
    private String serviceType;
    private byte[] serviceConfig;
    private ReadConsistency readConsistency = ReadConsistency.LINEARIZABLE;
    private long minTimeout;
    private long maxTimeout;

    /**
     * Sets the client node identifier.
     *
     * @param node The client node identifier.
     * @return The open session request builder.
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public Builder withMemberId(final MemberId node) {
      this.memberId = checkNotNull(node, "node cannot be null").id();
      return this;
    }

    /**
     * Sets the service name.
     *
     * @param serviceName The service name.
     * @return The open session request builder.
     * @throws NullPointerException if {@code serviceName} is {@code null}
     */
    public Builder withServiceName(final String serviceName) {
      this.serviceName = checkNotNull(serviceName, "serviceName cannot be null");
      return this;
    }

    /**
     * Sets the service type name.
     *
     * @param primitiveType The service type name.
     * @return The open session request builder.
     * @throws NullPointerException if {@code serviceType} is {@code null}
     */
    public Builder withServiceType(final PrimitiveType primitiveType) {
      this.serviceType = checkNotNull(primitiveType, "serviceType cannot be null").name();
      return this;
    }

    /**
     * Sets the service configuration.
     *
     * @param config the service configuration
     * @return the open session request builder
     * @throws NullPointerException if the configuration is {@code null}
     */
    public Builder withServiceConfig(final byte[] config) {
      this.serviceConfig = checkNotNull(config, "config cannot be null");
      return this;
    }

    /**
     * Sets the session read consistency.
     *
     * @param readConsistency the session read consistency
     * @return the session request builder
     * @throws NullPointerException if the {@code readConsistency} is null
     */
    public Builder withReadConsistency(final ReadConsistency readConsistency) {
      this.readConsistency = checkNotNull(readConsistency, "readConsistency cannot be null");
      return this;
    }

    /**
     * Sets the minimum session timeout.
     *
     * @param timeout The minimum session timeout.
     * @return The open session request builder.
     * @throws IllegalArgumentException if {@code timeout} is not positive
     */
    public Builder withMinTimeout(final long timeout) {
      checkArgument(timeout >= 0, "timeout must be positive");
      this.minTimeout = timeout;
      return this;
    }

    /**
     * Sets the maximum session timeout.
     *
     * @param timeout The maximum session timeout.
     * @return The open session request builder.
     * @throws IllegalArgumentException if {@code timeout} is not positive
     */
    public Builder withMaxTimeout(final long timeout) {
      checkArgument(timeout >= 0, "timeout must be positive");
      this.maxTimeout = timeout;
      return this;
    }

    /** @throws IllegalStateException is session is not positive */
    @Override
    public OpenSessionRequest build() {
      validate();
      return new OpenSessionRequest(
          memberId,
          serviceName,
          serviceType,
          serviceConfig,
          readConsistency,
          minTimeout,
          maxTimeout);
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(memberId, "memberId cannot be null");
      checkNotNull(serviceName, "name cannot be null");
      checkNotNull(serviceType, "typeName cannot be null");
      checkNotNull(serviceConfig, "serviceConfig cannot be null");
      checkArgument(minTimeout >= 0, "minTimeout must be positive");
      checkArgument(maxTimeout >= 0, "maxTimeout must be positive");
    }
  }
}
