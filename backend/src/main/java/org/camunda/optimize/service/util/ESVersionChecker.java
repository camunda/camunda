/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.client.Request;
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

@UtilityClass
@Slf4j
public class ESVersionChecker {
  @Getter
  private static List<String> supportedVersions = new ArrayList<>();

  static {
    supportedVersions.add("7.0.0");
    supportedVersions.add("7.1.0");
    supportedVersions.add("7.2.0");
    supportedVersions.add("7.3.0");
    supportedVersions.add("7.4.0");
    supportedVersions.add("7.5.0");
    supportedVersions.add("7.6.0");
  }

  public static void checkESVersionSupport(RestHighLevelClient esClient) throws IOException {
    String currentVersion = getCurrentESVersion(esClient);

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
    return getMajorVersionFrom(latestSupportedES).equals(getMajorVersionFrom(currentVersion)) &&
      Integer.parseInt(getMinorVersionFrom(latestSupportedES)) < Integer.parseInt(getMinorVersionFrom(currentVersion));
  }

  private static String getCurrentESVersion(RestHighLevelClient esClient) throws IOException {
    String responseJson = EntityUtils.toString(esClient.getLowLevelClient()
                                                 .performRequest(new Request("GET", "/"))
                                                 .getEntity());
    ObjectNode node = new ObjectMapper().readValue(responseJson, ObjectNode.class);

    return node.get("version").get("number").toString().replaceAll("\"", "");
  }

  public static boolean isCurrentVersionSupported(String currentVersion) {
    String currentMajorAndMinor = getMajorAndMinor(currentVersion);
    return supportedVersions.stream().anyMatch(v -> {
      String neededVersion = stripToPlainVersion(v);
      String neededMajorAndMinor = getMajorAndMinor(neededVersion);

      return currentMajorAndMinor.equals(neededMajorAndMinor)
        && Integer.parseInt(getPatchVersionFrom(currentVersion)) >= Integer.parseInt(getPatchVersionFrom
                                                                                       (neededVersion));
    });
  }

  public static String getLatestSupportedESVersion() {
    return supportedVersions.stream().max(Comparator.naturalOrder()).get();
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
