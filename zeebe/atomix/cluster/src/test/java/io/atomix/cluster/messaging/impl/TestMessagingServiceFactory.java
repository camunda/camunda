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
package io.atomix.cluster.messaging.impl;

import com.google.common.collect.Maps;
import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.utils.net.Address;
import java.util.Map;

/** Test messaging service factory. */
public class TestMessagingServiceFactory {
  private final Map<Address, TestMessagingService> services = Maps.newConcurrentMap();

  /**
   * Partitions the service at the given address.
   *
   * @param address the address of the service to partition
   */
  public void partition(final Address address) {
    final TestMessagingService service = services.get(address);
    services.values().stream()
        .filter(s -> !s.address().equals(address))
        .forEach(
            s -> {
              service.partition(s.address());
              s.partition(service.address());
            });
  }

  /**
   * Heals a partition of the service at the given address.
   *
   * @param address the address of the service to heal
   */
  public void heal(final Address address) {
    final TestMessagingService service = services.get(address);
    services.values().stream()
        .filter(s -> !s.address().equals(address))
        .forEach(
            s -> {
              service.heal(s.address());
              s.heal(service.address());
            });
  }

  /**
   * Creates a bi-directional partition between two services.
   *
   * @param address1 the first service
   * @param address2 the second service
   */
  public void partition(final Address address1, final Address address2) {
    final TestMessagingService service1 = services.get(address1);
    final TestMessagingService service2 = services.get(address2);
    service1.partition(service2.address());
    service2.partition(service1.address());
  }

  /**
   * Heals a bi-directional partition between two services.
   *
   * @param address1 the first service
   * @param address2 the second service
   */
  public void heal(final Address address1, final Address address2) {
    final TestMessagingService service1 = services.get(address1);
    final TestMessagingService service2 = services.get(address2);
    service1.heal(service2.address());
    service2.heal(service1.address());
  }

  /**
   * Returns a new test messaging service for the given address.
   *
   * @param address the address for which to return a messaging service
   * @return the messaging service for the given address
   */
  public ManagedMessagingService newMessagingService(final Address address) {
    return new TestMessagingService(address, services);
  }
}
