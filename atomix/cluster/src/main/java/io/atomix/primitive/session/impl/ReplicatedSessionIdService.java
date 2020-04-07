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
package io.atomix.primitive.session.impl;

import static io.atomix.primitive.operation.PrimitiveOperation.operation;
import static io.atomix.primitive.session.impl.SessionIdGeneratorOperations.NEXT;

import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.ManagedSessionIdService;
import io.atomix.primitive.session.SessionClient;
import io.atomix.primitive.session.SessionId;
import io.atomix.primitive.session.SessionIdService;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Serializer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Replicated ID generator service. */
public class ReplicatedSessionIdService implements ManagedSessionIdService {
  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder().register(SessionIdGeneratorOperations.NAMESPACE).build());
  private static final String PRIMITIVE_NAME = "session-id";

  private final PartitionGroup systemPartitionGroup;
  private SessionClient proxy;
  private final AtomicBoolean started = new AtomicBoolean();

  public ReplicatedSessionIdService(final PartitionGroup systemPartitionGroup) {
    this.systemPartitionGroup = systemPartitionGroup;
  }

  @Override
  public CompletableFuture<SessionId> nextSessionId() {
    return proxy
        .execute(operation(NEXT))
        .<Long>thenApply(SERIALIZER::decode)
        .thenApply(SessionId::from);
  }

  @Override
  public CompletableFuture<SessionIdService> start() {
    return systemPartitionGroup
        .getPartitions()
        .iterator()
        .next()
        .getClient()
        .sessionBuilder(PRIMITIVE_NAME, SessionIdGeneratorType.instance(), new ServiceConfig())
        .build()
        .connect()
        .thenApply(
            proxy -> {
              this.proxy = proxy;
              started.set(true);
              return this;
            });
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    return proxy.close().exceptionally(v -> null).thenRun(() -> started.set(false));
  }
}
