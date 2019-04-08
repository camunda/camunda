/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;

public class ESVersionChecker {
  private static List<String> supportedES = new ArrayList<>();
  private static List<String> warningES = new ArrayList<>();
  private static final Logger logger = LoggerFactory.getLogger(ESVersionChecker.class);

  static {
    supportedES.add("6.2.0");
    supportedES.add("6.3.1");
    supportedES.add("6.4.0");
    supportedES.add("6.5.0");
    warningES.add("6.6.0");
    warningES.add("6.7.0");
    warningES.add("6.8.0");
    warningES.add("6.9.0");
  }


  public static void checkESVersionSupport(RestHighLevelClient esClient) throws IOException {
    String responseJson = EntityUtils.toString(esClient.getLowLevelClient()
                                                 .performRequest(new Request("GET", "/"))
                                                 .getEntity());
    ObjectNode node = new ObjectMapper().readValue(responseJson, ObjectNode.class);

    String currentVersion = node.get("version").get("number").toString().replaceAll("\"", "");

    Optional<String> matchedVersion = findMatchedVersion(currentVersion, supportedES);

    if (!matchedVersion.isPresent()) {
      Optional<String> unsupportedVersion = findMatchedVersion(currentVersion, warningES);
      if (unsupportedVersion.isPresent()) {
        logger.warn("The version of Elasticsearch you're using is not officially supported by Camunda Optimize." +
                      "\nWe can not guarantee full functionality." +
                      "\nPlease check the technical guide for the list of supported Elasticsearch versions");
      } else {
        throw new OptimizeRuntimeException(buildUnsupportedESErrorMessage(currentVersion));
      }
    }
  }


  private static Optional<String> findMatchedVersion(String currentVersion, List<String> supportedVersions) {
    String currentMajorAndMinor = getMajorAndMinor(currentVersion);
    return supportedVersions.stream().filter(v -> {
      String neededVersion = stripToPlainVersion(v);
      String neededMajorAndMinor = getMajorAndMinor(neededVersion);

      return currentMajorAndMinor.equals(neededMajorAndMinor)
        && Integer.parseInt(getPatchVersionFrom(currentVersion)) >= Integer.parseInt(getPatchVersionFrom
                                                                                       (neededVersion));
    }).findFirst();
  }

  private static String buildUnsupportedESErrorMessage(String ESVersion) {
    StringBuilder message = new StringBuilder("Elasticsearch version is not supported by Optimize.\n");

    message.append("Current version of Optimize supports following Elasticsearch versions:\n");
    for (String version : supportedES) {
      message.append(version).append("+\n");
    }

    message.append("Your current Elasticsearch version is: ").append(ESVersion);
    return message.toString();
  }

}
