/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;

public abstract class ScheduledTrigger implements StreamProcessorLifecycleAware {

  private ScheduledTimer timer;
  private ReadonlyProcessingContext processingContext;

  protected abstract Duration getPollingInterval();

  protected abstract void executeOnTrigger();

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    this.processingContext = context;
    timer =
        this.processingContext
            .getActor()
            .runAtFixedRate(getPollingInterval(), this::executeOnTrigger);
  }

  @Override
  public void onClose() {
    cancelTimer();
  }

  @Override
  public void onFailed() {
    cancelTimer();
  }

  @Override
  public void onPaused() {
    cancelTimer();
  }

  @Override
  public void onResumed() {
    if (timer == null) {
      timer =
          processingContext.getActor().runAtFixedRate(getPollingInterval(), this::executeOnTrigger);
    }
  }

  private void cancelTimer() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }
}
