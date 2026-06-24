/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

/**
 * Builds a stream's command processors and event appliers, mirroring the engine's {@code
 * TypedRecordProcessorFactory}. Invoked once while a {@link RecordProcessingEngine} is constructed:
 * the {@link RecordProcessors} context already exposes the {@link StateWriter}, so processors
 * capture it (and any other writers) at construction rather than receiving it per call.
 */
@FunctionalInterface
public interface RecordProcessorsFactory {

  void createProcessors(RecordProcessors processors);
}
