/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39preview1to39;

import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateProcessOverviewIndexIT extends AbstractUpgrade39preview1IT {

  @Test
  public void kpiLastEvaluationFieldIsAdded() {
    // given
    executeBulk("steps/3.9preview1/39preview1-process-overview-without-lastKpiEvaluation.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(PROCESS_OVERVIEW_INDEX.getIndexName()))
      .singleElement()
      .satisfies(process -> {
        final Map<String, Object> processAsMap = process.getSourceAsMap();
        assertThat(processAsMap).containsEntry(ProcessOverviewDto.Fields.lastKpiEvaluationResults, new HashMap<>());
      });
  }

  @Test
  public void digestCheckIntervalFieldIsRemoved() {
    // given
    executeBulk("steps/3.9preview1/39preview1-process-overview-without-lastKpiEvaluation.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(PROCESS_OVERVIEW_INDEX.getIndexName()))
      .singleElement()
      .satisfies(process -> {
        final Map<String, Object> processAsMap = process.getSourceAsMap();
        assertThat(processAsMap).doesNotContainKey("checkInterval");
      });
  }
}
