/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.property;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;

public sealed interface Action {
  record WriteRecord(int partitionId, LogAppendEntry entry) implements Action {}

  record ProcessRecord(int partitionId, boolean deliverIpc) implements Action {}

  record ExecuteScheduledTask(int partitionId, boolean deliverIpc) implements Action {}

  record UpdateClock(int partitionId) implements Action {}
}
