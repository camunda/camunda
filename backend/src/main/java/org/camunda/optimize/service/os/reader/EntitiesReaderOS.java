/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.service.db.reader.EntitiesReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EntitiesReaderOS implements EntitiesReader {

  @Override
  public List<CollectionEntity> getAllPrivateEntities() {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<CollectionEntity> getAllPrivateEntities(final String userId) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public Map<String, Map<EntityType, Long>> countEntitiesForCollections(final List<? extends BaseCollectionDefinitionDto> collections) {
    //todo will be handled in the OPT-7230
    return new HashMap<>();
  }

  @Override
  public List<CollectionEntity> getAllEntitiesForCollection(final String collectionId) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public Optional<EntityNameResponseDto> getEntityNames(final EntityNameRequestDto requestDto, final String locale) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

}
