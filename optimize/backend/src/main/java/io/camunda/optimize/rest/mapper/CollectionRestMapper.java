/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.mapper;

import io.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CollectionRestMapper {

  private final AbstractIdentityService identityService;

  public CollectionRestMapper(final AbstractIdentityService identityService) {
    this.identityService = identityService;
  }

  public void prepareRestResponse(
      final AuthorizedCollectionDefinitionRestDto collectionDefinitionRestDto) {
    resolveOwnerAndModifierNames(collectionDefinitionRestDto.getDefinitionDto());
  }

  private void resolveOwnerAndModifierNames(
      final BaseCollectionDefinitionDto collectionDefinitionDto) {
    Optional.ofNullable(collectionDefinitionDto.getOwner())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(collectionDefinitionDto::setOwner);
    Optional.ofNullable(collectionDefinitionDto.getLastModifier())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(collectionDefinitionDto::setLastModifier);
  }
}
