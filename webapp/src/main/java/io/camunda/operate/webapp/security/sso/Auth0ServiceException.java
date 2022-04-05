/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.sso;

// Wrap auth0 exceptions for testing and specific handling
public class Auth0ServiceException extends RuntimeException {

  public Auth0ServiceException(String message){
    super(message);
  }

  public Auth0ServiceException(final Exception e) {
    super(e);
  }
}
