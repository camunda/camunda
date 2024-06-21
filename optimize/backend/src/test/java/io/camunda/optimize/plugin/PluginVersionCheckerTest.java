/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin;

import static io.camunda.optimize.plugin.PluginVersionChecker.OPTIMIZE_VERSION_FILE_NAME;
import static io.camunda.optimize.plugin.PluginVersionChecker.OPTIMIZE_VERSION_KEY;
import static io.camunda.optimize.plugin.PluginVersionChecker.buildMissingPluginVersionMessage;
import static io.camunda.optimize.plugin.PluginVersionChecker.buildUnsupportedPluginVersionMessage;
import static io.camunda.optimize.plugin.PluginVersionChecker.validatePluginVersion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.metadata.Version;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PluginVersionCheckerTest {

  @Mock
  PluginClassLoader classLoader;

  @Test
  public void validatePluginVersion_validVersion() throws IOException {
    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
        .thenReturn(IOUtils.toInputStream(OPTIMIZE_VERSION_KEY + "=" + Version.VERSION, "UTF-8"));

    // then
    assertDoesNotThrow(() -> validatePluginVersion(classLoader));
  }

  @Test
  public void validatePluginVersion_validSnapshotVersion() throws IOException {
    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
        .thenReturn(
            IOUtils.toInputStream(OPTIMIZE_VERSION_KEY + "=" + Version.RAW_VERSION, "UTF-8"));

    // then
    assertDoesNotThrow(() -> validatePluginVersion(classLoader));
  }

  @Test
  public void validatePluginVersion_missingVersion() throws IOException {
    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME)).thenReturn(null);

    // then
    final OptimizeRuntimeException exception =
        assertThrows(OptimizeRuntimeException.class, () -> validatePluginVersion(classLoader));
    assertThat(exception.getMessage()).isEqualTo(buildMissingPluginVersionMessage(Version.VERSION));
  }

  @Test
  public void validatePluginVersion_invalidVersionString() throws IOException {
    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
        .thenReturn(
            IOUtils.toInputStream(
                OPTIMIZE_VERSION_KEY + "=" + "nope_definitely_not_valid", "UTF-8"));

    // then
    final OptimizeRuntimeException exception =
        assertThrows(OptimizeRuntimeException.class, () -> validatePluginVersion(classLoader));
    assertThat(exception.getMessage())
        .isEqualTo(
            buildUnsupportedPluginVersionMessage("nope_definitely_not_valid", Version.VERSION));
  }

  @Test
  public void validatePluginVersion_unexpectedVersionFileEntry() throws IOException {
    // given
    when(classLoader.getPluginResourceAsStream(OPTIMIZE_VERSION_FILE_NAME))
        .thenReturn(IOUtils.toInputStream("hello.world" + "=" + "how.are.you", "UTF-8"));

    // then
    final OptimizeRuntimeException exception =
        assertThrows(OptimizeRuntimeException.class, () -> validatePluginVersion(classLoader));
    assertThat(exception.getMessage()).isEqualTo(buildMissingPluginVersionMessage(Version.VERSION));
  }

  @Test
  public void validatePluginVersion_nullParameter() {
    assertThrows(IllegalArgumentException.class, () -> validatePluginVersion(null));
  }
}
