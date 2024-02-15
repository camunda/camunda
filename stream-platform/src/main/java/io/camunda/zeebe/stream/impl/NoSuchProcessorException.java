/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.exception.UnrecoverableException;

/**
 * Exception type thrown by the {@link ProcessingStateMachine} when encountering a command that is
 * not accepted by any of the registered processors.
 */
final class NoSuchProcessorException extends UnrecoverableException {

  NoSuchProcessorException(final TypedRecord<?> command) {
    super("No processor registered for command type %s".formatted(command.getValueType()));
  }
}
