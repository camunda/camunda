/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntityUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.List;

@AllArgsConstructor
@Secured
@Path("/collection")
@Component
public class CollectionRestService {
  private final CollectionService collectionService;
  private final SessionService sessionService;

  /**
   * Creates an empty collection.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewCollection(@Context ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return collectionService.createNewCollectionAndReturnId(userId);
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
                                      @NotNull PartialCollectionUpdateDto updatedCollection) {

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.updatePartialCollection(collectionId, userId, updatedCollection);
  }

  @GET
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<CollectionRoleDto> getRoles(@Context ContainerRequestContext requestContext,
                       @PathParam("id") String collectionId){
    return  collectionService.getCollectionDefinition(collectionId).getData().getRoles();
  }

  @POST
  @Path("/{id}/role/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public IdDto addRole(@Context ContainerRequestContext requestContext,
                       @PathParam("id") String collectionId,
                       @NotNull CollectionRoleDto roleDtoRequest) throws OptimizeConflictException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    CollectionRoleDto collectionRoleDto = collectionService.addRoleToCollection(collectionId, roleDtoRequest, userId);
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
    //
    collectionService.updateRoleOfCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  @DELETE
  @Path("/{id}/role/{roleEntryId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void removeRole(@Context ContainerRequestContext requestContext,
                         @PathParam("id") String collectionId,
                         @PathParam("roleEntryId") String roleEntryId) throws OptimizeConflictException {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.removeRoleFromCollection(collectionId, roleEntryId, userId);
  }

  /**
   * Get a list of all available entity collections with their entities being resolved
   * instead of just containing the ids of the report.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ResolvedCollectionDefinitionDto> getAllResolvedCollections(@Context UriInfo uriInfo) {
    return collectionService.getAllResolvedCollections(uriInfo.getQueryParameters());
  }

  /**
   * Retrieve the collection to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public SimpleCollectionDefinitionDto getCollection(@PathParam("id") String collectionId) {
    return collectionService.getCollectionDefinition(collectionId);
  }

  /**
   * Delete the collection to the specified id.
   */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteCollection(@PathParam("id") String collectionId,
                               @QueryParam("force") boolean force) throws OptimizeConflictException {
    collectionService.deleteCollection(collectionId, force);
  }

  /**
   * Retrieve the conflicting items that would occur on performing a delete.
   */
  @GET
  @Path("/{id}/delete-conflicts")
  @Produces(MediaType.APPLICATION_JSON)
  public ConflictResponseDto getDeleteConflicts(@Context ContainerRequestContext requestContext,
                                                @PathParam("id") String collectionId) {
    return collectionService.getDeleteConflictingItems(collectionId);
  }

}

