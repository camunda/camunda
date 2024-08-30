/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.util.DatabaseVersionChecker.MIN_OS_SUPPORTED_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;

@ExtendWith(MockitoExtension.class)
public class OpensearchDatabaseVersionCheckerTest {

  public static final Semver MIN_SUPPORTED_OS_VERSION_SEMVER = new Semver(MIN_OS_SUPPORTED_VERSION);
  public static final Integer PATCH_PART = MIN_SUPPORTED_OS_VERSION_SEMVER.getPatch();
  public static final Integer MINOR_PART = MIN_SUPPORTED_OS_VERSION_SEMVER.getMinor();
  public static final Integer MAJOR_PART = MIN_SUPPORTED_OS_VERSION_SEMVER.getMajor();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  OpenSearchClient openSearchClient;

  @ParameterizedTest
  @MethodSource("supportedOSVersions")
  public void shouldNotThrowExceptionWhenUsingVersionOfOSEqualOrGreaterThanMinimumSupported(
      final String currentVersion) throws IOException {
    // given
    when(openSearchClient.info().version().number()).thenReturn(currentVersion);

    // then
    assertDoesNotThrow(() -> DatabaseVersionChecker.checkOSVersionSupport(openSearchClient));
  }

  @ParameterizedTest
  @MethodSource("unsupportedOSVersions")
  public void shouldThrowExceptionWhenUsingVersionOfOSLowerThanMinimumSupported(
      final String currentVersion) throws IOException {
    // given
    when(openSearchClient.info().version().number()).thenReturn(currentVersion);

    // then
    final OptimizeRuntimeException exception =
        assertThrows(
            OptimizeRuntimeException.class,
            () -> DatabaseVersionChecker.checkOSVersionSupport(openSearchClient));
    assertThat(exception.getMessage())
        .isEqualTo(currentVersion + " version of Database is not supported by Optimize");
  }

  private static Stream<String> unsupportedOSVersions() {
    final List<String> versionsToTest = new ArrayList<>();
    versionsToTest.add(buildVersionString(MAJOR_PART - 1, MINOR_PART, PATCH_PART));
    versionsToTest.add(buildVersionString(MAJOR_PART - 1, MINOR_PART + 1, PATCH_PART));
    if (MINOR_PART != 0) {
      versionsToTest.add(buildVersionString(MAJOR_PART, MINOR_PART - 1, PATCH_PART));
      versionsToTest.add(buildVersionString(MAJOR_PART, MINOR_PART - 1, PATCH_PART + 1));
    }
    if (PATCH_PART != 0) {
      versionsToTest.add(buildVersionString(MAJOR_PART, MINOR_PART - 1, PATCH_PART - 1));
    }
    return versionsToTest.stream();
  }

  private static Stream<String> supportedOSVersions() {
    return Stream.of(
        buildVersionString(MAJOR_PART, MINOR_PART, PATCH_PART),
        buildVersionString(MAJOR_PART + 1, MINOR_PART, PATCH_PART),
        buildVersionString(MAJOR_PART, MINOR_PART + 1, PATCH_PART),
        buildVersionString(MAJOR_PART, MINOR_PART, PATCH_PART + 1),
        buildVersionString(MAJOR_PART + 1, MINOR_PART + 1, PATCH_PART + 1));
  }

  private static String buildVersionString(
      final Integer major, final Integer minor, final Integer patch) {
    return major + "." + minor + "." + patch;
  }
}
