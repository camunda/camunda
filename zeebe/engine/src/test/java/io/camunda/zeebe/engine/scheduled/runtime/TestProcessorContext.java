/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.time.Instant;
import java.time.InstantSource;

final class TestProcessorContext {

  static ReadonlyStreamProcessorContext with(
      final ProcessingScheduleService scheduleService, final InstantSource clock) {
    final StreamClock streamClock =
        new StreamClock() {
          @Override
          public Modification currentModification() {
            return Modification.none();
          }

          @Override
          public Instant instant() {
            return clock.instant();
          }

          @Override
          public long millis() {
            return clock.millis();
          }
        };
    return new ReadonlyStreamProcessorContext() {

      @Override
      public ProcessingScheduleService getScheduleService() {
        return scheduleService;
      }

      @Override
      public int getPartitionId() {
        return 1;
      }

      @Override
      public boolean enableAsyncScheduledTasks() {
        return false;
      }

      @Override
      public StreamClock getClock() {
        return streamClock;
      }
    };
  }
}
