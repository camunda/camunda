/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.entities.EntityImportService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + ImportRestService.IMPORT_PATH)
public class ImportRestService {

  public static final String IMPORT_PATH = "/import";

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

  @PostMapping
  public List<EntityIdResponseDto> importEntities(
      @RequestParam(name = "collectionId", required = false) final String collectionId,
      @RequestBody final String exportedDtoJson,
      final HttpServletRequest request) {
    final Set<OptimizeEntityExportDto> exportDtos =
        entityImportService.readExportDtoOrFailIfInvalid(exportedDtoJson);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
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
