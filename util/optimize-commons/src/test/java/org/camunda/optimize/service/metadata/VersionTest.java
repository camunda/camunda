/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.metadata;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class VersionTest {
  @Test
  public void testStripSnapshot() {
    assertThat(Version.stripToPlainVersion("2.2.0-SNAPSHOT"), is("2.2.0"));
  }

  @Test
  public void testStripAlpha() {
    assertThat(Version.stripToPlainVersion("2.2.0-alpha3"), is("2.2.0"));
  }

  @Test
  public void testStripAlreadyCleanVersion() {
    assertThat(Version.stripToPlainVersion("2.2.0"), is("2.2.0"));
  }
}
