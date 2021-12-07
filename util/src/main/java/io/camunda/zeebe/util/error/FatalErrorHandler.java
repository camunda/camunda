/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.error;

/**
 * FatalErrorHandler can be used to handle all {@link Throwable}s safely and consistently.
 * Implementations interpret a throwable and take <i>some</i> action when the throwable is
 * considered fatal.
 *
 * @see FatalErrorHandlers
 * @see VirtualMachineErrorHandler
 */
public interface FatalErrorHandler {
  /**
   * Handles arbitrary {@link Throwable}s. Use this method whenever catching a {@link Throwable},
   * before carrying on with your regular error handling.
   *
   * <p>{@link VirtualMachineErrorHandler} will exit on all {@link VirtualMachineError}s
   *
   * @param e the throwable
   */
  void handleError(Throwable e);
}
