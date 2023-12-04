/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

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
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryResponseDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import org.camunda.optimize.rest.mapper.AlertRestMapper;
import org.camunda.optimize.rest.mapper.CollectionRestMapper;
import org.camunda.optimize.rest.mapper.EntityRestMapper;
import org.camunda.optimize.rest.mapper.ReportRestMapper;
import org.camunda.optimize.service.collection.CollectionEntityService;
import org.camunda.optimize.service.collection.CollectionRoleService;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;

@AllArgsConstructor
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

  /**
   * Creates a new collection.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewCollection(@Context ContainerRequestContext requestContext,
                                           PartialCollectionDefinitionRequestDto partialCollectionDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.createNewCollectionAndReturnId(
      userId,
      Optional.ofNullable(partialCollectionDefinitionDto)
        .orElse(new PartialCollectionDefinitionRequestDto())
    );
  }

  /**
   * Retrieve the collection to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedCollectionDefinitionRestDto getCollection(@Context ContainerRequestContext requestContext,
                                                             @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    AuthorizedCollectionDefinitionRestDto authorizedCollectionDefinitionRestDto =
      collectionService.getCollectionDefinitionRestDto(userId, collectionId);
    collectionRestMapper.prepareRestResponse(authorizedCollectionDefinitionRestDto);
    return authorizedCollectionDefinitionRestDto;
  }

  /**
   * Updates the name and/or configuration of a collection
   *
   * @param collectionId      the id of the collection
   * @param updatedCollection collection that needs to be updated. Only the fields that are defined here are actually
   *                          updated.
   */
  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateCollectionPartial(@Context ContainerRequestContext requestContext,
                                      @PathParam("id") String collectionId,
                                      @NotNull PartialCollectionDefinitionRequestDto updatedCollection) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.updatePartialCollection(userId, collectionId, updatedCollection);
  }

  /**
   * Delete the collection to the specified id.
   */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteCollection(@Context ContainerRequestContext requestContext,
                               @PathParam("id") String collectionId,
                               @QueryParam("force") boolean force) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.deleteCollection(userId, collectionId, force);
  }

  /**
   * Retrieve the conflicting items that would occur on performing a delete.
   */
  @GET
  @Path("/{id}/delete-conflicts")
  @Produces(MediaType.APPLICATION_JSON)
  public ConflictResponseDto getDeleteConflicts(@Context ContainerRequestContext requestContext,
                                                @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.getDeleteConflictingItems(userId, collectionId);
  }

  @PUT
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void addScopeEntries(@Context ContainerRequestContext requestContext,
                              @PathParam("id") String collectionId,
                              @NotNull List<CollectionScopeEntryDto> scopeUpdates) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.addScopeEntriesToCollection(userId, collectionId, scopeUpdates);
  }

  @DELETE
  @Path("/{id}/scope/{scopeEntryId}")
  public void deleteScopeEntry(@Context ContainerRequestContext requestContext,
                               @PathParam("id") String collectionId,
                               @PathParam("scopeEntryId") String scopeEntryId,
                               @QueryParam("force") boolean force) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.deleteScopeEntry(userId, collectionId, scopeEntryId, force);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{id}/scope/{scopeEntryId}/delete-conflicts")
  public ConflictResponseDto getScopeDeleteConflicts(@Context ContainerRequestContext requestContext,
                                                     @PathParam("id") String collectionId,
                                                     @PathParam("scopeEntryId") String scopeEntryId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return new ConflictResponseDto(
      collectionScopeService.getAllConflictsOnScopeDeletion(userId, collectionId, scopeEntryId)
    );
  }

  @PUT
  @Path("/{id}/scope/{scopeEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateScopeEntry(@Context ContainerRequestContext requestContext,
                               @PathParam("id") String collectionId,
                               @NotNull CollectionScopeEntryUpdateDto entryDto,
                               @PathParam("scopeEntryId") String scopeEntryId,
                               @QueryParam("force") boolean force) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.updateScopeEntry(userId, collectionId, entryDto, scopeEntryId, force);
  }

  @GET
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionScopeEntryResponseDto> getScopes(@Context ContainerRequestContext requestContext,
                                                         @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionScopeService.getCollectionScope(userId, collectionId);
  }

  @GET
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionRoleResponseDto> getRoles(@Context ContainerRequestContext requestContext,
                                                  @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionRoleService.getAllRolesOfCollectionSorted(userId, collectionId);
  }

  @POST
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void addRoles(@Context ContainerRequestContext requestContext,
                       @PathParam("id") String collectionId,
                       @NotNull List<CollectionRoleRequestDto> rolesToAdd) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionRoleService.addRolesToCollection(userId, collectionId, rolesToAdd);
  }

  @PUT
  @Path("/{id}/role/{roleEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void updateRole(@Context ContainerRequestContext requestContext,
                         @PathParam("id") String collectionId,
                         @PathParam("roleEntryId") String roleEntryId,
                         @NotNull CollectionRoleUpdateRequestDto roleUpdateDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(userId, collectionId, roleEntryId);
    collectionRoleService.updateRoleOfCollection(userId, collectionId, roleEntryId, roleUpdateDto);
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyCollection(@Context ContainerRequestContext requestContext,
                                      @PathParam("id") String collectionId,
                                      @QueryParam("name") String newCollectionName) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.copyCollection(userId, collectionId, newCollectionName);
  }

  @DELETE
  @Path("/{id}/role/{roleEntryId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void removeRole(@Context ContainerRequestContext requestContext,
                         @PathParam("id") String collectionId,
                         @PathParam("roleEntryId") String roleEntryId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(userId, collectionId, roleEntryId);
    collectionRoleService.removeRoleFromCollectionUnlessIsLastManager(userId, collectionId, roleEntryId);
  }

  @GET
  @Path("/{id}/alerts/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AlertDefinitionDto> getAlerts(@Context ContainerRequestContext requestContext,
                                            @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<AlertDefinitionDto> alerts = collectionEntityService.getStoredAlertsForCollection(userId, collectionId);
    alerts.forEach(alertRestMapper::prepareRestResponse);
    return alerts;
  }

  @GET
  @Path("/{id}/reports/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AuthorizedReportDefinitionResponseDto> getReports(@Context ContainerRequestContext requestContext,
                                                                @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<AuthorizedReportDefinitionResponseDto> reports = collectionEntityService.findAndFilterReports(
      userId,
      collectionId
    );
    reports.forEach(
      authorizedReportDefinitionDto ->
        reportRestMapper.prepareLocalizedRestResponse(
          authorizedReportDefinitionDto,
          requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE)
        )
    );
    return reports;
  }

  @GET
  @Path("/{id}/entities")
  @Produces(MediaType.APPLICATION_JSON)
  public List<EntityResponseDto> getEntities(@Context ContainerRequestContext requestContext,
                                             @PathParam("id") String collectionId,
                                             @BeanParam final EntitySorter entitySorter) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<EntityResponseDto> entities = collectionEntityService.getAuthorizedCollectionEntities(userId, collectionId);
    entities.forEach(entityRestMapper::prepareRestResponse);
    return entitySorter.applySort(entities);
  }

  @POST
  @Path("/{id}/scope/delete-conflicts")
  @Consumes(MediaType.APPLICATION_JSON)
  public boolean checkCollectionScopeConflicts(@Context ContainerRequestContext requestContext,
                                               @PathParam("id") String collectionId,
                                               @RequestBody List<String> collectionScopeIds) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionScopeService.hasConflictsForCollectionScopeDelete(userId, collectionId, collectionScopeIds);
  }

  @POST
  @Path("/{id}/roles/delete")
  @Produces(MediaType.APPLICATION_JSON)
  public void bulkRemoveCollectionRoles(@Context ContainerRequestContext requestContext,
                                        @PathParam("id") String collectionId,
                                        @NotNull @RequestBody List<String> roleEntryIds) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionRoleService.removeRolesFromCollection(userId, collectionId, roleEntryIds);
  }

  @POST
  @Path("/{id}/scope/delete")
  @Consumes(MediaType.APPLICATION_JSON)
  public void bulkDeleteCollectionScopes(@Context ContainerRequestContext requestContext,
                                         @PathParam("id") String collectionId,
                                         @NotNull @RequestBody List<String> collectionScopeIds) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionScopeService.bulkDeleteCollectionScopes(userId, collectionId, collectionScopeIds);
  }
}
