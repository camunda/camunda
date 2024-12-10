/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import io.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
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
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

@Path("/collection")
@Component
public class CollectionRestService {

  private final SessionService sessionService;
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
      final SessionService sessionService,
      final CollectionService collectionService,
      final AuthorizedCollectionService authorizedCollectionService,
      final CollectionRoleService collectionRoleService,
      final CollectionScopeService collectionScopeService,
      final CollectionEntityService collectionEntityService,
      final ReportRestMapper reportRestMapper,
      final CollectionRestMapper collectionRestMapper,
      final AlertRestMapper alertRestMapper,
      final EntityRestMapper entityRestMapper) {
    this.sessionService = sessionService;
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
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewCollection(
      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return collectionService.createNewCollectionAndReturnId(
        userId,
        Optional.ofNullable(partialCollectionDefinitionDto)
            .orElse(new PartialCollectionDefinitionRequestDto()));
  }

  /** Retrieve the collection to the specified id. */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedCollectionDefinitionRestDto getCollection(
      @PathParam("id") final String collectionId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
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
  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateCollectionPartial(
      @PathParam("id") final String collectionId,
      @NotNull final PartialCollectionDefinitionRequestDto updatedCollection,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionService.updatePartialCollection(userId, collectionId, updatedCollection);
  }

  /** Delete the collection to the specified id. */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteCollection(
      @PathParam("id") final String collectionId,
      @QueryParam("force") final boolean force,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionService.deleteCollection(userId, collectionId, force);
  }

  @PUT
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void addScopeEntries(
      @PathParam("id") final String collectionId,
      @NotNull final List<CollectionScopeEntryDto> scopeUpdates,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionScopeService.addScopeEntriesToCollection(userId, collectionId, scopeUpdates);
  }

  @DELETE
  @Path("/{id}/scope/{scopeEntryId}")
  public void deleteScopeEntry(
      @PathParam("id") final String collectionId,
      @PathParam("scopeEntryId") final String scopeEntryId,
      @QueryParam("force") final boolean force,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionScopeService.deleteScopeEntry(userId, collectionId, scopeEntryId, force);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{id}/scope/{scopeEntryId}/delete-conflicts")
  public ConflictResponseDto getScopeDeleteConflicts(
      @PathParam("id") final String collectionId,
      @PathParam("scopeEntryId") final String scopeEntryId,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return new ConflictResponseDto(
        collectionScopeService.getAllConflictsOnScopeDeletion(userId, collectionId, scopeEntryId));
  }

  @PUT
  @Path("/{id}/scope/{scopeEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateScopeEntry(
      @PathParam("id") final String collectionId,
      @NotNull final CollectionScopeEntryUpdateDto entryDto,
      @PathParam("scopeEntryId") final String scopeEntryId,
      @QueryParam("force") final boolean force,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionScopeService.updateScopeEntry(userId, collectionId, entryDto, scopeEntryId, force);
  }

  @GET
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionScopeEntryResponseDto> getScopes(
      @PathParam("id") final String collectionId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return collectionScopeService.getCollectionScope(userId, collectionId);
  }

  @GET
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionRoleResponseDto> getRoles(
      @PathParam("id") final String collectionId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return collectionRoleService.getAllRolesOfCollectionSorted(userId, collectionId);
  }

  @POST
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void addRoles(
      @PathParam("id") final String collectionId,
      @NotNull final List<CollectionRoleRequestDto> rolesToAdd,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionRoleService.addRolesToCollection(userId, collectionId, rolesToAdd);
  }

  @PUT
  @Path("/{id}/role/{roleEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void updateRole(
      @PathParam("id") final String collectionId,
      @PathParam("roleEntryId") final String roleEntryId,
      @NotNull final CollectionRoleUpdateRequestDto roleUpdateDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
        userId, collectionId, roleEntryId);
    collectionRoleService.updateRoleOfCollection(userId, collectionId, roleEntryId, roleUpdateDto);
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyCollection(
      @PathParam("id") final String collectionId,
      @QueryParam("name") final String newCollectionName,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return collectionService.copyCollection(userId, collectionId, newCollectionName);
  }

  @DELETE
  @Path("/{id}/role/{roleEntryId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void removeRole(
      @PathParam("id") final String collectionId,
      @PathParam("roleEntryId") final String roleEntryId,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
        userId, collectionId, roleEntryId);
    collectionRoleService.removeRoleFromCollectionUnlessIsLastManager(
        userId, collectionId, roleEntryId);
  }

  @GET
  @Path("/{id}/alerts/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AlertDefinitionDto> getAlerts(
      @PathParam("id") final String collectionId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<AlertDefinitionDto> alerts =
        collectionEntityService.getStoredAlertsForCollection(userId, collectionId);
    alerts.forEach(alertRestMapper::prepareRestResponse);
    return alerts;
  }

  @GET
  @Path("/{id}/reports/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AuthorizedReportDefinitionResponseDto> getReports(
      @PathParam("id") final String collectionId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<AuthorizedReportDefinitionResponseDto> reports =
        collectionEntityService.findAndFilterReports(userId, collectionId);
    reports.forEach(
        authorizedReportDefinitionDto ->
            reportRestMapper.prepareLocalizedRestResponse(
                authorizedReportDefinitionDto, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE)));
    return reports;
  }

  @GET
  @Path("/{id}/entities")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntityResponseDto> getEntities(
      @PathParam("id") final String collectionId,
      @BeanParam final EntitySorter entitySorter,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<EntityResponseDto> entities =
        collectionEntityService.getAuthorizedCollectionEntities(userId, collectionId);
    entities.forEach(entityRestMapper::prepareRestResponse);
    return entitySorter.applySort(entities);
  }

  @POST
  @Path("/{id}/scope/delete-conflicts")
  @Consumes(MediaType.APPLICATION_JSON)
  public boolean checkCollectionScopeConflicts(
      @PathParam("id") final String collectionId,
      @RequestBody final List<String> collectionScopeIds,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return collectionScopeService.hasConflictsForCollectionScopeDelete(
        userId, collectionId, collectionScopeIds);
  }

  @POST
  @Path("/{id}/roles/delete")
  @Produces(MediaType.APPLICATION_JSON)
  public void bulkRemoveCollectionRoles(
      @PathParam("id") final String collectionId,
      @NotNull @RequestBody final List<String> roleEntryIds,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionRoleService.removeRolesFromCollection(userId, collectionId, roleEntryIds);
  }

  @POST
  @Path("/{id}/scope/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void bulkDeleteCollectionScopes(
      @PathParam("id") final String collectionId,
      @NotNull @RequestBody final List<String> collectionScopeIds,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    collectionScopeService.bulkDeleteCollectionScopes(userId, collectionId, collectionScopeIds);
  }
}
