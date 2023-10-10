/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.camunda.optimize.service.metadata.Version.getMajorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getMinorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;
import static org.camunda.optimize.service.os.OptimizeOpensearchClientFactory.getCurrentOSVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder.getCurrentESVersion;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class DatabaseVersionChecker {
  @Getter
  @Setter
  private static List<String> supportedVersionsES = new ArrayList<>();
  @Getter
  @Setter
  private static List<String> supportedVersionsOS = new ArrayList<>();

  public final static String ELASTICSEARCH = "Elasticsearch"; //TODO will be moved to DatabaseConstants in OPT-7229
  public final static String OPENSEARCH = "Opensearch"; //TODO will be moved to DatabaseConstants in OPT-7229

  static {
    // Elasticsearch versions
    supportedVersionsES.add("7.10.0");
    supportedVersionsES.add("7.11.0");
    supportedVersionsES.add("7.12.0");
    supportedVersionsES.add("7.13.0");
    supportedVersionsES.add("7.14.0");
    supportedVersionsES.add("7.15.0");
    supportedVersionsES.add("7.16.2");
    supportedVersionsES.add("7.17.0");
    supportedVersionsES.add("8.7.0");
    supportedVersionsES.add("8.8.0");

    // Opensearch versions
    supportedVersionsOS.add("2.5.0");
    supportedVersionsOS.add("2.6.0");
    supportedVersionsOS.add("2.7.0");
    supportedVersionsOS.add("2.8.0");
    supportedVersionsOS.add("2.9.0");
  }

  private static final Comparator<String> MAJOR_COMPARATOR = Comparator.comparingInt(major -> Integer.parseInt(
    getMajorVersionFrom(major)));
  private static final Comparator<String> MINOR_COMPARATOR = Comparator.comparingInt(minor -> Integer.parseInt(
    getMinorVersionFrom(minor)));
  private static final Comparator<String> PATCH_COMPARATOR = Comparator.comparingInt(patch -> Integer.parseInt(
    getPatchVersionFrom(patch)));
  private static final Comparator<String> LATEST_VERSION_COMPARATOR =
    MAJOR_COMPARATOR.thenComparing(MINOR_COMPARATOR).thenComparing(PATCH_COMPARATOR);

  public static void checkESVersionSupport(final RestHighLevelClient esClient,
                                           final RequestOptions requestOptions) throws IOException {
    checkDatabaseVersionSupported(getCurrentESVersion(esClient, requestOptions), ELASTICSEARCH);
  }

  public static void checkOSVersionSupport(final OpenSearchClient osClient,
                                           final RequestOptions requestOptions) throws IOException {
    checkDatabaseVersionSupported(getCurrentOSVersion(osClient), OPENSEARCH);
  }

  private static void checkDatabaseVersionSupported(final String currentVersion,
                                                    final String database) {
    List<String> supportedVersions = ELASTICSEARCH.equals(database) ? supportedVersionsES : supportedVersionsOS;
    if (!isCurrentVersionSupported(currentVersion, supportedVersions)) {
      if (doesVersionNeedWarning(currentVersion, getLatestSupportedVersion(supportedVersions))) {
        log.warn(String.format("""
                  The version of %1$s you're using is not officially supported by Camunda Optimize.
                  We can not guarantee full functionality.
                  Please check the technical guide for the list of supported %1$s versions
                 """, database));

      } else {
        throw new OptimizeRuntimeException(buildUnsupportedErrorMessage(currentVersion, database, supportedVersions));
      }
    }
  }

  public static boolean doesVersionNeedWarning(String currentVersion, String latestSupportedVersion) {
    return (Integer.parseInt(getMajorVersionFrom(currentVersion))
      == Integer.parseInt(getMajorVersionFrom(latestSupportedVersion)))
      && (Integer.parseInt(getMinorVersionFrom(currentVersion)) > Integer.parseInt(getMinorVersionFrom(latestSupportedVersion)));
  }

  public static boolean isCurrentElasticsearchVersionSupported(String currentVersion) {
    return isCurrentVersionSupported(currentVersion, supportedVersionsES);
  }

  public static boolean isCurrentOpensearchVersionSupported(String currentVersion) {
    return isCurrentVersionSupported(currentVersion, supportedVersionsOS);
  }

  private static boolean isCurrentVersionSupported(String currentVersion, List<String> supportedVersions) {
    String currentMajorAndMinor = getMajorAndMinor(currentVersion);
    return supportedVersions.stream().anyMatch(v -> {
      String neededVersion = stripToPlainVersion(v);
      String neededMajorAndMinor = getMajorAndMinor(neededVersion);

      return currentMajorAndMinor.equals(neededMajorAndMinor)
        && Integer.parseInt(getPatchVersionFrom(currentVersion)) >=
        Integer.parseInt(getPatchVersionFrom(neededVersion));
    });
  }

  public static String getLatestSupportedESVersion() {
    return getLatestSupportedVersion(supportedVersionsES);
  }

  public static String getLatestSupportedOSVersion() {
    return getLatestSupportedVersion(supportedVersionsOS);
  }

  private static String getLatestSupportedVersion(List<String> supportedVersions) {
    return supportedVersions.stream()
      .max(LATEST_VERSION_COMPARATOR)
      .orElseThrow(() -> new IllegalStateException("No supported versions found"));
  }

  private static String buildUnsupportedErrorMessage(String dbVersion, String database,
                                                     List<String> supportedVersions ) {
    StringBuilder message = new StringBuilder(database + " version is not supported by Optimize.\n");

    message.append("Current version of Optimize supports the following ").append(database).append(" versions:\n");
    for (String version : supportedVersions) {
      message.append(version).append("+\n");
    }

    message.append("Your current ").append(database).append(" version is: ").append(dbVersion);
    return message.toString();
  }

}
