/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.primitive.proxy.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.io.BaseEncoding;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.Partitioner;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.proxy.ProxySession;
import io.atomix.primitive.session.SessionClient;
import io.atomix.utils.serializer.Serializer;
import java.util.Collection;
import java.util.stream.Collectors;

/** Default proxy client. */
public class DefaultProxyClient<S> extends AbstractProxyClient<S> {
  private final Partitioner<String> partitioner;
  private final Serializer serializer;

  public DefaultProxyClient(
      final String name,
      final PrimitiveType type,
      final PrimitiveProtocol protocol,
      final Class<S> serviceType,
      final Collection<SessionClient> partitions,
      final Partitioner<String> partitioner) {
    super(name, type, protocol, createSessions(type, serviceType, partitions));
    this.partitioner = checkNotNull(partitioner);
    this.serializer = Serializer.using(type.namespace());
  }

  private static <S> Collection<ProxySession<S>> createSessions(
      final PrimitiveType primitiveType,
      final Class<S> serviceType,
      final Collection<SessionClient> partitions) {
    final Serializer serializer = Serializer.using(primitiveType.namespace());
    return partitions.stream()
        .map(partition -> new DefaultProxySession<>(partition, serviceType, serializer))
        .collect(Collectors.toList());
  }

  @Override
  public PartitionId getPartitionId(final String key) {
    return partitioner.partition(key, getPartitionIds());
  }

  @Override
  public PartitionId getPartitionId(final Object key) {
    return partitioner.partition(
        BaseEncoding.base16().encode(serializer.encode(key)), getPartitionIds());
  }
}
