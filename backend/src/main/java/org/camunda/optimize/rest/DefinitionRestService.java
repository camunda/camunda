/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionTenantsRequestDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import org.camunda.optimize.rest.providers.CacheRequest;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAllOrLatest;

@AllArgsConstructor
@Slf4j
@Path("/definition")
@Component
public class DefinitionRestService {

  private final DefinitionService definitionService;
  private final CollectionScopeService collectionScopeService;
  private final SessionService sessionService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<DefinitionResponseDto> getDefinitions(@Context ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getFullyImportedDefinitions(userId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}")
  public List<DefinitionOptimizeResponseDto> getDefinitions(@Context ContainerRequestContext requestContext,
                                                            @PathParam("type") DefinitionType type,
                                                            @QueryParam("includeXml") boolean includeXml) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getFullyImportedDefinitions(type, userId, includeXml);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/_resolveTenantsForVersions")
  public List<DefinitionWithTenantsResponseDto> getDefinitionTenantsForMultipleKeys(
    @Context final ContainerRequestContext requestContext,
    @PathParam("type") final DefinitionType type,
    @Valid @RequestBody final MultiDefinitionTenantsRequestDto request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    if (CollectionUtils.isEmpty(request.getDefinitions())) {
      return Collections.emptyList();
    }

    return request.getDefinitions().stream()
      .map(definition -> {
        final List<TenantResponseDto> tenantsForDefinitionVersions = getTenantsForDefinitionVersions(
          definition.getKey(), type, definition.getVersions(), request.getFilterByCollectionScope(), userId
        );
        return new DefinitionWithTenantsResponseDto(
          definition.getKey(), definition.getVersions(), tenantsForDefinitionVersions
        );
      })
      .collect(Collectors.toList());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}")
  public DefinitionResponseDto getDefinition(@Context ContainerRequestContext requestContext,
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
  public List<DefinitionVersionResponseDto> getDefinitionVersions(@Context final ContainerRequestContext requestContext,
                                                                  @PathParam("type") final DefinitionType type,
                                                                  @PathParam("key") final String key,
                                                                  @QueryParam("filterByCollectionScope") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<String> optionalCollectionId = Optional.ofNullable(collectionId);

    final List<DefinitionVersionResponseDto> definitionVersions = optionalCollectionId
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

    definitionVersions.sort(Comparator.comparing(
      (DefinitionVersionResponseDto definitionVersionDto) -> Integer.valueOf(definitionVersionDto.getVersion()))
                              .reversed());
    return definitionVersions;
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}/_resolveTenantsForVersions")
  public List<TenantResponseDto> getDefinitionTenants(@Context final ContainerRequestContext requestContext,
                                                      @PathParam("type") final DefinitionType type,
                                                      @PathParam("key") final String key,
                                                      @RequestBody final DefinitionTenantsRequestDto request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    return getTenantsForDefinitionVersions(
      key, type, request.getVersions(), request.getFilterByCollectionScope(), userId
    );
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/keys")
  public List<DefinitionKeyResponseDto> getDefinitionKeys(@Context final ContainerRequestContext requestContext,
                                                          @PathParam("type") final DefinitionType type,
                                                          @QueryParam("filterByCollectionScope") final String collectionId,
                                                          @QueryParam("camundaEventImportedOnly") final boolean camundaEventImportedOnly) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    List<DefinitionResponseDto> definitions = getDefinitions(
      type,
      collectionId,
      camundaEventImportedOnly,
      userId
    );
    return definitions.stream()
      .map(definition -> new DefinitionKeyResponseDto(definition.getKey(), definition.getName()))
      .collect(Collectors.toList());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/_groupByTenant")
  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenant(@Context ContainerRequestContext requestContext) {
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
    final Optional<DefinitionOptimizeResponseDto> definitionDto =
      definitionService.getDefinitionWithXml(type, userId, key, version, tenantId);

    if (!definitionDto.isPresent()) {
      logAndThrowNotFoundException(type, key, version);
    }
    final Response.ResponseBuilder responseBuilder = Response.ok().type(MediaType.APPLICATION_XML);
    if (isDefinitionVersionSetToAllOrLatest(version)) {
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

  private List<TenantResponseDto> getTenantsForDefinitionVersions(final String definitionKey,
                                                                  final DefinitionType type,
                                                                  final List<String> versions,
                                                                  final String scopeCollectionId,
                                                                  final String userId) {
    final List<TenantDto> tenants = Optional.ofNullable(scopeCollectionId)
      .map(collectionId -> collectionScopeService.getCollectionDefinitionTenantsByKeyAndType(
        type, definitionKey, userId, versions, collectionId
      ))
      .orElseGet(() -> definitionService.getDefinitionTenants(type, definitionKey, userId, versions));

    if (tenants.isEmpty()) {
      final String reason = String.format(
        "Was not able to find definition tenants for type [%s], key [%s], versions [%s] in scope of collection [%s].",
        type, definitionKey, versions, scopeCollectionId
      );
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return tenants.stream()
      .map(tenantDto -> new TenantResponseDto(tenantDto.getId(), tenantDto.getName()))
      .collect(Collectors.toList());
  }

  private List<DefinitionResponseDto> getDefinitions(final DefinitionType type, final String collectionId,
                                                              final boolean camundaEventImportedOnly,
                                                              final String userId) {
    if (collectionId != null) {
      return getDefinitionKeysForCollection(type, camundaEventImportedOnly, userId, collectionId);
    } else {
      return getDefinitionKeys(type, camundaEventImportedOnly, userId);
    }
  }

  private List<DefinitionResponseDto> getDefinitionKeys(final DefinitionType type,
                                                        final boolean camundaEventImportedOnly,
                                                        final String userId) {
    if (camundaEventImportedOnly) {
      if (!DefinitionType.PROCESS.equals(type)) {
        throw new BadRequestException(
          "Cannot apply \"camundaEventImportedOnly\" when requesting decision definitions");
      }
      return definitionService.getFullyImportedCamundaEventImportedDefinitions(userId);
    } else {
      return definitionService.getFullyImportedDefinitions(type, userId);
    }
  }

  private List<DefinitionResponseDto> getDefinitionKeysForCollection(final DefinitionType type,
                                                                     final boolean camundaEventImportedOnly,
                                                                     final String userId,
                                                                     final String collectionId) {
    if (camundaEventImportedOnly) {
      throw new BadRequestException("Cannot apply \"camundaEventImportedOnly\" in the context of a collection");
    }
    return collectionScopeService.getCollectionDefinitions(type, userId, collectionId);
  }

  private void addNoStoreCacheHeader(final Response.ResponseBuilder processResponse) {
    processResponse.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
  }

  private void logAndThrowNotFoundException(final DefinitionType type,
                                            final String key,
                                            final String version) {
    final String notFoundErrorMessage = String.format(
      "Could not find xml for [%s] definition with key [%s] and version [%s]. " +
        "It is possible that it hasn't been imported yet.", type, key, version);
    log.error(notFoundErrorMessage);
    throw new NotFoundException(notFoundErrorMessage);
  }

}
