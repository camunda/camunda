/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.camunda.optimize.test.util.ReportsGenerator.createAllReportTypesForAllDefinitions;

@Slf4j
public class ReportQueryPerformanceTest extends AbstractQueryPerformanceTest {

  @RegisterExtension
  @Order(1)
  public static ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();

  @RegisterExtension
  @Order(2)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension =
    EmbeddedOptimizeExtension.customPropertiesBuilder().beforeAllMode(true).build();

  @BeforeAll
  public static void init() throws TimeoutException, InterruptedException {
    // given
    importEngineData();
    elasticSearchIntegrationTestExtension.disableCleanup();
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_unsavedReportEvaluation(SingleReportDataDto report) {
    // given the report to evaluate

    // when
    log.info("Evaluating report {}", report);
    Instant start = Instant.now();
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(report)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Instant finish = Instant.now();

    long timeElapsed = Duration.between(start, finish).toMillis();
    log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

    // then
    assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_savedReportEvaluation(SingleReportDataDto report) {
    // given a saved report
    final Response saveReportResponse = saveReportToOptimize(report);

    // we only evaluate reports that are valid and can be saved
    if (saveReportResponse.getStatus() == Response.Status.OK.getStatusCode()) {
      // when
      final String reportId = saveReportResponse.readEntity(IdDto.class).getId();

      log.info("Evaluating report with Id {}", reportId);
      Instant start = Instant.now();
      Response evaluationResponse = embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSavedReportRequest(reportId)
        .execute();
      assertThat(evaluationResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      Instant finish = Instant.now();

      long timeElapsed = Duration.between(start, finish).toMillis();
      log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

      // then
      assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
    }
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_savedReportCsvExport(SingleReportDataDto report) {
    // given a saved report
    final Response saveReportResponse = saveReportToOptimize(report);

    // we only export reports that are valid and can be saved
    if (saveReportResponse.getStatus() == Response.Status.OK.getStatusCode()) {
      // when
      final String reportId = saveReportResponse.readEntity(IdDto.class).getId();
      log.info("CSV export request for report with Id {}", reportId);
      Instant start = Instant.now();
      Response response = embeddedOptimizeExtension
        .getRequestExecutor()
        .buildCsvExportRequest(reportId, IdGenerator.getNextId() + ".csv")
        .execute();
      assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      Instant finish = Instant.now();

      long timeElapsed = Duration.between(start, finish).toMillis();
      log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

      // then
      assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
    }
  }

  @Test
  @SneakyThrows
  public void testQueryPerformance_getVariableNamesForProcessReport() {
    // given
    ProcessVariableNameRequestDto request = buildProcessVariableNameRequest();

    // when
    log.info("Fetching process variable names for request {}", request);
    Instant start = Instant.now();
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableNamesRequest(request)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Instant finish = Instant.now();
    long timeElapsed = Duration.between(start, finish).toMillis();
    log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

    // then
    assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  @Test
  public void testQueryPerformance_getVariableValuesForProcessReportVariables() {
    // given
    ProcessVariableNameRequestDto varNameRequest = buildProcessVariableNameRequest();
    List<ProcessVariableNameResponseDto> varNamesResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableNamesRequest(varNameRequest)
      .executeAndReturnList(ProcessVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    for (ProcessVariableNameResponseDto varName : varNamesResponse) {
      ProcessVariableValueRequestDto varValueRequest = new ProcessVariableValueRequestDto();
      varValueRequest.setName(varName.getName());
      varValueRequest.setType(varName.getType());
      varValueRequest.setProcessDefinitionKey(varNameRequest.getProcessDefinitionKey());
      varValueRequest.setProcessDefinitionVersions(varNameRequest.getProcessDefinitionVersions());
      varValueRequest.setTenantIds(varNameRequest.getTenantIds());

      // when
      log.info("Fetching process variable values for request {}", varValueRequest);
      Instant start = Instant.now();
      Response response = embeddedOptimizeExtension
        .getRequestExecutor()
        .buildProcessVariableValuesRequest(varValueRequest)
        .execute();
      assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      Instant finish = Instant.now();
      long timeElapsed = Duration.between(start, finish).toMillis();
      log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

      // then
      assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
    }
  }

  @Test
  @SneakyThrows
  public void testQueryPerformance_getVariableInputNamesForDecisionReport() {
    // given
    final DecisionVariableNameRequestDto request = buildDecisionVariableNameRequest();

    // when
    log.info("Fetching decision input variable names for request {}", request);
    Instant start = Instant.now();
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionInputVariableNamesRequest(request)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Instant finish = Instant.now();
    long timeElapsed = Duration.between(start, finish).toMillis();
    log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

    // then
    assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  @Test
  @SneakyThrows
  public void testQueryPerformance_getVariableOutputNamesForDecisionReport() {
    // given
    final DecisionVariableNameRequestDto request = buildDecisionVariableNameRequest();

    // when
    log.info("Fetching decision output variable names for request {}", request);
    Instant start = Instant.now();
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionOutputVariableNamesRequest(request)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Instant finish = Instant.now();
    long timeElapsed = Duration.between(start, finish).toMillis();
    log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

    // then
    assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
  }

  @Test
  public void testQueryPerformance_getInputVariableValuesForDecisionReportVariables() {
    // given
    final DecisionVariableNameRequestDto varNameRequest = buildDecisionVariableNameRequest();
    List<DecisionVariableNameDto> varNamesResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionInputVariableNamesRequest(varNameRequest)
      .executeAndReturnList(DecisionVariableNameDto.class, Response.Status.OK.getStatusCode());

    for (DecisionVariableNameDto varName : varNamesResponse) {
      DecisionVariableValueRequestDto varValueRequest = new DecisionVariableValueRequestDto();
      varValueRequest.setDecisionDefinitionKey(varNameRequest.getDecisionDefinitionKey());
      varValueRequest.setVariableId(varName.getId());
      varValueRequest.setVariableType(varName.getType());
      varValueRequest.setDecisionDefinitionVersions(varNameRequest.getDecisionDefinitionVersions());
      varValueRequest.setTenantIds(varNameRequest.getTenantIds());

      // when
      log.info("Fetching decision input variable values for request {}", varValueRequest);
      Instant start = Instant.now();
      Response response = embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDecisionInputVariableValuesRequest(varValueRequest)
        .execute();
      assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      Instant finish = Instant.now();
      long timeElapsed = Duration.between(start, finish).toMillis();
      log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

      // then
      assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
    }
  }

  @Test
  public void testQueryPerformance_getOutputVariableValuesForDecisionReportVariables() {
    // given
    final DecisionVariableNameRequestDto varNameRequest = buildDecisionVariableNameRequest();
    List<DecisionVariableNameDto> varNamesResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionOutputVariableNamesRequest(varNameRequest)
      .executeAndReturnList(DecisionVariableNameDto.class, Response.Status.OK.getStatusCode());

    for (DecisionVariableNameDto varName : varNamesResponse) {
      DecisionVariableValueRequestDto varValueRequest = new DecisionVariableValueRequestDto();
      varValueRequest.setDecisionDefinitionKey(varNameRequest.getDecisionDefinitionKey());
      varValueRequest.setVariableId(varName.getId());
      varValueRequest.setVariableType(varName.getType());
      varValueRequest.setDecisionDefinitionVersions(varNameRequest.getDecisionDefinitionVersions());
      varValueRequest.setTenantIds(varNameRequest.getTenantIds());

      // when
      log.info("Fetching decision output variable values for request {}", varValueRequest);
      Instant start = Instant.now();
      Response response = embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDecisionOutputVariableValuesRequest(varValueRequest)
        .execute();
      assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      Instant finish = Instant.now();
      long timeElapsed = Duration.between(start, finish).toMillis();
      log.info("{} query response time: {}", getTestDisplayName(), timeElapsed);

      // then
      assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedQueryTime());
    }
  }

  @SneakyThrows
  private ProcessVariableNameRequestDto buildProcessVariableNameRequest() {
    final Map.Entry<String, Set<String>> defKeyToTenants = getPossibleReports()
      .filter(ProcessReportDataDto.class::isInstance)
      .map(ProcessReportDataDto.class::cast)
      .filter(processReport -> processReport.getProcessDefinitionKey() != null)
      .collect(Collectors.groupingBy(
        ProcessReportDataDto::getProcessDefinitionKey,
        Collectors.mapping(ProcessReportDataDto::getTenantIds, Collectors.collectingAndThen(
          Collectors.toSet(),
          tenantList -> tenantList.stream()
            .flatMap(List::stream)
            .collect(Collectors.toSet())
        ))
      ))
      .entrySet().stream()
      .max(Comparator.comparing(entry -> entry.getValue().size()))
      .orElseGet(() -> fail("Could not build variable name request"));
    ProcessVariableNameRequestDto request = new ProcessVariableNameRequestDto();
    request.setProcessDefinitionKey(defKeyToTenants.getKey());
    request.setProcessDefinitionVersion("ALL");
    request.setTenantIds(new ArrayList<>(defKeyToTenants.getValue()));
    return request;
  }

  @SneakyThrows
  private DecisionVariableNameRequestDto buildDecisionVariableNameRequest() {
    final Map.Entry<String, Set<String>> defKeyToTenants = getPossibleReports()
      .filter(DecisionReportDataDto.class::isInstance)
      .map(DecisionReportDataDto.class::cast)
      .filter(processReport -> processReport.getDecisionDefinitionKey() != null)
      .collect(Collectors.groupingBy(
        DecisionReportDataDto::getDecisionDefinitionKey,
        Collectors.mapping(DecisionReportDataDto::getTenantIds, Collectors.collectingAndThen(
          Collectors.toSet(),
          tenantList -> tenantList.stream()
            .flatMap(List::stream)
            .collect(Collectors.toSet())
        ))
      ))
      .entrySet().stream()
      .max(Comparator.comparing(entry -> entry.getValue().size()))
      .orElseGet(() -> fail("Could not build variable name request"));
    DecisionVariableNameRequestDto request = new DecisionVariableNameRequestDto();
    request.setDecisionDefinitionKey(defKeyToTenants.getKey());
    request.setDecisionDefinitionVersion("ALL");
    request.setTenantIds(new ArrayList<>(defKeyToTenants.getValue()));
    return request;
  }

  private Response saveReportToOptimize(final SingleReportDataDto report) {
    if (report instanceof ProcessReportDataDto) {
      return embeddedOptimizeExtension.getRequestExecutor()
        .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionDto((ProcessReportDataDto) report))
        .execute();
    } else {
      return embeddedOptimizeExtension.getRequestExecutor()
        .buildCreateSingleDecisionReportRequest(new SingleDecisionReportDefinitionDto((DecisionReportDataDto) report))
        .execute();
    }
  }

  private static void importEngineData() throws InterruptedException, TimeoutException {
    log.info("Start importing engine data...");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(
      () -> embeddedOptimizeExtension.importAllEngineData()
    );

    executor.shutdown();
    final long importTimeout = getImportTimeout();
    boolean wasAbleToFinishImportInTime =
      executor.awaitTermination(importTimeout, TimeUnit.HOURS);
    if (!wasAbleToFinishImportInTime) {
      throw new TimeoutException("Import was not able to finish import in " + importTimeout + " hours!");
    }
    log.info("Finished importing engine data...");
  }

  private static Stream<SingleReportDataDto> getPossibleReports() {
    return createAllReportTypesForAllDefinitions().stream();
  }

}
