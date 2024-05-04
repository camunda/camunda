/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

/**
 * An action that is executed at the end of the processing. It will <b>not</b> be executed during
 * the re-processing.
 */
@FunctionalInterface
public interface SideEffectProducer {

  /**
   * Applies the side effect.
   *
   * @return <code>false</code> to indicate that the side effect could not be applied successfully
   */
  boolean flush();
}
