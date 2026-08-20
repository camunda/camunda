/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.entity.EntitiesDeleteRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import io.camunda.optimize.rest.mapper.EntityRestMapper;
import io.camunda.optimize.service.entities.EntitiesService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + EntitiesRestService.ENTITIES_PATH)
public class EntitiesRestService {

  public static final String ENTITIES_PATH = "/entities";

  private final EntitiesService entitiesService;
  private final SessionService sessionService;
  private final EntityRestMapper entityRestMapper;

  public EntitiesRestService(
      final EntitiesService entitiesService,
      final SessionService sessionService,
      final EntityRestMapper entityRestMapper) {
    this.entitiesService = entitiesService;
    this.sessionService = sessionService;
    this.entityRestMapper = entityRestMapper;
  }

  @GetMapping
  public List<EntityResponseDto> getEntities(
      @RequestParam(name = "sortBy", required = false) final String sortBy,
      @RequestParam(name = "sortOrder", required = false) final SortOrder sortOrder,
      final HttpServletRequest request) {
    final EntitySorter entitySorter = new EntitySorter(sortBy, sortOrder);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final List<EntityResponseDto> entities = entitiesService.getAllEntities(userId);
    entities.forEach(entityRestMapper::prepareRestResponse);
    return entitySorter.applySort(entities);
  }

  @GetMapping("/names")
  public EntityNameResponseDto getEntityNames(
      final EntityNameRequestDto requestDto, final HttpServletRequest request) {
    return entitiesService.getEntityNames(requestDto, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
  }

  @PostMapping("/delete-conflicts")
  public boolean entitiesHaveDeleteConflicts(
      @Valid @NotNull @RequestBody final EntitiesDeleteRequestDto entities,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return entitiesService.entitiesHaveConflicts(entities, userId);
  }

  @PostMapping("/delete")
  public void bulkDeleteEntities(
      @Valid @NotNull @RequestBody final EntitiesDeleteRequestDto entities,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    entitiesService.bulkDeleteEntities(entities, userId);
  }
}
