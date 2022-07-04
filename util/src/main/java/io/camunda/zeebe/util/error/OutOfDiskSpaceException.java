/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.error;

import java.io.IOException;

/**
 * An exception thrown when a write operation to disk fails because there is insufficient disk
 * space.
 */
public final class OutOfDiskSpaceException extends IOException {
  public OutOfDiskSpaceException(final String message) {
    super(message);
  }
}
