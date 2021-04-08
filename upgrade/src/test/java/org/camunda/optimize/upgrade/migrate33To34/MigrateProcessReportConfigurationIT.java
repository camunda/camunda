/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public class MigrateProcessReportConfigurationIT extends AbstractUpgrade33IT {

  public static final String PROCESS_VIEW_PROPERTY_PROPERTY_NAME = "property";
  public static final String PROCESS_REPORT_CONFIG_AGGREGATION_TYPE_PROPERTY_NAME = "aggregationType";
  public static final String PROCESS_REPORT_CONFIG_USER_TASK_DURATION_PROPERTY_NAME = "userTaskDurationTime";
  public static final String PROCESS_CONFIG_COLUMN_ORDER = "columnOrder";

  @SneakyThrows
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  @Test
  public void multiMeasureFieldsAreInitializedAndDeprecatedOnesRemoved() {
    // given
    executeBulk("steps/3.3/reports/33-process-reports-for-multi-measure-migration.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // then
    final List<SingleProcessReportDefinitionRequestDto> reportDtosBeforeUpgrade = getAllDocumentsOfIndexAs(
      PROCESS_REPORT_INDEX.getIndexName(),
      SingleProcessReportDefinitionRequestDto.class
    );
    final SearchHit[] reportHitsBeforeUpgrade = getAllDocumentsOfIndex(PROCESS_REPORT_INDEX.getIndexName());

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then all reports still exist
    final SearchHit[] reportsAfterUpgrade = getAllDocumentsOfIndex(PROCESS_REPORT_INDEX.getIndexName());
    assertThat(reportsAfterUpgrade)
      .hasSameSizeAs(reportHitsBeforeUpgrade)
      .allSatisfy(report -> {
        // AND do not have the deprecated properties anymore
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleProcessReportIndex.DATA);
        if (!reportAsMap.get(SingleDecisionReportIndex.ID).equals("no-view")) {
          final Map<String, Object> reportDataView =
            (Map<String, Object>) reportData.get(ProcessReportDataDto.Fields.view);
          assertThat(reportDataView.containsKey(PROCESS_VIEW_PROPERTY_PROPERTY_NAME)).isFalse();
        }
        final Map<String, Object> reportConfig =
          (Map<String, Object>) reportData.get(SingleProcessReportIndex.CONFIGURATION);
        assertThat(reportConfig.containsKey(PROCESS_REPORT_CONFIG_AGGREGATION_TYPE_PROPERTY_NAME)).isFalse();
        assertThat(reportConfig.containsKey(PROCESS_REPORT_CONFIG_USER_TASK_DURATION_PROPERTY_NAME)).isFalse();
      });
    // AND other fields aren't affected
    assertOtherFieldsAreNotAffected(reportDtosBeforeUpgrade);

    // AND previous values are migrated to new properties as expected
    assertThat(getProcessReportWithId("view-property-frequency")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getView().getProperties()).containsExactly(ViewProperty.FREQUENCY);
      });
    assertThat(getProcessReportWithId("view-property-duration-avg-total")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getView().getProperties()).containsExactly(ViewProperty.DURATION);
        assertThat(report.getData().getConfiguration().getAggregationTypes()).containsExactly(AggregationType.AVERAGE);
        assertThat(report.getData()
                     .getConfiguration()
                     .getUserTaskDurationTimes()).containsExactly(UserTaskDurationTime.TOTAL);
      });
    assertThat(getProcessReportWithId("view-property-duration-min-idle")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getView().getProperties()).containsExactly(ViewProperty.DURATION);
        assertThat(report.getData().getConfiguration().getAggregationTypes()).containsExactly(AggregationType.MIN);
        assertThat(report.getData()
                     .getConfiguration()
                     .getUserTaskDurationTimes()).containsExactly(UserTaskDurationTime.IDLE);
      });
    assertThat(getProcessReportWithId("view-property-duration-max-work")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getView().getProperties()).containsExactly(ViewProperty.DURATION);
        assertThat(report.getData().getConfiguration().getAggregationTypes()).containsExactly(AggregationType.MAX);
        assertThat(report.getData()
                     .getConfiguration()
                     .getUserTaskDurationTimes()).containsExactly(UserTaskDurationTime.WORK);
      });
    assertThat(getProcessReportWithId("view-property-rawData")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getView().getProperties()).containsExactly(ViewProperty.RAW_DATA);
      });
    // this case shouldn't be a real case it's just here to ensure this state does not cause the update to abort
    assertThat(getProcessReportWithId("view-property-null-null-null")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getView().getProperties()).isEmpty();
        assertThat(report.getData().getConfiguration().getAggregationTypes()).isEmpty();
        assertThat(report.getData().getConfiguration().getUserTaskDurationTimes()).isEmpty();
      });
  }

  @SneakyThrows
  @SuppressWarnings(UNCHECKED_CAST)
  @Test
  public void columnOrderIsMigrated() {
    // given
    executeBulk("steps/3.3/reports/33-process-report-columnsOrder.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // then
    final List<SingleProcessReportDefinitionRequestDto> reportDtosBeforeUpgrade = getAllDocumentsOfIndexAs(
      PROCESS_REPORT_INDEX.getIndexName(),
      SingleProcessReportDefinitionRequestDto.class
    );
    final SearchHit[] reportHitsBeforeUpgrade = getAllDocumentsOfIndex(PROCESS_REPORT_INDEX.getIndexName());

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then all reports still exist
    final SearchHit[] reportsAfterUpgrade = getAllDocumentsOfIndex(PROCESS_REPORT_INDEX.getIndexName());
    assertThat(reportsAfterUpgrade)
      .hasSameSizeAs(reportHitsBeforeUpgrade)
      .allSatisfy(report -> {
        // AND do not have the deprecated property anymore
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleProcessReportIndex.DATA);
        final Map<String, Object> reportConfig =
          (Map<String, Object>) reportData.get(SingleReportDataDto.Fields.configuration);
        assertThat(reportConfig).doesNotContainKey(PROCESS_CONFIG_COLUMN_ORDER);
      });
    // AND other fields aren't affected
    assertOtherFieldsAreNotAffected(reportDtosBeforeUpgrade);

    // AND previous values are migrated to new properties as expected
    assertThat(getProcessReportWithId("raw-data-with-column-order")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getConfiguration().getTableColumns().getColumnOrder())
          .containsExactly(
            // instance properties come first
            "startDate",
            "processDefinitionKey",
            "processDefinitionId",
            "businessKey",
            "processInstanceId",
            "endDate",
            "duration",
            "engineName",
            "tenantId",
            // then variables
            "variable:amount",
            "variable:approved",
            "variable:invoiceCategory",
            "variable:invoiceNumber",
            "variable:approver",
            "variable:creditor"
          );
      });
  }

  private void assertOtherFieldsAreNotAffected(final List<SingleProcessReportDefinitionRequestDto> reportDtosBeforeUpgrade) {
    final List<SingleProcessReportDefinitionRequestDto> allDocumentsOfIndexAfterUpgrade = getAllDocumentsOfIndexAs(
      PROCESS_REPORT_INDEX.getIndexName(), SingleProcessReportDefinitionRequestDto.class
    );
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .usingRecursiveFieldByFieldElementComparator()
      .usingElementComparatorIgnoringFields(SingleProcessReportIndex.DATA)
      .containsExactlyInAnyOrderElementsOf(reportDtosBeforeUpgrade);
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .extracting(ReportDefinitionDto::getData)
      .usingElementComparatorIgnoringFields(SingleReportDataDto.Fields.configuration, ProcessReportDataDto.Fields.view)
      .containsExactlyInAnyOrderElementsOf(
        reportDtosBeforeUpgrade.stream().map(SingleProcessReportDefinitionRequestDto::getData).collect(toList())
      );
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getView)
      .usingElementComparatorIgnoringFields(ProcessViewDto.Fields.properties)
      .containsExactlyInAnyOrderElementsOf(
        reportDtosBeforeUpgrade.stream()
          .map(SingleProcessReportDefinitionRequestDto::getData)
          .map(ProcessReportDataDto::getView)
          .collect(toList())
      );
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getConfiguration)
      .usingElementComparatorIgnoringFields(
        SingleReportConfigurationDto.Fields.aggregationTypes,
        SingleReportConfigurationDto.Fields.userTaskDurationTimes,
        SingleReportConfigurationDto.Fields.tableColumns
      )
      .containsExactlyInAnyOrderElementsOf(
        reportDtosBeforeUpgrade.stream()
          .map(SingleProcessReportDefinitionRequestDto::getData)
          .map(ProcessReportDataDto::getConfiguration)
          .collect(toList())
      );
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getConfiguration)
      .extracting(SingleReportConfigurationDto::getTableColumns)
      .usingElementComparatorIgnoringFields(TableColumnDto.Fields.columnOrder)
      .containsExactlyInAnyOrderElementsOf(
        reportDtosBeforeUpgrade.stream()
          .map(SingleProcessReportDefinitionRequestDto::getData)
          .map(ProcessReportDataDto::getConfiguration)
          .map(SingleReportConfigurationDto::getTableColumns)
          .collect(toList())
      );
  }

  private Optional<SingleProcessReportDefinitionRequestDto> getProcessReportWithId(final String reportId) {
    return getDocumentOfIndexByIdAs(
      new SingleProcessReportIndex().getIndexName(), reportId, SingleProcessReportDefinitionRequestDto.class
    );
  }

}
