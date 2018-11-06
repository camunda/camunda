package org.camunda.optimize.service.metadata;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

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
