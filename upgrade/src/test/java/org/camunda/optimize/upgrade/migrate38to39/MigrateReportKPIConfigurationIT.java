/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate38to39;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.CountProgressDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.DurationProgressDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetDto;
import org.camunda.optimize.service.es.schema.index.ProcessGoalIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class MigrateReportKPIConfigurationIT extends AbstractUpgrade38IT {

  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  @Test
  public void kpiConfigurationIsAdded() {
    // given
    executeBulk("steps/3.8/report/38-process-reports-without-kpi-configuration.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(SINGLE_PROCESS_REPORT_INDEX.getIndexName()))
      .singleElement()
      .satisfies(report -> {
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleProcessReportIndex.DATA);
        final Map<String, Object> reportConfig =
          (Map<String, Object>) reportData.get(SingleProcessReportIndex.CONFIGURATION);
        final Map<String, Object> targetValueConfig = (Map<String, Object>) reportConfig.get(
          SingleReportConfigurationDto.Fields.targetValue);
        assertThat(targetValueConfig).containsEntry(SingleReportTargetValueDto.Fields.isKpi, false);
      });
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  @Test
  public void durationProgressAndCountProgressReportHaveIsBelowConfiguration() {
    // given
    executeBulk("steps/3.8/report/38-process-reports-without-kpi-configuration.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(SINGLE_PROCESS_REPORT_INDEX.getIndexName()))
      .hasSize(1)
      .allSatisfy(report -> {
        final Map<String, Object> reportAsMap = report.getSourceAsMap();
        final Map<String, Object> reportData = (Map<String, Object>) reportAsMap.get(SingleProcessReportIndex.DATA);
        final Map<String, Object> reportConfig =
          (Map<String, Object>) reportData.get(SingleProcessReportIndex.CONFIGURATION);
        final Map<String, Object> targetValueConfig = (Map<String, Object>) reportConfig.get(
          SingleReportConfigurationDto.Fields.targetValue);
        final Map<String, Object> durationProgress = (Map<String, Object>) targetValueConfig.get(
          SingleReportTargetValueDto.Fields.durationProgress);
        final Map<String, Object> targetDto = (Map<String, Object>) durationProgress.get(
          DurationProgressDto.Fields.target);
        assertThat(targetDto).containsEntry(TargetDto.Fields.isBelow, false);
        final Map<String, Object> countProgress = (Map<String, Object>) targetValueConfig.get(
          SingleReportTargetValueDto.Fields.countProgress);
        assertThat(countProgress).containsEntry(CountProgressDto.Fields.isBelow, false);
      });
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  @Test
  public void goalProcessIndexGetsDelete() {
    // given
    executeBulk("steps/3.8/report/38-process-goal-index-data.json");

    // when
    performUpgrade();

    // then
    assertThat(indexExists(ProcessGoalIndex.LOWERCASE)).isFalse();
  }

  public boolean indexExists(final String indexOrAliasName) {
    final GetIndexRequest request = new GetIndexRequest(indexOrAliasName);
    try {
      return prefixAwareClient.exists(request);
    } catch (IOException e) {
      final String message = String.format(
        "Could not check if [%s] index already exists.", String.join(",", indexOrAliasName)
      );
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
