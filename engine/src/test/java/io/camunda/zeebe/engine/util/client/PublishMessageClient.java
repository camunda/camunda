/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class PublishMessageClient {

  private static final int DEFAULT_VALUE = -1;
  private static final Duration DEFAULT_MSG_TTL = Duration.ofHours(1);

  private static final Function<Message, Record<MessageRecordValue>>
      SUCCESSFUL_EXPECTATION_SUPPLIER =
          (message) ->
              RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                  .withPartitionId(message.partitionId)
                  .withCorrelationKey(message.correlationKey)
                  .withSourceRecordPosition(message.position)
                  .getFirst();

  private static final Function<Message, Record<MessageRecordValue>>
      REJECTION_EXPECTATION_SUPPLIER =
          (message) ->
              RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                  .onlyCommandRejections()
                  .withPartitionId(message.partitionId)
                  .withCorrelationKey(message.correlationKey)
                  .getFirst();

  private final MessageRecord messageRecord;
  private final CommandWriter writer;
  private final int partitionCount;

  private Function<Message, Record<MessageRecordValue>> expectation =
      SUCCESSFUL_EXPECTATION_SUPPLIER;
  private int partitionId = DEFAULT_VALUE;

  public PublishMessageClient(final CommandWriter environmentRule, final int partitionCount) {
    writer = environmentRule;
    this.partitionCount = partitionCount;

    messageRecord = new MessageRecord();
    messageRecord.setTimeToLive(DEFAULT_MSG_TTL.toMillis());
  }

  public PublishMessageClient withCorrelationKey(final String correlationKey) {
    messageRecord.setCorrelationKey(correlationKey);
    return this;
  }

  public PublishMessageClient withName(final String name) {
    messageRecord.setName(name);
    return this;
  }

  public PublishMessageClient withId(final String id) {
    messageRecord.setMessageId(id);
    return this;
  }

  public PublishMessageClient withTimeToLive(final Duration timeToLive) {
    return withTimeToLive(timeToLive.toMillis());
  }

  public PublishMessageClient withTimeToLive(final long timeToLive) {
    messageRecord.setTimeToLive(timeToLive);
    return this;
  }

  public PublishMessageClient withVariables(final Map<String, Object> variables) {
    return withVariables(MsgPackUtil.asMsgPack(variables));
  }

  public PublishMessageClient withVariables(final DirectBuffer variables) {
    messageRecord.setVariables(variables);
    return this;
  }

  public PublishMessageClient withVariables(final String variables) {
    messageRecord.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
    return this;
  }

  public PublishMessageClient onPartition(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public PublishMessageClient expectRejection() {
    expectation = REJECTION_EXPECTATION_SUPPLIER;
    return this;
  }

  public Record<MessageRecordValue> publish() {

    if (partitionId == DEFAULT_VALUE) {
      partitionId =
          SubscriptionUtil.getSubscriptionPartitionId(
              messageRecord.getCorrelationKeyBuffer(), partitionCount);
    }

    final long position =
        writer.writeCommandOnPartition(partitionId, MessageIntent.PUBLISH, messageRecord);

    return expectation.apply(new Message(partitionId, messageRecord.getCorrelationKey(), position));
  }

  private class Message {

    final int partitionId;
    final String correlationKey;
    final long position;

    Message(final int partitionId, final String correlationKey, final long position) {
      this.partitionId = partitionId;
      this.correlationKey = correlationKey;
      this.position = position;
    }
  }
}
