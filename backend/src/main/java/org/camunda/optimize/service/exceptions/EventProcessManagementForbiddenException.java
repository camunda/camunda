/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

import javax.ws.rs.ForbiddenException;

// For some reason, this suppression does not work if saved in the suppression constants file
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class EventProcessManagementForbiddenException extends ForbiddenException {
  public EventProcessManagementForbiddenException(String userId) {
    super(String.format(
      "The user %s is not authorized to use the event process management features of the API",
      userId
    ));
  }
}
