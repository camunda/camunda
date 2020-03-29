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

import com.google.common.collect.Maps;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.utils.concurrent.ThreadPoolContext;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/** Test protocol service registry. */
public class TestProtocolServiceRegistry {
  private final ScheduledExecutorService threadPool;
  private final Map<PartitionId, Map<String, TestProtocolService>> partitions =
      Maps.newConcurrentMap();

  TestProtocolServiceRegistry(final ScheduledExecutorService threadPool) {
    this.threadPool = threadPool;
  }

  /**
   * Gets or creates a test service.
   *
   * @param partitionId the partition identifier
   * @param name the service name
   * @param type the service type
   * @param config the service configuration
   * @return the test service
   */
  public TestProtocolService getOrCreateService(
      final PartitionId partitionId,
      final String name,
      final PrimitiveType type,
      final ServiceConfig config) {
    return partitions
        .computeIfAbsent(partitionId, id -> Maps.newConcurrentMap())
        .computeIfAbsent(
            name,
            n ->
                new TestProtocolService(
                    partitionId,
                    n,
                    type,
                    config,
                    type.newService(config),
                    this,
                    new ThreadPoolContext(threadPool)));
  }

  /**
   * Removes the given service.
   *
   * @param partitionId the partition identifier
   * @param name the service name
   */
  public void removeService(final PartitionId partitionId, final String name) {
    final Map<String, TestProtocolService> services = partitions.get(partitionId);
    if (services != null) {
      services.remove(name);
    }
  }
}
