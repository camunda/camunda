/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.exceptions;

import io.camunda.zeebe.util.exception.UnrecoverableException;

/**
 * A cluster ID mismatch won't resolve itself on retry, so this extends {@link
 * UnrecoverableException}: the broker's exporter-open retry loop treats it as terminal instead of
 * retrying forever.
 */
public class IncompatibleClusterIdException extends UnrecoverableException {
  public IncompatibleClusterIdException(final String message) {
    super(message);
  }
}
