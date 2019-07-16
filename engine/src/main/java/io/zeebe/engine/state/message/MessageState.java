/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public class MessageState {

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
   * find messages which are before a given timestamp */
  private final DbLong deadline;

  private final DbCompositeKey<DbLong, DbLong> deadlineMessageKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> deadlineColumnFamily;

  /**
   * <pre>name | correlation key | message id -> []
   *
   * exist a message for a given message name, correlation key and message id */
  private final DbString messageId;

  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>
      nameCorrelationMessageIdKey;
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, DbNil>
      messageIdColumnFamily;

  /**
   * <pre>key | workflow instance key -> []
   *
   * check if a message is correlated to a workflow instance */
  private final DbCompositeKey<DbLong, DbLong> messageWorkflowKey;

  private final DbLong workflowInstanceKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> correlatedMessageColumnFamily;

  public MessageState(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    messageKey = new DbLong();
    message = new Message();
    messageColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.MESSAGE_KEY, dbContext, messageKey, message);

    messageName = new DbString();
    correlationKey = new DbString();
    nameAndCorrelationKey = new DbCompositeKey<>(messageName, correlationKey);
    nameCorrelationMessageKey = new DbCompositeKey<>(nameAndCorrelationKey, messageKey);
    nameCorrelationMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGES, dbContext, nameCorrelationMessageKey, DbNil.INSTANCE);

    deadline = new DbLong();
    deadlineMessageKey = new DbCompositeKey<>(deadline, messageKey);
    deadlineColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_DEADLINES, dbContext, deadlineMessageKey, DbNil.INSTANCE);

    messageId = new DbString();
    nameCorrelationMessageIdKey = new DbCompositeKey<>(nameAndCorrelationKey, messageId);
    messageIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_IDS, dbContext, nameCorrelationMessageIdKey, DbNil.INSTANCE);

    workflowInstanceKey = new DbLong();
    messageWorkflowKey = new DbCompositeKey<>(messageKey, workflowInstanceKey);
    correlatedMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_CORRELATED, dbContext, messageWorkflowKey, DbNil.INSTANCE);
  }

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

  public void putMessageCorrelation(long messageKey, long workflowInstanceKey) {
    this.messageKey.wrapLong(messageKey);
    this.workflowInstanceKey.wrapLong(workflowInstanceKey);
    correlatedMessageColumnFamily.put(messageWorkflowKey, DbNil.INSTANCE);
  }

  public boolean existMessageCorrelation(long messageKey, long workflowInstanceKey) {
    this.messageKey.wrapLong(messageKey);
    this.workflowInstanceKey.wrapLong(workflowInstanceKey);

    return correlatedMessageColumnFamily.exists(messageWorkflowKey);
  }

  public void removeMessageCorrelation(long messageKey, long workflowInstanceKey) {
    this.messageKey.wrapLong(messageKey);
    this.workflowInstanceKey.wrapLong(workflowInstanceKey);

    correlatedMessageColumnFamily.delete(messageWorkflowKey);
  }

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

  public Message getMessage(long messageKey) {
    this.messageKey.wrapLong(messageKey);
    return messageColumnFamily.get(this.messageKey);
  }

  public void visitMessagesWithDeadlineBefore(final long timestamp, MessageVisitor visitor) {
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

  public boolean exist(
      final DirectBuffer name, final DirectBuffer correlationKey, final DirectBuffer messageId) {
    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);
    this.messageId.wrapBuffer(messageId);

    return messageIdColumnFamily.exists(nameCorrelationMessageIdKey);
  }

  public void remove(final long key) {
    final Message message = getMessage(key);
    if (message == null) {
      return;
    }

    messageKey.wrapLong(message.getKey());
    messageColumnFamily.delete(messageKey);

    messageName.wrapBuffer(message.getName());
    this.correlationKey.wrapBuffer(message.getCorrelationKey());

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

  @FunctionalInterface
  public interface MessageVisitor {
    boolean visit(Message message);
  }
}
