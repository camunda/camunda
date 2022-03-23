/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class CollectionRestMapper {

  private final AbstractIdentityService identityService;

  public void prepareRestResponse(final AuthorizedCollectionDefinitionRestDto collectionDefinitionRestDto) {
    resolveOwnerAndModifierNames(collectionDefinitionRestDto.getDefinitionDto());
  }

  private void resolveOwnerAndModifierNames(BaseCollectionDefinitionDto collectionDefinitionDto) {
    Optional.ofNullable(collectionDefinitionDto.getOwner())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(collectionDefinitionDto::setOwner);
    Optional.ofNullable(collectionDefinitionDto.getLastModifier())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(collectionDefinitionDto::setLastModifier);
  }

}
