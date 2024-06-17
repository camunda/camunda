/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import static io.camunda.tasklist.property.IdentityProperties.ALL_RESOURCES;
import static io.camunda.tasklist.property.IdentityProperties.FULL_GROUP_ACCESS;

import io.camunda.identity.autoconfigure.IdentityProperties;
import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuthorizationServiceImpl implements IdentityAuthorizationService {

  private final Logger logger = LoggerFactory.getLogger(IdentityAuthorizationServiceImpl.class);

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private IdentityProperties identityProperties;

  @Override
  public List<String> getUserGroups() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String accessToken = null;

    final Identity identity = SpringContextHolder.getBean(Identity.class);
    // Extract access token based on authentication type
    if (authentication instanceof IdentityAuthentication) {
      accessToken = ((IdentityAuthentication) authentication).getTokens().getAccessToken();
      return identity.authentication().getGroups(accessToken);
    } else if (authentication instanceof TokenAuthentication) {
      accessToken = ((TokenAuthentication) authentication).getAccessToken();
      final String organization = ((TokenAuthentication) authentication).getOrganization();
      // Sending explicit the audience null to get the groups in the organization
      // Method getGroups is not used because it returns the groups of the user without consider
      // organization
      return identity.authentication().getGroupsInOrganization(accessToken, null, organization);
    }

    // Fallback groups if authentication type is unrecognized or access token is null
    final List<String> defaultGroups = new ArrayList<>();
    defaultGroups.add(FULL_GROUP_ACCESS);
    return defaultGroups;
  }

  @Override
  public boolean isAllowedToStartProcess(final String processDefinitionKey) {
    return !Collections.disjoint(
        getProcessDefinitionsFromAuthorization(), Set.of(ALL_RESOURCES, processDefinitionKey));
  }

  @Override
  public List<String> getProcessReadFromAuthorization() {
    return getFromAuthorization(IdentityAuthorization.PROCESS_PERMISSION_READ);
  }

  @Override
  public List<String> getProcessDefinitionsFromAuthorization() {
    return getFromAuthorization(IdentityAuthorization.PROCESS_PERMISSION_START);
  }

  private Optional<IdentityAuthorization> getIdentityAuthorization() {
    if (!tasklistProperties.getIdentity().isResourcePermissionsEnabled()
        || identityProperties.baseUrl() == null) {
      return Optional.empty();
    }

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    return switch (authentication) {
      case final IdentityAuthentication identityAuthentication ->
          Optional.of(identityAuthentication.getAuthorizations());
      case final JwtAuthenticationToken jwtAuthenticationToken -> {
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        yield Optional.of(
            new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(jwtAuthenticationToken.getToken().getTokenValue())));
      }

      case final TokenAuthentication tokenAuthentication -> {
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        yield Optional.of(
            new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(
                        tokenAuthentication.getAccessToken(),
                        tokenAuthentication.getOrganization())));
      }
      default -> Optional.empty();
    };
  }

  private List<String> getFromAuthorization(final String type) {
    final Optional<IdentityAuthorization> optIdentityAuthorization = getIdentityAuthorization();
    if (optIdentityAuthorization.isEmpty()) {
      return Collections.singletonList(ALL_RESOURCES);
    }

    final IdentityAuthorization identityAuthorization = optIdentityAuthorization.get();

    return switch (type) {
      case IdentityAuthorization.PROCESS_PERMISSION_READ ->
          identityAuthorization.getProcessesAllowedToRead();
      case IdentityAuthorization.PROCESS_PERMISSION_START ->
          identityAuthorization.getProcessesAllowedToStart();
      default -> Collections.emptyList();
    };
  }
}
