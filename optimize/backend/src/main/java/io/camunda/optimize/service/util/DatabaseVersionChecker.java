/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static io.camunda.optimize.service.metadata.Version.getMajorVersionFrom;
import static io.camunda.optimize.service.metadata.Version.getMinorVersionFrom;
import static io.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static io.camunda.optimize.service.metadata.Version.stripToPlainVersion;
import static io.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder.getCurrentESVersion;
import static io.camunda.optimize.upgrade.os.OpenSearchClientBuilder.getCurrentOSVersion;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.opensearch.client.opensearch.OpenSearchClient;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class DatabaseVersionChecker {

  @Getter
  private static final EnumMap<Database, List<String>> databaseSupportedVersionsMap =
      new EnumMap<>(Database.class);

  private static final Comparator<String> MAJOR_COMPARATOR =
      Comparator.comparingInt(major -> Integer.parseInt(getMajorVersionFrom(major)));
  private static final Comparator<String> MINOR_COMPARATOR =
      Comparator.comparingInt(minor -> Integer.parseInt(getMinorVersionFrom(minor)));
  private static final Comparator<String> PATCH_COMPARATOR =
      Comparator.comparingInt(patch -> Integer.parseInt(getPatchVersionFrom(patch)));
  private static final Comparator<String> LATEST_VERSION_COMPARATOR =
      MAJOR_COMPARATOR.thenComparing(MINOR_COMPARATOR).thenComparing(PATCH_COMPARATOR);

  static {
    databaseSupportedVersionsMap.put(
        Database.ELASTICSEARCH,
        List.of(
            "7.10.0", "7.11.0", "7.12.0", "7.13.0", "7.14.0", "7.15.0", "7.16.2", "7.17.0",
            "8.9.0"));
    databaseSupportedVersionsMap.put(
        Database.OPENSEARCH,
        List.of("2.5.0", "2.6.0", "2.7.0", "2.8.0", "2.9.0", "2.10.0", "2.11.0", "2.12.0"));
  }

  public static void checkESVersionSupport(
      final RestHighLevelClient esClient, final RequestOptions requestOptions) throws IOException {
    checkDatabaseVersionSupported(
        getCurrentESVersion(esClient, requestOptions), Database.ELASTICSEARCH);
  }

  public static void checkOSVersionSupport(
      final OpenSearchClient osClient, final RequestOptions requestOptions) throws IOException {
    checkDatabaseVersionSupported(getCurrentOSVersion(osClient), Database.OPENSEARCH);
  }

  private static void checkDatabaseVersionSupported(
      final String currentVersion, final Database database) {
    final List<String> supportedVersions = databaseSupportedVersionsMap.get(database);
    if (!isCurrentVersionSupported(currentVersion, supportedVersions)) {
      if (doesVersionNeedWarning(currentVersion, getLatestSupportedVersion(supportedVersions))) {
        log.warn(
            String.format(
                """
                     The version of %1$s you're using is not officially supported by Camunda Optimize.
                     We can not guarantee full functionality.
                     Please check the technical guide for the list of supported %1$s versions
                    """,
                database));

      } else {
        throw new OptimizeRuntimeException(
            buildUnsupportedErrorMessage(currentVersion, database, supportedVersions));
      }
    }
  }

  public static boolean doesVersionNeedWarning(
      final String currentVersion, final String latestSupportedVersion) {
    try {
      return (Integer.parseInt(getMajorVersionFrom(currentVersion))
              == Integer.parseInt(getMajorVersionFrom(latestSupportedVersion)))
          && (Integer.parseInt(getMinorVersionFrom(currentVersion))
              > Integer.parseInt(getMinorVersionFrom(latestSupportedVersion)));
    } catch (final NumberFormatException exception) {
      throw new OptimizeConfigurationException(
          String.format(
              "Error parsing current (%s) and latest supported (%s) versions for Database version checking",
              currentVersion, latestSupportedVersion));
    }
  }

  public static boolean isCurrentElasticsearchVersionSupported(final String currentVersion) {
    return isCurrentVersionSupported(
        currentVersion, databaseSupportedVersionsMap.get(Database.ELASTICSEARCH));
  }

  public static boolean isCurrentOpenSearchVersionSupported(final String currentVersion) {
    return isCurrentVersionSupported(
        currentVersion, databaseSupportedVersionsMap.get(Database.OPENSEARCH));
  }

  private static boolean isCurrentVersionSupported(
      final String currentVersion, final List<String> supportedVersions) {
    final String currentMajorAndMinor = getMajorAndMinor(currentVersion);
    try {
      return supportedVersions.stream()
          .anyMatch(
              v -> {
                final String neededVersion = stripToPlainVersion(v);
                final String neededMajorAndMinor = getMajorAndMinor(neededVersion);

                return currentMajorAndMinor.equals(neededMajorAndMinor)
                    && Integer.parseInt(getPatchVersionFrom(currentVersion))
                        >= Integer.parseInt(getPatchVersionFrom(neededVersion));
              });
    } catch (final NumberFormatException exception) {
      throw new OptimizeConfigurationException("Was not able to determine Database version");
    }
  }

  public static String getLatestSupportedESVersion() {
    return getLatestSupportedVersion(databaseSupportedVersionsMap.get(Database.ELASTICSEARCH));
  }

  public static String getLatestSupportedOSVersion() {
    return getLatestSupportedVersion(databaseSupportedVersionsMap.get(Database.OPENSEARCH));
  }

  private static String getLatestSupportedVersion(final List<String> supportedVersions) {
    return supportedVersions.stream()
        .max(LATEST_VERSION_COMPARATOR)
        .orElseThrow(() -> new IllegalStateException("No supported versions found"));
  }

  private static String buildUnsupportedErrorMessage(
      final String dbVersion, final Database database, final List<String> supportedVersions) {
    final StringBuilder message =
        new StringBuilder(database + " version is not supported by Optimize.\n");

    message
        .append("Current version of Optimize supports the following ")
        .append(database)
        .append(" versions:\n");
    for (final String version : supportedVersions) {
      message.append(version).append("+\n");
    }

    message.append("Your current ").append(database).append(" version is: ").append(dbVersion);
    return message.toString();
  }

  enum Database {
    ELASTICSEARCH,
    OPENSEARCH
  }
}
