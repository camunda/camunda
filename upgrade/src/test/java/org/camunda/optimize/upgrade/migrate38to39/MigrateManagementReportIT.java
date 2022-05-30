/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate38to39;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateManagementReportIT extends AbstractUpgrade38IT {

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  @Test
  public void existingProcessReportsAreMarkedAsNonManagement() {
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
        final Boolean isManagementReport = (Boolean) reportData.get(ProcessReportDataDto.Fields.managementReport);
        assertThat(isManagementReport).isFalse();
      });
  }

}
