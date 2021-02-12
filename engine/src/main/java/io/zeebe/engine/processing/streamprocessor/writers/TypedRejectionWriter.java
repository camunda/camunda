/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;
import java.util.function.UnaryOperator;

public interface TypedRejectionWriter {

  void appendRejection(
      TypedRecord<? extends RecordValue> command, RejectionType type, String reason);

  /** @deprecated The modifier parameter is not used at the time of writing */
  @Deprecated
  void appendRejection(
      TypedRecord<? extends RecordValue> command,
      RejectionType type,
      String reason,
      UnaryOperator<RecordMetadata> modifier);
}
