/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.time.Duration;

public interface ReadonlyStreamProcessorContext {

  ProcessingScheduleService getScheduleService();

  /**
   * Returns the partition ID
   *
   * @return partition ID
   */
  int getPartitionId();

  StreamClock getClock();

  /**
   * How often the schedule service checks for due tasks. A task due at least half of it away runs
   * at the next check after it is due, so up to this long late; a task due sooner runs on time.
   */
  Duration getScheduledTaskCheckInterval();
}
