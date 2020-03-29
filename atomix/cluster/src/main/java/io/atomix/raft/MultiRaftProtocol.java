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
package io.atomix.raft;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionService;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.ProxyProtocol;
import io.atomix.primitive.proxy.ProxyClient;
import io.atomix.primitive.proxy.impl.DefaultProxyClient;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionClient;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.utils.config.ConfigurationException;
import java.util.Collection;
import java.util.stream.Collectors;

/** Multi-Raft protocol. */
public class MultiRaftProtocol implements ProxyProtocol {

  public static final Type TYPE = new Type();
  private final MultiRaftProtocolConfig config;

  protected MultiRaftProtocol(final MultiRaftProtocolConfig config) {
    this.config = checkNotNull(config, "config cannot be null");
  }

  /**
   * Returns an instance of the multi-Raft protocol with the default configuration.
   *
   * @return an instance of the multi-Raft protocol with the default configuration
   */
  public static MultiRaftProtocol instance() {
    return new MultiRaftProtocol(new MultiRaftProtocolConfig());
  }

  /**
   * Returns a new multi-Raft protocol builder.
   *
   * @return a new multi-Raft protocol builder
   */
  public static MultiRaftProtocolBuilder builder() {
    return new MultiRaftProtocolBuilder(new MultiRaftProtocolConfig());
  }

  /**
   * Returns a new multi-Raft protocol builder.
   *
   * @param group the partition group
   * @return the multi-Raft protocol builder
   */
  public static MultiRaftProtocolBuilder builder(final String group) {
    return new MultiRaftProtocolBuilder(new MultiRaftProtocolConfig().setGroup(group));
  }

  @Override
  public PrimitiveProtocol.Type type() {
    return TYPE;
  }

  @Override
  public String group() {
    return config.getGroup();
  }

  @Override
  public <S> ProxyClient<S> newProxy(
      final String primitiveName,
      final PrimitiveType primitiveType,
      final Class<S> serviceType,
      final ServiceConfig serviceConfig,
      final PartitionService partitionService) {
    final PartitionGroup partitionGroup = partitionService.getPartitionGroup(this);
    if (partitionGroup == null) {
      throw new ConfigurationException(
          "No Raft partition group matching the configured protocol exists");
    }

    final Collection<SessionClient> partitions =
        partitionGroup.getPartitions().stream()
            .map(
                partition ->
                    ((RaftPartition) partition)
                        .getClient()
                        .sessionBuilder(primitiveName, primitiveType, serviceConfig)
                        .withMinTimeout(config.getMinTimeout())
                        .withMaxTimeout(config.getMaxTimeout())
                        .withReadConsistency(config.getReadConsistency())
                        .withCommunicationStrategy(config.getCommunicationStrategy())
                        .withRecoveryStrategy(config.getRecoveryStrategy())
                        .withMaxRetries(config.getMaxRetries())
                        .withRetryDelay(config.getRetryDelay())
                        .build())
            .collect(Collectors.toList());
    return new DefaultProxyClient<>(
        primitiveName, primitiveType, this, serviceType, partitions, config.getPartitioner());
  }

  /** Multi-Raft protocol type. */
  public static final class Type implements PrimitiveProtocol.Type<MultiRaftProtocolConfig> {

    private static final String NAME = "multi-raft";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public MultiRaftProtocolConfig newConfig() {
      return new MultiRaftProtocolConfig();
    }

    @Override
    public PrimitiveProtocol newProtocol(final MultiRaftProtocolConfig config) {
      return new MultiRaftProtocol(config);
    }
  }
}
