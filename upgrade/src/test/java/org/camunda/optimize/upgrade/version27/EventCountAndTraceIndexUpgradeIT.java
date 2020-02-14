/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version27;

import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom27To30;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class EventCountAndTraceIndexUpgradeIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.7.0";

  protected static final EventTraceStateIndexV1 EVENT_TRACE_STATE_INDEX_V1 = new EventTraceStateIndexV1();

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      EVENT_INDEX_V1,
      EVENT_SEQUENCE_COUNT_INDEX_V1,
      EVENT_PROCESS_MAPPING_INDEX_V1,
      EVENT_PROCESS_PUBLISH_STATE_INDEX_V1,
      EVENT_TRACE_STATE_INDEX_V1
    ));

    setMetadataIndexVersion(FROM_VERSION);
  }

  @SneakyThrows
  @Test
  public void oldIndexesAreGoneAfterUpgrade() {
    // given
    assertThat(doesIndexExist(EVENT_SEQUENCE_COUNT_INDEX_V1.getIndexName())).isTrue();
    assertThat(doesIndexExist(EVENT_TRACE_STATE_INDEX_V1.getIndexName())).isTrue();
    final UpgradePlan upgradePlan = new UpgradeFrom27To30().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(doesIndexExist(EVENT_SEQUENCE_COUNT_INDEX_V1.getIndexName())).isFalse();
    assertThat(doesIndexExist(EVENT_TRACE_STATE_INDEX_V1.getIndexName())).isFalse();
  }

  private boolean doesIndexExist(final String aliasName) throws IOException {
    return prefixAwareClient.exists(new GetIndexRequest(aliasName + "*"), RequestOptions.DEFAULT);
  }

}
