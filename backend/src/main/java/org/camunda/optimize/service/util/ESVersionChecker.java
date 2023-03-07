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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.camunda.optimize.service.metadata.Version.getMajorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getMinorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder.getCurrentESVersion;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ESVersionChecker {
  @Getter
  @Setter
  private static List<String> supportedVersions = new ArrayList<>();

  static {
    supportedVersions.add("7.10.0");
    supportedVersions.add("7.11.0");
    supportedVersions.add("7.12.0");
    supportedVersions.add("7.13.0");
    supportedVersions.add("7.14.0");
    supportedVersions.add("7.15.0");
    supportedVersions.add("7.16.2");
    supportedVersions.add("7.17.0");
    supportedVersions.add("8.5.0");
    supportedVersions.add("8.6.0");
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
    String currentVersion = getCurrentESVersion(esClient, requestOptions);

    if (!isCurrentVersionSupported(currentVersion)) {
      String latestSupportedES = getLatestSupportedESVersion();
      if (doesVersionNeedWarning(currentVersion, latestSupportedES)) {
        log.warn("The version of Elasticsearch you're using is not officially supported by Camunda Optimize." +
                   "\nWe can not guarantee full functionality." +
                   "\nPlease check the technical guide for the list of supported Elasticsearch versions");
      } else {
        throw new OptimizeRuntimeException(buildUnsupportedESErrorMessage(currentVersion));
      }
    }
  }

  public static boolean doesVersionNeedWarning(String currentVersion, String latestSupportedES) {
    if (Integer.parseInt(getMajorVersionFrom(currentVersion)) > Integer.parseInt(getMajorVersionFrom(latestSupportedES))) {
      return true;
    } else {
      return Integer.parseInt(getMinorVersionFrom(currentVersion)) > Integer.parseInt(getMinorVersionFrom(latestSupportedES));
    }
  }

  public static boolean isCurrentVersionSupported(String currentVersion) {
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
    return supportedVersions.stream()
      .max(LATEST_VERSION_COMPARATOR)
      .orElseThrow(() -> new IllegalStateException("No supported versions found"));
  }

  private static String buildUnsupportedESErrorMessage(String esVersion) {
    StringBuilder message = new StringBuilder("Elasticsearch version is not supported by Optimize.\n");

    message.append("Current version of Optimize supports following Elasticsearch versions:\n");
    for (String version : supportedVersions) {
      message.append(version).append("+\n");
    }

    message.append("Your current Elasticsearch version is: ").append(esVersion);
    return message.toString();
  }

}
