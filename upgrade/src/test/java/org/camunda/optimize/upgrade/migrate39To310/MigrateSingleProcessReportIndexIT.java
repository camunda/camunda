/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39To310;

import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateSingleProcessReportIndexIT extends AbstractUpgrade310IT {

  @Test
  public void instantPreviewReportFieldAddedSuccessfully() {
    // given
    executeBulk("steps/3.9/importIndex/39-singleprocessreport-index.json");

    // when
    performUpgrade();

    // then
    final SearchHit[] reports = getAllDocumentsOfIndex(new SingleProcessReportIndex().getIndexName());
    assertThat(reports)
      .hasSize(6)
      .allSatisfy(doc -> {
        final Object dataObject = doc.getSourceAsMap().get("data");
        assertThat(dataObject).isInstanceOf(HashMap.class);
        final HashMap<String, Object> dataAsHash = (HashMap<String, Object>) dataObject;
        assertThat(dataAsHash).containsEntry("instantPreviewReport", Boolean.FALSE);
      });
  }
}
