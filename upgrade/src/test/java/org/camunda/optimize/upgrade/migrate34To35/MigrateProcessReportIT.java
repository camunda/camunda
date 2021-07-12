/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade34to35PlanFactory;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.APPLIED_TO_ALL_DEFINITIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.INSTANCE;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public class MigrateProcessReportIT extends AbstractUpgrade34IT {

  @SuppressWarnings(UNCHECKED_CAST)
  @SneakyThrows
  @Test
  public void definitionFieldsAreMigrated() {
    // given
    executeBulk("steps/3.4/report/34-process-report.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final String indexName = SINGLE_PROCESS_REPORT_INDEX.getIndexName();
    final SearchHit[] reportsAfterUpgrade = getAllDocumentsOfIndex(indexName);
    assertThat(reportsAfterUpgrade)
      .hasSize(3)
      .allSatisfy(report -> {
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleProcessReportIndex.DATA);
        // deprecated properties are gone from elastic
        assertThat(reportData).doesNotContainKey("processDefinitionKey");
        assertThat(reportData).doesNotContainKey("processDefinitionName");
        assertThat(reportData).doesNotContainKey("processDefinitionVersions");
        assertThat(reportData).doesNotContainKey("tenantIds");
        // and new definition structure is there
        assertThat(reportData).containsKey(SingleReportDataDto.Fields.definitions);
        final List<Map<String, Object>> definitions =
          (List<Map<String, Object>>) reportData.get(SingleReportDataDto.Fields.definitions);

        if (!definitions.isEmpty()) {
          assertThat(definitions)
            .singleElement()
            .satisfies(definitionDoc -> {
              assertThat(definitionDoc).containsKey(ReportDataDefinitionDto.Fields.identifier);
              assertThat(definitionDoc).containsKey(ReportDataDefinitionDto.Fields.key);
              assertThat(definitionDoc).containsKey(ReportDataDefinitionDto.Fields.name);
              assertThat(definitionDoc).containsKey(ReportDataDefinitionDto.Fields.displayName);
              assertThat(definitionDoc).containsKey(ReportDataDefinitionDto.Fields.versions);
              assertThat(definitionDoc).containsKey(ReportDataDefinitionDto.Fields.tenantIds);
            });
        }

        List<Map<String, Object>> filters =
          (List<Map<String, Object>>) reportData.get(ProcessReportDataDto.Fields.filter);
        if (!filters.isEmpty()) {
          assertThat(filters)
            .singleElement()
            .satisfies(filter -> {
              assertThat(filter).containsKey(ProcessFilterDto.Fields.appliedTo);
            });
        }
      });

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "50ad2101-4286-419d-b2e0-36c83d6bc3a0",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .satisfies(reportData -> {
        assertThat(reportData.getDefinitions())
          .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ReportDataDefinitionDto.Fields.identifier)
          .allSatisfy(reportDataDefinitionDto -> assertThat(reportDataDefinitionDto.getIdentifier()).isNotEmpty())
          .containsExactly(
            new ReportDataDefinitionDto(
              "invoice",
              "Invoice Receipt",
              Collections.singletonList("2"),
              List.of("tenant1")
            )
          );

        assertThat(reportData.getFilter())
          .singleElement()
          .satisfies(filterDto -> assertThat(filterDto.getAppliedTo())
            .containsExactly(reportData.getFirstDefinition().get().getIdentifier())
          );
      });

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "96c1f30b-6dc9-4951-b32a-f620cc93aab1",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .satisfies(reportData -> {
        assertThat(reportData.getDefinitions()).isEmpty();

        assertThat(reportData.getFilter())
          .singleElement()
          .satisfies(filterDto -> assertThat(filterDto.getAppliedTo()).containsExactly(APPLIED_TO_ALL_DEFINITIONS));
      });

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "96c1f30b-6dc9-4951-b32a-f620cc93aab2",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .extracting(SingleReportDataDto::getDefinitions)
      .satisfies(definitions -> {
        assertThat(definitions)
          .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ReportDataDefinitionDto.Fields.identifier)
          .allSatisfy(reportDataDefinitionDto -> assertThat(reportDataDefinitionDto.getIdentifier()).isNotEmpty())
          .containsExactly(
            new ReportDataDefinitionDto(
              "test",
              Collections.singletonList(ALL_VERSIONS),
              CollectionClient.DEFAULT_TENANTS
            )
          );
      });
  }

  @SuppressWarnings(UNCHECKED_CAST)
  @SneakyThrows
  @Test
  public void flowNodeSelectionConfigIsMigratedToFilter() {
    // given
    executeBulk("steps/3.4/report/34-process-reports-with-flow-node-selection.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then the hiddenNodes config field was removed and migrated to an executedFlowNodes filter instead
    final String indexName = SINGLE_PROCESS_REPORT_INDEX.getIndexName();
    assertThat(getAllDocumentsOfIndex(indexName))
      .hasSize(5)
      .allSatisfy(report -> {
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleProcessReportIndex.DATA);
        final Map<String, Object> reportConfig =
          (Map<String, Object>) reportData.get(SingleProcessReportIndex.CONFIGURATION);
        assertThat(reportConfig).doesNotContainKey("hiddenNodes");
      });

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "report-without-flow-node-selection",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getFilter)
      .satisfies(filters -> assertThat(filters).isEmpty());

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "report-selection-inactive-but-flow-nodes-selected",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getFilter)
      .satisfies(filters -> assertThat(filters).isEmpty());

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "report-selection-active-but-no-flow-nodes",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getFilter)
      .satisfies(filters -> assertThat(filters).isEmpty());

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "report-with-flow-node-selection",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getFilter)
      .satisfies(filters -> assertThat(filters)
        .containsExactlyElementsOf(
          ProcessFilterBuilder.filter()
            .executedFlowNodes()
            .operator(NOT_IN)
            .ids("flowNodeId1", "flowNodeId2")
            .filterLevel(VIEW)
            .add()
            .buildList())
      );

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "report-with-flow-node-selection-and-filter",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .extracting(ProcessReportDataDto::getFilter)
      .satisfies(filters -> assertThat(filters)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(ProcessFilterDto.Fields.appliedTo)
        .containsExactlyInAnyOrderElementsOf(
          ProcessFilterBuilder.filter()
            .executedFlowNodes()
            .operator(NOT_IN)
            .id("flowNodeId1")
            .filterLevel(VIEW)
            .add()
            .executingFlowNodes()
            .id("flowNodeId2")
            .filterLevel(INSTANCE)
            .add()
            .buildList()));
  }
}
