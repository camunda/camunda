/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.reader.ReportReader.REPORT_DATA_XML_PROPERTY;

import io.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.LocalizationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EntitiesReader {
  String INDEX_FIELD = "_index";
  String AGG_BY_INDEX_COUNT = "byIndexCount";

  String[] ENTITY_LIST_EXCLUDES = {REPORT_DATA_XML_PROPERTY};

  List<CollectionEntity> getAllPrivateEntities();

  List<CollectionEntity> getAllPrivateEntitiesForOwnerId(final String ownerId);

  Map<String, Map<EntityType, Long>> countEntitiesForCollections(
      final List<? extends BaseCollectionDefinitionDto<?>> collections);

  List<CollectionEntity> getAllEntitiesForCollection(final String collectionId);

  Optional<EntityNameResponseDto> getEntityNames(
      final EntityNameRequestDto requestDto, final String locale);

  default String getLocalizedReportName(
      final LocalizationService localizationService,
      final CollectionEntity reportEntity,
      final String locale) {
    if (reportEntity
        instanceof
        final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionRequestDto) {
      if (singleProcessReportDefinitionRequestDto.getData().isInstantPreviewReport()) {
        return localizationService.getLocalizationForInstantPreviewReportCode(
            locale, reportEntity.getName());
      } else if (singleProcessReportDefinitionRequestDto.getData().isManagementReport()) {
        return localizationService.getLocalizationForManagementReportCode(
            locale, reportEntity.getName());
      }
    }
    return reportEntity.getName();
  }
}
