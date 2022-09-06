/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39preview1to39;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class DeleteProcessGoalsIndexIT extends AbstractUpgrade39preview1IT {

  @SneakyThrows
  @Test
  public void goalProcessIndexGetsDeleted() {
    // given
    executeBulk("steps/3.9preview1/39-process-goal-index-data.json");
    assertThat(prefixAwareClient.exists(new GetIndexRequest("process-goals"))).isTrue();

    // when
    performUpgrade();

    // then
    assertThat(prefixAwareClient.exists(new GetIndexRequest("process-goals"))).isFalse();
  }

}
