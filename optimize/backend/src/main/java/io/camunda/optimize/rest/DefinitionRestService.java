/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAllOrLatest;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.dto.optimize.rest.TenantResponseDto;
import io.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsResponseDto;
import io.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.collection.CollectionScopeService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

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
  public List<DefinitionResponseDto> getDefinitions(
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getFullyImportedDefinitions(userId);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}")
  public List<DefinitionOptimizeResponseDto> getDefinitions(
      @Context final ContainerRequestContext requestContext,
      @PathParam("type") final DefinitionType type,
      @QueryParam("includeXml") final boolean includeXml) {
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
        .map(
            definition -> {
              final List<TenantResponseDto> tenantsForDefinitionVersions =
                  getTenantsForDefinitionVersions(
                      definition.getKey(),
                      type,
                      definition.getVersions(),
                      request.getFilterByCollectionScope(),
                      userId);
              return new DefinitionWithTenantsResponseDto(
                  definition.getKey(), definition.getVersions(), tenantsForDefinitionVersions);
            })
        .collect(Collectors.toList());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}")
  public DefinitionResponseDto getDefinition(
      @Context final ContainerRequestContext requestContext,
      @PathParam("type") final DefinitionType type,
      @PathParam("key") final String key) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService
        .getDefinitionWithAvailableTenants(type, key, userId)
        .orElseThrow(
            () -> {
              final String reason =
                  String.format(
                      "Was not able to find definition for type [%s] and key [%s].", type, key);
              log.error(reason);
              return new NotFoundException(reason);
            });
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/{key}/versions")
  public List<DefinitionVersionResponseDto> getDefinitionVersions(
      @Context final ContainerRequestContext requestContext,
      @PathParam("type") final DefinitionType type,
      @PathParam("key") final String key,
      @QueryParam("filterByCollectionScope") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<String> optionalCollectionId = Optional.ofNullable(collectionId);

    final List<DefinitionVersionResponseDto> definitionVersions =
        optionalCollectionId
            .map(
                id ->
                    collectionScopeService.getCollectionDefinitionVersionsByKeyAndType(
                        type, key, userId, id))
            .orElseGet(() -> definitionService.getDefinitionVersions(type, key, userId));

    if (definitionVersions.isEmpty()) {
      final String reason =
          String.format(
              "Was not able to find definition version for type [%s] and key [%s] in scope of collection [%s].",
              type, key, collectionId);
      log.error(reason);
      throw new NotFoundException(reason);
    }

    try {
      definitionVersions.sort(
          Comparator.comparing(
                  (DefinitionVersionResponseDto definitionVersionDto) ->
                      Integer.valueOf(definitionVersionDto.getVersion()))
              .reversed());
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException(
          "Error while parsing versions for sorting definitions: "
              + definitionVersions.stream().map(DefinitionVersionResponseDto::getVersion).toList());
    }
    return definitionVersions;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{type}/keys")
  public List<DefinitionKeyResponseDto> getDefinitionKeys(
      @Context final ContainerRequestContext requestContext,
      @PathParam("type") final DefinitionType type,
      @QueryParam("filterByCollectionScope") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    final List<DefinitionResponseDto> definitions = getDefinitions(type, collectionId, userId);
    return definitions.stream()
        .map(definition -> new DefinitionKeyResponseDto(definition.getKey(), definition.getName()))
        .collect(Collectors.toList());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/_groupByTenant")
  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenant(
      @Context final ContainerRequestContext requestContext) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return definitionService.getDefinitionsGroupedByTenant(userId);
  }

  /**
   * Get the definition xml to a given definition of the specified type with the given key and
   * version. If the version is set to "ALL", the xml of the latest version is returned.
   *
   * @param key The definition key of the desired definition xml.
   * @param version The definition version of the desired definition xml.
   * @param type The type of the definition (process or decision).
   * @return the definition xml requested or json error structure on failure
   */
  @GET
  @Produces(value = {MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @Path("/{type}/xml")
  @CacheRequest
  public Response getDefinitionXml(
      @Context final ContainerRequestContext requestContext,
      @PathParam("type") final DefinitionType type,
      @QueryParam("key") final String key,
      @QueryParam("version") final String version,
      @QueryParam("tenantId") final String tenantId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final Optional<DefinitionOptimizeResponseDto> definitionDto =
        definitionService.getDefinitionWithXml(type, userId, key, version, tenantId);

    if (definitionDto.isEmpty()) {
      logAndThrowNotFoundException(type, key, version);
    }
    final Response.ResponseBuilder responseBuilder = Response.ok().type(MediaType.APPLICATION_XML);
    if (isDefinitionVersionSetToAllOrLatest(version)) {
      addNoStoreCacheHeader(responseBuilder);
    }
    switch (type) {
      case PROCESS:
        final ProcessDefinitionOptimizeDto processDef =
            (ProcessDefinitionOptimizeDto) definitionDto.get();
        responseBuilder.entity(processDef.getBpmn20Xml());
        if (processDef.isEventBased()) {
          addNoStoreCacheHeader(responseBuilder);
        }
        break;
      case DECISION:
        final DecisionDefinitionOptimizeDto decisionDef =
            (DecisionDefinitionOptimizeDto) definitionDto.get();
        responseBuilder.entity(decisionDef.getDmn10Xml());
        break;
      default:
        throw new BadRequestException("Unknown DefinitionType:" + type);
    }

    return responseBuilder.build();
  }

  private List<TenantResponseDto> getTenantsForDefinitionVersions(
      final String definitionKey,
      final DefinitionType type,
      final List<String> versions,
      final String scopeCollectionId,
      final String userId) {
    final List<TenantDto> tenants =
        Optional.ofNullable(scopeCollectionId)
            .map(
                collectionId ->
                    collectionScopeService.getCollectionDefinitionTenantsByKeyAndType(
                        type, definitionKey, userId, versions, collectionId))
            .orElseGet(
                () ->
                    definitionService.getDefinitionTenants(type, definitionKey, userId, versions));

    if (tenants.isEmpty()) {
      final String reason =
          String.format(
              "Was not able to find definition tenants for type [%s], key [%s], versions [%s] in scope of collection [%s].",
              type, definitionKey, versions, scopeCollectionId);
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return tenants.stream()
        .map(tenantDto -> new TenantResponseDto(tenantDto.getId(), tenantDto.getName()))
        .collect(Collectors.toList());
  }

  private List<DefinitionResponseDto> getDefinitions(
      final DefinitionType type, final String collectionId, final String userId) {
    if (collectionId != null) {
      return getDefinitionKeysForCollection(type, userId, collectionId);
    } else {
      return getDefinitionKeys(type, userId);
    }
  }

  private List<DefinitionResponseDto> getDefinitionKeys(
      final DefinitionType type, final String userId) {
    return definitionService.getFullyImportedDefinitions(type, userId);
  }

  private List<DefinitionResponseDto> getDefinitionKeysForCollection(
      final DefinitionType type, final String userId, final String collectionId) {
    return collectionScopeService.getCollectionDefinitions(type, userId, collectionId);
  }

  private void addNoStoreCacheHeader(final Response.ResponseBuilder processResponse) {
    processResponse.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
  }

  private void logAndThrowNotFoundException(
      final DefinitionType type, final String key, final String version) {
    final String notFoundErrorMessage =
        String.format(
            "Could not find xml for [%s] definition with key [%s] and version [%s]. "
                + "It is possible that it hasn't been imported yet.",
            type, key, version);
    log.error(notFoundErrorMessage);
    throw new NotFoundException(notFoundErrorMessage);
  }
}
