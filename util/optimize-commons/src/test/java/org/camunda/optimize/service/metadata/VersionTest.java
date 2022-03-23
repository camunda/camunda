/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
