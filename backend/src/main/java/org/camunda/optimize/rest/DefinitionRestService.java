/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionTenantsRequest;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
  public List<DefinitionWithTenantsDto> getDefinitions(@Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getFullyImportedDefinitions(userId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}")
  public List<DefinitionOptimizeDto> getDefinitions(@Context ContainerRequestContext requestContext,
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
    return definitionService.getDefinitionWithAvailableTenants(type, key, userId)
      .orElseThrow(() -> {
        final String reason = String.format("Was not able to find definition for type [%s] and key [%s].", type, key);
        log.error(reason);
        return new NotFoundException(reason);
      });
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}/versions")
  public List<DefinitionVersionDto> getDefinitionVersions(@Context final ContainerRequestContext requestContext,
                                                          @PathParam("type") final DefinitionType type,
                                                          @PathParam("key") final String key,
                                                          @QueryParam("filterByCollectionScope") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<String> optionalCollectionId = Optional.ofNullable(collectionId);

    final List<DefinitionVersionDto> definitionVersions = optionalCollectionId
      .map(id -> collectionScopeService.getCollectionDefinitionVersionsByKeyAndType(type, key, userId, id))
      .orElseGet(() -> definitionService.getDefinitionVersions(type, key, userId));

    if (definitionVersions.isEmpty()) {
      final String reason = String.format(
        "Was not able to find definition version for type [%s] and key [%s] in scope of collection [%s].",
        type, key, collectionId
      );
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return definitionVersions;
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}/_resolveTenantsForVersions")
  public List<TenantResponseDto> getDefinitionTenants(@Context final ContainerRequestContext requestContext,
                                                      @PathParam("type") final DefinitionType type,
                                                      @PathParam("key") final String key,
                                                      @RequestBody final DefinitionTenantsRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    if (request.getVersions() != null && request.getVersions().isEmpty()) {
      return Collections.emptyList();
    }

    final List<TenantDto> tenants = request.getFilterByCollectionScope()
      .map(collectionId -> collectionScopeService.getCollectionDefinitionTenantsByKeyAndType(
        type, key, userId, request.getVersions(), collectionId
      ))
      .orElseGet(() -> definitionService.getDefinitionTenants(type, key, userId, request.getVersions()));

    if (tenants.isEmpty()) {
      final String reason = String.format(
        "Was not able to find definition tenants for type [%s], key [%s], versions [%s] in scope of collection [%s].",
        type, key, request.getVersions(), request.getFilterByCollectionScope()
      );
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return tenants.stream()
      .map(tenantDto -> new TenantResponseDto(tenantDto.getId(), tenantDto.getName()))
      .collect(Collectors.toList());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/keys")
  public List<DefinitionKeyDto> getDefinitionKeys(@Context final ContainerRequestContext requestContext,
                                                  @PathParam("type") final DefinitionType type,
                                                  @QueryParam("filterByCollectionScope") final String collectionId,
                                                  @QueryParam("excludeEventProcesses") final boolean excludeEventProcesses) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<String> optionalCollectionId = Optional.ofNullable(collectionId);

    final List<DefinitionWithTenantsDto> definitions = optionalCollectionId
      .map(id -> collectionScopeService.getCollectionDefinitions(type, excludeEventProcesses, userId, id))
      .orElseGet(() -> definitionService.getFullyImportedDefinitions(type, excludeEventProcesses, userId));

    return definitions.stream()
      .map(definition -> new DefinitionKeyDto(definition.getKey(), definition.getName()))
      .collect(Collectors.toList());
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
  public Response getDefinitionXml(@Context ContainerRequestContext requestContext,
                                   @PathParam("type") DefinitionType type,
                                   @QueryParam("key") String key,
                                   @QueryParam("version") String version,
                                   @QueryParam("tenantId") String tenantId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<DefinitionOptimizeDto> definitionDto =
      definitionService.getDefinitionWithXml(type, userId, key, version, tenantId);

    if (!definitionDto.isPresent()) {
      logAndThrowNotFoundException(type, key, version);
    }
    final Response.ResponseBuilder responseBuilder = Response.ok().type(MediaType.APPLICATION_XML);
    if (ReportConstants.LATEST_VERSION.equals(version)) {
      addNoStoreCacheHeader(responseBuilder);
    }
    switch (type) {
      case PROCESS:
        final ProcessDefinitionOptimizeDto processDef = (ProcessDefinitionOptimizeDto) definitionDto.get();
        responseBuilder.entity(processDef.getBpmn20Xml());
        if (processDef.isEventBased()) {
          addNoStoreCacheHeader(responseBuilder);
        }
        break;
      case DECISION:
        final DecisionDefinitionOptimizeDto decisionDef = (DecisionDefinitionOptimizeDto) definitionDto.get();
        responseBuilder.entity(decisionDef.getDmn10Xml());
        break;
      default:
        throw new BadRequestException("Unknown DefinitionType:" + type);
    }

    return responseBuilder.build();
  }

  private void addNoStoreCacheHeader(final Response.ResponseBuilder processResponse) {
    processResponse.header(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  private void logAndThrowNotFoundException(@PathParam("type") final DefinitionType type,
                                            @QueryParam("key") final String key,
                                            @QueryParam("version") final String version) {
    String notFoundErrorMessage = "Could not find xml for definition with key [" + key + "]," +
      " version [" + version + "] or type [" + type + "]." +
      " It is possible that is hasn't been imported yet.";
    log.error(notFoundErrorMessage);
    throw new NotFoundException(notFoundErrorMessage);
  }

}
