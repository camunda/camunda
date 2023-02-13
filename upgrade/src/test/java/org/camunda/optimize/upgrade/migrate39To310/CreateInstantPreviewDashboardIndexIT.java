/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39To310;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateInstantPreviewDashboardIndexIT extends AbstractUpgrade310IT {

  public static final String INSTANT_DASHBOARD_INDEX_NAME = "instant-dashboard";
  @Test
  public void isInstantPreviewDashboardIndexPresent() {
    // when
    performUpgrade();

    // then
    assertThat(indexExists(getVersionedIndexName(INSTANT_DASHBOARD_INDEX_NAME, 1))).isTrue();
  }

  private boolean indexExists(final String versionedIndexName) {
    try {
      GetIndexRequest request = new GetIndexRequest(versionedIndexName);
      return prefixAwareClient.getHighLevelClient().indices().exists(request, prefixAwareClient.requestOptions());
    } catch (IOException e) {
      return false;
    }
  }
}
