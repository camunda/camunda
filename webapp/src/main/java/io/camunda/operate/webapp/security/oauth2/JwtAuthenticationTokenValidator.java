/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.oauth2;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public interface JwtAuthenticationTokenValidator {

   boolean isValid(JwtAuthenticationToken token);

}
