/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams;

import io.zeebe.logstreams.impl.LogStreamBuilder;

public class LogStreams {
  public static LogStreamBuilder createFsLogStream(final int partitionId) {
    return new LogStreamBuilder(partitionId).logName(Integer.toString(partitionId));
  }
}
