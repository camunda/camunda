/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version25;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom25To26;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.version25.indexes.Version25CollectionIndex;
import org.camunda.optimize.upgrade.version25.indexes.Version25ProcessInstanceIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class UpgradeReportConfigIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.5.0";

  private static final DecisionDefinitionIndex DECISION_DEFINITION_INDEX_OBJECT = new DecisionDefinitionIndex();
  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  private static final Version25ProcessInstanceIndex PROCESS_INSTANCE_INDEX = new Version25ProcessInstanceIndex();
  private static final Version25CollectionIndex COLLECTION_INDEX = new Version25CollectionIndex();
  private static final DashboardIndex DASHBOARD_INDEX = new DashboardIndex();
  private static final CombinedReportIndex COMBINED_REPORT_INDEX = new CombinedReportIndex();

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      DECISION_DEFINITION_INDEX_OBJECT,
      SINGLE_DECISION_REPORT_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      PROCESS_INSTANCE_INDEX,
      COLLECTION_INDEX,
      COMBINED_REPORT_INDEX,
      DASHBOARD_INDEX
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/report_data/25-single-decision-report-bulk");
    executeBulk("steps/report_data/25-single-process-report-bulk");
  }

  @Test
  public void processReportsHaveNewHiddenNodesConfigurationFieldStructure() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleProcessReportDefinitionDto> allProcessReports =
      getAllReports(SINGLE_PROCESS_REPORT_INDEX.getIndexName(), SingleProcessReportDefinitionDto.class);
    assertThat(allProcessReports.size(), is(2));
    // report with hidden nodes
    SingleReportConfigurationDto configuration = allProcessReports.stream()
      .filter(this::isReportWithHiddenNodes)
      .findFirst()
      .get()
      .getData()
      .getConfiguration();
    assertThat(configuration.getHiddenNodes().isActive(), is(true));
    assertThat(
      configuration.getHiddenNodes().getKeys(),
      is(Lists.newArrayList("prepareBankTransfer", "reviewInvoice"))
    );
    // report without hidden nodes
    configuration = allProcessReports.stream()
      .filter(r -> !isReportWithHiddenNodes(r))
      .findFirst()
      .get()
      .getData()
      .getConfiguration();
    assertThat(configuration.getHiddenNodes().isActive(), is(false));
    assertThat(
      configuration.getHiddenNodes().getKeys(),
      is(Collections.emptyList())
    );
  }

  @Test
  public void decisionReportsHaveNewHiddenNodesConfigurationFieldStructure() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<SingleDecisionReportDefinitionDto> allProcessReports =
      getAllReports(SINGLE_DECISION_REPORT_INDEX.getIndexName(), SingleDecisionReportDefinitionDto.class);
    assertThat(allProcessReports.size(), is(2));
    // report with hidden nodes
    SingleReportConfigurationDto configuration = allProcessReports.get(0)
      .getData()
      .getConfiguration();
    assertThat(configuration.getHiddenNodes().isActive(), is(false));
    assertThat(
      configuration.getHiddenNodes().getKeys(),
      is(Collections.emptyList())
    );
  }

  private boolean isReportWithHiddenNodes(final SingleProcessReportDefinitionDto reportDefinitionDto) {
    return !reportDefinitionDto.getData().getConfiguration().getHiddenNodes().getKeys().isEmpty();
  }

  @SneakyThrows
  private <T extends ReportDefinitionDto> List<T> getAllReports(String reportEsIndexName, Class<T> returnClass) {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(reportEsIndexName).source(new SearchSourceBuilder().size(10000)),
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