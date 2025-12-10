/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import java.util.Optional;
import java.util.function.Predicate;

@FunctionalInterface
public interface ProcessInstanceStreamer {

  static ProcessInstanceStreamer noop() {
    return (processInstanceKey, filter) -> Optional.empty();
  }

  Optional<ProcessInstanceStreamer.ProcessInstanceStream> streamFor(
      final long processInstanceKey, final Predicate<Long> filter);

  default Optional<ProcessInstanceStreamer.ProcessInstanceStream> streamFor(
      final long processInstanceKey) {
    return streamFor(processInstanceKey, ignored -> true);
  }

  interface ProcessInstanceStream {

    long processInstanceKey();

    void push(final ProcessInstanceRecord payload);
  }
}
