/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizationType;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.service.entities.EntityImportService;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Path("/import")
@Component
public class ImportRestService {

  private final SessionService sessionService;
  private final EntityImportService entityImportService;
  private final AbstractIdentityService identityService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<EntityIdResponseDto> importEntities(@Context final ContainerRequestContext requestContext,
                                                  @QueryParam("collectionId") String collectionId,
                                                  final String exportedDtoJson) {
    final Set<OptimizeEntityExportDto> exportDtos = entityImportService.readExportDtoOrFailIfInvalid(exportedDtoJson);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateUserAuthorization(userId, collectionId);
    return entityImportService.importEntitiesAsUser(userId, collectionId, exportDtos);
  }

  private void validateUserAuthorization(final String userId, final String collectionId) {
    if (collectionId == null && !identityService.getUserAuthorizations(userId).contains(AuthorizationType.ENTITY_EDITOR)) {
      throw new ForbiddenException("User not authorized to create reports outside of a collection");
    }
  }

}
