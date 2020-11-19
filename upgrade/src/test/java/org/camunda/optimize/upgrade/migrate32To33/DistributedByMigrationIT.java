/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate32To33;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeFrom32To33Factory;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class DistributedByMigrationIT extends AbstractUpgrade32IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.2/reports/32-process-report-bulk");
    executeBulk("steps/3.2/reports/32-decision-report-bulk");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void migratedDistributedBy_processReports() {
    // given
    final UpgradePlan upgradePlan = UpgradeFrom32To33Factory.createUpgradePlan();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final SearchHit[] processReports = getAllSingleReports(SINGLE_PROCESS_REPORT_INDEX_NAME);
    Arrays.asList(processReports)
      .forEach(report -> {
        final Map<String, Object> data =
          (Map<String, Object>) report.getSourceAsMap().get(AbstractReportIndex.DATA);

        final Map<String, Object> configuration =
          (Map<String, Object>) data.get(SingleReportDataDto.Fields.configuration.name());

        assertThat(data).containsKey(ProcessReportDataDto.Fields.distributedBy);
        assertThat(configuration).doesNotContainKey(ProcessReportDataDto.Fields.distributedBy);

        final Map<String, Object> distributeBy =
          (Map<String, Object>) data.get(ProcessReportDataDto.Fields.distributedBy);

        assertThat(distributeBy)
          .containsEntry(ProcessDistributedByDto.Fields.type, parseReportIdToDistributedByType(report.getId()));
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  public void migratedDistributedBy_decisionReports() {
    // given
    final UpgradePlan upgradePlan = UpgradeFrom32To33Factory.createUpgradePlan();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    Arrays.asList(getAllSingleReports(SINGLE_DECISION_REPORT_INDEX_NAME))
      .forEach(report -> {
        final Map<String, Object> data =
          (Map<String, Object>) report.getSourceAsMap().get(AbstractReportIndex.DATA);

        final Map<String, Object> configuration =
          (Map<String, Object>) data.get(SingleReportDataDto.Fields.configuration.name());

        assertThat(data).containsKey(ProcessReportDataDto.Fields.distributedBy);
        assertThat(configuration).doesNotContainKey(ProcessReportDataDto.Fields.distributedBy);

        final Map<String, Object> distributeBy =
          (Map<String, Object>) data.get(ProcessReportDataDto.Fields.distributedBy);

        assertThat(distributeBy).containsEntry(ProcessDistributedByDto.Fields.type, DistributedByType.NONE.getId());
        assertThat(distributeBy).containsEntry(ProcessDistributedByDto.Fields.value, null);
      });
  }

  private SearchHit[] getAllSingleReports(final String reportIndexName) {
    return getAllDocumentsOfIndex(reportIndexName);
  }

  private String parseReportIdToDistributedByType(final String reportId) {
    switch (reportId) {
      case "distributed-by-flownode":
        return DistributedByType.FLOW_NODE.getId();
      case "distributed-by-assignee":
        return DistributedByType.ASSIGNEE.getId();
      default:
        return DistributedByType.NONE.getId();
    }
  }

}
