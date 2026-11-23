/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeIncidentImportIndexHandler;
import org.junit.jupiter.api.Test;

class PositionBasedImportIndexHandlerIdleTest {

  private final PositionBasedImportIndexHandler underTest =
      new ZeebeIncidentImportIndexHandler(new ZeebeDataSourceDto());

  @Test
  void shouldBeIdleSinceTheStartOfAnEmptyFetch() {
    // when
    underTest.getNextPage();
    underTest.recordFetchResult(true);

    // then
    assertThat(underTest.getIdleSince()).contains(underTest.getLastPageRequestedAt());
  }

  @Test
  void shouldNotBeIdleAfterAFetchWithRecords() {
    // when
    underTest.getNextPage();
    underTest.recordFetchResult(false);

    // then
    assertThat(underTest.getIdleSince()).isEmpty();
  }

  @Test
  void shouldNotBeIdleWhileAFetchedPageIsStillBeingWritten() {
    // given a page fetched up to position 10 that is not persisted yet
    underTest.updatePendingLastEntityPositionAndSequence(10, 0);

    // when the next fetch comes back empty
    underTest.getNextPage();
    underTest.recordFetchResult(true);

    // then
    assertThat(underTest.getIdleSince()).isEmpty();

    // and once the page is written
    underTest.updateLastPersistedEntityPositionAndSequence(10, 0);
    assertThat(underTest.getIdleSince()).isPresent();
  }
}
