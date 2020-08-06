/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.PartialCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import org.junit.jupiter.params.provider.Arguments;

import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.entityType;
import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.lastModified;
import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.lastModifier;
import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.name;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public abstract class AbstractEntitiesRestServiceIT extends AbstractIT {

  protected String addCollection(final String collectionName) {
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.updateCollection(collectionId, new PartialCollectionDefinitionDto(collectionName));
    return collectionId;
  }

  protected String addSingleReportToOptimize(String name, ReportType reportType) {
    return addSingleReportToOptimize(name, reportType, null, DEFAULT_USERNAME);
  }

  protected String addSingleReportToOptimize(String name, ReportType reportType, String collectionId, String user) {
    switch (reportType) {
      case PROCESS:
        SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
        singleProcessReportDefinitionDto.setName(name);
        singleProcessReportDefinitionDto.setCollectionId(collectionId);
        return reportClient.createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, user);
      case DECISION:
        SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
        singleDecisionReportDefinitionDto.setName(name);
        singleDecisionReportDefinitionDto.setCollectionId(collectionId);
        return reportClient.createNewDecisionReportAsUser(singleDecisionReportDefinitionDto, user, user);
      default:
        throw new IllegalStateException("ReportType not allowed!");
    }
  }

  protected String addDashboardToOptimize(String name) {
    return addDashboardToOptimize(name, null, DEFAULT_USERNAME);
  }

  protected String addDashboardToOptimize(String name, String collectionId, String user) {
    DashboardDefinitionDto dashboardDefinitionDto = new DashboardDefinitionDto();
    dashboardDefinitionDto.setName(name);
    dashboardDefinitionDto.setCollectionId(collectionId);
    return dashboardClient.createDashboardAsUser(dashboardDefinitionDto, user, user);
  }

  protected String addCombinedReport(String name) {
    return addCombinedReport(name, null);
  }

  protected String addCombinedReport(String name, String collectionId) {
    CombinedReportDefinitionDto combinedReportDefinitionDto = new CombinedReportDefinitionDto();
    combinedReportDefinitionDto.setName(name);
    combinedReportDefinitionDto.setCollectionId(collectionId);
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(IdDto.class, Response.Status.OK.getStatusCode()).getId();
  }

  protected EntitySorter entitySorter(final String sortBy, final SortOrder sortOrder) {
    final EntitySorter sorter = new EntitySorter();
    sorter.setSortBy(sortBy);
    sorter.setSortOrder(sortOrder);
    return sorter;
  }

  @SuppressWarnings("unused")
  protected static Stream<Arguments> sortParamsAndExpectedComparator() {
    return Stream.of(
      Arguments.of(name, SortOrder.ASC, Comparator.comparing(EntityDto::getName)),
      Arguments.of(name, SortOrder.DESC, Comparator.comparing(EntityDto::getName).reversed()),
      Arguments.of(entityType, SortOrder.ASC, Comparator.comparing(EntityDto::getEntityType)),
      Arguments.of(entityType, SortOrder.DESC, Comparator.comparing(EntityDto::getEntityType).reversed()),
      Arguments.of(lastModified, SortOrder.ASC, Comparator.comparing(EntityDto::getLastModified)),
      Arguments.of(lastModified, SortOrder.DESC, Comparator.comparing(EntityDto::getLastModified).reversed()),
      Arguments.of(lastModifier, SortOrder.ASC, Comparator.comparing(EntityDto::getLastModifier)),
      Arguments.of(lastModifier, SortOrder.DESC, Comparator.comparing(EntityDto::getLastModifier).reversed())
    );
  }

}
