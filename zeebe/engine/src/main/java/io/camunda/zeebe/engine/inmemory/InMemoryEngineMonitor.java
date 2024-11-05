/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.inmemory;

import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitor that monitors whether the engine is busy or in idle state. Busy state is a state in which
 * the engine is actively writing new events to the logstream. Idle state is a state in which the
 * process engine makes no progress and is waiting for new commands or events to trigger<br>
 * The busy state callbacks are notified immediately as soon as a new commit is registered in the
 * log storage.<br>
 * The idle state callbacks are notified when the idle state has lasted <code>
 * PERIOD * NOTIFICATION_THRESHOLD</code> ms<br>
 */
public final class InMemoryEngineMonitor implements LogStorage.CommitListener {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryEngineMonitor.class);

  private static final int GRACE_PERIOD_MS = 50;
  private static final int NOTIFICATION_THRESHOLD = 2;
  private static final Timer TIMER = new Timer();
  private final List<Runnable> idleCallbacks = new ArrayList<>();
  private final List<Runnable> processingCallbacks = new ArrayList<>();
  private final StreamProcessor streamProcessor;
  private volatile TimerTask stateNotifier;

  public InMemoryEngineMonitor(final StreamProcessor streamProcessor) {

    this.streamProcessor = streamProcessor;
  }

  public void addOnIdleCallback(final Runnable callback) {
    synchronized (idleCallbacks) {
      idleCallbacks.add(callback);
    }
    scheduleStateNotification();
  }

  public void addOnProcessingCallback(final Runnable callback) {
    synchronized (processingCallbacks) {
      processingCallbacks.add(callback);
    }
    scheduleStateNotification();
  }

  private synchronized void scheduleStateNotification() {
    if (stateNotifier != null) {
      // cancel last task
      stateNotifier.cancel();
      TIMER.purge();
    }

    stateNotifier = createStateNotifier();

    TIMER.scheduleAtFixedRate(stateNotifier, GRACE_PERIOD_MS, GRACE_PERIOD_MS);
  }

  private boolean isInIdleState() {
    try {
      return streamProcessor.hasProcessingReachedTheEnd().join();
    } catch (final Exception e) {
      LOG.debug("Exception occurred while checking idle state", e);
      // A ExecutionException may be thrown here if the actor is already closed. For some mysterious
      // reason this causes the testcontainer to terminate, which is why we need to catch it.
      // We cannot catch the ExecutionException itself, as Zeebe turns this into an unchecked
      // exception. Because of this we need to catch Exception instead.
      return streamProcessor.isActorClosed();
    }
  }

  @Override
  public void onCommit() {
    notifyProcessingCallbacks(); // notify processing callbacks immediately
    if (!idleCallbacks.isEmpty() || !processingCallbacks.isEmpty()) {
      scheduleStateNotification();
    }
  }

  private void notifyIdleCallbacks() {
    synchronized (idleCallbacks) {
      idleCallbacks.forEach(Runnable::run);
      idleCallbacks.clear();
    }
  }

  private void notifyProcessingCallbacks() {
    synchronized (processingCallbacks) {
      processingCallbacks.forEach(Runnable::run);
      processingCallbacks.clear();
    }
  }

  private TimerTask createStateNotifier() {
    return new TimerTask() {

      private int idleStateReachedCounter = 0;

      @Override
      public void run() {
        if (!idleCallbacks.isEmpty() || !processingCallbacks.isEmpty()) {
          if (isInIdleState()) {
            idleStateReachedCounter++;

            if (idleStateReachedCounter >= NOTIFICATION_THRESHOLD) {
              notifyIdleCallbacks();
            }
          } else {
            idleStateReachedCounter = 0;

            notifyProcessingCallbacks();
          }
        }
        if (idleCallbacks.isEmpty() && processingCallbacks.isEmpty()) {
          cancel();
        }
      }
    };
  }
}
