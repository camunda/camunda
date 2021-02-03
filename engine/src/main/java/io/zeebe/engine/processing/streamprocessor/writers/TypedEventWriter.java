/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.UnaryOperator;

public interface TypedEventWriter {

  void appendNewEvent(long key, Intent intent, RecordValue value);

  void appendFollowUpEvent(long key, Intent intent, RecordValue value);

  /** @deprecated The modifier parameter is not used at the time of writing */
  @Deprecated
  void appendFollowUpEvent(
      long key, Intent intent, RecordValue value, UnaryOperator<RecordMetadata> modifier);
}
