/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin;

import org.apache.commons.io.IOUtils;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.plugin.PluginVersionChecker.OPTIMIZE_VERSION_FILE_NAME;
import static org.camunda.optimize.plugin.PluginVersionChecker.OPTIMIZE_VERSION_KEY;
import static org.camunda.optimize.plugin.PluginVersionChecker.buildMissingPluginVersionMessage;
import static org.camunda.optimize.plugin.PluginVersionChecker.buildUnsupportedPluginVersionMessage;
import static org.camunda.optimize.plugin.PluginVersionChecker.validatePluginVersion;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PluginVersionCheckerTest {

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

    // then
    assertDoesNotThrow(() -> validatePluginVersion(classLoader));
  }

  @Test
  public void validatePluginVersion_validSnapshotVersion() throws IOException {
    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
      .thenReturn(IOUtils.toInputStream(
        OPTIMIZE_VERSION_KEY + "=" + Version.RAW_VERSION,
        "UTF-8"
      ));

    // then
    assertDoesNotThrow(() -> validatePluginVersion(classLoader));
  }

  @Test
  public void validatePluginVersion_missingVersion() throws IOException {
    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
      .thenReturn(null);

    // then
    OptimizeRuntimeException exception = assertThrows(
      OptimizeRuntimeException.class,
      () -> validatePluginVersion(classLoader)
    );
    assertThat(exception.getMessage()).isEqualTo(buildMissingPluginVersionMessage(Version.VERSION));
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
    OptimizeRuntimeException exception = assertThrows(
      OptimizeRuntimeException.class,
      () -> validatePluginVersion(classLoader)
    );
    assertThat(exception.getMessage()).isEqualTo(buildUnsupportedPluginVersionMessage(
      "nope_definitely_not_valid", Version.VERSION));
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
    OptimizeRuntimeException exception = assertThrows(
      OptimizeRuntimeException.class,
      () -> validatePluginVersion(classLoader)
    );
    assertThat(exception.getMessage()).isEqualTo(buildMissingPluginVersionMessage(Version.VERSION));
  }

  @Test
  public void validatePluginVersion_nullParameter() {
    assertThrows(IllegalArgumentException.class, () -> validatePluginVersion(null));
  }
}