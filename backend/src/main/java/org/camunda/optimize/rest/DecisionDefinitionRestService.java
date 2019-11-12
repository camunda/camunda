/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionAvailableVersionsWithTenants;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionVersionsWithTenantsRestDto;
import org.camunda.optimize.rest.mapper.DefinitionVersionsWithTenantsMapper;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.DecisionDefinitionService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


@AllArgsConstructor
@Secured
@Path("/decision-definition")
@Component
@Slf4j
public class DecisionDefinitionRestService {
  private final DecisionDefinitionService decisionDefinitionService;
  private final SessionService sessionService;

  /**
   * Retrieves all decision definition stored in Optimize.
   *
   * @param includeXml A parameter saying if the decision definition xml should be included to the response.
   * @return A collection of decision definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Collection<DecisionDefinitionOptimizeDto> getDecisionDefinitions(@Context ContainerRequestContext requestContext,
                                                                          @QueryParam("includeXml") boolean includeXml) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return decisionDefinitionService.getFullyImportedDecisionDefinitions(userId, includeXml);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/definitionVersionsWithTenants")
  public List<DefinitionVersionsWithTenantsRestDto> getProcessDefinitionVersionsWithTenants(
    @Context final ContainerRequestContext requestContext,
    @QueryParam("filterByCollectionScope") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<String> optionalCollectionId = Optional.ofNullable(collectionId);

    final List<DefinitionAvailableVersionsWithTenants> definitionVersionsWithTenants = optionalCollectionId
      .map(id -> decisionDefinitionService.getDecisionDefinitionVersionsWithTenants(userId, id))
      .orElseGet(() -> decisionDefinitionService.getDecisionDefinitionVersionsWithTenants(userId));

    return DefinitionVersionsWithTenantsMapper.mapToDefinitionVersionsWithTenantsRestDto(definitionVersionsWithTenants);
  }

  /**
   * Get the decision definition xml to a given decision definition key and version.
   * If the version is set to "ALL", the xml of the latest version is returned.
   *
   * @param decisionDefinitionKey     The decision definition key of the desired decision definition xml.
   * @param decisionDefinitionVersion The decision definition version of the desired decision definition xml.
   * @return the decision definition xml requested or json error structure on failure
   */
  @GET
  @Produces(value = {MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @Path("/xml")
  @CacheRequest
  public String getDecisionDefinitionXml(@Context ContainerRequestContext requestContext,
                                         @QueryParam("key") String decisionDefinitionKey,
                                         @QueryParam("version") String decisionDefinitionVersion,
                                         @QueryParam("tenantId") String tenantId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return decisionDefinitionService.getDecisionDefinitionXml(
      userId, decisionDefinitionKey, Lists.newArrayList(decisionDefinitionVersion), tenantId
    ).orElseThrow(() -> {
      String notFoundErrorMessage = "Could not find xml for decision definition with key [" + decisionDefinitionKey +
        "] and version [" + decisionDefinitionVersion + "]. It is possible that is hasn't been imported yet.";
      log.error(notFoundErrorMessage);
      return new NotFoundException(notFoundErrorMessage);
    });
  }


}
