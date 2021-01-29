/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableMessageState;
import org.agrona.DirectBuffer;

public final class DbMessageState implements MutableMessageState {

  /**
   * <pre>message key -> message
   */
  private final ColumnFamily<DbLong, Message> messageColumnFamily;

  private final DbLong messageKey;
  private final Message message;

  /**
   * <pre>name | correlation key | key -> []
   *
   * find message by name and correlation key - the message key ensures the queue ordering
   */
  private final DbString messageName;

  private final DbString correlationKey;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>
      nameCorrelationMessageKey;
  private final DbCompositeKey<DbString, DbString> nameAndCorrelationKey;
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>, DbNil>
      nameCorrelationMessageColumnFamily;

  /**
   * <pre>deadline | key -> []
   *
   * find messages which are before a given timestamp
   */
  private final DbLong deadline;

  private final DbCompositeKey<DbLong, DbLong> deadlineMessageKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> deadlineColumnFamily;

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
   * check if a message is correlated to a workflow
   */
  private final DbCompositeKey<DbLong, DbString> messageBpmnProcessIdKey;

  private final DbString bpmnProcessIdKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil> correlatedMessageColumnFamily;

  /**
   * <pre> bpmn process id | correlation key -> []
   *
   * check if a workflow instance is created by this correlation key
   */
  private final DbCompositeKey<DbString, DbString> bpmnProcessIdCorrelationKey;

  private final ColumnFamily<DbCompositeKey<DbString, DbString>, DbNil>
      activeWorkflowInstancesByCorrelationKeyColumnFamiliy;

  /**
   * <pre> workflow instance key -> correlation key
   *
   * get correlation key by workflow instance key
   */
  private final DbLong workflowInstanceKey;

  private final ColumnFamily<DbLong, DbString> workflowInstanceCorrelationKeyColumnFamiliy;

  public DbMessageState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageKey = new DbLong();
    message = new Message();
    messageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_KEY, transactionContext, messageKey, message);

    messageName = new DbString();
    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationMessageKey = new DbCompositeKey<>(nameAndCorrelationKey, messageKey);
    nameCorrelationMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGES,
            transactionContext,
            nameCorrelationMessageKey,
            DbNil.INSTANCE);

    deadline = new DbLong();
    deadlineMessageKey = new DbCompositeKey<>(deadline, messageKey);
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
    messageBpmnProcessIdKey = new DbCompositeKey<>(messageKey, bpmnProcessIdKey);
    correlatedMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_CORRELATED,
            transactionContext,
            messageBpmnProcessIdKey,
            DbNil.INSTANCE);

    bpmnProcessIdCorrelationKey = new DbCompositeKey<>(bpmnProcessIdKey, correlationKey);
    activeWorkflowInstancesByCorrelationKeyColumnFamiliy =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_WORKFLOWS_ACTIVE_BY_CORRELATION_KEY,
            transactionContext,
            bpmnProcessIdCorrelationKey,
            DbNil.INSTANCE);

    workflowInstanceKey = new DbLong();
    workflowInstanceCorrelationKeyColumnFamiliy =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_WORKFLOW_INSTANCE_CORRELATION_KEYS,
            transactionContext,
            workflowInstanceKey,
            correlationKey);
  }

  @Override
  public void put(final Message message) {
    messageKey.wrapLong(message.getKey());
    messageColumnFamily.put(messageKey, message);

    messageName.wrapBuffer(message.getName());
    correlationKey.wrapBuffer(message.getCorrelationKey());
    nameCorrelationMessageColumnFamily.put(nameCorrelationMessageKey, DbNil.INSTANCE);

    deadline.wrapLong(message.getDeadline());
    deadlineColumnFamily.put(deadlineMessageKey, DbNil.INSTANCE);

    final DirectBuffer messageId = message.getId();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.put(nameCorrelationMessageIdKey, DbNil.INSTANCE);
    }
  }

  @Override
  public void putMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    correlatedMessageColumnFamily.put(messageBpmnProcessIdKey, DbNil.INSTANCE);
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
  public void removeMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);

    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);

    correlatedMessageColumnFamily.delete(messageBpmnProcessIdKey);
  }

  @Override
  public boolean existActiveWorkflowInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    return activeWorkflowInstancesByCorrelationKeyColumnFamiliy.exists(bpmnProcessIdCorrelationKey);
  }

  @Override
  public void putActiveWorkflowInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    activeWorkflowInstancesByCorrelationKeyColumnFamiliy.put(
        bpmnProcessIdCorrelationKey, DbNil.INSTANCE);
  }

  @Override
  public void removeActiveWorkflowInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    activeWorkflowInstancesByCorrelationKeyColumnFamiliy.delete(bpmnProcessIdCorrelationKey);
  }

  @Override
  public void putWorkflowInstanceCorrelationKey(
      final long workflowInstanceKey, final DirectBuffer correlationKey) {
    ensureGreaterThan("workflow instance key", workflowInstanceKey, 0);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    this.workflowInstanceKey.wrapLong(workflowInstanceKey);
    this.correlationKey.wrapBuffer(correlationKey);
    workflowInstanceCorrelationKeyColumnFamiliy.put(this.workflowInstanceKey, this.correlationKey);
  }

  @Override
  public DirectBuffer getWorkflowInstanceCorrelationKey(final long workflowInstanceKey) {
    ensureGreaterThan("workflow instance key", workflowInstanceKey, 0);

    this.workflowInstanceKey.wrapLong(workflowInstanceKey);
    final var correlationKey =
        workflowInstanceCorrelationKeyColumnFamiliy.get(this.workflowInstanceKey);

    return correlationKey != null ? correlationKey.getBuffer() : null;
  }

  @Override
  public void removeWorkflowInstanceCorrelationKey(final long workflowInstanceKey) {
    ensureGreaterThan("workflow instance key", workflowInstanceKey, 0);

    this.workflowInstanceKey.wrapLong(workflowInstanceKey);
    workflowInstanceCorrelationKeyColumnFamiliy.delete(this.workflowInstanceKey);
  }

  @Override
  public void visitMessages(
      final DirectBuffer name, final DirectBuffer correlationKey, final MessageVisitor visitor) {

    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);

    nameCorrelationMessageColumnFamily.whileEqualPrefix(
        nameAndCorrelationKey,
        (compositeKey, nil) -> {
          final long messageKey = compositeKey.getSecond().getValue();
          final Message message = getMessage(messageKey);
          return visitor.visit(message);
        });
  }

  @Override
  public Message getMessage(final long messageKey) {
    this.messageKey.wrapLong(messageKey);
    return messageColumnFamily.get(this.messageKey);
  }

  @Override
  public void visitMessagesWithDeadlineBefore(final long timestamp, final MessageVisitor visitor) {
    deadlineColumnFamily.whileTrue(
        ((compositeKey, zbNil) -> {
          final long deadline = compositeKey.getFirst().getValue();
          if (deadline <= timestamp) {
            final long messageKey = compositeKey.getSecond().getValue();
            final Message message = getMessage(messageKey);
            return visitor.visit(message);
          }
          return false;
        }));
  }

  @Override
  public boolean exist(
      final DirectBuffer name, final DirectBuffer correlationKey, final DirectBuffer messageId) {
    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);
    this.messageId.wrapBuffer(messageId);

    return messageIdColumnFamily.exists(nameCorrelationMessageIdKey);
  }

  @Override
  public void remove(final long key) {
    final Message message = getMessage(key);
    if (message == null) {
      return;
    }

    messageKey.wrapLong(message.getKey());
    messageColumnFamily.delete(messageKey);

    messageName.wrapBuffer(message.getName());
    correlationKey.wrapBuffer(message.getCorrelationKey());

    nameCorrelationMessageColumnFamily.delete(nameCorrelationMessageKey);

    final DirectBuffer messageId = message.getId();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.delete(nameCorrelationMessageIdKey);
    }

    deadline.wrapLong(message.getDeadline());
    deadlineColumnFamily.delete(deadlineMessageKey);

    correlatedMessageColumnFamily.whileEqualPrefix(
        messageKey,
        ((compositeKey, zbNil) -> {
          correlatedMessageColumnFamily.delete(compositeKey);
        }));
  }
}
