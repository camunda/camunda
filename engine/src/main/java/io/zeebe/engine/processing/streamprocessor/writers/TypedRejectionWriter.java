/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;

public interface TypedRejectionWriter {

  void appendRejection(
      TypedRecord<? extends RecordValue> command, RejectionType type, String reason);
}
