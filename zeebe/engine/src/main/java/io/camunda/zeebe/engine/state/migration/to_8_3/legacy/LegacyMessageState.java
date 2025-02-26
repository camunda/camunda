/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3.legacy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.metrics.BufferedMessagesMetrics;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import org.agrona.DirectBuffer;

public final class LegacyMessageState {

  private static final String DEADLINE_MESSAGE_COUNT_KEY = "deadline_message_count";

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
   * <pre>count | key -> value
   *
   * gets the count of message deadlines
   */
  private final DbLong messagesDeadlineCount;

  private final DbString messagesDeadlineCountKey;
  private final ColumnFamily<DbString, DbLong> messagesDeadlineCountColumnFamily;

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
      activeProcessInstancesByCorrelationKeyColumnFamily;

  /**
   * <pre> process instance key -> correlation key
   *
   * get correlation key by process instance key
   */
  private final DbLong processInstanceKey;

  private final ColumnFamily<DbLong, DbString> processInstanceCorrelationKeyColumnFamily;
  private final BufferedMessagesMetrics bufferedMessagesMetrics;
  private Long localMessageDeadlineCount = 0L;

  public LegacyMessageState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final int partitionId) {
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
            ZbColumnFamilies.DEPRECATED_MESSAGES,
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

    messagesDeadlineCount = new DbLong();
    messagesDeadlineCountKey = new DbString();
    messagesDeadlineCountColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_STATS,
            transactionContext,
            messagesDeadlineCountKey,
            messagesDeadlineCount);

    messagesDeadlineCountKey.wrapString(DEADLINE_MESSAGE_COUNT_KEY);

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
    activeProcessInstancesByCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
            transactionContext,
            bpmnProcessIdCorrelationKey,
            DbNil.INSTANCE);

    processInstanceKey = new DbLong();
    processInstanceCorrelationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,
            transactionContext,
            processInstanceKey,
            correlationKey);

    bufferedMessagesMetrics = new BufferedMessagesMetrics(zeebeDb.getMeterRegistry());
  }

  public void put(final long key, final MessageRecord record) {
    messageKey.wrapLong(key);
    message.setMessageKey(key).setMessage(record);
    messageColumnFamily.insert(messageKey, message);

    messageName.wrapBuffer(record.getNameBuffer());
    correlationKey.wrapBuffer(record.getCorrelationKeyBuffer());
    nameCorrelationMessageColumnFamily.insert(nameCorrelationMessageKey, DbNil.INSTANCE);

    deadline.wrapLong(record.getDeadline());
    deadlineColumnFamily.insert(deadlineMessageKey, DbNil.INSTANCE);

    localMessageDeadlineCount += 1L;
    messagesDeadlineCount.wrapLong(localMessageDeadlineCount);
    messagesDeadlineCountColumnFamily.upsert(messagesDeadlineCountKey, messagesDeadlineCount);
    bufferedMessagesMetrics.setBufferedMessagesCounter(localMessageDeadlineCount);

    final DirectBuffer messageId = record.getMessageIdBuffer();
    if (messageId.capacity() > 0) {
      this.messageId.wrapBuffer(messageId);
      messageIdColumnFamily.upsert(nameCorrelationMessageIdKey, DbNil.INSTANCE);
    }
  }

  public ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbForeignKey<DbLong>>, DbNil>
      getNameCorrelationMessageColumnFamily() {
    return nameCorrelationMessageColumnFamily;
  }
}
