/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRole;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.writer.DashboardWriter;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.schema.version25dto.Version25CollectionDataDto;
import org.camunda.optimize.upgrade.steps.schema.version25dto.Version25CollectionDefinitionDto;
import org.camunda.optimize.upgrade.util.CollectionEntityDao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class UpgradeCollectionIndexStep extends UpdateIndexStep {

  private final CollectionEntityDao collectionEntityDao;

  // original entity -> (collectionId -> copied entity)
  private final Map<String, Map<String, String>> entityInCollectionMap = new HashMap<>();


  public UpgradeCollectionIndexStep(OptimizeElasticsearchClient client,
                                    ConfigurationService configurationService,
                                    ObjectMapper objectMapper) {
    super(
      new CollectionIndex(),
      createDefaultManagerRoleForCollectionsScript() +
        removeCollectionEntitiesScript()
    );

    final ReportWriter reportWriter = new ReportWriter(objectMapper, client);
    final DashboardWriter dashboardWriter = new DashboardWriter(client, objectMapper);

    this.collectionEntityDao = new CollectionEntityDao(
      objectMapper,
      client,
      configurationService,
      reportWriter,
      dashboardWriter
    );
  }

  private static String createDefaultManagerRoleForCollectionsScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("collectionOwnerField", BaseCollectionDefinitionDto.Fields.owner.name())
        .put("collectionDataField", BaseCollectionDefinitionDto.Fields.data.name())
        .put("collectionDataRolesField", CollectionDataDto.Fields.roles.name())
        .put("managerRole", CollectionRole.MANAGER.name())
        .build()
    );
    return substitutor.replace(
      // @formatter:off
      "String owner = ctx._source.${collectionOwnerField};\n" +
        "def identity = [ \"id\": owner, \"type\": \"USER\" ];\n" +
        "def roleEntry = [ \"id\": \"USER:\" + owner, \"identity\": identity, \"role\": \"${managerRole}\"];\n" +
        "ctx._source.${collectionDataField}.${collectionDataRolesField} = new ArrayList();\n" +
        "ctx._source.${collectionDataField}.${collectionDataRolesField}.add(roleEntry);\n"
      // @formatter:on
    );
  }

  private static String removeCollectionEntitiesScript() {
    return "ctx._source.data.remove(\"entities\")";
  }

  @Override
  public void execute(final ESIndexAdjuster esIndexAdjuster) {

    final List<Version25CollectionDefinitionDto> collections = collectionEntityDao.getAllCollectionsWithEntities();

    for (Version25CollectionDefinitionDto currCollection : collections) {
      final Version25CollectionDataDto data = currCollection.getData();

      data.getEntities().stream()
        // only upgrade and copy entities that are not already in collection
        .filter(entityId -> !entityInCollectionMap.containsKey(entityId)
          || !entityInCollectionMap.get(entityId).containsKey(currCollection.getId()))
        .map(collectionEntityDao::getResolvedEntity)
        .forEach(entity -> {
          if (entity instanceof DashboardDefinitionDto) {
            copyDashboardAndChildren(currCollection.getId(), entity);

          } else if (entity instanceof ReportDefinitionDto) {
            final ReportDefinitionDto report = (ReportDefinitionDto) entity;

            if (report.getCombined()) {
              copyCombinedReportAndChildren(currCollection.getId(), entity);
            } else {
              copySingleReport(report, currCollection.getId());
            }
          }
        });
    }

    // next we want to upgrade the children of private entities to assure the children with different owner get copied
    upgradePrivateEntityChildren();

    // last but not least do the actual collection index upgrade in super
    super.execute(esIndexAdjuster);

  }

  private void upgradePrivateEntityChildren() {
    final Map<String, Map<String, String>> entityInOwnerMap = new HashMap<>();

    for (CollectionEntity currEntity : collectionEntityDao.getPrivateCombinedReportsAndDashboards()) {
      if (currEntity instanceof DashboardDefinitionDto) {

        final List<ReportLocationDto> newReports = ((DashboardDefinitionDto) currEntity).getReports().stream()
          .map(locationDto -> {
            final CollectionEntity report = collectionEntityDao.getResolvedEntity(locationDto.getId());
            if (!StringUtils.equals(currEntity.getOwner(), report.getOwner())) {
              locationDto.setId(getPrivateChildEntityId(currEntity.getOwner(), locationDto.getId(), entityInOwnerMap));
            }
            return locationDto;
          }).collect(Collectors.toList());

        collectionEntityDao.updateDashboardReports(currEntity.getId(), newReports);
      } else if (currEntity instanceof CombinedReportDefinitionDto) {

        final List<CombinedReportItemDto> newReports = ((CombinedReportDefinitionDto) currEntity).getData()
          .getReports().stream()
          .map(item -> {
            final CollectionEntity report = collectionEntityDao.getResolvedEntity(item.getId());
            if (!StringUtils.equals(currEntity.getOwner(), report.getOwner())) {
              item.setId(getPrivateChildEntityId(currEntity.getOwner(), item.getId(), entityInOwnerMap));
            }
            return item;
          })
          .collect(Collectors.toList());

        collectionEntityDao.updateCombinedReportChildren(currEntity.getId(), newReports);
      }

    }
  }


  private void updateCopyMap(final String originalId, final String collectionId, final String newId) {
    entityInCollectionMap.putIfAbsent(originalId, new HashMap<>());
    entityInCollectionMap.get(originalId).put(collectionId, newId);
  }

  private String copyDashboardAndChildren(final String collectionId, final CollectionEntity entity) {
    // 1. copy dashboard to new collection
    String newId = collectionEntityDao.copyDashboardToCollection((DashboardDefinitionDto) entity, collectionId).getId();
    updateCopyMap(entity.getId(), collectionId, newId);

    // 2. handle children of dashboard and set them to the copied dashboard
    final List<ReportLocationDto> reportLocationDtos = upgradeDashboardChildren(
      collectionId,
      ((DashboardDefinitionDto) entity).getReports()
    );
    collectionEntityDao.updateDashboardReports(newId, reportLocationDtos);
    return newId;
  }


  private String copyCombinedReportAndChildren(final String collectionId,
                                               final CollectionEntity entity) {
    return copyCombinedReportAndChildren(collectionId, entity, null);
  }


  private String copyCombinedReportAndChildren(final String collectionId,
                                               final CollectionEntity entity,
                                               final String owner) {
    // 1. copy entity to
    String newId = collectionEntityDao.copyCombinedReportToCollection(
      (CombinedReportDefinitionDto) entity,
      collectionId,
      owner
    );
    final String originalId = entity.getId();
    updateCopyMap(originalId, collectionId, newId);

    // handle children
    final List<CombinedReportItemDto> newChildrenItems = upgradeCombinedReportChildren(
      collectionId,
      ((CombinedReportDefinitionDto) entity).getData().getReports()
    );
    collectionEntityDao.updateCombinedReportChildren(newId, newChildrenItems);
    return newId;
  }

  private String copySingleReport(final ReportDefinitionDto report, final String collectionId) {
    if (entityInCollectionMap.containsKey(report.getId()) && entityInCollectionMap.get(report.getId())
      .containsKey(collectionId)) {
      return report.getId();
    } else {
      String newId = collectionEntityDao.copySingleReportToCollection(report, collectionId);
      updateCopyMap(report.getId(), collectionId, newId);
      return newId;
    }
  }


  private List<CombinedReportItemDto> upgradeCombinedReportChildren(final String collectionId,
                                                                    final List<CombinedReportItemDto> children) {

    return children.stream()
      .map(item -> {
        item.setId(getOrCreateCollectionEntityForOriginalEntityId(collectionId, item.getId()));
        return item;
      }).collect(Collectors.toList());
  }

  private List<ReportLocationDto> upgradeDashboardChildren(final String collectionId,
                                                           final List<ReportLocationDto> children) {

    return children.stream()
      .map(locationDto -> {
        locationDto.setId(getOrCreateCollectionEntityForOriginalEntityId(collectionId, locationDto.getId()));
        return locationDto;
      }).collect(Collectors.toList());
  }


  private String getOrCreateCollectionEntityForOriginalEntityId(final String collectionId,
                                                                final String entityId) {

    if (entityInCollectionMap.containsKey(entityId) && entityInCollectionMap.get(entityId).containsKey(collectionId)) {

      return entityInCollectionMap.get(entityId).get(collectionId);
    } else {
      final ReportDefinitionDto resolvedEntity = (ReportDefinitionDto) collectionEntityDao.getResolvedEntity(entityId);

      if (resolvedEntity.getCombined()) {
        return copyCombinedReportAndChildren(collectionId, resolvedEntity);
      } else {
        return copySingleReport(resolvedEntity, collectionId);
      }
    }
  }

  private String getPrivateChildEntityId(final String owner,
                                         final String entityId,
                                         final Map<String, Map<String, String>> entityCopyMap) {

    if (entityCopyMap.containsKey(entityId) && entityCopyMap.get(entityId).containsKey(owner)) {

      return entityCopyMap.get(entityId).get(owner);
    } else {
      final ReportDefinitionDto resolvedEntity = (ReportDefinitionDto) collectionEntityDao.getResolvedEntity(entityId);

      if (resolvedEntity.getCombined()) {

        return copyCombinedReportAndChildren(null, resolvedEntity, owner);
      } else {

        resolvedEntity.setOwner(owner);
        String newId = collectionEntityDao.copySingleReportToCollection(resolvedEntity, null);
        entityCopyMap.putIfAbsent(entityId, new HashMap<>());
        entityCopyMap.get(entityId).put(owner, newId);

        return newId;
      }
    }
  }

}
