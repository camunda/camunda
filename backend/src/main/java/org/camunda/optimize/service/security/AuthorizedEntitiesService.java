/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.reader.EntitiesReader;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class AuthorizedEntitiesService {

  private final EntitiesReader entitiesReader;
  private final AbstractIdentityService identityService;
  private final ReportAuthorizationService reportAuthorizationService;

  public List<EntityResponseDto> getAuthorizedPrivateEntities(final String userId) {

    final List<CollectionEntity> collectionEntities;
    if (identityService.isSuperUserIdentity(userId)) {
      collectionEntities = entitiesReader.getAllPrivateEntities();
    } else {
      collectionEntities = entitiesReader.getAllPrivateEntities(userId);
    }

    return collectionEntities.stream()
      .map(collectionEntity -> Pair.of(collectionEntity, collectionEntity.toEntityDto()))
      .filter(collectionEntityAndEntityDto -> {
        final EntityResponseDto entityDto = collectionEntityAndEntityDto.getValue();
        if (entityDto.getEntityType().equals(EntityType.REPORT)) {
          return reportAuthorizationService.isAuthorizedToAccessReportDefinition(
            userId, (ReportDefinitionDto) collectionEntityAndEntityDto.getKey()
          );
        } else {
          return true;
        }
      })
      .map(Pair::getValue)
      .collect(Collectors.toList());
  }

  public List<EntityResponseDto> getAuthorizedCollectionEntities(final String userId, final String collectionId) {
    return entitiesReader
      .getAllEntitiesForCollection(collectionId)
      .stream()
      .map(collectionEntity -> Pair.of(collectionEntity, collectionEntity.toEntityDto()))
      .filter(collectionEntityAndEntityDto -> {
        final EntityResponseDto entityDto = collectionEntityAndEntityDto.getValue();
        if (entityDto.getEntityType().equals(EntityType.REPORT)) {
          return reportAuthorizationService.isAuthorizedToAccessReportDefinition(
            userId, (ReportDefinitionDto) collectionEntityAndEntityDto.getKey()
          );
        } else {
          return true;
        }
      })
      .map(Pair::getValue)
      .collect(Collectors.toList());
  }

}
