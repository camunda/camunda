/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.InstantSource;

/** Per-invocation context handed to a {@link ScheduledTask}. */
public interface Context {
  InstantSource clock();

  TaskResultBuilder resultBuilder();
}
