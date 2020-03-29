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

import com.google.common.io.BaseEncoding;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.log.LogClient;
import io.atomix.primitive.log.LogSession;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.proxy.ProxySession;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.utils.serializer.Serializer;
import java.util.Collection;
import java.util.stream.Collectors;

/** Log proxy client. */
public class LogProxyClient<S> extends AbstractProxyClient<S> {
  private final LogClient client;
  private final Serializer serializer;

  public LogProxyClient(
      final String name,
      final PrimitiveType type,
      final PrimitiveProtocol protocol,
      final Class<S> serviceType,
      final ServiceConfig serviceConfig,
      final LogClient client) {
    super(
        name,
        type,
        protocol,
        createSessions(name, type, serviceType, serviceConfig, client.getPartitions()));
    this.client = client;
    this.serializer = Serializer.using(type.namespace());
  }

  private static <S> Collection<ProxySession<S>> createSessions(
      final String name,
      final PrimitiveType primitiveType,
      final Class<S> serviceType,
      final ServiceConfig serviceConfig,
      final Collection<LogSession> partitions) {
    final Serializer serializer = Serializer.using(primitiveType.namespace());
    return partitions.stream()
        .map(
            partition ->
                new LogProxySession<S>(
                    name, primitiveType, serviceType, serviceConfig, serializer, partition))
        .collect(Collectors.toList());
  }

  @Override
  public PartitionId getPartitionId(final String key) {
    return client.getPartitionId(key);
  }

  @Override
  public PartitionId getPartitionId(final Object key) {
    return client.getPartitionId(BaseEncoding.base16().encode(serializer.encode(key)));
  }
}
