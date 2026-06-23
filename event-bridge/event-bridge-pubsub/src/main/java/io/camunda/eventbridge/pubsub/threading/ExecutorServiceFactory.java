/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.threading;

import java.util.concurrent.ExecutorService;

/**
 * Factory for creating {@link ExecutorService} instances. Abstracts the threading model —
 * implementations can provide virtual threads, a fixed thread pool, or a test executor.
 *
 * <p>Used by components that need to dispatch blocking or I/O-bound work off the main thread (e.g.,
 * fetch reads, compaction, bulk exports).
 */
@FunctionalInterface
public interface ExecutorServiceFactory {

  /**
   * Creates a new executor service.
   *
   * @param name logical name for the executor — implementations should use this for thread naming
   *     (e.g., "fetch-reader", "compaction")
   * @return a new executor service
   */
  ExecutorService create(String name);
}
