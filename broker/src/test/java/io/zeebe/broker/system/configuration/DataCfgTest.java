/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.storage.StorageLevel;
import org.junit.Test;

public class DataCfgTest {

  @Test
  public void shouldGetMappedAtomixStorageLevel() {
    // given
    final var sutDataCfg = new DataCfg();

    // when
    sutDataCfg.setUseMmap(true);

    // then
    final var actual = sutDataCfg.getAtomixStorageLevel();
    assertThat(actual).isEqualTo(StorageLevel.MAPPED);
  }

  @Test
  public void shouldGetDiskAtomixStorageLevel() {
    // given
    final var sutDataCfg = new DataCfg();

    // when
    sutDataCfg.setUseMmap(false);

    // then
    final var actual = sutDataCfg.getAtomixStorageLevel();
    assertThat(actual).isEqualTo(StorageLevel.DISK);
  }

  @Test
  public void shouldGetDiskAtomixStorageLevelAsDefault() {
    // given
    final var sutDataCfg = new DataCfg();

    // then
    final var actual = sutDataCfg.getAtomixStorageLevel();
    assertThat(actual).isEqualTo(StorageLevel.DISK);
  }
}
