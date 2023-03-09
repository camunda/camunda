/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39To310;

import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.upgrade.migrate39To310.indices.DashboardIndexV6;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateDashboardIndexIT extends AbstractUpgrade310IT {

  @Test
  public void addIsInstantPreviewInformationAndDashboardTileType() {
    // given
    executeBulk("steps/3.9/importIndex/39-dashboard-index.json");
    assertThat(getAllDocumentsOfIndex(new DashboardIndexV6().getIndexName()))
      .hasSize(3)
      .allSatisfy(doc -> {
        final Map<String, Object> sourceAsMap = doc.getSourceAsMap();
        assertThat(sourceAsMap)
          .doesNotContainKey("instantPreviewDashboard")
          .containsKey("reports");
      });

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(new DashboardIndexV6().getIndexName()))
      .hasSize(3)
      .allSatisfy(doc -> {
        final Map<String, Object> sourceAsMap = doc.getSourceAsMap();
        assertThat(sourceAsMap)
          .containsEntry("instantPreviewDashboard", Boolean.FALSE)
          .doesNotContainKey("reports");
        @SuppressWarnings({SuppressionConstants.UNCHECKED_CAST, SuppressionConstants.RAW_TYPES})
        List<Map> reportMaps = (List<Map>) sourceAsMap.get(DashboardIndex.TILES);
        reportMaps.forEach(tile -> {
          final String reportId = (String) tile.get(DashboardIndex.REPORT_ID);
          if (!StringUtils.isEmpty(reportId)) {
            assertThat(tile.get(DashboardIndex.REPORT_TILE_TYPE).equals(DashboardTileType.OPTIMIZE_REPORT.getId())).isTrue();
          } else {
            assertThat(tile.get(DashboardIndex.REPORT_TILE_TYPE).equals(DashboardTileType.EXTERNAL_URL.getId())).isTrue();
          }
        });
      });
  }
}
