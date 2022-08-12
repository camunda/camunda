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

public final class DbMessageState implements MutableMessageState {

  /**
   * <pre>message key -> message
   */
  private final ColumnFamily<DbLong, StoredMessage> messageColumnFamily;

  private final DbLong messageKey;
  private final DbForeignKey<DbLong> fkMessage;
  private final StoredMessage message;
  private final DbString messageName;
  private final DbString correlationKey;
  private final DbString tenantId;

  /**
   * <pre>tenant id | name | correlation key | key -> []
   *
   * find message by name and correlation key - the message key ensures the queue ordering
   */
  private final DbCompositeKey<DbString, DbCompositeKey<DbString, DbString>>
      tenantIdNameCorrelationKey;

  private final DbCompositeKey<
          DbCompositeKey<DbString, DbCompositeKey<DbString, DbString>>, DbForeignKey<DbLong>>
      tenantIdNameCorrelationMessageKey;

  private final ColumnFamily<
          DbCompositeKey<
              DbCompositeKey<DbString, DbCompositeKey<DbString, DbString>>, DbForeignKey<DbLong>>,
          DbNil>
      tenantIdNameCorrelationMessageColumnFamily;

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
   * <pre>tenant id | name | correlation key | message id -> []
   *
   * exist a message for a given message name, correlation key and message id
   */
  private final DbString messageId;

  private final DbCompositeKey<
          DbCompositeKey<DbString, DbCompositeKey<DbString, DbString>>, DbString>
      tenantNameCorrelationMessageIdKey;
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbCompositeKey<DbString, DbString>>, DbString>,
          DbNil>
      messageIdColumnFamily;

  /**
   * <pre>key | tenant id | bpmn process id -> []
   *
   * check if a message is correlated to a process
   */
  private final DbCompositeKey<DbForeignKey<DbLong>, DbCompositeKey<DbString, DbString>>
      messageTenantBpmnProcessIdKey;

  private final DbString bpmnProcessIdKey;
  private final ColumnFamily<
          DbCompositeKey<DbForeignKey<DbLong>, DbCompositeKey<DbString, DbString>>, DbNil>
      correlatedMessageColumnFamily;

  /**
   * <pre> tenant id | bpmn process id | correlation key -> []
   *
   * check if a process instance is created by this correlation key
   */
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>
      tenantBpmnProcessIdCorrelationKey;

  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, DbNil>
      activeProcessInstancesByCorrelationKeyColumnFamily;

  /**
   * <pre> process instance key -> correlation key
   *
   * get correlation key by process instance key
   */
  private final DbLong processInstanceKey;

  private final ColumnFamily<DbLong, DbString> processInstanceCorrelationKeyColumnFamily;

  public DbMessageState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageKey = new DbLong();
    fkMessage = new DbForeignKey<>(messageKey, ZbColumnFamilies.MESSAGE_KEY);
    message = new StoredMessage();
    messageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_KEY, transactionContext, messageKey, message);

    tenantId = new DbString();
    messageName = new DbString();
    correlationKey = new DbString();
    tenantIdNameCorrelationKey =
        new DbCompositeKey<>(tenantId, new DbCompositeKey<>(messageName, correlationKey));
    tenantIdNameCorrelationMessageKey = new DbCompositeKey<>(tenantIdNameCorrelationKey, fkMessage);
    tenantIdNameCorrelationMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGES,
            transactionContext,
            tenantIdNameCorrelationMessageKey,
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
    tenantNameCorrelationMessageIdKey = new DbCompositeKey<>(tenantIdNameCorrelationKey, messageId);
    messageIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_IDS,
            transactionContext,
            tenantNameCorrelationMessageIdKey,
            DbNil.INSTANCE);

    bpmnProcessIdKey = new DbString();
    messageTenantBpmnProcessIdKey =
        new DbCompositeKey<>(fkMessage, new DbCompositeKey<>(tenantId, bpmnProcessIdKey));
    correlatedMessageColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_CORRELATED,
            transactionContext,
            messageTenantBpmnProcessIdKey,
            DbNil.INSTANCE);

    tenantBpmnProcessIdCorrelationKey =
        new DbCompositeKey<>(new DbCompositeKey<>(tenantId, bpmnProcessIdKey), correlationKey);
    activeProcessInstancesByCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
            transactionContext,
            tenantBpmnProcessIdCorrelationKey,
            DbNil.INSTANCE);

    processInstanceKey = new DbLong();
    processInstanceCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,
            transactionContext,
            processInstanceKey,
            correlationKey);
  }

  @Override
  public void put(final long key, final MessageRecord record) {
    tenantId.wrapBuffer(record.getTenantIdBuffer());
    messageKey.wrapLong(key);
    message.setMessageKey(key).setMessage(record);
    messageColumnFamily.insert(messageKey, message);

    messageName.wrapBuffer(record.getNameBuffer());
    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    tenantIdNameCorrelationMessageColumnFamily.insert(
        tenantIdNameCorrelationMessageKey, DbNil.INSTANCE);

    deadline.wrapLong(record.getDeadline());
    deadlineColumnFamily.insert(deadlineMessageKey, DbNil.INSTANCE);

    final DirectBuffer messageId = record.getMessageIdBuffer();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.upsert(tenantNameCorrelationMessageIdKey, DbNil.INSTANCE);
    }
  }

  @Override
  public void putMessageCorrelation(
      final long messageKey, final DirectBuffer bpmnProcessId, final DirectBuffer tenantId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("Tenant ID", tenantId);

    this.tenantId.wrapBuffer(tenantId);
    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    correlatedMessageColumnFamily.insert(messageTenantBpmnProcessIdKey, DbNil.INSTANCE);
  }

  @Override
  public void removeMessageCorrelation(
      final long messageKey, final DirectBuffer bpmnProcessId, final DirectBuffer tenantId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("tenant id", tenantId);

    this.tenantId.wrapBuffer(tenantId);
    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);

    correlatedMessageColumnFamily.deleteExisting(messageTenantBpmnProcessIdKey);
  }

  @Override
  public void putActiveProcessInstance(
      final DirectBuffer bpmnProcessId,
      final DirectBuffer correlationKey,
      final DirectBuffer tenantId) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);
    ensureNotNullOrEmpty("tenant id", tenantId);

    this.tenantId.wrapBuffer(tenantId);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    activeProcessInstancesByCorrelationKeyColumnFamily.insert(
        tenantBpmnProcessIdCorrelationKey, DbNil.INSTANCE);
  }

  @Override
  public void removeActiveProcessInstance(
      final DirectBuffer bpmnProcessId,
      final DirectBuffer correlationKey,
      final DirectBuffer tenantId) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);
    ensureNotNullOrEmpty("tenant id", tenantId);

    this.tenantId.wrapBuffer(tenantId);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    activeProcessInstancesByCorrelationKeyColumnFamily.deleteExisting(
        tenantBpmnProcessIdCorrelationKey);
  }

  @Override
  public void putProcessInstanceCorrelationKey(
      final long processInstanceKey, final DirectBuffer correlationKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);
    ensureNotNullOrEmpty("correlation key", correlationKey);

    this.processInstanceKey.wrapLong(processInstanceKey);
    this.correlationKey.wrapBuffer(correlationKey);
    processInstanceCorrelationKeyColumnFamily.insert(this.processInstanceKey, this.correlationKey);
  }

  @Override
  public void removeProcessInstanceCorrelationKey(final long processInstanceKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);

    this.processInstanceKey.wrapLong(processInstanceKey);
    processInstanceCorrelationKeyColumnFamily.deleteExisting(this.processInstanceKey);
  }

  @Override
  public void remove(final long key) {
    final StoredMessage storedMessage = getMessage(key);
    if (storedMessage == null) {
      return;
    }

    tenantId.wrapBuffer(storedMessage.getMessage().getTenantIdBuffer());
    messageKey.wrapLong(storedMessage.getMessageKey());
    messageColumnFamily.deleteExisting(messageKey);

    messageName.wrapBuffer(storedMessage.getMessage().getNameBuffer());
    correlationKey.wrapBuffer(storedMessage.getMessage().getCorrelationKeyBuffer());

    tenantIdNameCorrelationMessageColumnFamily.deleteExisting(tenantIdNameCorrelationMessageKey);

    final DirectBuffer messageId = storedMessage.getMessage().getMessageIdBuffer();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.deleteExisting(tenantNameCorrelationMessageIdKey);
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
  public boolean existMessageCorrelation(
      final long messageKey, final DirectBuffer bpmnProcessId, final DirectBuffer tenantId) {
    ensureGreaterThan("message key", messageKey, 0);
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("Tenant id", tenantId);

    this.tenantId.wrapBuffer(tenantId);
    this.messageKey.wrapLong(messageKey);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);

    return correlatedMessageColumnFamily.exists(messageTenantBpmnProcessIdKey);
  }

  @Override
  public boolean existActiveProcessInstance(
      final DirectBuffer bpmnProcessId,
      final DirectBuffer correlationKey,
      final DirectBuffer tenantId) {
    ensureNotNullOrEmpty("BPMN process id", bpmnProcessId);
    ensureNotNullOrEmpty("correlation key", correlationKey);
    ensureNotNullOrEmpty("tenant id", tenantId);

    this.tenantId.wrapBuffer(tenantId);
    bpmnProcessIdKey.wrapBuffer(bpmnProcessId);
    this.correlationKey.wrapBuffer(correlationKey);
    return activeProcessInstancesByCorrelationKeyColumnFamily.exists(
        tenantBpmnProcessIdCorrelationKey);
  }

  @Override
  public DirectBuffer getProcessInstanceCorrelationKey(final long processInstanceKey) {
    ensureGreaterThan("process instance key", processInstanceKey, 0);

    this.processInstanceKey.wrapLong(processInstanceKey);
    final var correlationKey =
        processInstanceCorrelationKeyColumnFamily.get(this.processInstanceKey);

    return correlationKey != null ? correlationKey.getBuffer() : null;
  }

  @Override
  public void visitMessages(
      final DirectBuffer name,
      final DirectBuffer correlationKey,
      final DirectBuffer tenantId,
      final MessageVisitor visitor) {

    this.tenantId.wrapBuffer(tenantId);
    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);

    tenantIdNameCorrelationMessageColumnFamily.whileEqualPrefix(
        tenantIdNameCorrelationKey,
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
  public void visitMessagesWithDeadlineBefore(final long timestamp, final MessageVisitor visitor) {
    deadlineColumnFamily.whileTrue(
        ((compositeKey, zbNil) -> {
          final long deadline = compositeKey.first().getValue();
          if (deadline <= timestamp) {
            final long messageKey = compositeKey.second().inner().getValue();
            final StoredMessage message = getMessage(messageKey);
            return visitor.visit(message);
          }
          return false;
        }));
  }

  @Override
  public boolean exist(
      final DirectBuffer name,
      final DirectBuffer correlationKey,
      final DirectBuffer messageId,
      final DirectBuffer tenantId) {
    messageName.wrapBuffer(name);
    this.correlationKey.wrapBuffer(correlationKey);
    this.messageId.wrapBuffer(messageId);
    this.tenantId.wrapBuffer(tenantId);

    return messageIdColumnFamily.exists(tenantNameCorrelationMessageIdKey);
  }
}
