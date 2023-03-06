/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import static io.camunda.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.camunda.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableBoolean;

public final class DbMessageState implements MutableMessageState {

  /**
   * <pre>message key -> message
   */
  private final ColumnFamily<DbLong, StoredMessage> messageColumnFamily;

  private final DbLong messageKey;
  private final DbForeignKey<DbLong> fkMessage;
  private final StoredMessage message;

  /**
   * <pre>name | correlation key | key -> []
   *
   * find message by name and correlation key - the message key ensures the queue ordering
   */
  private final DbString messageName;

  private final DbString correlationKey;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbForeignKey<DbLong>>
      nameCorrelationMessageKey;
  private final DbCompositeKey<DbString, DbString> nameAndCorrelationKey;
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbForeignKey<DbLong>>, DbNil>
      nameCorrelationMessageColumnFamily;

  /**
   * <pre>deadline | key -> []
   *
   * find messages which are before a given timestamp
   */
  private final DbLong deadline;

  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> deadlineMessageKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil>
      deadlineColumnFamily;

  /**
   * <pre>name | correlation key | message id -> []
   *
   * exist a message for a given message name, correlation key and message id
   */
  private final DbString messageId;

  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>
      nameCorrelationMessageIdKey;
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, DbNil>
      messageIdColumnFamily;

  /**
   * <pre>key | bpmn process id -> []
   *
   * check if a message is correlated to a process
   */
  private final DbCompositeKey<DbForeignKey<DbLong>, DbString> messageBpmnProcessIdKey;

  private final DbString bpmnProcessIdKey;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbString>, DbNil>
      correlatedMessageColumnFamily;

  /**
   * <pre> bpmn process id | correlation key -> []
   *
   * check if a process instance is created by this correlation key
   */
  private final DbCompositeKey<DbString, DbString> bpmnProcessIdCorrelationKey;

  private final ColumnFamily<DbCompositeKey<DbString, DbString>, DbNil>
      activeProcessInstancesByCorrelationKeyColumnFamiliy;

  /**
   * <pre> process instance key -> correlation key
   *
   * get correlation key by process instance key
   */
  private final DbLong processInstanceKey;

  private final ColumnFamily<DbLong, DbString> processInstanceCorrelationKeyColumnFamiliy;

  public DbMessageState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageKey = new DbLong();
    fkMessage = new DbForeignKey<>(messageKey, ZbColumnFamilies.MESSAGE_KEY);
    message = new StoredMessage();
    messageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_KEY, transactionContext, messageKey, message);

    messageName = new DbString();
    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationMessageKey = new DbCompositeKey<>(nameAndCorrelationKey, fkMessage);
    nameCorrelationMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGES,
            transactionContext,
            nameCorrelationMessageKey,
            DbNil.INSTANCE);

    deadline = new DbLong();
    deadlineMessageKey = new DbCompositeKey<>(deadline, fkMessage);
    deadlineColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_DEADLINES,
            transactionContext,
            deadlineMessageKey,
            DbNil.INSTANCE);

    messageId = new DbString();
    nameCorrelationMessageIdKey = new DbCompositeKey<>(nameAndCorrelationKey, messageId);
    messageIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_IDS,
            transactionContext,
            nameCorrelationMessageIdKey,
            DbNil.INSTANCE);

    bpmnProcessIdKey = new DbString();
    messageBpmnProcessIdKey = new DbCompositeKey<>(fkMessage, bpmnProcessIdKey);
    correlatedMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_CORRELATED,
            transactionContext,
            messageBpmnProcessIdKey,
            DbNil.INSTANCE);

    bpmnProcessIdCorrelationKey = new DbCompositeKey<>(bpmnProcessIdKey, correlationKey);
    activeProcessInstancesByCorrelationKeyColumnFamiliy =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
            transactionContext,
            bpmnProcessIdCorrelationKey,
            DbNil.INSTANCE);

    processInstanceKey = new DbLong();
    processInstanceCorrelationKeyColumnFamiliy =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,
            transactionContext,
            processInstanceKey,
            correlationKey);
  }

  @Override
  public void put(final long key, final MessageRecord record) {
    messageKey.wrapLong(key);
    message.setMessageKey(key).setMessage(record);
    messageColumnFamily.insert(messageKey, message);

    messageName.wrapBuffer(record.getNameBuffer());
    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    nameCorrelationMessageColumnFamily.insert(nameCorrelationMessageKey, DbNil.INSTANCE);

    deadline.wrapLong(record.getDeadline());
    deadlineColumnFamily.insert(deadlineMessageKey, DbNil.INSTANCE);

    final DirectBuffer messageId = record.getMessageIdBuffer();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.upsert(nameCorrelationMessageIdKey, DbNil.INSTANCE);
    }
  }

  @Override
  public void putMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    correlatedMessageColumnFamily.insert(messageBpmnProcessIdKey, DbNil.INSTANCE);
  }

  @Override
  public void removeMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);

    correlatedMessageColumnFamily.deleteExisting(messageBpmnProcessIdKey);
  }

  @Override
  public void putActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    activeProcessInstancesByCorrelationKeyColumnFamiliy.insert(
        bpmnProcessIdCorrelationKey, DbNil.INSTANCE);
  }

  @Override
  public void removeActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    activeProcessInstancesByCorrelationKeyColumnFamiliy.deleteExisting(bpmnProcessIdCorrelationKey);
  }

  @Override
  public void putProcessInstanceCorrelationKey(
      final long processInstanceKey, final DirectBuffer correlationKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    this.processInstanceKey.wrapLong(processInstanceKey);
    this.correlationKey.wrapBuffer(correlationKey);
    processInstanceCorrelationKeyColumnFamiliy.insert(this.processInstanceKey, this.correlationKey);
  }

  @Override
  public void removeProcessInstanceCorrelationKey(final long processInstanceKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);

    this.processInstanceKey.wrapLong(processInstanceKey);
    processInstanceCorrelationKeyColumnFamiliy.deleteExisting(this.processInstanceKey);
  }

  @Override
  public void remove(final long key) {
    final StoredMessage storedMessage = getMessage(key);
    if (storedMessage == null) {
      return;
    }

    messageKey.wrapLong(storedMessage.getMessageKey());
    messageColumnFamily.deleteExisting(messageKey);

    messageName.wrapBuffer(storedMessage.getMessage().getNameBuffer());
    correlationKey.wrapBuffer(storedMessage.getMessage().getCorrelationKeyBuffer());

    nameCorrelationMessageColumnFamily.deleteExisting(nameCorrelationMessageKey);

    final DirectBuffer messageId = storedMessage.getMessage().getMessageIdBuffer();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.deleteExisting(nameCorrelationMessageIdKey);
    }

    deadline.wrapLong(storedMessage.getMessage().getDeadline());
    deadlineColumnFamily.deleteExisting(deadlineMessageKey);

    correlatedMessageColumnFamily.whileEqualPrefix(
        messageKey,
        ((compositeKey, zbNil) -> {
          correlatedMessageColumnFamily.deleteExisting(compositeKey);
        }));
  }

  @Override
  public boolean existMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);

    return correlatedMessageColumnFamily.exists(messageBpmnProcessIdKey);
  }

  @Override
  public boolean existActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    return activeProcessInstancesByCorrelationKeyColumnFamiliy.exists(bpmnProcessIdCorrelationKey);
  }

  @Override
  public DirectBuffer getProcessInstanceCorrelationKey(final long processInstanceKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);

    this.processInstanceKey.wrapLong(processInstanceKey);
    final var correlationKey =
        processInstanceCorrelationKeyColumnFamiliy.get(this.processInstanceKey);

    return correlationKey != null ? correlationKey.getBuffer() : null;
  }

  @Override
  public void visitMessages(
      final DirectBuffer name, final DirectBuffer correlationKey, final MessageVisitor visitor) {

    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);

    nameCorrelationMessageColumnFamily.whileEqualPrefix(
        nameAndCorrelationKey,
        (compositeKey, nil) -> {
          final long messageKey = compositeKey.second().inner().getValue();
          final StoredMessage message = getMessage(messageKey);
          return visitor.visit(message);
        });
  }

  @Override
  public StoredMessage getMessage(final long messageKey) {
    this.messageKey.wrapLong(messageKey);
    return messageColumnFamily.get(this.messageKey);
  }

  @Override
  public boolean visitMessagesWithDeadlineBeforeTimestamp(
      final long timestamp, final Index startAt, final ExpiredMessageVisitor visitor) {
    final DbCompositeKey<DbLong, DbForeignKey<DbLong>> startAtKey;
    if (startAt != null) {
      deadline.wrapLong(startAt.deadline());
      messageKey.wrapLong(startAt.key());
      startAtKey = deadlineMessageKey;
    } else {
      startAtKey = null;
    }
    final var stoppedByVisitor = new MutableBoolean(false);
    deadlineColumnFamily.whileTrue(
        startAtKey,
        (key, value) -> {
          final var shouldContinue = visit(timestamp, visitor, key);
          stoppedByVisitor.set(!shouldContinue);
          return shouldContinue;
        });

    return stoppedByVisitor.get();
  }

  @Override
  public boolean exist(
      final DirectBuffer name, final DirectBuffer correlationKey, final DirectBuffer messageId) {
    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);
    this.messageId.wrapBuffer(messageId);

    return messageIdColumnFamily.exists(nameCorrelationMessageIdKey);
  }

  private static boolean visit(
      final long timestamp,
      final ExpiredMessageVisitor visitor,
      final DbCompositeKey<DbLong, DbForeignKey<DbLong>> compositeDeadlineKey) {
    final long deadline = compositeDeadlineKey.first().getValue();
    if (deadline <= timestamp) {
      final long messageKey = compositeDeadlineKey.second().inner().getValue();
      return visitor.visit(deadline, messageKey);
    }
    return false;
  }
}
