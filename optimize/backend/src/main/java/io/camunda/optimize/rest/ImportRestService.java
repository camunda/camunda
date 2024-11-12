/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.service.entities.EntityImportService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Path("/import")
@Component
public class ImportRestService {

  private final SessionService sessionService;
  private final EntityImportService entityImportService;
  private final AbstractIdentityService identityService;

  public ImportRestService(
      final SessionService sessionService,
      final EntityImportService entityImportService,
      final AbstractIdentityService identityService) {
    this.sessionService = sessionService;
    this.entityImportService = entityImportService;
    this.identityService = identityService;
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<EntityIdResponseDto> importEntities(
      @Context final ContainerRequestContext requestContext,
      @QueryParam("collectionId") final String collectionId,
      final String exportedDtoJson) {
    final Set<OptimizeEntityExportDto> exportDtos =
        entityImportService.readExportDtoOrFailIfInvalid(exportedDtoJson);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateUserAuthorization(collectionId);
    return entityImportService.importEntitiesAsUser(userId, collectionId, exportDtos);
  }

  private void validateUserAuthorization(final String collectionId) {
    if (collectionId == null
        && !identityService.getEnabledAuthorizations().contains(AuthorizationType.ENTITY_EDITOR)) {
      throw new ForbiddenException("User not authorized to create reports outside of a collection");
    }
  }
}
