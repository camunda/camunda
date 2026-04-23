/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Creates virtual-thread-per-task executors. Each submitted task runs on its own virtual thread.
 * Virtual threads unmount from the carrier thread during blocking I/O (e.g., mmap page faults),
 * allowing other virtual threads to continue without stalling.
 *
 * <p>Thread names follow the pattern {@code {name}-{sequence}}, e.g., {@code fetch-reader-0},
 * {@code fetch-reader-1}.
 */
public final class VirtualThreadExecutorFactory implements ExecutorServiceFactory {

  @Override
  public ExecutorService create(final String name) {
    Executors.newScheduledThreadPool(4, Thread.ofVirtual().name(name + "-", 0).factory());
    return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(name + "-", 0).factory());
  }
}
