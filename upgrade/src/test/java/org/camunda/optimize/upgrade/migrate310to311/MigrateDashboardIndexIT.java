/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateDashboardIndexIT extends AbstractUpgrade311IT {

  @Test
  public void addDashboardDescriptionField() {
    // given
    executeBulk("steps/3.10/dashboards/310-dashboard-index-data.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(new DashboardIndex().getIndexName()))
      .hasSize(2)
      .allSatisfy(doc -> {
        final Map<String, Object> sourceAsMap = doc.getSourceAsMap();
        assertThat(sourceAsMap)
          .containsEntry("description", null);
      });
  }

}
