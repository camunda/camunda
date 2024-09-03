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

public class DeleteImportIndexIndex86IT extends AbstractUpgrade86IT {

  @Test
  @SneakyThrows
  public void deleteOnboardingStateIndex() {
    // given
    assertThat(getImportIndexIndexNames()).hasSize(1);

    // when
    performUpgrade();

    // then
    assertThat(getImportIndexIndexNames()).isEmpty();
  }

  @SneakyThrows
  private List<String> getImportIndexIndexNames() {
    return prefixAwareClient.getAllIndexNames().stream()
        .filter(indexName -> indexName.contains("import-index"))
        .toList();
  }
}
