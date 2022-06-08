/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.query.performance;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.util.PropertyUtil;
import org.camunda.optimize.util.BpmnModels;
import org.camunda.optimize.util.DmnModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public abstract class AbstractQueryPerformanceTest {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected static final String DEFAULT_USER = "demo";

  @Autowired
  protected static ApplicationContext applicationContext;

  private static final String PROPERTY_LOCATION = "query-performance.properties";
  private static final Properties PROPERTIES = PropertyUtil.loadProperties(PROPERTY_LOCATION);
  private static String testDisplayName;

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();

  @RegisterExtension
  @Order(2)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  @BeforeEach
  public void init(TestInfo testInfo) {
    testDisplayName = testInfo.getTestMethod().map(Method::getName).orElseGet(testInfo::getDisplayName);
  }

  protected static long getMaxAllowedQueryTime() {
    String maxQueryTimeString = PROPERTIES.getProperty("camunda.optimize.test.query.max.time.in.ms");
    return Long.parseLong(maxQueryTimeString);
  }

  protected static long getMaxAllowedReportQueryTime() {
    String maxQueryTimeString = PROPERTIES.getProperty("camunda.optimize.test.query.report.max.time.in.ms");
    return Long.parseLong(maxQueryTimeString);
  }

  protected static long getMaxAllowedQueryTimeWithWarmCaches() {
    String maxQueryTimeString = PROPERTIES.getProperty("camunda.optimize.test.query.warm.cache.max.time.in.ms");
    return Long.parseLong(maxQueryTimeString);
  }

  protected static int getNumberOfEntities() {
    String entityCountString = PROPERTIES.getProperty("camunda.optimize.test.query.entity.count");
    return Integer.parseInt(entityCountString);
  }

  protected static int getNumberOfEventsToIngest() {
    String eventCountString = PROPERTIES.getProperty("camunda.optimize.test.ingest.event.count");
    return Integer.parseInt(eventCountString);
  }

  protected static int getNumberOfEvents() {
    String eventCountString = PROPERTIES.getProperty("camunda.optimize.test.query.event.count");
    return Integer.parseInt(eventCountString);
  }

  protected static int getNumberOfDefinitions() {
    String definitionCountString = PROPERTIES.getProperty("camunda.optimize.test.query.definition.count");
    return Integer.parseInt(definitionCountString);
  }

  protected static int getNumberOfDefinitionVersions() {
    String definitionVersionCountString =
      PROPERTIES.getProperty("camunda.optimize.test.query.definition.version.count");
    return Integer.parseInt(definitionVersionCountString);
  }

  protected <T> void assertThatListEndpointMaxAllowedQueryTimeIsMet(final int numberOfExpectedElements,
                                                                    final Supplier<List<T>> elementFetcher) {
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      numberOfExpectedElements,
      elementFetcher,
      null,
      getMaxAllowedQueryTime()
    );
  }

  protected <T> void assertThatListEndpointMaxAllowedQueryTimeIsMetForWarmCaches(final int numberOfExpectedElements,
                                                                                 final Supplier<List<T>> elementFetcher) {
    assertThatListEndpointMaxAllowedQueryTimeIsMet(
      numberOfExpectedElements, elementFetcher, null, getMaxAllowedQueryTimeWithWarmCaches()
    );
  }

  protected <T> void assertThatListEndpointMaxAllowedQueryTimeIsMet(final int numberOfExpectedElements,
                                                                    final Supplier<List<T>> elementFetcher,
                                                                    final Consumer<List<T>> additionalAsserts,
                                                                    final long maxAllowedQueryTime) {
    final Instant start = Instant.now();
    final List<T> elements = elementFetcher.get();
    final Instant finish = Instant.now();
    long responseTimeMs = Duration.between(start, finish).toMillis();

    // then
    assertThat(elements).hasSize(numberOfExpectedElements);
    if (additionalAsserts != null) {
      additionalAsserts.accept(elements);
    }
    log.info("{} query response time in ms: {}", getTestDisplayName(), responseTimeMs);
    assertThat(responseTimeMs).isLessThanOrEqualTo(maxAllowedQueryTime);
  }

  protected static long getImportTimeout() {
    String timeoutString =
      PROPERTIES.getProperty("camunda.optimize.test.import.timeout.in.hours");
    return Long.parseLong(timeoutString);
  }

  protected static String getTestDisplayName() {
    return testDisplayName;
  }

  protected static ProcessDefinitionOptimizeDto createProcessDefinition(final String key,
                                                                        final String version,
                                                                        final String tenantId,
                                                                        final String name,
                                                                        final String engineAlias) {
    return ProcessDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId + "-" + engineAlias + "-" + version)
      .key(key)
      .name(name)
      .version(version)
      .versionTag("aVersionTag")
      .tenantId(tenantId)
      .dataSource(new EngineDataSourceDto(engineAlias))
      .bpmn20Xml(Bpmn.convertToString(BpmnModels.getSingleUserTaskDiagram()))
      .build();
  }

  protected static DecisionDefinitionOptimizeDto createDecisionDefinition(final String key, final String version,
                                                                          final String tenantId, final String name,
                                                                          final String engineAlias) {
    return DecisionDefinitionOptimizeDto.builder()
      .id(key + "-" + version + "-" + tenantId + "-" + engineAlias)
      .key(key)
      .version(version)
      .versionTag("aVersionTag")
      .tenantId(tenantId)
      .dataSource(new EngineDataSourceDto(engineAlias))
      .name(name)
      .dmn10Xml(Dmn.convertToString(DmnModels.createDefaultDmnModel()))
      .build();
  }

}
