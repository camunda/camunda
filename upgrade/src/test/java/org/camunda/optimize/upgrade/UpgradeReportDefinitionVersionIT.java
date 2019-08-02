/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom25To26;
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
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

public class UpgradeReportDefinitionVersionIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.5.0";

  private static final DecisionDefinitionType DECISION_DEFINITION_TYPE_OBJECT = new DecisionDefinitionType();
  private static final SingleDecisionReportType SINGLE_DECISION_REPORT_TYPE = new SingleDecisionReportType();
  private static final SingleProcessReportType SINGLE_PROCESS_REPORT_TYPE = new SingleProcessReportType();

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_TYPE,
      DECISION_DEFINITION_TYPE_OBJECT,
      SINGLE_DECISION_REPORT_TYPE,
      SINGLE_PROCESS_REPORT_TYPE
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/report_data/25-single-decision-report-bulk");
    executeBulk("steps/report_data/25-single-process-report-bulk");
  }

  @Test
  public void processReportsHaveExpectedVersions() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllReports(SINGLE_PROCESS_REPORT_TYPE.getType(), SingleProcessReportDefinitionDto.class);
    assertThat(allProcessReports.size(), is(1));
    assertThat(allProcessReports.get(0).getData().getDefinitionVersions(), contains("2"));
  }

  @Test
  public void decisionReportsHaveExpectedVersions() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleDecisionReportDefinitionDto> allDecisionReports =
      getAllReports(SINGLE_DECISION_REPORT_TYPE.getType(), SingleDecisionReportDefinitionDto.class);
    assertThat(allDecisionReports.size(), is(1));
    assertThat(allDecisionReports.get(0).getData().getDefinitionVersions(), contains("2"));
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