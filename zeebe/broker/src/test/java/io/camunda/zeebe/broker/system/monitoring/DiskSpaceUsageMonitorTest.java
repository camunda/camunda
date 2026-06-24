/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.DataCfg;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

public class DiskSpaceUsageMonitorTest {

  @Test
  public void shouldThrowUncheckedIOExceptionIfFolderDoesNotExist() {
    // given
    final var dataCfg = new DataCfg();
    dataCfg.setDirectory("something-that-does-not-exist");

    // when + then
    assertThatThrownBy(() -> new DiskSpaceUsageMonitorActor(dataCfg))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessage("java.io.IOException: Folder 'something-that-does-not-exist' does not exist.");
  }
}
