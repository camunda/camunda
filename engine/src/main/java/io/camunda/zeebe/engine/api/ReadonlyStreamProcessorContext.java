/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.metrics.StreamProcessorMetrics;
import io.camunda.zeebe.scheduler.ActorSchedulingService;

public interface ReadonlyStreamProcessorContext {

  ProcessingScheduleService getScheduleService();

  /**
   * Returns the partition ID
   *
   * @return partition ID
   */
  int getPartitionId();

  StreamProcessorMetrics getMetrics();

  ZeebeDb getZeebeDb();

  ActorSchedulingService getActorSchedulingService();
}
