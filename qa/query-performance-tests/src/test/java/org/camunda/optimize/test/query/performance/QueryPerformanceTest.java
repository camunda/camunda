/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.query.performance;

import org.apache.commons.collections4.ListUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.PropertyUtil;
import org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.YEARS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

public class QueryPerformanceTest {

  private static final Logger logger = LoggerFactory.getLogger(QueryPerformanceTest.class);
  private static final String PROPERTY_LOCATION = "query-performance.properties";
  private static final Properties properties = PropertyUtil.loadProperties(PROPERTY_LOCATION);
  private static final Random randomGen = new Random();
  public static final String DOUBLE_VAR = "doubleVar";
  public static final String VARIABLE_ID = "clause1";

  @RegisterExtension
  @Order(1)
  public static ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();
  @RegisterExtension
  @Order(3)
  public static EngineIntegrationExtension engineIntegrationExtension = new EngineIntegrationExtension(
    "default",
    false
  );

  private static String authenticationToken;

  @BeforeAll
  public static void init() throws TimeoutException, InterruptedException {
    embeddedOptimizeExtension.setupOptimize();
    elasticSearchIntegrationTestExtension.disableCleanup();
    // given
    importEngineData();

    // if the import takes a long time the auth header
    // will time out and the requests will fail with a 401.
    // Therefore, we need to make sure that renew the auth header
    // after the import and before we start the tests
    authenticationToken = embeddedOptimizeExtension.getNewAuthenticationToken();
  }

  private static List<SingleReportDataDto> createAllPossibleReports() {
    List<ProcessDefinitionEngineDto> latestDefinitionVersions =
      engineIntegrationExtension.getLatestProcessDefinitions();

    List<DecisionDefinitionEngineDto> latestDecisionDefs =
      engineIntegrationExtension.getLatestDecisionDefinitions();


    List<ProcessReportDataDto> processReportDataDtos = latestDefinitionVersions
      .stream()
      .map(QueryPerformanceTest::createProcessReportsFromDefinition)
      .reduce(ListUtils::union)
      .orElse(Collections.emptyList());

    List<DecisionReportDataDto> decisionReportDataDtos = latestDecisionDefs
      .stream()
      .map(QueryPerformanceTest::createDecisionReportsFromDefinition)
      .reduce(ListUtils::union)
      .orElse(Collections.emptyList());
    List<SingleReportDataDto> reports = new ArrayList<>();
    reports.addAll(decisionReportDataDtos);
    reports.addAll(processReportDataDtos);
    return reports;
  }

  private static List<ProcessReportDataDto> createProcessReportsFromDefinition(ProcessDefinitionEngineDto definition) {
    List<ProcessReportDataDto> reports = new ArrayList<>();
    ProcessPartDto processPart = createProcessPart(definition);
    for (ProcessReportDataType reportDataType : ProcessReportDataType.values()) {
      ProcessReportDataBuilder reportDataBuilder = ProcessReportDataBuilder.createReportData()
        .setReportDataType(reportDataType)
        .setProcessDefinitionKey(definition.getKey())
        .setProcessDefinitionVersion(definition.getVersionAsString())
        .setVariableName(DOUBLE_VAR)
        .setVariableType(VariableType.DOUBLE)
        .setDateInterval(GroupByDateUnit.WEEK)
        .setStartFlowNodeId(processPart.getStart())
        .setEndFlowNodeId(processPart.getEnd())
        .setFilter(createProcessFilter());

      ProcessReportDataDto reportDataLatestDefinitionVersion =
        reportDataBuilder.build();
      reports.add(reportDataLatestDefinitionVersion);
      reportDataBuilder.setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS);
      ProcessReportDataDto reportDataAllDefinitionVersions = reportDataBuilder.build();
      reports.add(reportDataAllDefinitionVersions);
    }
    return reports;
  }

  private static List<DecisionReportDataDto> createDecisionReportsFromDefinition(DecisionDefinitionEngineDto definition) {
    List<DecisionReportDataDto> reports = new ArrayList<>();

    for (DecisionReportDataType type : DecisionReportDataType.values()) {
      DecisionReportDataDto reportDataDto = DecisionReportDataBuilder.create().setReportDataType(type)
        .setDecisionDefinitionKey(definition.getKey())
        .setDecisionDefinitionVersion(definition.getVersionAsString())
        .setDateInterval(GroupByDateUnit.DAY)
        .setFilter(createDecisionFilter())
        .setVariableName(DOUBLE_VAR)
        .setVariableType(VariableType.DOUBLE)
        .setVariableId(VARIABLE_ID)
        .build();
      reports.add(reportDataDto);
    }
    return reports;
  }

  private static ProcessPartDto createProcessPart(ProcessDefinitionEngineDto definition) {
    String xml = engineIntegrationExtension
      .getProcessDefinitionXml(definition.getId())
      .getBpmn20Xml();
    ModelInstance model = Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
    String startFlowNodeId = model.getModelElementsByType(StartEvent.class).stream().findFirst().get().getId();
    String endFlowNodeId = model.getModelElementsByType(EndEvent.class).stream().findFirst().get().getId();
    ProcessPartDto processPart = new ProcessPartDto();
    processPart.setStart(startFlowNodeId);
    processPart.setEnd(endFlowNodeId);
    return processPart;
  }

  private static List<ProcessFilterDto> createProcessFilter() {
    // @formatter:off
    return ProcessFilterBuilder
      .filter()
        .variable()
        .booleanType()
        .values(Collections.singletonList(String.valueOf(randomGen.nextBoolean())))
        .name("boolVar")
      .add()
        .fixedStartDate()
        .start(OffsetDateTime.now().minusYears(200L))
        .end(OffsetDateTime.now().plusYears(100L))
      .add()
        .fixedEndDate()
        .start(OffsetDateTime.now().minusYears(100L))
        .end(OffsetDateTime.now().plusYears(100L))
      .add()
        .completedInstancesOnly()
      .add()
        .duration()
        .unit(YEARS.toString())
        .value((long) 100)
        .operator(LESS_THAN)
      .add()
      .buildList();
  }
  // @formatter:on

  private static List<DecisionFilterDto> createDecisionFilter() {
    List<DecisionFilterDto> list = new ArrayList<>();
    list.add(DecisionFilterUtilHelper.createBooleanInputVariableFilter(
      "boolVar",
      String.valueOf(randomGen.nextBoolean())
    ));
    list.add(DecisionFilterUtilHelper.createBooleanOutputVariableFilter(
      "boolVar",
      String.valueOf(randomGen.nextBoolean())
    ));
    list.add(DecisionFilterUtilHelper.createFixedEvaluationDateFilter(
      OffsetDateTime.now().minusDays(1L),
      OffsetDateTime.now()
    ));
    return list;
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
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    logger.info("Finished importing engine data...");
  }

  private static long getImportTimeout() {
    String timeoutAsString =
      properties.getProperty("camunda.optimize.test.import.timeout.in.hours", "2");
    return Long.parseLong(timeoutAsString);
  }

  @ParameterizedTest
  @MethodSource("getPossibleReports")
  public void testQueryPerformance(SingleReportDataDto report) {
    // given the report to evaluate

    // when
    long timeElapsed = evaluateReportAndReturnEvaluationTime(report);

    // then
    assertThat(timeElapsed, lessThan(getMaxAllowedQueryTime()));
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
      .withGivenAuthToken(authenticationToken)
      .execute();
    assertThat(response.getStatus(), is(200));
    Instant finish = Instant.now();

    long timeElapsed = Duration.between(start, finish).toMillis();
    logger.info("Evaluation of report took {} milliseconds", timeElapsed);
    return timeElapsed;
  }

  private static Stream<SingleReportDataDto> getPossibleReports() {
    return createAllPossibleReports().stream();
  }
}
