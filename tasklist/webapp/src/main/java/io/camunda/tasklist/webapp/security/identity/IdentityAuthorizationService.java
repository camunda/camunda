/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuthorizationService {

  private final Logger logger = LoggerFactory.getLogger(IdentityAuthorizationService.class);

  @Autowired private TasklistProperties tasklistProperties;

  public List<String> getProcessDefinitionsFromAuthorization() {
    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()
        && tasklistProperties.getIdentity().getBaseUrl() != null) {
      final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof IdentityAuthentication) {
        final IdentityAuthentication identityAuthentication =
            (IdentityAuthentication) authentication;
        return identityAuthentication.getAuthorizations().getProcessesAllowedToStart();
      } else if (authentication instanceof JwtAuthenticationToken) {
        final JwtAuthenticationToken jwtAuthenticationToken =
            (JwtAuthenticationToken) authentication;
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        return new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(jwtAuthenticationToken.getToken().getTokenValue()))
            .getProcessesAllowedToStart();
      } else if (authentication instanceof TokenAuthentication) {
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        return new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(
                        ((TokenAuthentication) authentication).getAccessToken(),
                        ((TokenAuthentication) authentication).getOrganization()))
            .getProcessesAllowedToStart();
      }
    }
    final List<String> result = new ArrayList<String>();
    result.add(IdentityProperties.ALL_RESOURCES);
    return result;
  }

  public List<String> getUserGroups() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String accessToken = null;
    final Identity identity = SpringContextHolder.getBean(Identity.class);
    // Extract access token based on authentication type
    if (authentication instanceof IdentityAuthentication) {
      accessToken = ((IdentityAuthentication) authentication).getTokens().getAccessToken();
      return identity.authentication().verifyToken(accessToken).getUserDetails().getGroups();
    } else if (authentication instanceof TokenAuthentication) {
      accessToken = ((TokenAuthentication) authentication).getAccessToken();
      final String organization = ((TokenAuthentication) authentication).getOrganization();
      return identity
          .authentication()
          .verifyToken(accessToken, organization)
          .getUserDetails()
          .getGroups();
    }

    // Fallback groups if authentication type is unrecognized or access token is null
    final List<String> defaultGroups = new ArrayList<>();
    defaultGroups.add(IdentityProperties.FULL_GROUP_ACCESS);
    return defaultGroups;
  }

  public boolean isAllowedToStartProcess(final String processDefinitionKey) {
    return !Collections.disjoint(
        getProcessDefinitionsFromAuthorization(),
        Set.of(IdentityProperties.ALL_RESOURCES, processDefinitionKey));
  }
}
