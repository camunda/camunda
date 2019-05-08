/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntityUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.List;


@Secured
@Path("/collection")
@Component
public class CollectionRestService {

  private final CollectionService collectionService;
  private final SessionService sessionService;

  @Autowired
  public CollectionRestService(final CollectionService collectionService,
                               final SessionService sessionService) {
    this.collectionService = collectionService;
    this.sessionService = sessionService;
  }

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
                                      @NotNull PartialCollectionDefinitionDto updatedCollection) {

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.updatePartialCollection(collectionId, userId, updatedCollection);
  }


  /**
   * Adds entity to collection (if it wasn't already contained before)
   *
   * @param collectionId    the id of the collection
   * @param entityUpdateDto contains the id of the entity to add
   */
  @POST
  @Path("/{id}/entity/")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void addEntity(@Context ContainerRequestContext requestContext,
                        @PathParam("id") String collectionId,
                        @NotNull CollectionEntityUpdateDto entityUpdateDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.addEntityToCollection(collectionId, entityUpdateDto.getEntityId(), userId);
  }

  /**
   * Removes entity from collection.
   *
   * @param collectionId the id of the collection
   * @param entityId     the id of the entity to remove
   */
  @DELETE
  @Path("/{id}/entity/{entityId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void removeEntity(@Context ContainerRequestContext requestContext,
                           @PathParam("id") String collectionId,
                           @PathParam("entityId") String entityId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    collectionService.removeEntityFromCollection(collectionId, entityId, userId);
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
  public SimpleCollectionDefinitionDto getCollections(@PathParam("id") String collectionId) {
    return collectionService.getCollectionDefinition(collectionId);
  }

  /**
   * Delete the collection to the specified id.
   */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteCollection(@PathParam("id") String collectionId) {
    collectionService.deleteCollection(collectionId);
  }


}

