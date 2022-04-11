/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateReportConfigurationLogScaleFieldIT extends AbstractUpgrade37IT {

  @Test
  public void logScaleConfigFieldIsAdded() {
    // given
    executeBulk("steps/3.7/report/37-process-reports-with-date-filters.json");
    executeBulk("steps/3.7/report/37-decision-reports.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(SINGLE_PROCESS_REPORT_INDEX.getIndexName())).hasSize(4)
      .allSatisfy(this::assertDefaultValueLogScaleFieldApplied);
    assertThat(getAllDocumentsOfIndex(SINGLE_DECISION_REPORT_INDEX.getIndexName())).hasSize(2)
      .allSatisfy(this::assertDefaultValueLogScaleFieldApplied);
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private void assertDefaultValueLogScaleFieldApplied(final SearchHit reportHit) {
    final Map<String, Object> reportProperties = reportHit.getSourceAsMap();
    final Map<String, Object> reportData = (Map<String, Object>) reportProperties.get("data");
    final Map<String, Object> reportConfig = (Map<String, Object>) reportData.get("configuration");
    assertThat((Boolean) (reportConfig.get("logScale"))).isFalse();
  }

}
