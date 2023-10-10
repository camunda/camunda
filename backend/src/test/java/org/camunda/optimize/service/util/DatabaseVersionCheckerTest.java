/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.metadata.Version.getMajorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getMinorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static org.camunda.optimize.service.util.DatabaseVersionChecker.getLatestSupportedESVersion;
import static org.camunda.optimize.service.util.DatabaseVersionChecker.getLatestSupportedOSVersion;
import static org.camunda.optimize.service.util.DatabaseVersionChecker.getSupportedVersionsES;
import static org.camunda.optimize.service.util.DatabaseVersionChecker.getSupportedVersionsOS;

public class DatabaseVersionCheckerTest {
  public static final List<String> SUPPORTED_VERSIONS_ES = DatabaseVersionChecker.getSupportedVersionsES();
  public static final List<String> SUPPORTED_VERSIONS_OS = DatabaseVersionChecker.getSupportedVersionsOS();

  @AfterEach
  public void resetSupportedVersions() {
    DatabaseVersionChecker.setSupportedVersionsES(SUPPORTED_VERSIONS_ES);
    DatabaseVersionChecker.setSupportedVersionsOS(SUPPORTED_VERSIONS_OS);
  }

  @ParameterizedTest
  @MethodSource("validESVersions")
  public void testValidESVersions(final String version) {
    final boolean isSupported = DatabaseVersionChecker.isCurrentElasticsearchVersionSupported(version);

    assertThat(isSupported).isTrue();
  }

  @ParameterizedTest
  @MethodSource("validOSVersions")
  public void testValidOSVersions(final String version) {
    final boolean isSupported = DatabaseVersionChecker.isCurrentOpensearchVersionSupported(version);
    assertThat(isSupported).isTrue();
  }

  @ParameterizedTest
  @MethodSource("invalidESVersions")
  public void testInvalidESVersions(final String version) {
    final boolean isSupported = DatabaseVersionChecker.isCurrentElasticsearchVersionSupported(version);
    assertThat(isSupported).isFalse();
  }

  @ParameterizedTest
  @MethodSource("invalidOSVersions")
  public void testInvalidOSVersions(final String version) {
    final boolean isSupported = DatabaseVersionChecker.isCurrentOpensearchVersionSupported(version);
    assertThat(isSupported).isFalse();
  }

  @Test
  public void testWarningESVersions() {
    // given
    String version = constructWarningVersionHigherMinor(getLatestSupportedESVersion());

    // then
    assertThat(DatabaseVersionChecker.doesVersionNeedWarning(version, getLatestSupportedESVersion())).isTrue();
  }

  @Test
  public void testWarningOSVersions() {
    // given
    String version = constructWarningVersionHigherMinor(getLatestSupportedOSVersion());

    // then
    assertThat(DatabaseVersionChecker.doesVersionNeedWarning(version, getLatestSupportedOSVersion())).isTrue();
  }

  @Test
  public void testGetLatestSupportedVersion() {
    // given
    final String expectedLatestVersion = "7.11.5";

    List<String> versionsToTest = Arrays.asList("0.0.1", "7.2.0", expectedLatestVersion, "7.11.4", "7.10.6");
    DatabaseVersionChecker.setSupportedVersionsOS(versionsToTest);
    DatabaseVersionChecker.setSupportedVersionsES(versionsToTest);

    // then
    assertThat(getLatestSupportedOSVersion()).isEqualTo(expectedLatestVersion);
    assertThat(getLatestSupportedESVersion()).isEqualTo(expectedLatestVersion);
  }

  private static Stream<String> validVersions(List<String> supportedVersions) {
    List<String> validVersionsToTest = new ArrayList<>();
    for (String supportedVersion : supportedVersions) {
      validVersionsToTest.add(supportedVersion);
      validVersionsToTest.add(constructValidVersionHigherPatch(supportedVersion));
    }
    return validVersionsToTest.stream();
  }

  private static Stream<String> validESVersions() {
    return validVersions(getSupportedVersionsES());
  }

  private static Stream<String> validOSVersions() {
    return validVersions(getSupportedVersionsOS());
  }

  private static String constructValidVersionHigherPatch(String supportedVersion) {
    final String major = getMajorVersionFrom(supportedVersion);
    final String minor = getMinorVersionFrom(supportedVersion);
    final String patch = getPatchVersionFrom(supportedVersion);
    return buildVersionFromParts(major, minor, incrementVersionPart(patch));
  }

  private static String constructInvalidVersionLowerMajor(String leastSupportedVersion) {
    final String major = getMajorVersionFrom(leastSupportedVersion);
    final String minor = getMinorVersionFrom(leastSupportedVersion);
    final String patch = getPatchVersionFrom(leastSupportedVersion);
    return buildVersionFromParts(decrementVersionPart(major), minor, patch);
  }

  private static String constructInvalidVersionHigherMajor(String latestSupportedVersion) {
    final String major = getMajorVersionFrom(latestSupportedVersion);
    final String minor = getMinorVersionFrom(latestSupportedVersion);
    final String patch = getPatchVersionFrom(latestSupportedVersion);
    return buildVersionFromParts(incrementVersionPart(major), minor, patch);
  }

  private static String constructWarningVersionHigherMinor(String latestSupportedVersion) {
    final String major = getMajorVersionFrom(latestSupportedVersion);
    final String minor = getMinorVersionFrom(latestSupportedVersion);
    final String patch = getPatchVersionFrom(latestSupportedVersion);
    return buildVersionFromParts(major, incrementVersionPart(minor), patch);
  }

  private static String constructInvalidVersionLowerPatch(String patchedVersion) {
    final String major = getMajorVersionFrom(patchedVersion);
    final String minor = getMinorVersionFrom(patchedVersion);
    final String patch = getPatchVersionFrom(patchedVersion);
    return buildVersionFromParts(major, minor, decrementVersionPart(patch));
  }

  private static Stream<String> invalidESVersions() {
    return invalidVersions(getSupportedVersionsES(), getLatestSupportedESVersion());
  }

  private static Stream<String> invalidOSVersions() {
    return invalidVersions(getSupportedVersionsOS(), getLatestSupportedOSVersion());
  }

  private static Stream<String> invalidVersions(List<String> supportedVersions, String latestSupportedVersion) {
    List<String> invalidVersions = new ArrayList<>();

    if (findPatchedVersionIfPresent(supportedVersions).isPresent()) {
      invalidVersions.add(constructInvalidVersionLowerPatch(findPatchedVersionIfPresent(supportedVersions).get()));
    }
    invalidVersions.add(constructInvalidVersionHigherMajor(latestSupportedVersion));
    invalidVersions.add(constructInvalidVersionLowerMajor(getLeastSupportedVersion(supportedVersions)));

    return invalidVersions.stream();
  }

  private static String buildVersionFromParts(final String major, final String minor, final String patch) {
    return String.join(".", major, minor, patch);
  }

  private static String decrementVersionPart(final String versionPart) {
    return String.valueOf(Long.parseLong(versionPart) - 1);
  }

  private static String incrementVersionPart(final String versionPart) {
    return String.valueOf(Long.parseLong(versionPart) + 1);
  }

  private static Optional<String> findPatchedVersionIfPresent(List<String> supportedVersions) {
      return supportedVersions.stream().filter(v -> Integer.parseInt(getPatchVersionFrom(v)) > 0).findFirst();
  }

  private static String getLeastSupportedVersion(List<String> supportedVersions) {
    return supportedVersions.stream().min(Comparator.naturalOrder()).get();
  }

}
