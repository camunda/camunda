/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.From26To27;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom26To27;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class UpgradeReportParameterFieldIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.6.0";

  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      SINGLE_PROCESS_REPORT_INDEX
    ));

    setMetadataIndexVersion(FROM_VERSION);

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
    assertThat(allProcessReports.size(), is(1));
    assertTrue(allProcessReports.get(0).getData().getConfiguration().getSorting().isPresent());
    assertThat(allProcessReports.get(0).getData().getConfiguration().getSorting().get().getBy().get(), is("key"));
    assertThat(allProcessReports.get(0).getData().getConfiguration().getSorting().get().getOrder().get(), is(SortOrder.ASC));
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
    assertThat(allProcessReports.size(), is(1));
    assertTrue(allProcessReports.get(0).getData().getConfiguration().getProcessPart().isPresent());
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
  public void decisionReportsHaveExpectedSortParam() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleDecisionReportDefinitionDto> allDecisionReports =
      getAllReports(SINGLE_DECISION_REPORT_INDEX.getIndexName(), SingleDecisionReportDefinitionDto.class);
    assertThat(allDecisionReports.size(), is(1));
    assertTrue(allDecisionReports.get(0).getData().getConfiguration().getSorting().isPresent());
    assertThat(
      allDecisionReports.get(0).getData().getConfiguration().getSorting().get().getBy().get(),
      is("evaluationDateTime")
    );
    assertThat(allDecisionReports.get(0).getData().getConfiguration().getSorting().get().getOrder().get(), is(SortOrder.ASC));
  }

  @SneakyThrows
  private <T extends ReportDefinitionDto> List<T> getAllReports(String reportEsType, Class<T> returnClass) {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(reportEsType).source(new SearchSourceBuilder().size(10000)),
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