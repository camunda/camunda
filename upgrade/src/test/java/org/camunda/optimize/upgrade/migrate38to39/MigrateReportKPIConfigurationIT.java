/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate38to39;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.KPIConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.SingleReportTargetValueDto;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


public class MigrateReportKPIConfigurationIT extends AbstractUpgrade38IT {

  @Test
  public void kpiConfigurationIsAdded() {
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
        final Map<String, Object> kpiConfig =
          (Map<String, Object>) targetValueConfig.get(SingleReportTargetValueDto.Fields.kpi);
        assertThat(kpiConfig)
          .containsEntry(KPIConfigurationDto.Fields.active, false);
        assertThat(kpiConfig)
          .containsEntry(KPIConfigurationDto.Fields.type, null);
      });
  }
}
