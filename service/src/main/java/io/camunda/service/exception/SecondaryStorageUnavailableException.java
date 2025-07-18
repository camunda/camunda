/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

public class SecondaryStorageUnavailableException extends ServiceException {
  public static final String NO_SECONDARY_STORAGE_MESSAGE =
      "This endpoint requires a secondary storage, but none is set. Secondary storage can be configured using the 'camunda.database.type' property.";

  public SecondaryStorageUnavailableException() {
    super(NO_SECONDARY_STORAGE_MESSAGE, ServiceException.Status.FORBIDDEN);
  }
}
