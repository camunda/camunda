/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/** Here the interface is just a suggestion. Can be whatever PDT team thinks is best to work with */
public interface TaskResultBuilder {

  /**
   * Appends a record to the result
   *
   * @return returns itself for method chaining
   */
  TaskResultBuilder appendCommandRecord(
      final long key, final Intent intent, final RecordValue value);

  TaskResult build();
}
