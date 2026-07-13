/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.management;

import io.camunda.service.exception.ServiceException;

public class IncompleteTopologyException extends ServiceException {
  public IncompleteTopologyException(final String message) {
    super(message, Status.UNAVAILABLE);
  }
}
