/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;

public final class EmptyTaskResult implements TaskResult {

  public static final TaskResult INSTANCE = new EmptyTaskResult();

  private EmptyTaskResult() {}

  @Override
  public long writeRecordsToStream(final LogStreamBatchWriter logStreamBatchWriter) {
    return 0;
  }
}
