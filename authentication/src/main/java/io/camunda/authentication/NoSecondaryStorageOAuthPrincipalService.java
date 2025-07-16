/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.OAuthContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Fallback OAuth principal service that is used when secondary storage is not available.
 * This service always fails OAuth authentication attempts with a clear error message.
 */
public class NoSecondaryStorageOAuthPrincipalService implements CamundaOAuthPrincipalService {

  private static final Logger LOG = LoggerFactory.getLogger(NoSecondaryStorageOAuthPrincipalService.class);

  @Override
  public OAuthContext loadOAuthContext(final Map<String, Object> claims) {
    final String subject = (String) claims.get("sub");
    LOG.error(
        "OAuth authentication attempted for subject '{}' but secondary storage is disabled (camunda.database.type=none). "
            + "OAuth authentication requires secondary storage to be configured.",
        subject);
    
    throw new OAuth2AuthenticationException(
        new OAuth2Error(
            OAuth2ErrorCodes.SERVER_ERROR,
            "OAuth authentication is not available when secondary storage is disabled (camunda.database.type=none). "
                + "Please configure secondary storage to enable OAuth authentication.",
            null));
  }
}