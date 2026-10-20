/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.ObjectStores;
import java.io.IOException;

/**
 * Runs the Event Plane's service until the process is stopped. The store, the port and the peers
 * come from system properties or environment variables: see {@link ServiceSettings}, for example
 *
 * <pre>
 * java -Deventplane.store.type=filesystem -Deventplane.store.path=/data/event-plane \
 *      -Deventplane.service.port=8085 -Deventplane.service.peers=http://service-2:8085 \
 *      -cp … io.camunda.zeebe.eventplane.service.ServiceApp
 * </pre>
 */
public final class ServiceApp {

  private ServiceApp() {}

  public static void main(final String[] args) throws Exception {
    final var settings = ServiceSettings.fromEnvironment();
    final var store = ObjectStores.open(settings.store());
    final var service = new EventPlaneService(store, settings.service());
    Runtime.getRuntime()
        .addShutdownHook(new Thread(() -> stop(service, store), "event-plane-service-shutdown"));
    try {
      service.start();
    } catch (final IOException | RuntimeException e) {
      stop(service, store);
      throw e;
    }
    System.out.println("The Event Plane service listens on port " + service.port());
    // The HTTP server's threads do the work. This one waits for the shutdown hook to stop them.
    Thread.currentThread().join();
  }

  private static void stop(final EventPlaneService service, final ObjectStore store) {
    service.close();
    try {
      store.close();
    } catch (final IOException e) {
      System.err.println("Expected to close the store, but got " + e);
    }
  }
}
