/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging;

import com.google.common.collect.Maps;
import io.atomix.utils.net.Address;
import java.util.Map;

/** Test unicast service factory. */
public class TestUnicastServiceFactory {
  private final Map<Address, TestUnicastService> services = Maps.newConcurrentMap();

  /**
   * Partitions the service at the given address.
   *
   * @param address the address of the service to partition
   */
  public void partition(final Address address) {
    final TestUnicastService service = services.get(address);
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
    final TestUnicastService service = services.get(address);
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
    final TestUnicastService service1 = services.get(address1);
    final TestUnicastService service2 = services.get(address2);
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
    final TestUnicastService service1 = services.get(address1);
    final TestUnicastService service2 = services.get(address2);
    service1.heal(service2.address());
    service2.heal(service1.address());
  }

  /**
   * Returns a new test unicast service for the given endpoint.
   *
   * @param address the address to which to bind
   * @return the unicast service for the given endpoint
   */
  public ManagedUnicastService newUnicastService(final Address address) {
    return new TestUnicastService(address, services);
  }
}
