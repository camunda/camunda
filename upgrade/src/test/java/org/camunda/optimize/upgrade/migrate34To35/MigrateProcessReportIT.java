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
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade34to35PlanFactory;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

public class MigrateProcessReportIT extends AbstractUpgrade34IT {

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
      });

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "50ad2101-4286-419d-b2e0-36c83d6bc3a0",
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
              "invoice",
              "Invoice Receipt",
              Collections.singletonList("2"),
              Arrays.asList("tenant1")
            )
          );
      });

    assertThat(getDocumentOfIndexByIdAs(
      indexName,
      "96c1f30b-6dc9-4951-b32a-f620cc93aab1",
      SingleProcessReportDefinitionRequestDto.class
    ))
      .isPresent().get()
      .extracting(ReportDefinitionDto::getData)
      .extracting(SingleReportDataDto::getDefinitions)
      .satisfies(definitions -> assertThat(definitions).isEmpty());

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

}
