/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin;

import org.apache.commons.io.IOUtils;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import static org.camunda.optimize.plugin.PluginVersionChecker.OPTIMIZE_VERSION_FILE_NAME;
import static org.camunda.optimize.plugin.PluginVersionChecker.OPTIMIZE_VERSION_KEY;
import static org.camunda.optimize.plugin.PluginVersionChecker.buildMissingPluginVersionMessage;
import static org.camunda.optimize.plugin.PluginVersionChecker.buildUnsupportedPluginVersionMessage;
import static org.camunda.optimize.plugin.PluginVersionChecker.validatePluginVersion;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PluginVersionCheckerTest {

  @Rule
  public ExpectedException expectedExceptionRule = ExpectedException.none();

  @Mock
  PluginClassLoader classLoader;


  @Test
  public void validatePluginVersion_validVersion() throws IOException {

    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
      .thenReturn(IOUtils.toInputStream(
        OPTIMIZE_VERSION_KEY + "=" + Version.VERSION,
        "UTF-8"
      ));

    // when
    validatePluginVersion(classLoader);

    // then no exception should be thrown
  }

  @Test
  public void validatePluginVersion_validSnapshotVersion() throws IOException {

    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
      .thenReturn(IOUtils.toInputStream(
        OPTIMIZE_VERSION_KEY + "=" + Version.RAW_VERSION,
        "UTF-8"
      ));

    // when
    validatePluginVersion(classLoader);

    // then no exception should be thrown
  }

  @Test
  public void validatePluginVersion_missingVersion() throws IOException {

    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
      .thenReturn(null);

    // then
    expectedExceptionRule.expect(OptimizeRuntimeException.class);
    expectedExceptionRule.expectMessage(buildMissingPluginVersionMessage(Version.VERSION));

    // when
    validatePluginVersion(classLoader);
  }

  @Test
  public void validatePluginVersion_invalidVersionString() throws IOException {

    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
      .thenReturn(IOUtils.toInputStream(
        OPTIMIZE_VERSION_KEY + "=" + "nope_definitely_not_valid",
        "UTF-8"
      ));

    // then
    expectedExceptionRule.expect(OptimizeRuntimeException.class);
    expectedExceptionRule.expectMessage(buildUnsupportedPluginVersionMessage(
      "nope_definitely_not_valid",
      Version.VERSION
    ));

    // when
    validatePluginVersion(classLoader);
  }

  @Test
  public void validatePluginVersion_unexpectedVersionFileEntry() throws IOException {

    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
      .thenReturn(IOUtils.toInputStream(
        "hello.world" + "=" + "how.are.you",
        "UTF-8"
      ));

    // then
    expectedExceptionRule.expect(OptimizeRuntimeException.class);
    expectedExceptionRule.expectMessage(buildMissingPluginVersionMessage(Version.VERSION));

    // when
    validatePluginVersion(classLoader);
  }

  @Test(expected = IllegalArgumentException.class)
  public void validatePluginVersion_nullParameter() {
    validatePluginVersion(null);
  }
}