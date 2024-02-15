/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.util.exception.UnrecoverableException;

public class NoSuchProcessorException extends UnrecoverableException {

  public NoSuchProcessorException(final TypedRecord<?> command) {
    super("No processor found for command with key " + command.getKey());
  }
}
