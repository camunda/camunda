/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

import jakarta.ws.rs.ForbiddenException;

// For some reason, this suppression does not work if saved in the suppression constants file
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class EventProcessManagementForbiddenException extends ForbiddenException {
  public EventProcessManagementForbiddenException(String userId) {
    super(
        String.format(
            "The user %s is not authorized to use the event process management features of the API",
            userId));
  }
}
