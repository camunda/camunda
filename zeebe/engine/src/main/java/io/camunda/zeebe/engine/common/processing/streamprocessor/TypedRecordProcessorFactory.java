/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.streamprocessor;

@FunctionalInterface
public interface TypedRecordProcessorFactory {

  /**
   * Creates typed record processors with the given context.
   *
   * @param typedRecordProcessorContext the processing context which contains value information to
   *     create record processors
   * @return the created typed record processors
   */
  TypedRecordProcessors createProcessors(TypedRecordProcessorContext typedRecordProcessorContext);
}
