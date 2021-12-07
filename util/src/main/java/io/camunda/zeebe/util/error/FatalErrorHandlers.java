/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.error;

import java.lang.Thread.UncaughtExceptionHandler;
import org.slf4j.Logger;

/** Factories for constructing common {@link FatalErrorHandler}s */
public class FatalErrorHandlers {

  /**
   * Builds a {@link FatalErrorHandler} that can be used as the default uncaught exception handler
   */
  public static UncaughtExceptionHandler uncaughtExceptionHandler(final Logger logger) {
    return new VirtualMachineErrorHandler(logger);
  }

  /** Builds the default {@link FatalErrorHandler} */
  public static FatalErrorHandler withLogger(final Logger logger) {
    return new VirtualMachineErrorHandler(logger);
  }
}
