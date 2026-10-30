/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class MessageExpireProcessor implements TypedRecordProcessor<MessageRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(MessageExpireProcessor.class);

  private final StateWriter stateWriter;
  private final MessageCorrelationMetrics metrics;

  public MessageExpireProcessor(
      final StateWriter stateWriter, final MessageCorrelationMetrics metrics) {
    this.stateWriter = stateWriter;
    this.metrics = metrics;
  }

  @Override
  public void processRecord(final TypedRecord<MessageRecord> record) {

    stateWriter.appendFollowUpEvent(record.getKey(), MessageIntent.EXPIRED, record.getValue());
    if (metrics.expireCrossPartitionAsks(record.getKey())) {
      LOG.atDebug()
          .addKeyValue("messageKey", record.getKey())
          .log("Buffered message expired while its cross-partition ask was still pending");
    }
  }
}
