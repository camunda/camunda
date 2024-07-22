/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scale;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;

public class ScaleMsgSubscriptionRelocationStartProcessor
    implements TypedRecordProcessor<ScaleRecord> {

  private final MessageSubscriptionState messageSubscriptionState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final KeyGenerator keyGenerator;

  public ScaleMsgSubscriptionRelocationStartProcessor(
      final ProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final KeyGenerator keyGenerator) {
    messageSubscriptionState = processingState.getMessageSubscriptionState();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    // find all message subscription records
    // write command distribution -> on complete write RELOCATION_COMPLETE

    // TODO: Do it in smaller batches
    messageSubscriptionState.visitSubscriptions(
        record.getValue().getCorrelationKey(),
        messageSubscription -> {
          commandDistributionBehavior.distributeCommand(
              keyGenerator.nextKey(),
              ValueType.MESSAGE_SUBSCRIPTION,
              MessageSubscriptionIntent.CREATE,
              messageSubscription.getRecord(),
              List.of(getNewPartition(record)),
              ValueType.SCALE,
              ScaleIntent.MSG_SUBSCRIPTION_RELOCATION_ACKNOWLEDGE,
              new ScaleRecord().setCorrelationKey(record.getValue().getCorrelationKey()));

          // on processing ACKNOWLEDGE -> write MOVED, if this is the last subscription to be
          // moved, then write  MSG_SUBSCRIPTION_RELOCATION_COMPLETED for this correlationKey

          return true;
        });
  }

  private int getNewPartition(final TypedRecord<ScaleRecord> record) {
    // TODO:
    return 0;
  }
}
