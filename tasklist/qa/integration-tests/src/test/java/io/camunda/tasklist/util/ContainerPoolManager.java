/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

public class ContainerPoolManager<T extends GenericContainer<?>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPoolManager.class);

  private final ConcurrentLinkedQueue<T> availableContainers = new ConcurrentLinkedQueue<>();

  private final Supplier<T> containerCreator;

  private final ExecutorService executorService;

  private final int size;

  private final Class<T> containerType;

  public ContainerPoolManager(int size, Supplier<T> containerCreator, Class<T> containerType) {
    this.containerCreator = containerCreator;
    this.size = size;
    this.containerType = containerType;
    this.executorService = Executors.newFixedThreadPool(size);
  }

  public ContainerPoolManager init() {
    LOGGER.info("Initializing {} pool of size {}", containerType.getSimpleName(), size);
    IntStream.range(0, size)
        .parallel()
        .forEach(i -> availableContainers.add(createAndStartContainer()));
    return this;
  }

  public T getContainer() {
    final T container = availableContainers.poll();
    if (container != null) {
      CompletableFuture.runAsync(
          () -> {
            final T newContainer = createAndStartContainer();
            availableContainers.add(newContainer);
          },
          executorService);
      return container;
    } else {
      LOGGER.warn("No containers were available in the pool. Creating a new one");
      return createAndStartContainer();
    }
  }

  private T createAndStartContainer() {
    final T newContainer = containerCreator.get();
    newContainer.start();
    return newContainer;
  }
}
