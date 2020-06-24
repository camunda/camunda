/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.camunda.optimize.test.util.ReportsGenerator.createAllPossibleReports;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

public class QueryPerformanceTest {

  private static final Logger logger = LoggerFactory.getLogger(QueryPerformanceTest.class);
  private static final String PROPERTY_LOCATION = "query-performance.properties";
  private static final Properties properties = PropertyUtil.loadProperties(PROPERTY_LOCATION);

  @RegisterExtension
  @Order(1)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension(true);

  @BeforeAll
  public static void init() throws TimeoutException, InterruptedException {
    // given
    importEngineData();

    // if the import takes a long time the auth header
    // will time out and the requests will fail with a 401.
    // Therefore, we need to make sure that renew the auth header
    // after the import and before we start the tests
    embeddedOptimizeExtension.refreshAuthenticationToken();
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance_reportEvaluation(SingleReportDataDto report) {
    // given the report to evaluate

    // when
    long timeElapsed = evaluateReportAndReturnEvaluationTime(report);

    // then
    assertThat(timeElapsed, lessThan(getMaxAllowedQueryTime()));
  }

  private static void importEngineData() throws InterruptedException, TimeoutException {
    logger.info("Start importing engine data...");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(
      () -> embeddedOptimizeExtension.importAllEngineData()
    );

    executor.shutdown();
    boolean wasAbleToFinishImportInTime =
      executor.awaitTermination(getImportTimeout(), TimeUnit.HOURS);
    if (!wasAbleToFinishImportInTime) {
      throw new TimeoutException("Import was not able to finish import in " + 2 + " hours!");
    }
    logger.info("Finished importing engine data...");
  }

  private static long getImportTimeout() {
    String timeoutAsString =
      properties.getProperty("camunda.optimize.test.import.timeout.in.hours", "2");
    return Long.parseLong(timeoutAsString);
  }

  private long getMaxAllowedQueryTime() {
    String timeoutAsString =
      properties.getProperty("camunda.optimize.test.import.max.query.time.in.ms", "5000");
    return Long.parseLong(timeoutAsString);
  }

  private long evaluateReportAndReturnEvaluationTime(SingleReportDataDto report) {
    logger.info("Evaluating report {}", report);
    Instant start = Instant.now();
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(report)
      .execute();
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    Instant finish = Instant.now();

    long timeElapsed = Duration.between(start, finish).toMillis();
    logger.info("Evaluation of report took {} milliseconds", timeElapsed);
    return timeElapsed;
  }

  private static Stream<SingleReportDataDto> getPossibleReports() {
    return createAllPossibleReports().stream();
  }
}
