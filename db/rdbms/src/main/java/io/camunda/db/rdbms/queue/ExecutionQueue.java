/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.queue;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionQueue extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(ExecutionQueue.class);

  private final SqlSessionFactory sessionFactory;
  private final List<FlushListener> preFlushListeners = new ArrayList<>();
  private final List<FlushListener> postFlushListeners = new ArrayList<>();

  private final Queue<QueueItem> queue = new ConcurrentLinkedQueue<>();

  public ExecutionQueue(final ActorScheduler actorScheduler, final SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;

    actorScheduler.submitActor(this, SchedulingHints.IO_BOUND);
    actor.run(() -> actor.schedule(Duration.ofSeconds(5), this::flushAndReschedule)); // TODO make timeout configurable
  }

  public void executeInQueue(final QueueItem entry) {
    LOG.debug("Added entry to queue: {}", entry);
    queue.add(entry);
    checkQueueForFlush();
  }

  public void registerPreFlushListener(final FlushListener listener) {
    preFlushListeners.add(listener);
  }

  public void registerPostFlushListener(final FlushListener listener) {
    postFlushListeners.add(listener);
  }

  public void flush() {
    if (queue.isEmpty()) {
      LOG.trace("Skip Flushing because execution queue is empty");
      return;
    }
    LOG.debug("[RDBMS Execution Queue] Flushing execution queue with {} items", queue.size());

    final var startMillis = System.currentTimeMillis();
    final var queueSize = queue.size();
    final var session = sessionFactory.openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_UNCOMMITTED);

    try {
      var preFlushListenersCalled = false;
      while (!queue.isEmpty()) {
        final var entry = queue.peek();
        LOG.trace("[RDBMS Execution Queue] Executing entry: {}", entry);
        session.update(entry.statementId(), entry.parameter());
        queue.poll();

        if (queue.isEmpty() && !postFlushListeners.isEmpty() && !preFlushListenersCalled) {
          LOG.debug("[RDBMS Execution Queue] Call pre flush listeners");
          preFlushListeners.forEach(FlushListener::onFlushSuccess);
          preFlushListenersCalled = true;
        }
      }
      session.flushStatements();
      session.commit();
      LOG.debug("[RDBMS Execution Queue] Commit queue with {} entries in {}ms", queueSize, System.currentTimeMillis() - startMillis);
    } catch (final Exception e) {
      LOG.error("Error while executing queue", e);
      session.rollback();
    } finally {
      session.close();
      if (!postFlushListeners.isEmpty()) {
        LOG.debug("[RDBMS Execution Queue] Call post flush listeners");
        postFlushListeners.forEach(FlushListener::onFlushSuccess);
      }
    }
  }

  private void checkQueueForFlush() {
    LOG.trace("Checking if queue is flushed. Queue size: {}", queue.size());
    if (queue.size() > 50) {
      flush();
    }
  }

  private void flushAndReschedule() {
    flush();
    actor.schedule(Duration.ofSeconds(5), this::flushAndReschedule);
  }

}
