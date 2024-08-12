/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.property;

import io.camunda.zeebe.engine.property.InMemoryEngine.TestLogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.protocol.record.Record;
import java.time.Duration;
import java.time.InstantSource;
import java.util.stream.Stream;

public interface ControllableEngine {
  void writeRecord(final LogAppendEntry entry);

  void processNextCommand(boolean deliverIpc);

  void executeScheduledTask(final boolean deliverIpc);

  void updateClock(Duration duration);

  Stream<? extends Record<?>> records();

  static ControllableEngine createEngine() {
    final var logStorage = new ListLogStorage();
    final var logStreamWriter = new TestLogStreamWriter(logStorage, InstantSource.system());

    return new InMemoryEngine(1, 1, logStorage, new LogStreamWriter[] {logStreamWriter});
  }
}
