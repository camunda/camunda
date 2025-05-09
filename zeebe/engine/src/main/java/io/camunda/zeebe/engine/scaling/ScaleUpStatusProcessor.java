/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ScaleUpStatusProcessor implements TypedRecordProcessor<ScaleRecord> {

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
    // The desired partition count in the request can be smaller than the value in the state in case
    // a late request is received and another scale up is in progress
    if (request.getDesiredPartitionCount() > desiredPartitions.size()) {
      final String message =
          String.format(
              "In progress scale up number of desired partitions is %d, but desired partitions in the request are %d.",
              desiredPartitions.size(), request.getDesiredPartitionCount());
      writers.rejection().appendRejection(command, RejectionType.INVALID_ARGUMENT, message);
      writers.response().writeRejectionOnCommand(command, RejectionType.INVALID_ARGUMENT, message);
    } else {
      final var response = new ScaleRecord();
      response
          .setDesiredPartitionCount(desiredPartitions.size())
          .setRedistributedPartitions(routingState.currentPartitions());
      writers.state().appendFollowUpEvent(key, ScaleIntent.STATUS_RESPONSE, response);
      writers.response().writeEventOnCommand(key, ScaleIntent.STATUS_RESPONSE, response, command);
    }
  }
}
