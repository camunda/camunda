/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.ViewPublisher;
import io.camunda.zeebe.db.layered.typed.LayeredViewReader;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableBoolean;

/**
 * A {@link MessageState} for the asynchronous message-TTL checker over the layered state store's
 * read views (experimental; only wired when the layered-state flag is on): each {@link
 * #visitMessagesWithDeadlineBeforeTimestamp} acquires the latest published {@link ReadOnlyView},
 * scans it, and releases it — so the scan observes a consistent, slightly stale cut of the
 * committed state instead of reading a separate transaction context that would miss the buffered
 * writes. Key encoding mirrors {@link DbMessageState}'s deadline column family exactly.
 *
 * <p>Only the deadline scan is supported — it is the only read the checker performs. The other
 * {@link MessageState} reads belong to processors, which read the owner state.
 *
 * <p><b>Threading:</b> one instance per reader; the flyweights are shared across calls, so calls
 * must not overlap (the checker executes on a single actor, which guarantees exactly that).
 */
public final class LayeredViewMessageState implements MessageState {

  private static final String UNSUPPORTED_MESSAGE =
      "expected only deadline scans on the layered view message state, but %s was called";

  private final ViewPublisher views;

  private final DbLong messageKey = new DbLong();
  private final DbForeignKey<DbLong> fkMessage =
      new DbForeignKey<>(messageKey, ZbColumnFamilies.MESSAGE_KEY);
  private final DbLong deadline = new DbLong();
  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> deadlineMessageKey =
      new DbCompositeKey<>(deadline, fkMessage);

  public LayeredViewMessageState(final ViewPublisher views) {
    this.views = Objects.requireNonNull(views, "views");
  }

  @Override
  public boolean visitMessagesWithDeadlineBeforeTimestamp(
      final long timestamp, final Index startAt, final ExpiredMessageVisitor visitor) {
    if (startAt != null) {
      // the TTL checker always scans from the beginning; resumable iteration belongs to the
      // expiry processor, which reads the owner state
      throw new UnsupportedOperationException(
          UNSUPPORTED_MESSAGE.formatted(
              "visitMessagesWithDeadlineBeforeTimestamp with a start index"));
    }
    final ReadOnlyView view = views.acquireLatest();
    try {
      final var deadlines =
          new LayeredViewReader<>(
              view, ZbColumnFamilies.MESSAGE_DEADLINES.name(), deadlineMessageKey, DbNil.INSTANCE);
      final var stoppedByVisitor = new MutableBoolean(false);
      deadlines.whileTrue(
          (key, nil) -> {
            boolean shouldContinue = false;
            final long deadlineEntry = key.first().getValue();
            if (deadlineEntry <= timestamp) {
              final long messageKeyEntry = key.second().inner().getValue();
              shouldContinue = visitor.visit(deadlineEntry, messageKeyEntry);
              stoppedByVisitor.set(!shouldContinue);
            }
            return shouldContinue;
          });
      return stoppedByVisitor.get();
    } finally {
      views.release(view);
    }
  }

  @Override
  public boolean existMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    throw new UnsupportedOperationException(
        UNSUPPORTED_MESSAGE.formatted("existMessageCorrelation"));
  }

  @Override
  public boolean existActiveProcessInstance(
      final String tenantId, final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    throw new UnsupportedOperationException(
        UNSUPPORTED_MESSAGE.formatted("existActiveProcessInstance"));
  }

  @Override
  public DirectBuffer getProcessInstanceCorrelationKey(final long processInstanceKey) {
    throw new UnsupportedOperationException(
        UNSUPPORTED_MESSAGE.formatted("getProcessInstanceCorrelationKey"));
  }

  @Override
  public void visitMessages(
      final String tenantId,
      final DirectBuffer name,
      final DirectBuffer correlationKey,
      final MessageVisitor visitor) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("visitMessages"));
  }

  @Override
  public void visitMessagesWithBusinessId(
      final String tenantId, final DirectBuffer businessId, final MessageVisitor visitor) {
    throw new UnsupportedOperationException(
        UNSUPPORTED_MESSAGE.formatted("visitMessagesWithBusinessId"));
  }

  @Override
  public StoredMessage getMessage(final long messageKey) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("getMessage"));
  }

  @Override
  public boolean exist(
      final DirectBuffer name,
      final DirectBuffer correlationKey,
      final DirectBuffer messageId,
      final String tenantId) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("exist"));
  }

  @Override
  public void visitCrossPartitionStartLocks(final CrossPartitionStartLockVisitor visitor) {
    throw new UnsupportedOperationException(
        UNSUPPORTED_MESSAGE.formatted("visitCrossPartitionStartLocks"));
  }

  @Override
  public long getCrossPartitionStartLockHolder(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    throw new UnsupportedOperationException(
        UNSUPPORTED_MESSAGE.formatted("getCrossPartitionStartLockHolder"));
  }
}
