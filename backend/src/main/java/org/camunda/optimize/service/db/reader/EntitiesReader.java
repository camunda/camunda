/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.db.reader.ReportReader.REPORT_DATA_XML_PROPERTY;

public interface EntitiesReader {

   String AGG_BY_INDEX_COUNT = "byIndexCount";

   String[] ENTITY_LIST_EXCLUDES = {REPORT_DATA_XML_PROPERTY};

   List<CollectionEntity> getAllPrivateEntities();

   List<CollectionEntity> getAllPrivateEntities(final String userId);

   Map<String, Map<EntityType, Long>> countEntitiesForCollections(final List<? extends BaseCollectionDefinitionDto> collections);

   List<CollectionEntity> getAllEntitiesForCollection(final String collectionId);

   Optional<EntityNameResponseDto> getEntityNames(final EntityNameRequestDto requestDto, final String locale);
}
