/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.EngineVersionDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.importing.EngineConstants;

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

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EngineVersionChecker {

  @Getter
  private static final List<String> supportedEngines = new ArrayList<>();

  // Any minor or major versions newer than specified here will also be accepted
  static {
    supportedEngines.add("7.12.11");
    supportedEngines.add("7.13.5");
    supportedEngines.add("7.14.0");
  }

  public static void checkEngineVersionSupport(String engineRestPath, EngineContext engineContext) {
    Client client = engineContext.getEngineClient();
    Response response;
    try {
      response = client.target(engineRestPath + EngineConstants.VERSION_ENDPOINT)
        .request()
        .get();
    } catch (Exception e) {
      // if we can't connect to the engine, we will just log an error and skip the check
      String errorMessage = buildEngineConnectionRefusedErrorMessage(engineContext.getEngineAlias());
      log.error(errorMessage, e);
      return;
    }

    int status = response.getStatus();
    if (status != Response.Status.OK.getStatusCode()) {
      String errorMessageTemplate = "While checking the Engine version, following error occurred:";
      if (status == Response.Status.NOT_FOUND.getStatusCode()) {
        String errorMessage = "While checking the Engine version, following error occurred: Status code: 404,\n this " +
          "means you either configured a wrong endpoint or you have an unsupported engine version < " + supportedEngines.get(0);
        throw new OptimizeRuntimeException(errorMessage);
      } else {
        throw new OptimizeRuntimeException(errorMessageTemplate +
                                             "\nStatus code:" + response.getStatus() +
                                             "\nResponse body:" + response.readEntity(String.class));
      }
    }

    String currentVersion = response.readEntity(EngineVersionDto.class).getVersion();
    boolean versionMatched = isVersionSupported(currentVersion, supportedEngines);

    if (!versionMatched) {
      throw new OptimizeRuntimeException(buildUnsupportedEngineErrorMessage(currentVersion));
    }
  }

  public static boolean isVersionSupported(String currentVersion, List<String> supportedVersions) {
    String patchVersion = getPatchVersionFrom(currentVersion);
    if (patchVersion.contains("alpha")) {
      log.warn("You are using a development version of the engine");
    }

    String plainVersion = stripToPlainVersion(currentVersion);
    String currentMajorAndMinor = getMajorAndMinor(currentVersion);
    return supportedVersions.stream()
      .filter(v -> currentMajorAndMinor.equals(getMajorAndMinor(v)))
      .findFirst()
      .map(s -> Integer.parseInt(getPatchVersionFrom(plainVersion)) >= Integer.parseInt(getPatchVersionFrom(s)))
      .orElseGet(() -> isCurrentBiggerThanSupported(plainVersion, supportedVersions));
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

    message.append("Current version of Optimize supports following engine versions:\n");
    for (String version : supportedEngines) {
      message.append(version).append("+\n");
    }

    message.append("Your current engine version is: ").append(engineVersion);
    return message.toString();
  }

  private static String buildEngineConnectionRefusedErrorMessage(String engineAlias) {
    return "Engine with alias [{" + engineAlias + "}] didn't respond. Can not verify this engine's version";
  }
}
