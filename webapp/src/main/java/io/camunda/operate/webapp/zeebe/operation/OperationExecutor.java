/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.es.writer.BatchOperationWriter;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import static io.camunda.operate.util.ThreadUtil.*;

@Component
@Configuration
public class OperationExecutor extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(OperationExecutor.class);

  private boolean shutdown = false;

  @Autowired
  private List<OperationHandler> handlers;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  @Qualifier("operationsThreadPoolExecutor")
  private ThreadPoolTaskExecutor operationsTaskExecutor;

  private List<ExecutionFinishedListener> listeners = new ArrayList<>();

  public void startExecuting() {
    if (operateProperties.getOperationExecutor().isExecutorEnabled()) {
      start();
    }
  }

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown OperationExecutor");
    shutdown = true;
  }

  @Override
  public void run() {
    while (!shutdown) {
      try {
        final List<Future<?>> operations = executeOneBatch();

        //TODO backoff strategy
        if (operations.size() == 0) {

          notifyExecutionFinishedListeners();
          sleepFor(2000);
        }

      } catch (Exception ex) {
        //retry
        logger.error("Something went wrong, while executing operations batch. Will be retried.", ex);

        sleepFor(2000);
      }
    }
  }

  public List<Future<?>> executeOneBatch() throws PersistenceException {
    List<Future<?>> futures = new ArrayList<>();

    //lock the operations
    final List<OperationEntity> lockedOperations = batchOperationWriter.lockBatch();

    //execute all locked operations
    for (OperationEntity operation : lockedOperations) {
      final OperationHandler handler = getOperationHandlers().get(operation.getType());
      if (handler == null) {
        logger.info("Operation {} on worflowInstanceId {} won't be processed, as no suitable handler was found.", operation.getType(), operation.getProcessInstanceKey());
      } else {
        OperationCommand operationCommand = new OperationCommand(operation, handler);
        futures.add(operationsTaskExecutor.submit(operationCommand));
      }
    }
    return futures;
  }

  @Bean
  public Map<OperationType, OperationHandler> getOperationHandlers() {
    //populate handlers map
    Map<OperationType, OperationHandler> handlerMap = new HashMap<>();
    for (OperationHandler handler: handlers) {
      handler.getTypes().forEach(t -> handlerMap.put(t, handler));
    }
    return handlerMap;
  }

  public void registerListener(ExecutionFinishedListener listener) {
    this.listeners.add(listener);
  }

  private void notifyExecutionFinishedListeners() {
    for (ExecutionFinishedListener listener: listeners) {
      listener.onExecutionFinished();
    }
  }

}
