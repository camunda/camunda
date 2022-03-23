/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.performance;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.schema.index.BusinessKeyIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Period;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Slf4j
@Tag("engine-cleanup")
@Tag("event-cleanup")
public class ProcessCleanupPerformanceStaticDataTest extends AbstractDataCleanupTest {

  public static final TimeValue SCROLL_KEEP_ALIVE = new TimeValue(5, TimeUnit.MINUTES);

  @BeforeAll
  public static void setUp() {
    embeddedOptimizeExtension.setupOptimize();
    // given
    // Note that when these tests run on jenkins, data is usually imported already during the "import" stage of the job
    importEngineData();
  }

  @Test
  @Order(1)
  public void cleanupModeProcessVariablesPerformanceTest() throws Exception {
    // given TTL of 0
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setEnabled(true);
    getCleanupConfiguration().setTtl(Period.parse("P0D"));
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
    embeddedOptimizeExtension.reloadConfiguration();
    final int processInstanceCount = getCamundaProcessInstanceCount();
    // we assert there is some data as a precondition as data is expected to be provided by the environment
    assertThat(processInstanceCount).isPositive();
    assertThat(getFinishedProcessInstanceVariableCount()).isPositive();
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // temporary logs for debugging
    logEndDateRangeForInstancesWithVariables();
    logProcessKeysOfCompletedInstancesWithVariables();

    // then no variables are left on finished process instances
    assertThat(getFinishedProcessInstanceVariableCount()).isZero();
    // and only variable updates related to running process instances are there
    verifyThatAllCamundaVariableUpdatesAreRelatedToRunningInstancesOnly();
    // but instances are untouched
    assertThat(getCamundaProcessInstanceCount()).isEqualTo(processInstanceCount);
  }

  @Test
  @Order(2)
  public void cleanupModeAllPerformanceTest() throws Exception {
    // given ttl of 0
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setEnabled(true);
    getCleanupConfiguration().setTtl(Period.parse("P0D"));
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    // we assert there is some data as a precondition as data is expected to be provided by the environment
    assertThat(getCamundaProcessInstanceCount()).isPositive();
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then no finished process instances should be left in optimize
    assertThat(getFinishedProcessInstanceCount()).isZero();
    // and only camunda activity events related to running process instances are there
    verifyThatAllCamundaActivityEventsAreRelatedToRunningInstancesOnly();
    // and only variable updates related to running process instances are there
    verifyThatAllCamundaVariableUpdatesAreRelatedToRunningInstancesOnly();
    // and only businessKey entries related to running process instances are there
    verifyThatAllBusinessKeyEntriesAreRelatedToRunningInstancesOnly();
  }

  @SneakyThrows
  private void verifyThatAllCamundaVariableUpdatesAreRelatedToRunningInstancesOnly() {
    verifyThatAllDocumentsOfIndexAreRelatedToRunningInstancesOnly(
      VARIABLE_UPDATE_INSTANCE_INDEX_NAME + "*", VariableUpdateInstanceIndex.PROCESS_INSTANCE_ID
    );
  }

  @SneakyThrows
  private void verifyThatAllCamundaActivityEventsAreRelatedToRunningInstancesOnly() {
    verifyThatAllDocumentsOfIndexAreRelatedToRunningInstancesOnly(
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*", CamundaActivityEventIndex.PROCESS_INSTANCE_ID
    );
  }

  @SneakyThrows
  private void verifyThatAllBusinessKeyEntriesAreRelatedToRunningInstancesOnly() {
    verifyThatAllDocumentsOfIndexAreRelatedToRunningInstancesOnly(
      BUSINESS_KEY_INDEX_NAME + "*", BusinessKeyIndex.PROCESS_INSTANCE_ID
    );
  }

  @SneakyThrows
  private void verifyThatAllDocumentsOfIndexAreRelatedToRunningInstancesOnly(final String entityIndex,
                                                                             final String processInstanceField) {
    final SearchRequest variableUpdateSearchRequest = new SearchRequest()
      .indices(entityIndex)
      .source(
        new SearchSourceBuilder()
          .query(matchAllQuery())
          .fetchSource(processInstanceField, null)
          .size(10_000)
      );

    SearchResponse camundaActivityEventsResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(variableUpdateSearchRequest.scroll(SCROLL_KEEP_ALIVE));

    while (camundaActivityEventsResponse.getHits().getHits().length > 0) {
      final Set<Object> processInstanceIds = Arrays.stream(camundaActivityEventsResponse.getHits().getHits())
        .map(SearchHit::getSourceAsMap)
        .map(hit -> hit.get(processInstanceField))
        .collect(Collectors.toSet());

      // all of these instances should be running
      final Integer finishedProcessInstanceCount = countFinishedProcessInstancedById(processInstanceIds);
      assertThat(finishedProcessInstanceCount).isZero();

      camundaActivityEventsResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient().scroll(
        new SearchScrollRequest(camundaActivityEventsResponse.getScrollId()).scroll(SCROLL_KEEP_ALIVE)
      );
    }
  }

  private Integer countFinishedProcessInstancedById(final Set<Object> processInstanceIds) {
    return elasticSearchIntegrationTestExtension.getDocumentCountOf(
      PROCESS_INSTANCE_MULTI_ALIAS,
      boolQuery()
        .filter(termsQuery(ProcessInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds))
        .filter(existsQuery(ProcessInstanceIndex.END_DATE))
    );
  }

  private Integer getCamundaProcessInstanceCount() {
    return elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS);
  }

  private Integer getFinishedProcessInstanceCount() {
    return elasticSearchIntegrationTestExtension.getDocumentCountOf(
      PROCESS_INSTANCE_MULTI_ALIAS, boolQuery().must(existsQuery(ProcessInstanceIndex.END_DATE))
    );
  }

  private Integer getFinishedProcessInstanceVariableCount() {
    return elasticSearchIntegrationTestExtension.getVariableInstanceCount(
      boolQuery().must(existsQuery(ProcessInstanceIndex.END_DATE))
    );
  }

  // temporary debug logs
  private void logEndDateRangeForInstancesWithVariables() {
    final MinMaxStatDto endDateStats =
      elasticSearchIntegrationTestExtension.getEndDateRangeForInstancesWithVariables();
    log.info(
      "End date range of completed instances with variables: min endDate is {} and max endDate is: {}. Now is {}.",
      endDateStats.getMinAsString(),
      endDateStats.getMaxAsString(),
      LocalDateUtil.getCurrentDateTime()
    );
  }

  private void logProcessKeysOfCompletedInstancesWithVariables() {
    log.info("Definition Keys of completed instances with variables: " +
               elasticSearchIntegrationTestExtension.getInstanceDefinitionKeysForFinishedInstancesWithVariables());
  }

}
