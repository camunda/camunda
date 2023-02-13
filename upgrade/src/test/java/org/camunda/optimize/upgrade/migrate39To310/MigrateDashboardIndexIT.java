/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39To310;

import org.camunda.optimize.upgrade.migrate39To310.indices.DashboardIndexV6;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateDashboardIndexIT extends AbstractUpgrade310IT {

  @Test
  public void addIsInstantPreviewInformation() {
    // given
    executeBulk("steps/3.9/importIndex/39-dashboard-index.json");

    // when
    performUpgrade();

    // then
    final SearchHit[] dashboards = getAllDocumentsOfIndex(new DashboardIndexV6().getIndexName());
    assertThat(dashboards)
      .hasSize(2)
      .allSatisfy(doc -> assertThat(doc.getSourceAsMap())
      .containsEntry("instantPreviewDashboard", Boolean.FALSE));
  }
}
