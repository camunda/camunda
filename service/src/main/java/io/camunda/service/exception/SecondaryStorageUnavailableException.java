/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

/**
 * Exception thrown when a request requires secondary storage but the system is running in headless mode.
 */
public class SecondaryStorageUnavailableException extends ServiceException {

  public SecondaryStorageUnavailableException() {
    super("This endpoint requires secondary storage to be configured. The current deployment is running in headless mode (database.type=none). Please configure a secondary storage system to access this functionality.", 
          ServiceException.Status.FORBIDDEN);
  }
}