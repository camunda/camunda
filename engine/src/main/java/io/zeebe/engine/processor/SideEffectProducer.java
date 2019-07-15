/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

@FunctionalInterface
public interface SideEffectProducer {

  /**
   * Applies the side effect. Called by the stream processor in the appropriate stage in the record
   * processing lifecycle.
   *
   * @return false in case of backpressure, else true
   */
  boolean flush();
}
