/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.operate.util.ThreadUtil.*;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.BackoffIdleStrategy;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConditionalOnRdbmsDisabled
public class OperationExecutor extends Thread {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperationExecutor.class);

  private volatile boolean shutdown = false;
  private final int defaultBackoff = 2000;

  @Autowired private List<OperationHandler> handlers;

  @Autowired private BatchOperationWriter batchOperationWriter;

  @Autowired private OperateProperties operateProperties;

  private final BackoffIdleStrategy errorStrategy =
      new BackoffIdleStrategy(defaultBackoff, 1.2f, 10_000);

  @Autowired
  @Qualifier("operationsThreadPoolExecutor")
  private ThreadPoolTaskExecutor operationsTaskExecutor;

  private final List<ExecutionFinishedListener> listeners = new ArrayList<>();

  public void startExecuting() {
    if (operateProperties.getOperationExecutor().isExecutorEnabled()) {
      start();
    }
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutdown OperationExecutor");
    shutdown = true;
  }

  @Override
  public void run() {
    while (!shutdown) {
      try {
        final List<Future<?>> operations = executeOneBatch();

        if (operations.size() == 0) {

          notifyExecutionFinishedListeners();
          errorStrategy.reset();
          sleepFor(defaultBackoff);
        }

      } catch (final Exception ex) {
        // retry
        LOGGER.error(
            "Something went wrong, while executing operations batch. Will be retried.", ex);
        errorStrategy.idle();
        sleepFor(errorStrategy.idleTime());
      }
    }
  }

  public List<Future<?>> executeOneBatch() throws PersistenceException {
    final List<Future<?>> futures = new ArrayList<>();

    // lock the operations
    final List<OperationEntity> lockedOperations = batchOperationWriter.lockBatch();

    // execute all locked operations
    for (final OperationEntity operation : lockedOperations) {
      final OperationHandler handler = getOperationHandlers().get(operation.getType());
      if (handler == null) {
        LOGGER.info(
            "Operation {} on worflowInstanceId {} won't be processed, as no suitable handler was found.",
            operation.getType(),
            operation.getProcessInstanceKey());
      } else {
        final OperationCommand operationCommand = new OperationCommand(operation, handler);
        futures.add(operationsTaskExecutor.submit(operationCommand));
      }
    }
    return futures;
  }

  @Bean
  public Map<OperationType, OperationHandler> getOperationHandlers() {
    // populate handlers map
    final Map<OperationType, OperationHandler> handlerMap = new HashMap<>();
    for (final OperationHandler handler : handlers) {
      handler.getTypes().forEach(t -> handlerMap.put(t, handler));
    }
    return handlerMap;
  }

  public void registerListener(final ExecutionFinishedListener listener) {
    listeners.add(listener);
  }

  private void notifyExecutionFinishedListeners() {
    for (final ExecutionFinishedListener listener : listeners) {
      listener.onExecutionFinished();
    }
  }
}
