/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MessageCorrelationClient {
  private static final Function<Message, Record<MessageCorrelationRecordValue>>
      SUCCESSFUL_EXPECTATION =
          (message) ->
              RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
                  .withPartitionId(message.partitionId)
                  .withCorrelationKey(message.correlationKey)
                  .getFirst();

  private static final Function<Message, Record<MessageCorrelationRecordValue>> NOT_CORRELATED =
      (message) ->
          RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.NOT_CORRELATED)
              .withPartitionId(message.partitionId)
              .withCorrelationKey(message.correlationKey)
              .getFirst();
  private static final Function<Message, Record<MessageCorrelationRecordValue>>
      REJECTION_EXPECTATION =
          (message) ->
              RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATE)
                  .onlyCommandRejections()
                  .withPartitionId(message.partitionId)
                  .withCorrelationKey(message.correlationKey)
                  .getFirst();
  private static final Function<Message, Record<MessageCorrelationRecordValue>> EXPECT_NOTHING =
      (message) ->
          RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATE)
              .withPartitionId(message.partitionId)
              .withCorrelationKey(message.correlationKey)
              .getFirst();
  private static final int NOT_SET = -1;
  private final MessageCorrelationRecord messageCorrelationRecord;
  private final CommandWriter writer;
  private final int partitionCount;
  private Function<Message, Record<MessageCorrelationRecordValue>> expectation =
      SUCCESSFUL_EXPECTATION;
  private int partitionId = NOT_SET;

  public MessageCorrelationClient(final CommandWriter environmentRule, final int partitionCount) {
    writer = environmentRule;
    this.partitionCount = partitionCount;
    messageCorrelationRecord = new MessageCorrelationRecord();
  }

  public MessageCorrelationClient withName(final String name) {
    messageCorrelationRecord.setName(name);
    return this;
  }

  public MessageCorrelationClient withCorrelationKey(final String correlationKey) {
    messageCorrelationRecord.setCorrelationKey(correlationKey);
    return this;
  }

  public MessageCorrelationClient withVariables(final Map<String, Object> variables) {
    return withVariables(MsgPackUtil.asMsgPack(variables));
  }

  public MessageCorrelationClient withVariables(final DirectBuffer variables) {
    messageCorrelationRecord.setVariables(variables);
    return this;
  }

  public MessageCorrelationClient withVariables(final String variables) {
    messageCorrelationRecord.setVariables(
        new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
    return this;
  }

  public MessageCorrelationClient withTenantId(final String tenantId) {
    messageCorrelationRecord.setTenantId(tenantId);
    return this;
  }

  public MessageCorrelationClient onPartition(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public MessageCorrelationClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }

  public MessageCorrelationClient expectNotCorrelated() {
    expectation = NOT_CORRELATED;
    return this;
  }

  public MessageCorrelationClient expectNothing() {
    expectation = EXPECT_NOTHING;
    return this;
  }

  public Record<MessageCorrelationRecordValue> correlate() {

    if (partitionId == NOT_SET) {
      partitionId =
          SubscriptionUtil.getSubscriptionPartitionId(
              messageCorrelationRecord.getCorrelationKeyBuffer(), partitionCount);
    }

    final var position =
        writer.writeCommandOnPartition(
            partitionId, MessageCorrelationIntent.CORRELATE, messageCorrelationRecord);
    return expectation.apply(
        new Message(messageCorrelationRecord.getCorrelationKey(), position, partitionId));
  }

  public Record<MessageCorrelationRecordValue> correlate(final String username) {

    if (partitionId == NOT_SET) {
      partitionId =
          SubscriptionUtil.getSubscriptionPartitionId(
              messageCorrelationRecord.getCorrelationKeyBuffer(), partitionCount);
    }

    final var position =
        writer.writeCommandOnPartition(
            partitionId, MessageCorrelationIntent.CORRELATE, messageCorrelationRecord, username);
    return expectation.apply(
        new Message(messageCorrelationRecord.getCorrelationKey(), position, partitionId));
  }

  private record Message(String correlationKey, long position, int partitionId) {}
}
