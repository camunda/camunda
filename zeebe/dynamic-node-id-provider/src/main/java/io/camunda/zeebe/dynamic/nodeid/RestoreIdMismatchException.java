/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

public class RestoreIdMismatchException extends RuntimeException {

  public RestoreIdMismatchException(final long expectedRestoreId, final long actualRestoreId) {
    super(
        String.format(
            "Restore ID mismatch: expected %d but got %d", expectedRestoreId, actualRestoreId));
  }
}
