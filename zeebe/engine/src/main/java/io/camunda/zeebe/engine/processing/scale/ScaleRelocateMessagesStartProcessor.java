/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scale;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.protocol.record.value.ScaleRecordValue.RoutingInfoRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ScaleRelocateMessagesStartProcessor
    implements DistributedTypedRecordProcessor<ScaleRecord> {

  private final CommandDistributionBehavior commandDistributionBehavior;
  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  public ScaleRelocateMessagesStartProcessor(
      final CommandDistributionBehavior commandDistributionBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  @Override
  public void processNewCommand(final TypedRecord<ScaleRecord> command) {
    // only on partition 1
    // distribute relocate command to all partitions other than 1
    final var distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior.distributeCommand(distributionKey, command);

    stateWriter.appendFollowUpEvent(
        distributionKey, ScaleIntent.RELOCATE_MESSAGES_STARTED, command.getValue());

    startRelocateMessagesToOtherPartitions(command.getValue().getRoutingInfo());
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ScaleRecord> command) {
    startRelocateMessagesToOtherPartitions(command.getValue().getRoutingInfo());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void startRelocateMessagesToOtherPartitions(final RoutingInfoRecordValue routingInfo) {
    // TODO
  }
}
