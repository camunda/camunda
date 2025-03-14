/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.oauth2;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.util.CollectionUtil.firstOrDefault;
import static io.camunda.operate.util.CollectionUtil.getOrDefaultFromMap;
import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.config.operate.OperateProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + IDENTITY_AUTH_PROFILE)
public class CCSaaSJwtAuthenticationTokenValidator implements JwtAuthenticationTokenValidator {

  public static final String AUDIENCE = "aud";
  public static final String CLUSTER_ID_CLAIM = "https://camunda.com/clusterId";
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired private OperateProperties operateProperties;

  @Override
  public boolean isValid(final JwtAuthenticationToken token) {
    final Map<String, Object> payload = token.getTokenAttributes();
    return isValid(payload);
  }

  private boolean isValid(final Map<String, Object> payload) {
    try {
      return getClusterId(payload).equals(getClusterIdFromConfiguration())
          && getAudience(payload).equals(getAudienceFromConfiguration());
    } catch (final Exception e) {
      logger.error(
          String.format(
              "Validation of JWT payload failed due to %s. Request is not authenticated.",
              e.getMessage()),
          e);
      return false;
    }
  }

  private String getClusterId(final Map<String, Object> payload) {
    final Object clusterIdObject = payload.get(CLUSTER_ID_CLAIM);
    if (clusterIdObject == null) {
      throw new OperateRuntimeException(
          "Couldn't get clusterId from JWT payload. Maybe wrong clusterId configuration?");
    }
    if (clusterIdObject instanceof String) {
      return (String) clusterIdObject;
    }
    if (clusterIdObject instanceof List) {
      return firstOrDefault(
          (List<String>) getOrDefaultFromMap(payload, AUDIENCE, Collections.emptyList()), null);
    }
    throw new OperateRuntimeException(
        "Couldn't get clusterId from JWT payload as String or list of Strings. Maybe wrong clusterId configuration?");
  }

  private String getAudience(final Map<String, Object> payload) {
    final Object audienceObject = payload.get(AUDIENCE);
    if (audienceObject == null) {
      throw new OperateRuntimeException("Couldn't get audience from JWT payload.");
    }
    if (audienceObject instanceof String) {
      return (String) audienceObject;
    }
    if (audienceObject instanceof List) {
      return ((List<String>) audienceObject).get(0);
    }
    throw new OperateRuntimeException(
        "Couldn't get audience from JWT payload as String or array of Strings.");
  }

  private String getClusterIdFromConfiguration() {
    String clusterId = operateProperties.getCloud().getClusterId();
    if (stringIsEmpty(clusterId)) {
      // fallback to old configuration from client properties
      logger.warn(
          "ClusterId should come from 'CAMUNDA_OPERATE_CLOUD_CLUSTERID' try 'CAMUNDA_OPERATE_CLIENT_CLUSTERID'");
      clusterId = operateProperties.getClient().getClusterId();
    }
    if (stringIsEmpty(clusterId)) {
      throw new OperateRuntimeException(
          "No configuration found in 'CAMUNDA_OPERATE_CLOUD_CLUSTERID' or 'CAMUNDA_OPERATE_CLIENT_CLUSTERID'");
    }
    return clusterId;
  }

  private String getAudienceFromConfiguration() {
    final String audience = operateProperties.getClient().getAudience();
    if (stringIsEmpty(audience)) {
      throw new OperateRuntimeException(
          "No configuration found in 'CAMUNDA_OPERATE_CLIENT_AUDIENCE'");
    }
    return audience;
  }
}
