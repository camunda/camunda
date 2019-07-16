/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

public interface StreamProcessorLifecycleAware {

  default void onOpen(ReadonlyProcessingContext context) {}

  /** Callback after reprocessing was successful and before regular processing begins */
  default void onRecovered(ReadonlyProcessingContext context) {}

  default void onClose() {}
}
