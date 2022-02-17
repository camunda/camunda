/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.oauth2;

import static io.camunda.operate.util.CollectionUtil.firstOrDefault;
import static io.camunda.operate.util.CollectionUtil.getOrDefaultFromMap;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OAuthClientProperties;
import io.camunda.operate.property.OperateProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CCSaaSJwtAuthenticationTokenValidator implements JwtAuthenticationTokenValidator{

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  public static final String AUDIENCE = "aud";
  public static final String SCOPE = "scope";

  @Autowired
  private OperateProperties operateProperties;
  @Override
  public boolean isValid(final JwtAuthenticationToken token) {
    final Map<String, Object> payload = token.getTokenAttributes();
    return isValid(payload);
  }

  private boolean isValid(final Map<String, Object> payload) {
    try {
      final String audience = getAudience(payload);
      final String scope = getScope(payload);
      final OAuthClientProperties clientConfig = operateProperties.getClient();
      return clientConfig.getAudience().equals(audience)
          && clientConfig.getScope().equals(scope);
    } catch (Exception e) {
      logger.error(
          String.format("Validation of JWT payload failed due to %s. Request is not authenticated.", e.getMessage()), e);
      return false;
    }
  }

  private String getScope(final Map<String, Object> payload) {
    final Object scopeObject = payload.get(SCOPE);
    if (scopeObject instanceof String) {
      return (String) scopeObject;
    }
    if (scopeObject instanceof List) {
      return firstOrDefault(
          (List<String>) getOrDefaultFromMap(payload, AUDIENCE, Collections.emptyList()), null);
    }
    throw new OperateRuntimeException(
        String.format("Couldn't get scope from type %s", scopeObject.getClass()));
  }

  private String getAudience(final Map<String, Object> payload) {
    return firstOrDefault(
        (List<String>) getOrDefaultFromMap(payload, AUDIENCE, Collections.emptyList()), null);
  }
}
