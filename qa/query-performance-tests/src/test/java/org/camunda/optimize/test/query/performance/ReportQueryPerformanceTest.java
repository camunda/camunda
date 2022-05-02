/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.query.performance;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.exceptions.evaluation.TooManyBucketsException;
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
    new EmbeddedOptimizeExtension(true);

  @BeforeAll
  public static void init() throws TimeoutException, InterruptedException {
    // given
    importEngineData();
    elasticSearchIntegrationTestExtension.disableCleanup();
    // We set a higher token limit to avoid a time out because the extension is initialized in beforeAll mode
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setTokenLifeTime(120);
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_unsavedReportEvaluation(SingleReportDataDto report) {
    // given the report to evaluate

    // when
    log.info("Evaluating report {}", getPrintableReportDetails(report));
    executeRequestAndAssertBelowMaxQueryTime(
      embeddedOptimizeExtension.getRequestExecutor().buildEvaluateSingleUnsavedReportRequest(report)
    );
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_savedReportEvaluation(SingleReportDataDto report) {
    // given a saved report
    try (final Response saveReportResponse = saveReportToOptimize(report)) {

      // we only evaluate reports that are valid and can be saved
      if (saveReportResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        // when
        final String reportId = saveReportResponse.readEntity(IdResponseDto.class).getId();

        log.info("Evaluating report {}", getPrintableReportDetails(report));
        executeRequestAndAssertBelowMaxQueryTime(
          embeddedOptimizeExtension.getRequestExecutor().buildEvaluateSavedReportRequest(reportId)
        );
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_savedReportCsvExport(SingleReportDataDto report) {
    // given a saved report
    try (final Response saveReportResponse = saveReportToOptimize(report)) {

      // we only export reports that are valid and can be saved
      if (saveReportResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        // when
        final String reportId = saveReportResponse.readEntity(IdResponseDto.class).getId();
        log.info("CSV export request for report {}", getPrintableReportDetails(report));
        executeRequestAndAssertBelowMaxQueryTime(
          embeddedOptimizeExtension.getRequestExecutor()
            .buildCsvExportRequest(reportId, IdGenerator.getNextId() + ".csv")
        );
      }
    }
  }

  @Test
  @SneakyThrows
  public void testQueryPerformance_getVariableNamesForProcessReport() {
    // given
    ProcessVariableNameRequestDto request = buildProcessVariableNameRequest();

    // when
    log.info("Fetching process variable names for request {}", request);
    executeRequestAndAssertBelowMaxQueryTime(
      embeddedOptimizeExtension.getRequestExecutor().buildProcessVariableNamesRequest(request)
    );
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
      executeRequestAndAssertBelowMaxQueryTime(
        embeddedOptimizeExtension.getRequestExecutor().buildProcessVariableValuesRequest(varValueRequest)
      );
    }
  }

  @Test
  @SneakyThrows
  public void testQueryPerformance_getVariableInputNamesForDecisionReport() {
    // given
    final DecisionVariableNameRequestDto request = buildDecisionVariableNameRequest();

    // when
    log.info("Fetching decision input variable names for request {}", request);
    executeRequestAndAssertBelowMaxQueryTime(
      embeddedOptimizeExtension.getRequestExecutor().buildDecisionInputVariableNamesRequest(request)
    );
  }

  @Test
  @SneakyThrows
  public void testQueryPerformance_getVariableOutputNamesForDecisionReport() {
    // given
    final DecisionVariableNameRequestDto request = buildDecisionVariableNameRequest();

    // when
    log.info("Fetching decision output variable names for request {}", request);
    executeRequestAndAssertBelowMaxQueryTime(
      embeddedOptimizeExtension.getRequestExecutor().buildDecisionOutputVariableNamesRequest(request)
    );
  }

  @Test
  public void testQueryPerformance_getInputVariableValuesForDecisionReportVariables() {
    // given
    final DecisionVariableNameRequestDto varNameRequest = buildDecisionVariableNameRequest();
    List<DecisionVariableNameResponseDto> varNamesResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionInputVariableNamesRequest(varNameRequest)
      .executeAndReturnList(DecisionVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    for (DecisionVariableNameResponseDto varName : varNamesResponse) {
      DecisionVariableValueRequestDto varValueRequest = new DecisionVariableValueRequestDto();
      varValueRequest.setDecisionDefinitionKey(varNameRequest.getDecisionDefinitionKey());
      varValueRequest.setVariableId(varName.getId());
      varValueRequest.setVariableType(varName.getType());
      varValueRequest.setDecisionDefinitionVersions(varNameRequest.getDecisionDefinitionVersions());
      varValueRequest.setTenantIds(varNameRequest.getTenantIds());

      // when
      log.info("Fetching decision input variable values for request {}", varValueRequest);
      executeRequestAndAssertBelowMaxQueryTime(
        embeddedOptimizeExtension.getRequestExecutor().buildDecisionInputVariableValuesRequest(varValueRequest)
      );
    }
  }

  @Test
  public void testQueryPerformance_getOutputVariableValuesForDecisionReportVariables() {
    // given
    final DecisionVariableNameRequestDto varNameRequest = buildDecisionVariableNameRequest();
    List<DecisionVariableNameResponseDto> varNamesResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDecisionOutputVariableNamesRequest(varNameRequest)
      .executeAndReturnList(DecisionVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    for (DecisionVariableNameResponseDto varName : varNamesResponse) {
      DecisionVariableValueRequestDto varValueRequest = new DecisionVariableValueRequestDto();
      varValueRequest.setDecisionDefinitionKey(varNameRequest.getDecisionDefinitionKey());
      varValueRequest.setVariableId(varName.getId());
      varValueRequest.setVariableType(varName.getType());
      varValueRequest.setDecisionDefinitionVersions(varNameRequest.getDecisionDefinitionVersions());
      varValueRequest.setTenantIds(varNameRequest.getTenantIds());

      // when
      log.info("Fetching decision output variable values for request {}", varValueRequest);
      executeRequestAndAssertBelowMaxQueryTime(
        embeddedOptimizeExtension.getRequestExecutor().buildDecisionOutputVariableValuesRequest(varValueRequest)
      );
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

  protected void executeRequestAndAssertBelowMaxQueryTime(final OptimizeRequestExecutor requestExecutor) {
    final Instant start = Instant.now();
    try (final Response response = requestExecutor.execute()) {
      if (Response.Status.BAD_REQUEST.getStatusCode() == response.getStatus()
        && response.readEntity(ErrorResponseDto.class).getErrorCode().equals(TooManyBucketsException.ERROR_CODE)) {
        log.warn("Report evaluation failed due to too many buckets");
      } else {
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      }
      final Instant finish = Instant.now();
      long timeElapsed = Duration.between(start, finish).toMillis();
      final String additionalInfo = timeElapsed > 15000 ? " The query took over 15 seconds." : "";
      log.info("{} query response time: {}.{}", getTestDisplayName(), timeElapsed, additionalInfo);

      // then
      assertThat(timeElapsed).isLessThanOrEqualTo(getMaxAllowedReportQueryTime());
    }
  }

  private Response saveReportToOptimize(final SingleReportDataDto report) {
    if (report instanceof ProcessReportDataDto) {
      return embeddedOptimizeExtension.getRequestExecutor()
        .buildCreateSingleProcessReportRequest(new SingleProcessReportDefinitionRequestDto((ProcessReportDataDto) report))
        .execute();
    } else {
      return embeddedOptimizeExtension.getRequestExecutor()
        .buildCreateSingleDecisionReportRequest(new SingleDecisionReportDefinitionRequestDto((DecisionReportDataDto) report))
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

  private String getPrintableReportDetails(SingleReportDataDto report) {
    return String.format(
      "definitionKey=%s, definitionVersion=%s, tenants=%s, configuration=%s",
      report.getDefinitionKey(), report.getDefinitionVersions(), report.getTenantIds(), report.createCommandKey()
    );
  }

  private static Stream<SingleReportDataDto> getPossibleReports() {
    return createAllReportTypesForAllDefinitions().stream();
  }

}
