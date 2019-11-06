/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.collection.CollectionScopeService;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
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
@Secured
@Path("/collection")
@Component
public class CollectionRestService {
  private final AlertService alertService;
  private final CollectionService collectionService;
  private final CollectionScopeService collectionScopeService;
  private final SessionService sessionService;
  private final ReportService reportService;

  /**
   * Creates a new collection.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewCollection(@Context ContainerRequestContext requestContext,
                                   PartialCollectionDefinitionDto partialCollectionDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.createNewCollectionAndReturnId(
      userId,
      Optional.ofNullable(partialCollectionDefinitionDto)
        .orElse(new PartialCollectionDefinitionDto())
    );
  }

  /**
   * Retrieve the collection to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedResolvedCollectionDefinitionDto getCollection(@Context ContainerRequestContext requestContext,
                                                                 @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.getResolvedCollectionDefinition(userId, collectionId);
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
                                      @NotNull PartialCollectionDefinitionDto updatedCollection) {
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
                               @QueryParam("force") boolean force) throws OptimizeConflictException {
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

  @POST
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public IdDto addScopeEntry(@Context ContainerRequestContext requestContext,
                             @PathParam("id") String collectionId,
                             @NotNull CollectionScopeEntryDto entryDto) throws OptimizeConflictException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final CollectionScopeEntryDto scopeEntryDto = collectionService.addScopeEntryToCollection(
      userId, collectionId, entryDto
    );
    return new IdDto(scopeEntryDto.getId());
  }

  @DELETE
  @Path("/{id}/scope/{scopeEntryId}")
  public void removeScopeEntry(@Context ContainerRequestContext requestContext,
                               @PathParam("id") String collectionId,
                               @PathParam("scopeEntryId") String scopeEntryId) throws OptimizeConflictException,
                                                                                      NotFoundException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    collectionService.removeScopeEntry(userId, collectionId, scopeEntryId);
  }

  @PUT
  @Path("/{id}/scope/{scopeEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateScopeEntry(@Context ContainerRequestContext requestContext,
                               @PathParam("id") String collectionId,
                               @NotNull CollectionScopeEntryUpdateDto entryDto,
                               @PathParam("scopeEntryId") String scopeEntryId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.updateScopeEntry(userId, collectionId, entryDto, scopeEntryId);
  }

  @GET
  @Path("/{id}/scope")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionScopeEntryRestDto> getScopes(@Context ContainerRequestContext requestContext,
                                                     @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionScopeService.getCollectionScope(userId, collectionId);
  }

  @GET
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionRoleDto> getRoles(@Context ContainerRequestContext requestContext,
                                          @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.getSimpleCollectionDefinitionWithRoleMetadata(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getRoles();
  }

  @POST
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public IdDto addRole(@Context ContainerRequestContext requestContext,
                       @PathParam("id") String collectionId,
                       @NotNull CollectionRoleDto roleDtoRequest) throws OptimizeConflictException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    CollectionRoleDto collectionRoleDto = collectionService.addRoleToCollection(userId, collectionId, roleDtoRequest);
    return new IdDto(collectionRoleDto.getId());
  }

  @PUT
  @Path("/{id}/role/{roleEntryId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void updateRole(@Context ContainerRequestContext requestContext,
                         @PathParam("id") String collectionId,
                         @PathParam("roleEntryId") String roleEntryId,
                         @NotNull CollectionRoleUpdateDto roleUpdateDto) throws OptimizeConflictException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    collectionService.updateRoleOfCollection(userId, collectionId, roleEntryId, roleUpdateDto);
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdDto copyCollection(@Context ContainerRequestContext requestContext,
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
                         @PathParam("roleEntryId") String roleEntryId) throws OptimizeConflictException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.removeRoleFromCollection(userId, collectionId, roleEntryId);
  }

  @GET
  @Path("/{id}/alerts/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AlertDefinitionDto> getAlerts(@Context ContainerRequestContext requestContext,
                                            @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return alertService.getStoredAlertsForCollection(userId, collectionId);
  }

  @GET
  @Path("/{id}/reports/")
  @Produces(MediaType.APPLICATION_JSON)
  public List<AuthorizedReportDefinitionDto> getReports(@Context ContainerRequestContext requestContext,
                                                        @PathParam("id") String collectionId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return reportService.findAndFilterReports(userId, collectionId);
  }
}
