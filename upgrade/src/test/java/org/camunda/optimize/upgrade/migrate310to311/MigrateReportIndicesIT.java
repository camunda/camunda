/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateReportIndicesIT extends AbstractUpgrade311IT {

  @Test
  public void addReportDescriptionField() {
    // given
    executeBulk("steps/3.10/reports/310-report-index-data.json");

    // when
    performUpgrade();

    // then
    assertNewDescriptionFieldExists(getAllDocumentsOfIndex(new SingleProcessReportIndex().getIndexName()));
    assertNewDescriptionFieldExists(getAllDocumentsOfIndex(new SingleDecisionReportIndex().getIndexName()));
    assertNewDescriptionFieldExists(getAllDocumentsOfIndex(new CombinedReportIndex().getIndexName()));
  }

  private void assertNewDescriptionFieldExists(final SearchHit[] allDocumentsOfIndex) {
    assertThat(allDocumentsOfIndex)
      .singleElement()
      .satisfies(doc -> {
        final Map<String, Object> sourceAsMap = doc.getSourceAsMap();
        assertThat(sourceAsMap)
          .containsEntry("description", null);
      });
  }

}
