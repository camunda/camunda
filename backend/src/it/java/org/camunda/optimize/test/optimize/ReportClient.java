/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.apache.http.HttpStatus;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;

@AllArgsConstructor
public class ReportClient {

  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public String createCombinedReport(String collectionId, List<String> singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setCollectionId(collectionId);
    report.setData(createCombinedReportData(singleReportIds.toArray(new String[]{})));
    return createNewCombinedReport(report);
  }

  public String createEmptyCombinedReport(final String collectionId) {
    return createCombinedReport(collectionId, Collections.emptyList());
  }

  public String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReportData(singleReportIds));
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
    getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .execute();
  }

  public void updateSingleProcessReport(final String reportId, final SingleProcessReportDefinitionDto updatedReport) {
    getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(reportId, updatedReport)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto combinedReportDefinitionDto) {
    return getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  public String createSingleReport(final String collectionId, final DefinitionType definitionType,
                                   final String definitionKey, final List<String> tenants) {
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
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
      collectionId,
      definitionKey,
      tenants
    );
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public SingleProcessReportDefinitionDto createSingleProcessReportDefinitionDto(String collectionId,
                                                                                 String definitionKey,
                                                                                 List<String> tenants) {
    ProcessReportDataDto numberReport = TemplatedProcessReportDataBuilder
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
    return singleProcessReportDefinitionDto;
  }

  private SingleDecisionReportDefinitionDto createSingleDecisionReportDefinitionDto(final String collectionId,
                                                                                    final String definitionKey,
                                                                                    final List<String> tenants) {
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
    return decisionReportDefinition;
  }

  public String createSingleProcessReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  public String createReportForCollectionAsUser(final String collectionId, final DefinitionType resourceType,
                                                final String definitionKey, final List<String> tenants) {
    return createReportForCollectionAsUser(
      collectionId,
      resourceType,
      definitionKey,
      tenants,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
  }

  public String createReportForCollectionAsUser(final String collectionId, final DefinitionType resourceType,
                                                final String definitionKey, final List<String> tenants,
                                                final String user, final String pw) {
    switch (resourceType) {
      case PROCESS:
        SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
          collectionId,
          definitionKey,
          tenants
        );
        return createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, pw);

      case DECISION:
        SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = createSingleDecisionReportDefinitionDto(
          collectionId,
          definitionKey,
          tenants
        );
        return createNewDecisionReportAsUser(singleDecisionReportDefinitionDto, user, pw);

      default:
        throw new OptimizeRuntimeException("Unknown definition type provided.");
    }
  }

  public String createSingleProcessReportAsUser(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto,
                                                final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  public String createNewDecisionReportAsUser(final SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto,
                                              final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  public String createSingleDecisionReport(SingleDecisionReportDefinitionDto decisionReportDefinition) {
    return getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(decisionReportDefinition)
      .execute(IdDto.class, HttpStatus.SC_OK)
      .getId();
  }

  private String createAndStoreDecisionReport(String collectionId, String definitionKey, List<String> tenants) {
    SingleDecisionReportDefinitionDto decisionReportDefinition = createSingleDecisionReportDefinitionDto(
      collectionId,
      definitionKey,
      tenants
    );
    return createSingleDecisionReport(decisionReportDefinition);
  }

  public SingleProcessReportDefinitionDto getSingleProcessReportDefinitionDto(String originalReportId) {
    return getRequestExecutor()
      .buildGetReportRequest(originalReportId)
      .execute(SingleProcessReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public void deleteReport(final String reportId) {
    deleteReport(reportId, false);
  }

  public void deleteReport(final String reportId, final boolean force) {
    getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
