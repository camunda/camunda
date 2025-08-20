/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;

public final class TasklistAuthenticationUtil {

  private TasklistAuthenticationUtil() {}

  public static boolean isApiUser(CamundaAuthentication authenticatedUser) {

    if (authenticatedUser.authenticatedUsername() != null) {
      return false;
    } else if (authenticatedUser.authenticatedClientId() != null) {
      return true;
    } else {
      throw new InvalidRequestException(
          "Expecting authentication to either have username or client id");
    }
  }
}
