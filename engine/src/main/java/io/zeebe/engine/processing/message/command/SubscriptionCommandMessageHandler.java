/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message.command;

import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class SubscriptionCommandMessageHandler
    implements Function<byte[], CompletableFuture<Void>> {

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final OpenMessageSubscriptionCommand openMessageSubscriptionCommand =
      new OpenMessageSubscriptionCommand();

  private final OpenProcessMessageSubscriptionCommand openProcessMessageSubscriptionCommand =
      new OpenProcessMessageSubscriptionCommand();

  private final CorrelateProcessMessageSubscriptionCommand
      correlateProcessMessageSubscriptionCommand = new CorrelateProcessMessageSubscriptionCommand();

  private final CorrelateMessageSubscriptionCommand correlateMessageSubscriptionCommand =
      new CorrelateMessageSubscriptionCommand();

  private final CloseMessageSubscriptionCommand closeMessageSubscriptionCommand =
      new CloseMessageSubscriptionCommand();

  private final CloseProcessMessageSubscriptionCommand closeProcessMessageSubscriptionCommand =
      new CloseProcessMessageSubscriptionCommand();

  private final RejectCorrelateMessageSubscriptionCommand resetMessageCorrelationCommand =
      new RejectCorrelateMessageSubscriptionCommand();

  private final RecordMetadata recordMetadata = new RecordMetadata();

  private final MessageSubscriptionRecord messageSubscriptionRecord =
      new MessageSubscriptionRecord();

  private final ProcessMessageSubscriptionRecord processMessageSubscriptionRecord =
      new ProcessMessageSubscriptionRecord();

  private final Consumer<Runnable> enviromentToRun;
  private final IntFunction<LogStreamRecordWriter> logstreamRecordWriterSupplier;

  public SubscriptionCommandMessageHandler(
      final Consumer<Runnable> enviromentToRun,
      final IntFunction<LogStreamRecordWriter> logstreamRecordWriterSupplier) {
    this.enviromentToRun = enviromentToRun;
    this.logstreamRecordWriterSupplier = logstreamRecordWriterSupplier;
  }

  @Override
  public CompletableFuture<Void> apply(final byte[] bytes) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    enviromentToRun.accept(
        () -> {
          final DirectBuffer buffer = new UnsafeBuffer(bytes);
          final int offset = 0;
          final int length = buffer.capacity();
          messageHeaderDecoder.wrap(buffer, offset);

          if (messageHeaderDecoder.schemaId() == OpenMessageSubscriptionDecoder.SCHEMA_ID) {

            switch (messageHeaderDecoder.templateId()) {
              case OpenMessageSubscriptionDecoder.TEMPLATE_ID:
                onOpenMessageSubscription(buffer, offset, length);
                break;
              case OpenProcessMessageSubscriptionDecoder.TEMPLATE_ID:
                onOpenProcessMessageSubscription(buffer, offset, length);
                break;
              case CorrelateProcessMessageSubscriptionDecoder.TEMPLATE_ID:
                onCorrelateProcessMessageSubscription(buffer, offset, length);
                break;
              case CorrelateMessageSubscriptionDecoder.TEMPLATE_ID:
                onCorrelateMessageSubscription(buffer, offset, length);
                break;
              case CloseMessageSubscriptionDecoder.TEMPLATE_ID:
                onCloseMessageSubscription(buffer, offset, length);
                break;
              case CloseProcessMessageSubscriptionDecoder.TEMPLATE_ID:
                onCloseProcessMessageSubscription(buffer, offset, length);
                break;
              case RejectCorrelateMessageSubscriptionDecoder.TEMPLATE_ID:
                onRejectCorrelateMessageSubscription(buffer, offset, length);
                break;
              default:
                break;
            }
          }
          future.complete(null);
        });
    return future;
  }

  private boolean onOpenMessageSubscription(
      final DirectBuffer buffer, final int offset, final int length) {
    openMessageSubscriptionCommand.wrap(buffer, offset, length);

    messageSubscriptionRecord
        .setProcessInstanceKey(openMessageSubscriptionCommand.getProcessInstanceKey())
        .setElementInstanceKey(openMessageSubscriptionCommand.getElementInstanceKey())
        .setBpmnProcessId(openMessageSubscriptionCommand.getBpmnProcessId())
        .setMessageKey(-1)
        .setMessageName(openMessageSubscriptionCommand.getMessageName())
        .setCorrelationKey(openMessageSubscriptionCommand.getCorrelationKey())
        .setInterrupting(openMessageSubscriptionCommand.shouldCloseOnCorrelate());

    return writeCommand(
        openMessageSubscriptionCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CREATE,
        messageSubscriptionRecord);
  }

  private boolean onOpenProcessMessageSubscription(
      final DirectBuffer buffer, final int offset, final int length) {
    openProcessMessageSubscriptionCommand.wrap(buffer, offset, length);

    final long processInstanceKey = openProcessMessageSubscriptionCommand.getProcessInstanceKey();
    final int processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);

    processMessageSubscriptionRecord.reset();
    processMessageSubscriptionRecord
        .setSubscriptionPartitionId(
            openProcessMessageSubscriptionCommand.getSubscriptionPartitionId())
        .setProcessInstanceKey(processInstanceKey)
        .setElementInstanceKey(openProcessMessageSubscriptionCommand.getElementInstanceKey())
        .setMessageKey(-1)
        .setMessageName(openProcessMessageSubscriptionCommand.getMessageName())
        .setInterrupting(openProcessMessageSubscriptionCommand.shouldCloseOnCorrelate());

    return writeCommand(
        processInstancePartitionId,
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CREATE,
        processMessageSubscriptionRecord);
  }

  private boolean onCorrelateProcessMessageSubscription(
      final DirectBuffer buffer, final int offset, final int length) {
    correlateProcessMessageSubscriptionCommand.wrap(buffer, offset, length);

    final long processInstanceKey =
        correlateProcessMessageSubscriptionCommand.getProcessInstanceKey();
    final int processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);

    processMessageSubscriptionRecord
        .setSubscriptionPartitionId(
            correlateProcessMessageSubscriptionCommand.getSubscriptionPartitionId())
        .setProcessInstanceKey(processInstanceKey)
        .setElementInstanceKey(correlateProcessMessageSubscriptionCommand.getElementInstanceKey())
        .setBpmnProcessId(correlateProcessMessageSubscriptionCommand.getBpmnProcessId())
        .setMessageKey(correlateProcessMessageSubscriptionCommand.getMessageKey())
        .setMessageName(correlateProcessMessageSubscriptionCommand.getMessageName())
        .setVariables(correlateProcessMessageSubscriptionCommand.getVariables())
        .setCorrelationKey(correlateProcessMessageSubscriptionCommand.getCorrelationKey());

    return writeCommand(
        processInstancePartitionId,
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CORRELATE,
        processMessageSubscriptionRecord);
  }

  private boolean onCorrelateMessageSubscription(
      final DirectBuffer buffer, final int offset, final int length) {
    correlateMessageSubscriptionCommand.wrap(buffer, offset, length);

    messageSubscriptionRecord.reset();
    messageSubscriptionRecord
        .setProcessInstanceKey(correlateMessageSubscriptionCommand.getProcessInstanceKey())
        .setElementInstanceKey(correlateMessageSubscriptionCommand.getElementInstanceKey())
        .setBpmnProcessId(correlateMessageSubscriptionCommand.getBpmnProcessId())
        .setMessageKey(-1)
        .setMessageName(correlateMessageSubscriptionCommand.getMessageName());

    return writeCommand(
        correlateMessageSubscriptionCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CORRELATE,
        messageSubscriptionRecord);
  }

  private boolean onCloseMessageSubscription(
      final DirectBuffer buffer, final int offset, final int length) {
    closeMessageSubscriptionCommand.wrap(buffer, offset, length);

    messageSubscriptionRecord.reset();
    messageSubscriptionRecord
        .setProcessInstanceKey(closeMessageSubscriptionCommand.getProcessInstanceKey())
        .setElementInstanceKey(closeMessageSubscriptionCommand.getElementInstanceKey())
        .setMessageKey(-1L)
        .setMessageName(closeMessageSubscriptionCommand.getMessageName());

    return writeCommand(
        closeMessageSubscriptionCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.DELETE,
        messageSubscriptionRecord);
  }

  private boolean onCloseProcessMessageSubscription(
      final DirectBuffer buffer, final int offset, final int length) {
    closeProcessMessageSubscriptionCommand.wrap(buffer, offset, length);

    final long processInstanceKey = closeProcessMessageSubscriptionCommand.getProcessInstanceKey();
    final int processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);

    processMessageSubscriptionRecord.reset();
    processMessageSubscriptionRecord
        .setSubscriptionPartitionId(
            closeProcessMessageSubscriptionCommand.getSubscriptionPartitionId())
        .setProcessInstanceKey(processInstanceKey)
        .setElementInstanceKey(closeProcessMessageSubscriptionCommand.getElementInstanceKey())
        .setMessageKey(-1)
        .setMessageName(closeProcessMessageSubscriptionCommand.getMessageName());

    return writeCommand(
        processInstancePartitionId,
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.DELETE,
        processMessageSubscriptionRecord);
  }

  private boolean onRejectCorrelateMessageSubscription(
      final DirectBuffer buffer, final int offset, final int length) {
    resetMessageCorrelationCommand.wrap(buffer, offset, length);

    final long processInstanceKey = resetMessageCorrelationCommand.getProcessInstanceKey();

    messageSubscriptionRecord.reset();
    messageSubscriptionRecord
        .setProcessInstanceKey(processInstanceKey)
        .setElementInstanceKey(-1L)
        .setBpmnProcessId(resetMessageCorrelationCommand.getBpmnProcessId())
        .setMessageName(resetMessageCorrelationCommand.getMessageName())
        .setCorrelationKey(resetMessageCorrelationCommand.getCorrelationKey())
        .setMessageKey(resetMessageCorrelationCommand.getMessageKey())
        .setInterrupting(false);

    return writeCommand(
        resetMessageCorrelationCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.REJECT,
        messageSubscriptionRecord);
  }

  private boolean writeCommand(
      final int partitionId,
      final ValueType valueType,
      final Intent intent,
      final UnpackedObject command) {

    final LogStreamRecordWriter logStreamRecordWriter =
        logstreamRecordWriterSupplier.apply(partitionId);
    if (logStreamRecordWriter == null) {
      // ignore message if you are not the leader of the partition
      return true;
    }

    logStreamRecordWriter.reset();
    recordMetadata.reset().recordType(RecordType.COMMAND).valueType(valueType).intent(intent);

    final long position =
        logStreamRecordWriter
            .key(-1)
            .metadataWriter(recordMetadata)
            .valueWriter(command)
            .tryWrite();

    return position > 0;
  }
}
