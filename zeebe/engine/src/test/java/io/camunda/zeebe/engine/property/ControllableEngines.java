/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.property;

import io.camunda.zeebe.engine.property.EngineAction.ExecuteScheduledTask;
import io.camunda.zeebe.engine.property.EngineAction.ProcessNextCommand;
import io.camunda.zeebe.engine.property.EngineAction.UpdateClock;
import io.camunda.zeebe.engine.property.EngineAction.WriteRecord;
import io.camunda.zeebe.engine.property.InMemoryEngine.TestLogStreamWriter;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import java.time.InstantSource;

public interface ControllableEngines {
  void runAction(final EngineAction engineAction);

  ControllableEngine getEngine(int partitionId);

  static ControllableEngines createEngines(final int partitionCount) {
    final var logStorages = new ListLogStorage[partitionCount];
    final var logStreamWriters = new TestLogStreamWriter[partitionCount];
    for (int i = 0; i < partitionCount; i++) {
      final var logStorage = new ListLogStorage();
      final var logStreamWriter = new TestLogStreamWriter(logStorage, InstantSource.system());
      logStorages[i] = logStorage;
      logStreamWriters[i] = logStreamWriter;
    }
    final var engines = new ControllableEngine[partitionCount];
    for (int i = 0; i < partitionCount; i++) {
      engines[i] = new InMemoryEngine(i + 1, partitionCount, logStorages[i], logStreamWriters);
    }
    return new EngineHolder(engines);
  }

  final class EngineHolder implements ControllableEngines {
    private final ControllableEngine[] engines;

    public EngineHolder(final ControllableEngine[] engines) {
      this.engines = engines;
    }

    @Override
    public void runAction(final EngineAction action) {
      final var partitionId = action.partitionId();
      final var engine = engines[partitionId - 1];
      switch (action) {
        case final WriteRecord writeRecord -> engine.writeRecord(writeRecord.entry());
        case final ProcessNextCommand processNextCommand ->
            engine.processNextCommand(processNextCommand.deliverIpc());
        case final ExecuteScheduledTask executeScheduledTask ->
            engine.executeScheduledTask(executeScheduledTask.deliverIpc());
        case final UpdateClock updateClock -> engine.updateClock(updateClock.difference());
      }
    }

    @Override
    public ControllableEngine getEngine(final int partitionId) {
      return engines[partitionId - 1];
    }
  }
}
