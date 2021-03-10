/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class VersionUtilTest {

  @Test
  public void shouldGetTheVersionFromFile() {
    final String version = VersionUtil.getVersion();
    assertThat(version).isNotNull();
  }

  @Test
  public void shouldGetVersionLowerCase() {
    final String version = VersionUtil.getVersion();
    final String versionLowerCase = VersionUtil.getVersionLowerCase();
    assertThat(version.toLowerCase()).isEqualTo(versionLowerCase);
  }
}
