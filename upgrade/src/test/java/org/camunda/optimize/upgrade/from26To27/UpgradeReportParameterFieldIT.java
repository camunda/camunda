/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.from26To27;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom26To27;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UpgradeReportParameterFieldIT extends AbstractUpgradeIT {

  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  private static final int EXPECTED_NUMBER_OF_DECISION_REPORTS = 2;
  private static final int EXPECTED_NUMBER_OF_PROCESS_REPORTS = 3;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    for (StrictIndexMappingCreator index : ALL_INDICES) {
      createOptimizeIndexWithTypeAndVersion(
        index,
        index.getIndexName(),
        index.getVersion() - 1
      );
    }
    setMetadataIndexVersionWithType(FROM_VERSION, METADATA_INDEX.getIndexName());

    executeBulk("steps/report_data/26-single-decision-report-bulk");
    executeBulk("steps/report_data/26-single-process-report-bulk");
  }

  @Test
  public void processReportsHaveExpectedSortParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllReports(SINGLE_PROCESS_REPORT_INDEX.getIndexName(), SingleProcessReportDefinitionDto.class);

    assertThat(allProcessReports.size(), is(EXPECTED_NUMBER_OF_PROCESS_REPORTS));
    assertThat(allProcessReports.get(0).getData().getConfiguration().getSorting().isPresent(), is(true));
    assertThat(
      allProcessReports.get(0).getData().getConfiguration().getSorting().get().getBy().get(),
      is("key")
    );
    assertThat(
      allProcessReports.get(0).getData().getConfiguration().getSorting().get().getOrder().get(),
      is(SortOrder.ASC)
    );
  }

  @Test
  public void processReportsHaveNoneSortParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllReports(SINGLE_PROCESS_REPORT_INDEX.getIndexName(), SingleProcessReportDefinitionDto.class);

    assertThat(allProcessReports.size(), is(EXPECTED_NUMBER_OF_PROCESS_REPORTS));
    assertThat(allProcessReports.get(1).getData().getConfiguration().getSorting().isPresent(), is(false));
  }

  @Test
  public void processReportsHaveExpectedProcessPartParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllReports(SINGLE_PROCESS_REPORT_INDEX.getIndexName(), SingleProcessReportDefinitionDto.class);

    assertThat(allProcessReports.size(), is(EXPECTED_NUMBER_OF_PROCESS_REPORTS));
    assertThat(allProcessReports.get(0).getData().getConfiguration().getProcessPart().isPresent(), is(true));
    assertThat(
      allProcessReports.get(0).getData().getConfiguration().getProcessPart().get().getStart(),
      is("StartEvent_1")
    );
    assertThat(
      allProcessReports.get(0).getData().getConfiguration().getProcessPart().get().getEnd(),
      is("approveInvoice")
    );
  }

  @Test
  public void processReportsHaveNoneProcessPartParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllReports(SINGLE_PROCESS_REPORT_INDEX.getIndexName(), SingleProcessReportDefinitionDto.class);

    assertThat(allProcessReports.size(), is(EXPECTED_NUMBER_OF_PROCESS_REPORTS));
    assertThat(allProcessReports.get(1).getData().getConfiguration().getProcessPart().isPresent(), is(false));
  }

  @Test
  public void decisionReportsHaveExpectedSortParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleDecisionReportDefinitionDto> allDecisionReports =
      getAllReports(SINGLE_DECISION_REPORT_INDEX.getIndexName(), SingleDecisionReportDefinitionDto.class);

    assertThat(allDecisionReports.size(), is(EXPECTED_NUMBER_OF_DECISION_REPORTS));
    assertThat(allDecisionReports.get(0).getData().getConfiguration().getSorting().isPresent(), is(true));
    assertThat(
      allDecisionReports.get(0).getData().getConfiguration().getSorting().get().getBy().get(),
      is("evaluationDateTime")
    );
    assertThat(
      allDecisionReports.get(0).getData().getConfiguration().getSorting().get().getOrder().get(),
      is(SortOrder.ASC)
    );
  }

  @Test
  public void decisionReportsHaveNoneSortParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleDecisionReportDefinitionDto> allDecisionReports =
      getAllReports(SINGLE_DECISION_REPORT_INDEX.getIndexName(), SingleDecisionReportDefinitionDto.class);

    assertThat(allDecisionReports.size(), is(EXPECTED_NUMBER_OF_DECISION_REPORTS));
    assertThat(allDecisionReports.get(1).getData().getConfiguration().getSorting().isPresent(), is(false));
  }

  @SneakyThrows
  private <T extends ReportDefinitionDto> List<T> getAllReports(String indexName, Class<T> returnClass) {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(indexName).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), returnClass
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }

}