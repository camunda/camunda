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
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

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
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.rest.providers.CacheRequest;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.collection.CollectionScopeService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + DefinitionRestService.DEFINITION_PATH)
public class DefinitionRestService {

  public static final String DEFINITION_PATH = "/definition";

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(DefinitionRestService.class);
  private final DefinitionService definitionService;
  private final CollectionScopeService collectionScopeService;
  private final SessionService sessionService;

  public DefinitionRestService(
      final DefinitionService definitionService,
      final CollectionScopeService collectionScopeService,
      final SessionService sessionService) {
    this.definitionService = definitionService;
    this.collectionScopeService = collectionScopeService;
    this.sessionService = sessionService;
  }

  @GetMapping()
  public List<DefinitionResponseDto> getDefinitions(final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return definitionService.getFullyImportedDefinitions(userId);
  }

  @GetMapping("/{type}")
  public List<DefinitionOptimizeResponseDto> getDefinitions(
      @PathVariable("type") final DefinitionType type,
      @RequestParam(name = "includeXml", required = false) final boolean includeXml,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return definitionService.getFullyImportedDefinitions(type, userId, includeXml);
  }

  @PostMapping("/{type}/_resolveTenantsForVersions")
  public List<DefinitionWithTenantsResponseDto> getDefinitionTenantsForMultipleKeys(
      @PathVariable("type") final DefinitionType type,
      @Valid @RequestBody final MultiDefinitionTenantsRequestDto request,
      final HttpServletRequest servletRequest) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(servletRequest);

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

  @GetMapping("/{type}/{key}")
  public DefinitionResponseDto getDefinition(
      @PathVariable("type") final DefinitionType type,
      @PathVariable("key") final String key,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return definitionService
        .getDefinitionWithAvailableTenants(type, key, userId)
        .orElseThrow(
            () -> {
              final String reason =
                  String.format(
                      "Was not able to find definition for type [%s] and key [%s].", type, key);
              LOG.error(reason);
              return new NotFoundException(reason);
            });
  }

  @GetMapping("/{type}/{key}/versions")
  public List<DefinitionVersionResponseDto> getDefinitionVersions(
      @PathVariable("type") final DefinitionType type,
      @PathVariable("key") final String key,
      @RequestParam(value = "filterByCollectionScope", required = false) final String collectionId,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
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
      LOG.error(reason);
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

  @GetMapping("/{type}/keys")
  public List<DefinitionKeyResponseDto> getDefinitionKeys(
      @PathVariable(name = "type") final DefinitionType type,
      @RequestParam(name = "filterByCollectionScope", required = false) final String collectionId,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);

    final List<DefinitionResponseDto> definitions = getDefinitions(type, collectionId, userId);
    return definitions.stream()
        .map(definition -> new DefinitionKeyResponseDto(definition.getKey(), definition.getName()))
        .collect(Collectors.toList());
  }

  @GetMapping("/_groupByTenant")
  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenant(
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
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
  @GetMapping(path = "/{type}/xml", produces = MediaType.APPLICATION_XML_VALUE)
  @CacheRequest
  public ResponseEntity<String> getDefinitionXml(
      @PathVariable("type") final DefinitionType type,
      @RequestParam(name = "key", required = false) final String key,
      @RequestParam(name = "version", required = false) final String version,
      @RequestParam(name = "tenantId", required = false) final String tenantId,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final Optional<DefinitionOptimizeResponseDto> definitionDto =
        definitionService.getDefinitionWithXml(type, userId, key, version, tenantId);

    if (definitionDto.isEmpty()) {
      logAndThrowNotFoundException(type, key, version);
    }

    final BodyBuilder bodyBuilder = ResponseEntity.ok().contentType(MediaType.APPLICATION_XML);
    if (isDefinitionVersionSetToAllOrLatest(version)) {
      addNoStoreCacheHeader(bodyBuilder);
    }

    final String xml =
        switch (type) {
          case PROCESS -> ((ProcessDefinitionOptimizeDto) definitionDto.get()).getBpmn20Xml();
          case DECISION -> ((DecisionDefinitionOptimizeDto) definitionDto.get()).getDmn10Xml();
        };

    return bodyBuilder.body(xml);
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
      LOG.error(reason);
      throw new NotFoundException(reason);
    }
    return tenants.stream()
        .map(tenantDto -> new TenantResponseDto(tenantDto.getId(), tenantDto.getName()))
        .collect(Collectors.toList());
  }

  private List<DefinitionResponseDto> getDefinitions(
      final DefinitionType type, final String collectionId, final String userId) {
    if (collectionId != null) {
      return collectionScopeService.getCollectionDefinitions(type, userId, collectionId);
    } else {
      return definitionService.getFullyImportedDefinitions(type, userId);
    }
  }

  private void addNoStoreCacheHeader(final BodyBuilder bodyBuilder) {
    bodyBuilder.header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
  }

  private void logAndThrowNotFoundException(
      final DefinitionType type, final String key, final String version) {
    final String notFoundErrorMessage =
        String.format(
            "Could not find xml for [%s] definition with key [%s] and version [%s]. "
                + "It is possible that it hasn't been imported yet.",
            type, key, version);
    LOG.error(notFoundErrorMessage);
    throw new NotFoundException(notFoundErrorMessage);
  }
}
