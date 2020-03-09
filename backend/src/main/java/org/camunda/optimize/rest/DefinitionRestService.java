/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Slf4j
@Secured
@Path("/definition")
@Component
public class DefinitionRestService {

  private final DefinitionService definitionService;
  private final CollectionScopeService collectionScopeService;
  private final SessionService sessionService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/")
  public List<DefinitionWithTenantsDto> getDefinitions(@Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getDefinitions(userId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}")
  public List<? extends DefinitionOptimizeDto> getDefinitions(@Context ContainerRequestContext requestContext,
                                                              @PathParam("type") DefinitionType type,
                                                              @QueryParam("includeXml") boolean includeXml) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getFullyImportedProcessDefinitions(type, userId, includeXml);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}")
  public DefinitionWithTenantsDto getDefinition(@Context ContainerRequestContext requestContext,
                                                @PathParam("type") DefinitionType type,
                                                @PathParam("key") String key) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getDefinition(type, key, userId)
      .orElseThrow(() -> {
        final String reason = String.format("Was not able to find definition for type [%s] and key [%s].", type, key);
        log.error(reason);
        return new NotFoundException(reason);
      });
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/definitionVersionsWithTenants")
  public List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenants(
    @Context final ContainerRequestContext requestContext,
    @PathParam("type") DefinitionType type,
    @QueryParam("filterByCollectionScope") final String collectionId,
    @QueryParam("excludeEventProcesses") final boolean excludeEventProcesses) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<String> optionalCollectionId = Optional.ofNullable(collectionId);

    return optionalCollectionId
      .map(id -> collectionScopeService.getCollectionDefinitionsGroupedByVersionAndTenantForType(
        type, excludeEventProcesses, userId, id
      ))
      .orElseGet(() -> definitionService.getDefinitionsGroupedByVersionAndTenantForType(
        type, excludeEventProcesses, userId
      ));
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/_groupByTenant")
  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenant(@Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getDefinitionsGroupedByTenant(userId);
  }

  /**
   * Get the definition xml to a given definition of the specified type with the given key and version.
   * If the version is set to "ALL", the xml of the latest version is returned.
   *
   * @param key     The definition key of the desired definition xml.
   * @param version The definition version of the desired definition xml.
   * @param type    The type of the definition (process or decision).
   * @return the definition xml requested or json error structure on failure
   */
  @GET
  @Produces(value = {MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @Path("/{type}/xml")
  @CacheRequest
  public String getDefinitionXml(@Context ContainerRequestContext requestContext,
                                 @PathParam("type") DefinitionType type,
                                 @QueryParam("key") String key,
                                 @QueryParam("version") String version,
                                 @QueryParam("tenantId") String tenantId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<String> definitionXml = definitionService.getDefinitionXml(type, userId, key, version, tenantId);

    return definitionXml.orElseThrow(() -> {
      String notFoundErrorMessage = "Could not find xml for definition with key [" + key + "]," +
        " version [" + version + "] or type [" + type + "]." +
        " It is possible that is hasn't been imported yet.";
      log.error(notFoundErrorMessage);
      return new NotFoundException(notFoundErrorMessage);
    });
  }
}
