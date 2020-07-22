/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.ReportsGenerator.createAllReportTypesForAllDefinitions;

@Slf4j
public class ReportEvaluationQueryPerformanceTest extends AbstractQueryPerformanceTest {

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

    // if the import takes a long time the auth header
    // will time out and the requests will fail with a 401.
    // Therefore, we need to make sure that we renew the auth header
    // after the import and before we start the tests
    embeddedOptimizeExtension.refreshAuthenticationToken();
    elasticSearchIntegrationTestExtension.disableCleanup();
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_unsavedReportEvaluation(SingleReportDataDto report) {
    // given the report to evaluate

    // when
    long timeElapsed = evaluateUnsavedReportAndReturnEvaluationTime(report);

    // then
    assertThat(timeElapsed).isLessThan(getMaxAllowedQueryTime());
  }

  @ParameterizedTest
  @MethodSource("getExportableReports")
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
      log.info("Evaluation of saved report with Id {} took {} milliseconds", reportId, timeElapsed);

      // then
      assertThat(timeElapsed).isLessThan(getMaxAllowedQueryTime());
    }
  }

  @ParameterizedTest
  @MethodSource("getExportableReports")
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
      log.info("CSV export request of saved report with Id {} took {} milliseconds", reportId, timeElapsed);

      // then
      assertThat(timeElapsed).isLessThan(getMaxAllowedQueryTime());
    }
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

  private long evaluateUnsavedReportAndReturnEvaluationTime(SingleReportDataDto report) {
    log.info("Evaluating report {}", report);
    Instant start = Instant.now();
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(report)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    Instant finish = Instant.now();

    long timeElapsed = Duration.between(start, finish).toMillis();
    log.info("Evaluation of unsaved report took {} milliseconds", timeElapsed);
    return timeElapsed;
  }

  // Will be fixed in https://jira.camunda.com/browse/OPT-4018 and the test can use all possible reports instead
  private static Stream<SingleReportDataDto> getExportableReports() {
    return getPossibleReports()
      .filter(report -> {
        if (report instanceof ProcessReportDataDto) {
          final ProcessVisualization reportVisualisation = ((ProcessReportDataDto) report).getVisualization();
          return !ProcessVisualization.NUMBER.equals(reportVisualisation) &&
            !ProcessVisualization.HEAT.equals(reportVisualisation);
        }
        return true;
      });
  }

  private static Stream<SingleReportDataDto> getPossibleReports() {
    return createAllReportTypesForAllDefinitions().stream();
  }

}
