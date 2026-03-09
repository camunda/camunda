/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import io.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryResponseDto;
import io.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import io.camunda.optimize.rest.mapper.AlertRestMapper;
import io.camunda.optimize.rest.mapper.CollectionRestMapper;
import io.camunda.optimize.rest.mapper.EntityRestMapper;
import io.camunda.optimize.rest.mapper.ReportRestMapper;
import io.camunda.optimize.service.collection.CollectionEntityService;
import io.camunda.optimize.service.collection.CollectionRoleService;
import io.camunda.optimize.service.collection.CollectionScopeService;
import io.camunda.optimize.service.collection.CollectionService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import io.camunda.optimize.service.security.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + CollectionRestService.COLLECTION_PATH)
public class CollectionRestService {

  public static final String COLLECTION_PATH = "/collection";

  private final CollectionService collectionService;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionRoleService collectionRoleService;
  private final CollectionScopeService collectionScopeService;
  private final CollectionEntityService collectionEntityService;
  private final ReportRestMapper reportRestMapper;
  private final CollectionRestMapper collectionRestMapper;
  private final AlertRestMapper alertRestMapper;
  private final EntityRestMapper entityRestMapper;

  public CollectionRestService(
      final CollectionService collectionService,
      final AuthorizedCollectionService authorizedCollectionService,
      final CollectionRoleService collectionRoleService,
      final CollectionScopeService collectionScopeService,
      final CollectionEntityService collectionEntityService,
      final ReportRestMapper reportRestMapper,
      final CollectionRestMapper collectionRestMapper,
      final AlertRestMapper alertRestMapper,
      final EntityRestMapper entityRestMapper) {
    this.collectionService = collectionService;
    this.authorizedCollectionService = authorizedCollectionService;
    this.collectionRoleService = collectionRoleService;
    this.collectionScopeService = collectionScopeService;
    this.collectionEntityService = collectionEntityService;
    this.reportRestMapper = reportRestMapper;
    this.collectionRestMapper = collectionRestMapper;
    this.alertRestMapper = alertRestMapper;
    this.entityRestMapper = entityRestMapper;
  }

  /** Creates a new collection. */
  @PostMapping
  public IdResponseDto createNewCollection(
      @RequestBody final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    return collectionService.createNewCollectionAndReturnId(
        userId,
        Optional.ofNullable(partialCollectionDefinitionDto)
            .orElse(new PartialCollectionDefinitionRequestDto()));
  }

  /** Retrieve the collection to the specified id. */
  @GetMapping("/{id}")
  public AuthorizedCollectionDefinitionRestDto getCollection(
      @PathVariable("id") final String collectionId) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    final AuthorizedCollectionDefinitionRestDto authorizedCollectionDefinitionRestDto =
        collectionService.getCollectionDefinitionRestDto(userId, collectionId);
    collectionRestMapper.prepareRestResponse(authorizedCollectionDefinitionRestDto);
    return authorizedCollectionDefinitionRestDto;
  }

  /**
   * Updates the name and/or configuration of a collection
   *
   * @param collectionId the id of the collection
   * @param updatedCollection collection that needs to be updated. Only the fields that are defined
   *     here are actually updated.
   */
  @PutMapping("/{id}")
  public void updateCollectionPartial(
      @PathVariable("id") final String collectionId,
      @NotNull @RequestBody final PartialCollectionDefinitionRequestDto updatedCollection) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionService.updatePartialCollection(userId, collectionId, updatedCollection);
  }

  /** Delete the collection to the specified id. */
  @DeleteMapping("/{id}")
  public void deleteCollection(
      @PathVariable("id") final String collectionId,
      @RequestParam(name = "force", required = false) final boolean force) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionService.deleteCollection(userId, collectionId, force);
  }

  @PutMapping("/{id}/scope")
  public void addScopeEntries(
      @PathVariable("id") final String collectionId,
      @NotNull @RequestBody final List<CollectionScopeEntryDto> scopeUpdates) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionScopeService.addScopeEntriesToCollection(userId, collectionId, scopeUpdates);
  }

  @DeleteMapping("/{id}/scope/{scopeEntryId}")
  public void deleteScopeEntry(
      @PathVariable("id") final String collectionId,
      @PathVariable("scopeEntryId") final String scopeEntryId,
      @RequestParam(name = "force", required = false) final boolean force) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionScopeService.deleteScopeEntry(userId, collectionId, scopeEntryId, force);
  }

  @GetMapping("/{id}/scope/{scopeEntryId}/delete-conflicts")
  public ConflictResponseDto getScopeDeleteConflicts(
      @PathVariable("id") final String collectionId,
      @PathVariable("scopeEntryId") final String scopeEntryId) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    return new ConflictResponseDto(
        collectionScopeService.getAllConflictsOnScopeDeletion(userId, collectionId, scopeEntryId));
  }

  @PutMapping("/{id}/scope/{scopeEntryId}")
  public void updateScopeEntry(
      @PathVariable("id") final String collectionId,
      @NotNull final CollectionScopeEntryUpdateDto entryDto,
      @PathVariable("scopeEntryId") final String scopeEntryId,
      @RequestParam(name = "force", required = false) final boolean force) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionScopeService.updateScopeEntry(userId, collectionId, entryDto, scopeEntryId, force);
  }

  @GetMapping("/{id}/scope")
  public List<CollectionScopeEntryResponseDto> getScopes(
      @PathVariable("id") final String collectionId) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    return collectionScopeService.getCollectionScope(userId, collectionId);
  }

  @GetMapping("/{id}/role")
  public List<CollectionRoleResponseDto> getRoles(@PathVariable("id") final String collectionId) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    return collectionRoleService.getAllRolesOfCollectionSorted(userId, collectionId);
  }

  @PostMapping("/{id}/role")
  public void addRoles(
      @PathVariable("id") final String collectionId,
      @NotNull @RequestBody final List<CollectionRoleRequestDto> rolesToAdd) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionRoleService.addRolesToCollection(userId, collectionId, rolesToAdd);
  }

  @PutMapping("/{id}/role/{roleEntryId}")
  public void updateRole(
      @PathVariable("id") final String collectionId,
      @PathVariable("roleEntryId") final String roleEntryId,
      @NotNull @RequestBody final CollectionRoleUpdateRequestDto roleUpdateDto) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
        userId, collectionId, roleEntryId);
    collectionRoleService.updateRoleOfCollection(userId, collectionId, roleEntryId, roleUpdateDto);
  }

  @PostMapping("/{id}/copy")
  public IdResponseDto copyCollection(
      @PathVariable("id") final String collectionId,
      @RequestParam(name = "name", required = false) final String newCollectionName) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    return collectionService.copyCollection(userId, collectionId, newCollectionName);
  }

  @DeleteMapping("/{id}/role/{roleEntryId}")
  public void removeRole(
      @PathVariable("id") final String collectionId,
      @PathVariable("roleEntryId") final String roleEntryId) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
        userId, collectionId, roleEntryId);
    collectionRoleService.removeRoleFromCollectionUnlessIsLastManager(
        userId, collectionId, roleEntryId);
  }

  @GetMapping("/{id}/alerts")
  public List<AlertDefinitionDto> getAlerts(@PathVariable("id") final String collectionId) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    final List<AlertDefinitionDto> alerts =
        collectionEntityService.getStoredAlertsForCollection(userId, collectionId);
    alerts.forEach(alertRestMapper::prepareRestResponse);
    return alerts;
  }

  @GetMapping("/{id}/reports")
  public List<AuthorizedReportDefinitionResponseDto> getReports(
      @PathVariable("id") final String collectionId, final HttpServletRequest request) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    final List<AuthorizedReportDefinitionResponseDto> reports =
        collectionEntityService.findAndFilterReports(userId, collectionId);
    reports.forEach(
        authorizedReportDefinitionDto ->
            reportRestMapper.prepareLocalizedRestResponse(
                authorizedReportDefinitionDto, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE)));
    return reports;
  }

  @GetMapping("/{id}/entities")
  public List<EntityResponseDto> getEntities(
      @PathVariable("id") final String collectionId,
      @RequestParam(name = "sortBy", required = false) final String sortBy,
      @RequestParam(name = "sortOrder", required = false) final SortOrder sortOrder) {
    final EntitySorter entitySorter = new EntitySorter(sortBy, sortOrder);
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    final List<EntityResponseDto> entities =
        collectionEntityService.getAuthorizedCollectionEntities(userId, collectionId);
    entities.forEach(entityRestMapper::prepareRestResponse);
    return entitySorter.applySort(entities);
  }

  @PostMapping("/{id}/scope/delete-conflicts")
  public boolean checkCollectionScopeConflicts(
      @PathVariable("id") final String collectionId,
      @RequestBody final List<String> collectionScopeIds) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    return collectionScopeService.hasConflictsForCollectionScopeDelete(
        userId, collectionId, collectionScopeIds);
  }

  @PostMapping("/{id}/roles/delete")
  public void bulkRemoveCollectionRoles(
      @PathVariable("id") final String collectionId,
      @NotNull @RequestBody final List<String> roleEntryIds) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionRoleService.removeRolesFromCollection(userId, collectionId, roleEntryIds);
  }

  @PostMapping("/{id}/scope/delete")
  public void bulkDeleteCollectionScopes(
      @PathVariable("id") final String collectionId,
      @NotNull @RequestBody final List<String> collectionScopeIds) {
    final String userId = SecurityContextUtils.getAuthenticatedUser();
    collectionScopeService.bulkDeleteCollectionScopes(userId, collectionId, collectionScopeIds);
  }
}
