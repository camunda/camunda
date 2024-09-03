/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class DeleteEventIndicesAndTemplates86IT extends AbstractUpgrade86IT {

  @SneakyThrows
  @Test
  public void allEventIndicesAndTemplatesAreRemoved() {
    // given
    final List<String> eventIndexListPreUpgrade =
        prefixAwareClient.getAllIndexNames().stream()
            .filter(indexName -> indexName.contains("event"))
            .toList();
    assertThat(eventIndexListPreUpgrade).hasSize(6);
    assertThat(prefixAwareClient.templateExists("optimize-event_v4")).isTrue();

    // when
    performUpgrade();

    // then
    final List<String> eventIndexListPostUpgrade =
        prefixAwareClient.getAllIndexNames().stream()
            .filter(indexName -> indexName.contains("event"))
            .toList();
    assertThat(eventIndexListPostUpgrade).isEmpty();
    assertThat(prefixAwareClient.templateExists("optimize-event_v4")).isFalse();
  }
}
