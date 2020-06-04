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
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.Optional;
import java.util.function.Consumer;

/** Things that any actor can write to a partition. */
public interface TypedCommandWriter {

  void appendNewCommand(Intent intent, UnpackedObject value);

  void appendFollowUpCommand(long key, Intent intent, UnpackedObject value);

  void appendFollowUpCommand(
      long key, Intent intent, UnpackedObject value, Consumer<RecordMetadata> metadata);

  void reset();

  /** @return an optional with a future to be completed with the position after it's written */
  Optional<ActorFuture<Long>> flush();
}
