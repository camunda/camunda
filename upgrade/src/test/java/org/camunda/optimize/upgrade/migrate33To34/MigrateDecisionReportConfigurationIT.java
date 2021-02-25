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
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
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

public class MigrateDecisionReportConfigurationIT extends AbstractUpgrade33IT {

  public static final String DECISION_VIEW_PROPERTY_PROPERTY_NAME = "property";

  @SneakyThrows
  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  @Test
  public void viewPropertiesFieldIsAddedAndPreviousPropertyFieldRemoved() {
    // given
    executeBulk("steps/3.3/reports/33-decision-reports.json");
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // then
    final List<SingleDecisionReportDefinitionRequestDto> reportDtosBeforeUpgrade = getAllDocumentsOfIndexAs(
      DECISION_REPORT_INDEX.getIndexName(),
      SingleDecisionReportDefinitionRequestDto.class
    );
    final SearchHit[] reportHitsBeforeUpgrade = getAllDocumentsOfIndex(DECISION_REPORT_INDEX.getIndexName());

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then all reports still exist
    final SearchHit[] reportsAfterUpgrade = getAllDocumentsOfIndex(DECISION_REPORT_INDEX.getIndexName());
    assertThat(reportsAfterUpgrade)
      .hasSameSizeAs(reportHitsBeforeUpgrade)
      .allSatisfy(report -> {
        // AND do not have the deprecated property anymore
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleDecisionReportIndex.DATA);
        final Map<String, Object> reportDataView =
          (Map<String, Object>) reportData.get(DecisionReportDataDto.Fields.view);
        assertThat(reportDataView.containsKey(DECISION_VIEW_PROPERTY_PROPERTY_NAME)).isFalse();
      });
    // AND other fields aren't affected
    assertOtherFieldsAreNotAffected(reportDtosBeforeUpgrade);

    // AND previous values are migrated to new properties as expected
    assertThat(getDecisionReportWithId("view-property-frequency")).isPresent().get()
      .satisfies(report -> {
        assertThat(report.getData().getView().getProperties()).containsExactly(ViewProperty.FREQUENCY);
      });
  }

  private void assertOtherFieldsAreNotAffected(final List<SingleDecisionReportDefinitionRequestDto> reportDtosBeforeUpgrade) {
    final List<SingleDecisionReportDefinitionRequestDto> allDocumentsOfIndexAfterUpgrade = getAllDocumentsOfIndexAs(
      DECISION_REPORT_INDEX.getIndexName(), SingleDecisionReportDefinitionRequestDto.class
    );
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .usingRecursiveFieldByFieldElementComparator()
      .usingElementComparatorIgnoringFields(SingleDecisionReportIndex.DATA)
      .containsExactlyInAnyOrderElementsOf(reportDtosBeforeUpgrade);
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .extracting(ReportDefinitionDto::getData)
      .usingElementComparatorIgnoringFields(SingleReportDataDto.Fields.configuration, DecisionReportDataDto.Fields.view)
      .containsExactlyInAnyOrderElementsOf(
        reportDtosBeforeUpgrade.stream().map(SingleDecisionReportDefinitionRequestDto::getData).collect(toList())
      );
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .extracting(ReportDefinitionDto::getData)
      .extracting(DecisionReportDataDto::getView)
      .usingElementComparatorIgnoringFields(DecisionViewDto.Fields.properties)
      .containsExactlyInAnyOrderElementsOf(
        reportDtosBeforeUpgrade.stream()
          .map(SingleDecisionReportDefinitionRequestDto::getData)
          .map(DecisionReportDataDto::getView)
          .collect(toList())
      );
    assertThat(allDocumentsOfIndexAfterUpgrade)
      .extracting(ReportDefinitionDto::getData)
      .extracting(DecisionReportDataDto::getConfiguration)
      .usingElementComparatorIgnoringFields(
        SingleReportConfigurationDto.Fields.aggregationTypes,
        SingleReportConfigurationDto.Fields.userTaskDurationTimes
      )
      .containsExactlyInAnyOrderElementsOf(
        reportDtosBeforeUpgrade.stream()
          .map(SingleDecisionReportDefinitionRequestDto::getData)
          .map(DecisionReportDataDto::getConfiguration)
          .collect(toList())
      );
  }

  private Optional<SingleDecisionReportDefinitionRequestDto> getDecisionReportWithId(final String reportId) {
    return getDocumentOfIndexByIdAs(
      new SingleDecisionReportIndex().getIndexName(), reportId, SingleDecisionReportDefinitionRequestDto.class
    );
  }

}
