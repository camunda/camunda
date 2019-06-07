/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.engine.EngineVersionDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.camunda.optimize.service.metadata.Version.getMajorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getMinorVersionFrom;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;

public class EngineVersionChecker {
  private static List<String> supportedEngines = new ArrayList<>();
  private static final Logger logger = LoggerFactory.getLogger(EngineVersionChecker.class);

  static {
    supportedEngines.add("7.9.12");
    supportedEngines.add("7.10.6");
    supportedEngines.add("7.11.0");
  }

  public static void checkEngineVersionSupport(String engineRestPath, EngineContext engineContext) {
    Client client = engineContext.getEngineClient();
    Response response;
    try {
      response = client.target(engineRestPath + "/version")
        .request()
        .get();
    } catch (Exception e) {
      // if we can't connect to the engine, we will just log an error and skip the check
      String errorMessage = buildEngineConnectionRefusedErrorMessage(engineContext.getEngineAlias());
      logger.error(errorMessage, e);
      return;
    }


    if (response.getStatus() != 200) {
      throw new OptimizeRuntimeException(buildUnsupportedEngineErrorMessage(null));
    }

    String currentVersion = stripToPlainVersion(response.readEntity(EngineVersionDto.class).getVersion());

    boolean versionMatched = isVersionSupported(currentVersion, supportedEngines);

    if (!versionMatched) {
      throw new OptimizeRuntimeException(buildUnsupportedEngineErrorMessage(currentVersion));
    }
  }

  private static boolean isVersionSupported(String currentVersion, List<String> supportedVersions) {
    String currentMajorAndMinor = getMajorAndMinor(currentVersion);

    return supportedVersions.stream()
      .filter(v -> currentMajorAndMinor.equals(getMajorAndMinor(v)))
      .findFirst()
      .map(s -> Integer.parseInt(getPatchVersionFrom(currentVersion)) >= Integer.parseInt(getPatchVersionFrom(s)))
      .orElseGet(() -> isCurrentBiggerThanSupported(currentVersion, supportedVersions));
  }

  private static boolean isCurrentBiggerThanSupported(String currentVersion, List<String> supportedVersions) {
    boolean match;
    Comparator<String> versionComparator = (String a, String b) ->
      Integer.parseInt(getMajorVersionFrom(a)) - Integer.parseInt(getMajorVersionFrom(b)) != 0 ?
        Integer.parseInt(getMajorVersionFrom(a)) - Integer.parseInt(getMajorVersionFrom(b)) :
        Integer.parseInt(getMinorVersionFrom(a)) - Integer.parseInt(getMinorVersionFrom(b));

    supportedEngines.sort(versionComparator);

    String biggestVersion = supportedVersions.get(0);
    match = versionComparator.compare(currentVersion, biggestVersion) > 0;
    return match;
  }

  private static String buildUnsupportedEngineErrorMessage(String engineVersion) {
    StringBuilder message = new StringBuilder("Engine version is not supported by Optimize.\n");

    String currentEngineVersion = engineVersion == null ? "can't be determined. Either the engine is outdated, " +
      "or you configured a wrong engine REST path." :
      "is: " + engineVersion;

    message.append("Current version of Optimize supports following engine versions:\n");
    for (String version : supportedEngines) {
      message.append(version).append("+\n");
    }

    message.append("Your current engine version ").append(currentEngineVersion);
    return message.toString();
  }

  private static String buildEngineConnectionRefusedErrorMessage(String engineAlias) {
    return "Engine with alias [{" + engineAlias + "}] didn't respond. Can not verify this engine's version";
  }
}

