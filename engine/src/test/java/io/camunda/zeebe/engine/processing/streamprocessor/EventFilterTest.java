/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.streamprocessor.EventFilter;
import org.junit.Test;

public final class EventFilterTest {

  @Test
  public void testConjunction() {
    // given
    final EventFilter acceptFilter = (e) -> true;
    final EventFilter rejectFilter = (e) -> false;

    final LoggedEvent event = mock(LoggedEvent.class);

    // when/then
    assertThat(acceptFilter.and(acceptFilter).applies(event)).isTrue();
    assertThat(acceptFilter.and(rejectFilter).applies(event)).isFalse();
    assertThat(rejectFilter.and(rejectFilter).applies(event)).isFalse();
  }
}
