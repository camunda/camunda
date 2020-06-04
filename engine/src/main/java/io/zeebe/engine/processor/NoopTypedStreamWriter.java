/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Optional;
import java.util.function.Consumer;

final class NoopTypedStreamWriter implements TypedStreamWriter {

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType type,
      final String reason) {
    // no op implementation
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends UnpackedObject> command,
      final RejectionType type,
      final String reason,
      final Consumer<RecordMetadata> metadata) {
    // no op implementation
  }

  @Override
  public void appendNewEvent(final long key, final Intent intent, final UnpackedObject value) {
    // no op implementation
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final UnpackedObject value) {
    // no op implementation
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final Consumer<RecordMetadata> metadata) {
    // no op implementation
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    // no op implementation
  }

  @Override
  public void appendNewCommand(final Intent intent, final UnpackedObject value) {
    // no op implementation
  }

  @Override
  public void appendFollowUpCommand(
      final long key, final Intent intent, final UnpackedObject value) {
    // no op implementation
  }

  @Override
  public void appendFollowUpCommand(
      final long key,
      final Intent intent,
      final UnpackedObject value,
      final Consumer<RecordMetadata> metadata) {
    // no op implementation
  }

  @Override
  public void reset() {
    // no op implementation
  }

  @Override
  public Optional<ActorFuture<Long>> flush() {
    final CompletableActorFuture<Long> future = new CompletableActorFuture<>();
    future.complete(0L);
    return Optional.of(future);
  }
}
