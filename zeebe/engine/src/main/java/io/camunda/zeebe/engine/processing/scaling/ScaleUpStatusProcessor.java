/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scaling;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public class ScaleUpStatusProcessor implements TypedRecordProcessor<ScaleRecord> {
  private static final Logger LOG = LoggerFactory.getLogger(ScaleUpStatusProcessor.class);

  private final Writers writers;
  private final KeyGenerator keyGenerator;
  private final RoutingState routingState;

  public ScaleUpStatusProcessor(
      final KeyGenerator keyGenerator, final Writers writers, final RoutingState routingState) {
    this.keyGenerator = keyGenerator;
    this.writers = writers;
    this.routingState = routingState;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> command) {
    final var key = keyGenerator.nextKey();
    final var request = command.getValue();
    final var desiredPartitions = routingState.desiredPartitions();
    final var response = new ScaleRecord();
    final var desiredPartitionCount = routingState.desiredPartitions().size();
    final var scalingStartedAt = routingState.scalingStartedAt(desiredPartitionCount);
    if (scalingStartedAt <= 0) {
      final var reason =
          "Scaling has not started for the desired partition count " + desiredPartitionCount;
      writers.rejection().appendRejection(command, RejectionType.INVALID_STATE, reason);
      writers.response().writeRejectionOnCommand(command, RejectionType.INVALID_STATE, reason);
    }
    response.statusResponse(
        desiredPartitions.size(),
        routingState.currentPartitions(),
        routingState.messageCorrelation().partitionCount(),
        routingState.scalingStartedAt(desiredPartitionCount));
    writers.state().appendFollowUpEvent(key, ScaleIntent.STATUS_RESPONSE, response);
    writers.response().writeEventOnCommand(key, ScaleIntent.STATUS_RESPONSE, response, command);
  }
}
