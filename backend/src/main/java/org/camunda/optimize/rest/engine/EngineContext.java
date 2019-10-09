/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine;

import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.EngineGroupDto;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConstantsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.GROUP_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MEMBER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;

public class EngineContext {
  private static final Logger logger = LoggerFactory.getLogger(EngineContext.class);

  private String engineAlias;
  private Client engineClient;
  private ConfigurationService configurationService;

  public EngineContext(String engineAlias, Client engineClient, ConfigurationService configurationService) {
    this.engineAlias = engineAlias;
    this.engineClient = engineClient;
    this.configurationService = configurationService;
  }

  public Client getEngineClient() {
    return engineClient;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public Optional<String> getDefaultTenantId() {
    return configurationService.getEngineDefaultTenantIdOfCustomEngine(engineAlias);
  }

  public List<EngineGroupDto> getAllGroupsOfUser(String userId) {
    try {
      Response response = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .queryParam(MEMBER, userId)
        .path(GROUP_ENDPOINT)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == 200) {
        // @formatter:off
        return response.readEntity(new GenericType<List<EngineGroupDto>>() {});
        // @formatter:on
      }
    } catch (Exception e) {
      logger.error("Could not fetch groups for user [{}]", userId, e);
    }
    return new ArrayList<>();
  }

  public Optional<GroupDto> getGroupById(final String groupId) {
    EngineGroupDto groupDto = null;
    try {
      Response response = getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
        .path(EngineConstantsUtil.GROUP_BY_ID_ENDPOINT_TEMPLATE)
        .resolveTemplate("id", groupId)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == 200) {
        groupDto = response.readEntity(EngineGroupDto.class);
      }
    } catch (Exception e) {
      logger.error("Could not fetch group with id [{}]", groupId, e);
    }
    return Optional.ofNullable(groupDto)
      .map(group -> new GroupDto(group.getId(), group.getName()));
  }

  public List<AuthorizationDto> getAllApplicationAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_APPLICATION);
    } catch (Exception e) {
      logger.error("Could not fetch application authorizations from the Engine to check the access permissions.", e);
    }
    return new ArrayList<>();
  }

  public List<AuthorizationDto> getAllProcessDefinitionAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_PROCESS_DEFINITION);
    } catch (Exception e) {
      logger.error(
        "Could not fetch process definition authorizations from the Engine to check the access permissions.",
        e
      );
    }
    return new ArrayList<>();
  }

  public List<AuthorizationDto> getAllDecisionDefinitionAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_DECISION_DEFINITION);
    } catch (Exception e) {
      logger.error(
        "Could not fetch decision definition authorizations from the Engine to check the access permissions.",
        e
      );
    }
    return new ArrayList<>();
  }

  public List<AuthorizationDto> getAllTenantAuthorizations() {
    try {
      return getAuthorizationsForType(RESOURCE_TYPE_TENANT);
    } catch (Exception e) {
      logger.error(
        "Could not fetch tenant authorizations from the Engine to check the access permissions.",
        e
      );
    }
    return new ArrayList<>();
  }

  private List<AuthorizationDto> getAuthorizationsForType(final int resourceType) {
    final Response response = getEngineClient()
      .target(configurationService.getEngineRestApiEndpointOfCustomEngine(getEngineAlias()))
      .path(AUTHORIZATION_ENDPOINT)
      .queryParam(RESOURCE_TYPE, resourceType)
      .request(MediaType.APPLICATION_JSON)
      .get();
    if (response.getStatus() == 200) {
      // @formatter:off
      return response.readEntity(new GenericType<List<AuthorizationDto>>() {});
      // @formatter:on
    } else {
      return new ArrayList<>();
    }
  }

}
