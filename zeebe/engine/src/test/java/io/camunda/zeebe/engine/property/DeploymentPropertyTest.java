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
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;

final class DeploymentPropertyTest {
  @Property(tries = 1)
  void distributeDeployment(
      @ForAll("engineActions") final ActionChain<ControllableEngines> engineActions) {
    engineActions.run();
    final var engines = engineActions.finalState().orElseThrow();

    engineActions.transformations().forEach(System.out::println);
    //    new
    // CompactRecordLogger(engines.getEngine(1).records().collect(Collectors.toUnmodifiableList()))
    //        .log();
  }

  @Provide
  Arbitrary<ActionChain<ControllableEngines>> engineActions() {
    final var partitionCount = 3;
    return ActionChain.startWith(
            () -> {
              final var engines = ControllableEngines.createEngines(partitionCount);
              deployProcess(engines.getEngine(1));
              return engines;
            })
        .withAction(new ProcessCommandAction(partitionCount))
        .withAction(new UpdateClockAction(partitionCount))
        .withAction(new ExecuteScheduledTaskAction(partitionCount))
        .withMaxTransformations(1000);
  }

  private void deployProcess(final ControllableEngine engine) {
    final var process =
        Bpmn.createExecutableProcess("test").startEvent().manualTask().endEvent().done();

    final var deploymentMetadata = new RecordMetadata();
    deploymentMetadata
        .recordType(RecordType.COMMAND)
        .valueType(ValueType.DEPLOYMENT)
        .intent(DeploymentIntent.CREATE);
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResource(Bpmn.convertToString(process).getBytes(StandardCharsets.UTF_8))
        .setResourceName("process.xml");

    engine.writeRecord(LogAppendEntry.of(deploymentMetadata, deploymentRecord));
  }

  static class ExecuteScheduledTaskAction implements Action.Independent<ControllableEngines> {
    private final int partitionCount;

    ExecuteScheduledTaskAction(final int partitionCount) {
      this.partitionCount = partitionCount;
    }

    @Override
    public Arbitrary<Transformer<ControllableEngines>> transformer() {
      final var arbitraryPartitionId = Arbitraries.integers().between(1, partitionCount);
      final var arbitraryDeliverIpc = Arbitraries.of(true, false);

      return Combinators.combine(arbitraryPartitionId, arbitraryDeliverIpc)
          .as(
              (partitionId, deliverIpc) ->
                  Transformer.transform(
                      "Execute scheduled task on partition "
                          + partitionId
                          + " with deliverIpc="
                          + deliverIpc,
                      engines -> {
                        engines.runAction(new ExecuteScheduledTask(partitionId, deliverIpc));
                        return engines;
                      }));
    }
  }

  static class UpdateClockAction implements Action.Independent<ControllableEngines> {
    private final int partitionCount;

    UpdateClockAction(final int partitionCount) {
      this.partitionCount = partitionCount;
    }

    @Override
    public Arbitrary<Transformer<ControllableEngines>> transformer() {
      final var arbitraryPartitionId = Arbitraries.integers().between(1, partitionCount);
      final var arbitraryDuration = Arbitraries.integers().between(1, 30).map(Duration::ofSeconds);

      return Combinators.combine(arbitraryPartitionId, arbitraryDuration)
          .as(
              (partitionId, duration) ->
                  Transformer.transform(
                      "Update clock on partition " + partitionId + " by " + duration,
                      engines -> {
                        engines.runAction(new UpdateClock(partitionId, duration));
                        return engines;
                      }));
    }
  }

  static class ProcessCommandAction implements Action.Independent<ControllableEngines> {
    private final int partitionCount;

    ProcessCommandAction(final int partitionCount) {
      this.partitionCount = partitionCount;
    }

    @Override
    public Arbitrary<Transformer<ControllableEngines>> transformer() {
      final var arbitraryPartitionId = Arbitraries.integers().between(1, partitionCount);
      final var arbitraryDeliverIpc = Arbitraries.of(true, false);

      return Combinators.combine(arbitraryPartitionId, arbitraryDeliverIpc)
          .as(
              (partitionId, deliverIpc) ->
                  Transformer.transform(
                      "Process next command on partition "
                          + partitionId
                          + " with deliverIpc="
                          + deliverIpc,
                      engines -> {
                        engines.runAction(new ProcessNextCommand(partitionId, deliverIpc));
                        return engines;
                      }));
    }
  }
}
