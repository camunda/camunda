/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class VersionTest {

  @Test
  public void testStripSnapshot() {
    assertThat(Version.stripToPlainVersion("2.2.0-SNAPSHOT")).isEqualTo("2.2.0");
  }

  @Test
  public void testStripAlpha() {
    assertThat(Version.stripToPlainVersion("2.2.0-alpha3")).isEqualTo("2.2.0");
  }

  @Test
  public void testStripAlreadyCleanVersion() {
    assertThat(Version.stripToPlainVersion("2.2.0")).isEqualTo("2.2.0");
  }
}
