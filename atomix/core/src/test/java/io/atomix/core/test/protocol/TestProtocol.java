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
package io.atomix.core.test.protocol;

import static io.atomix.utils.concurrent.Threads.namedThreads;

import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionService;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.ProxyProtocol;
import io.atomix.primitive.proxy.ProxyClient;
import io.atomix.primitive.proxy.impl.DefaultProxyClient;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionClient;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.concurrent.ThreadPoolContext;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test primitive protocol. */
public class TestProtocol implements ProxyProtocol {
  public static final Type TYPE = new Type();
  private static final Logger LOGGER = LoggerFactory.getLogger(TestProtocol.class);
  private final ScheduledExecutorService threadPool =
      Executors.newScheduledThreadPool(4, namedThreads("test-protocol-service-%d", LOGGER));
  private final TestProtocolConfig config;
  private final TestProtocolServiceRegistry registry;
  private final AtomicLong sessionIds = new AtomicLong();

  TestProtocol(final TestProtocolConfig config) {
    this.config = config;
    this.registry = new TestProtocolServiceRegistry(threadPool);
  }

  /**
   * Returns a new test protocol builder.
   *
   * @return a new test protocol builder
   */
  public static TestProtocolBuilder builder() {
    return new TestProtocolBuilder(new TestProtocolConfig());
  }

  @Override
  public Type type() {
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
    final Collection<SessionClient> partitions =
        IntStream.range(0, config.getPartitions())
            .mapToObj(
                partition -> {
                  final SessionId sessionId = SessionId.from(sessionIds.incrementAndGet());
                  return new TestSessionClient(
                      primitiveName,
                      primitiveType,
                      sessionId,
                      PartitionId.from(group(), partition),
                      new ThreadPoolContext(threadPool),
                      registry.getOrCreateService(
                          PartitionId.from(group(), partition),
                          primitiveName,
                          primitiveType,
                          serviceConfig));
                })
            .collect(Collectors.toList());
    return new DefaultProxyClient<>(
        primitiveName, primitiveType, this, serviceType, partitions, config.getPartitioner());
  }

  /** Closes the protocol. */
  public void close() {
    threadPool.shutdownNow();
  }

  /** Multi-Raft protocol type. */
  public static final class Type implements PrimitiveProtocol.Type<TestProtocolConfig> {
    private static final String NAME = "multi-raft";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public TestProtocolConfig newConfig() {
      return new TestProtocolConfig();
    }

    @Override
    public PrimitiveProtocol newProtocol(final TestProtocolConfig config) {
      return new TestProtocol(config);
    }
  }
}
