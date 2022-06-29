/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;

// After the migration, none of these should be in use anymore and replaced by the CommandWriter and
// StateWriter passed along to the constructors of the concrete processors.
public interface TypedRecordProcessor<T extends UnifiedRecordValue>
    extends StreamProcessorLifecycleAware {

  /**
   * @param record the record to process
   */
  default void processRecord(final TypedRecord<T> record) {}
}
