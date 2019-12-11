/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.apache.http.HttpStatus;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;

@AllArgsConstructor
public class ReportClient {

  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";

  private final EmbeddedOptimizeExtension embeddedOptimizeExtension;

  public String createCombinedReport(String collectionId, List<String> singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setCollectionId(collectionId);
    report.setData(createCombinedReportData(singleReportIds.toArray(new String[]{})));
    return createNewCombinedReport(report);
  }

  public void updateCombinedReport(final String combinedReportId, final List<String> containedReportIds) {
    final CombinedReportDefinitionDto combinedReportData = new CombinedReportDefinitionDto();
    combinedReportData.getData()
      .getReports()
      .addAll(
        containedReportIds.stream()
          .map(CombinedReportItemDto::new)
          .collect(Collectors.toList())
      );
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .execute();
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto combinedReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  public String createSingleReport(final String collectionId, final DefinitionType definitionType,
                                   final String definitionKey,
                                   final List<String> tenants) {
    switch (definitionType) {
      case PROCESS:
        return createAndStoreProcessReport(collectionId, definitionKey, tenants);
      case DECISION:
        return createAndStoreDecisionReport(collectionId, definitionKey, tenants);
      default:
        throw new IllegalStateException("Uncovered definitionType: " + definitionType);
    }
  }

  private String createAndStoreProcessReport(String collectionId, String definitionKey, List<String> tenants) {
    ProcessReportDataDto numberReport = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(numberReport);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createSingleProcessReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  private String createSingleDecisionReport(SingleDecisionReportDefinitionDto decisionReportDefinition) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(decisionReportDefinition)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  private String createAndStoreDecisionReport(String collectionId, String definitionKey, List<String> tenants) {
    DecisionReportDataDto rawDataReport = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(definitionKey)
      .setDecisionDefinitionVersion(RANDOM_VERSION)
      .setTenantIds(tenants)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleDecisionReportDefinitionDto decisionReportDefinition = new SingleDecisionReportDefinitionDto();
    decisionReportDefinition.setData(rawDataReport);
    decisionReportDefinition.setId(RANDOM_STRING);
    decisionReportDefinition.setLastModifier(RANDOM_STRING);
    decisionReportDefinition.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    decisionReportDefinition.setCreated(someDate);
    decisionReportDefinition.setLastModified(someDate);
    decisionReportDefinition.setOwner(RANDOM_STRING);
    decisionReportDefinition.setCollectionId(collectionId);
    return createSingleDecisionReport(decisionReportDefinition);
  }

  public void deleteReport(final String reportId) {
    embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportRequest(reportId)
      .execute();
  }
}
