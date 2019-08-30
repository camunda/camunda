/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.service.collection.CollectionService;
import org.camunda.optimize.service.es.reader.EntitiesReader;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
public class EntitiesService {

  private CollectionService collectionService;
  private EntitiesReader entitiesReader;

  public List<EntityDto> getAllEntities(String userId) {
    final List<SimpleCollectionDefinitionDto> collectionDefinitions = collectionService.getAllCollectionDefinitions();
    final List<EntityDto> privateEntities = entitiesReader.getAllPrivateEntities(userId);

    return Stream.concat(
      collectionDefinitions.stream().map(BaseCollectionDefinitionDto::toEntityDto),
      privateEntities.stream()
    ).sorted(
      Comparator.comparing(EntityDto::getEntityType)
        .thenComparing(EntityDto::getLastModified, Comparator.reverseOrder())
    ).collect(Collectors.toList());
  }

}
