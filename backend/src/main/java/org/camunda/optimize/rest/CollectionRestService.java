package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.ResolvedReportCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.collection.CollectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUser;


@Secured
@Path("/collection")
@Component
public class CollectionRestService {

  private final CollectionService collectionService;

  @Autowired
  public CollectionRestService(final CollectionService collectionService) {
    this.collectionService = collectionService;
  }

  /**
   * Creates an empty collection.
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewCollection(@Context ContainerRequestContext requestContext) {
    String userId = getRequestUser(requestContext);
    return collectionService.createNewCollectionAndReturnId(userId);
  }

  /**
   * Updates the given fields of a collection to the given id.
   *
   * @param collectionId      the id of the collection
   * @param updatedCollection collection that needs to be updated. Only the fields that are defined here are actually
   *                         updated.
   */
  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateCollection(@Context ContainerRequestContext requestContext,
                               @PathParam("id") String collectionId,
                               SimpleCollectionDefinitionDto updatedCollection) {
    updatedCollection.setId(collectionId);
    String userId = getRequestUser(requestContext);
    collectionService.updateCollection(updatedCollection, userId);
  }


  /**
   * Get a list of all available report collections with their entities being resolved
   * instead of just containing the ids of the report.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ResolvedReportCollectionDefinitionDto> getAllResolvedReportCollections() {
    return collectionService.getAllResolvedReportCollections();
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

