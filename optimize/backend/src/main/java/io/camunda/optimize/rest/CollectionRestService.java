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
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
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
      @Context final ContainerRequestContext requestContext,
      final PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @NotNull final PartialCollectionDefinitionRequestDto updatedCollection) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.updatePartialCollection(userId, collectionId, updatedCollection);
  }

  /** Delete the collection to the specified id. */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteCollection(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @QueryParam("force") final boolean force) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.deleteCollection(userId, collectionId, force);
  }

  @PUT
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void addScopeEntries(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @NotNull final List<CollectionScopeEntryDto> scopeUpdates) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.addScopeEntriesToCollection(userId, collectionId, scopeUpdates);
  }

  @DELETE
  @Path("/{id}/scope/{scopeEntryId}")
  public void deleteScopeEntry(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @PathParam("scopeEntryId") final String scopeEntryId,
      @QueryParam("force") final boolean force) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.deleteScopeEntry(userId, collectionId, scopeEntryId, force);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{id}/scope/{scopeEntryId}/delete-conflicts")
  public ConflictResponseDto getScopeDeleteConflicts(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @PathParam("scopeEntryId") final String scopeEntryId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return new ConflictResponseDto(
        collectionScopeService.getAllConflictsOnScopeDeletion(userId, collectionId, scopeEntryId));
  }

  @PUT
  @Path("/{id}/scope/{scopeEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateScopeEntry(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @NotNull final CollectionScopeEntryUpdateDto entryDto,
      @PathParam("scopeEntryId") final String scopeEntryId,
      @QueryParam("force") final boolean force) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.updateScopeEntry(userId, collectionId, entryDto, scopeEntryId, force);
  }

  @GET
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionScopeEntryResponseDto> getScopes(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionScopeService.getCollectionScope(userId, collectionId);
  }

  @GET
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionRoleResponseDto> getRoles(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionRoleService.getAllRolesOfCollectionSorted(userId, collectionId);
  }

  @POST
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void addRoles(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @NotNull final List<CollectionRoleRequestDto> rolesToAdd) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionRoleService.addRolesToCollection(userId, collectionId, rolesToAdd);
  }

  @PUT
  @Path("/{id}/role/{roleEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void updateRole(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @PathParam("roleEntryId") final String roleEntryId,
      @NotNull final CollectionRoleUpdateRequestDto roleUpdateDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
        userId, collectionId, roleEntryId);
    collectionRoleService.updateRoleOfCollection(userId, collectionId, roleEntryId, roleUpdateDto);
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyCollection(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @QueryParam("name") final String newCollectionName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.copyCollection(userId, collectionId, newCollectionName);
  }

  @DELETE
  @Path("/{id}/role/{roleEntryId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void removeRole(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @PathParam("roleEntryId") final String roleEntryId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
        userId, collectionId, roleEntryId);
    collectionRoleService.removeRoleFromCollectionUnlessIsLastManager(
        userId, collectionId, roleEntryId);
  }

  @GET
  @Path("/{id}/alerts/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AlertDefinitionDto> getAlerts(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<AlertDefinitionDto> alerts =
        collectionEntityService.getStoredAlertsForCollection(userId, collectionId);
    alerts.forEach(alertRestMapper::prepareRestResponse);
    return alerts;
  }

  @GET
  @Path("/{id}/reports/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AuthorizedReportDefinitionResponseDto> getReports(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<AuthorizedReportDefinitionResponseDto> reports =
        collectionEntityService.findAndFilterReports(userId, collectionId);
    reports.forEach(
        authorizedReportDefinitionDto ->
            reportRestMapper.prepareLocalizedRestResponse(
                authorizedReportDefinitionDto,
                requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE)));
    return reports;
  }

  @GET
  @Path("/{id}/entities")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntityResponseDto> getEntities(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @BeanParam final EntitySorter entitySorter) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final List<EntityResponseDto> entities =
        collectionEntityService.getAuthorizedCollectionEntities(userId, collectionId);
    entities.forEach(entityRestMapper::prepareRestResponse);
    return entitySorter.applySort(entities);
  }

  @POST
  @Path("/{id}/scope/delete-conflicts")
  @Consumes(MediaType.APPLICATION_JSON)
  public boolean checkCollectionScopeConflicts(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @RequestBody final List<String> collectionScopeIds) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionScopeService.hasConflictsForCollectionScopeDelete(
        userId, collectionId, collectionScopeIds);
  }

  @POST
  @Path("/{id}/roles/delete")
  @Produces(MediaType.APPLICATION_JSON)
  public void bulkRemoveCollectionRoles(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @NotNull @RequestBody final List<String> roleEntryIds) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionRoleService.removeRolesFromCollection(userId, collectionId, roleEntryIds);
  }

  @POST
  @Path("/{id}/scope/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void bulkDeleteCollectionScopes(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String collectionId,
      @NotNull @RequestBody final List<String> collectionScopeIds) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.bulkDeleteCollectionScopes(userId, collectionId, collectionScopeIds);
  }
}
