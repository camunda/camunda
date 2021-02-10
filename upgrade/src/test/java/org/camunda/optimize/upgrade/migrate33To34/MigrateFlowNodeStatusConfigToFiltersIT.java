/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateFlowNodeStatusConfigToFiltersIT extends AbstractUpgrade33IT {

  public static final String FLOW_NODE_EXECUTION_STATE_PROPERTY = "flowNodeExecutionState";

  @SneakyThrows
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  @Test
  public void flowNodeStatusConfigIsFullyMigratedToViewLevelFiltersWhereRequired() {
    // given
    executeBulk("steps/3.3/reports/33-process-report-with-flow-node-status-config.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan();

    // then
    final List<SingleProcessReportDefinitionRequestDto> reportDtosBeforeUpgrade = getAllDocumentsOfIndexAs(
      PROCESS_REPORT_INDEX.getIndexName(),
      SingleProcessReportDefinitionRequestDto.class
    );
    final SearchHit[] reportHitsBeforeUpgrade = getAllDocumentsOfIndex(PROCESS_REPORT_INDEX.getIndexName());

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then all reports still exist and none have a flow node execution state
    final SearchHit[] reportsAfterUpgrade = getAllDocumentsOfIndex(PROCESS_REPORT_INDEX.getIndexName());
    assertThat(reportsAfterUpgrade)
      .hasSameSizeAs(reportHitsBeforeUpgrade)
      .allSatisfy(report -> {
        final Map<String, Object> reportData =
          (Map<String, Object>) report.getSourceAsMap().get(SingleProcessReportIndex.DATA);
        final Map<String, Object> reportConfig =
          (Map<String, Object>) reportData.get(SingleProcessReportIndex.CONFIGURATION);
        assertThat(reportConfig.containsKey(FLOW_NODE_EXECUTION_STATE_PROPERTY)).isFalse();
      });
    // that other fields aren't affected
    assertThat(
      getAllDocumentsOfIndexAs(
        new SingleProcessReportIndex().getIndexName(),
        SingleProcessReportDefinitionRequestDto.class
      ))
      .usingRecursiveFieldByFieldElementComparator()
      .usingElementComparatorIgnoringFields(SingleProcessReportIndex.DATA)
      .containsExactlyInAnyOrderElementsOf(reportDtosBeforeUpgrade);
    // and the previous flow node execution states have been migrated to filters as expected
    assertThat(getProcessReportWithId("running-flow-node-frequency-with-filters")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).hasSize(3)
        .hasAtLeastOneElementOfType(RunningFlowNodesOnlyFilterDto.class));
    assertThat(getProcessReportWithId("completed-flow-node-frequency-no-filters")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).hasSize(1)
        .hasAtLeastOneElementOfType(CompletedOrCanceledFlowNodesOnlyFilterDto.class));
    assertThat(getProcessReportWithId("canceled-user-task-duration-no-filters")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).hasSize(1)
        .hasAtLeastOneElementOfType(CanceledFlowNodesOnlyFilterDto.class));
    assertThat(getProcessReportWithId("all-user-task-duration-no-filters")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).isEmpty());
    assertThat(getProcessReportWithId("all-raw-data-no-filters")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).isEmpty());
    assertThat(getProcessReportWithId("completed-pInstance-frequency-with-filter")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).hasSize(1).noneMatch(this::isFlowNodeStatusFilter));
    assertThat(getProcessReportWithId("running-incident-frequency-with-filter")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).hasSize(1).noneMatch(this::isFlowNodeStatusFilter));
    assertThat(getProcessReportWithId("canceled-variable-with-filter")).isPresent().get()
      .extracting(updatedReport -> updatedReport.getData().getFilter())
      .satisfies(filters -> assertThat(filters).hasSize(1).noneMatch(this::isFlowNodeStatusFilter));
  }

  private boolean isFlowNodeStatusFilter(final ProcessFilterDto<?> filter) {
    return (filter instanceof RunningFlowNodesOnlyFilterDto
      || filter instanceof CompletedOrCanceledFlowNodesOnlyFilterDto
      || filter instanceof CanceledFlowNodesOnlyFilterDto);
  }

  private Optional<SingleProcessReportDefinitionRequestDto> getProcessReportWithId(final String reportId) {
    return getDocumentOfIndexByIdAs(
      new SingleProcessReportIndex().getIndexName(), reportId, SingleProcessReportDefinitionRequestDto.class);
  }

}
