/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.EngineVersionDto;
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
import static org.camunda.optimize.service.metadata.Version.isAlphaVersion;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EngineVersionChecker {

  private static final String ERROR_CONNECTION_REFUSED = "Engine didn't respond. Can not verify this engine's version";

  @Getter
  private static final List<String> supportedEngines = new ArrayList<>();

  // Any minor or major versions newer than specified here will also be accepted
  static {
    supportedEngines.add("7.18.0");
    supportedEngines.add("7.19.0");
  }

  public static void checkEngineVersionSupport(final Client engineClient,
                                               final String engineRestPath) {
    final Response response;
    try {
      response = engineClient.target(engineRestPath + EngineConstants.VERSION_ENDPOINT).request().get();
    } catch (Exception e) {
      log.error(ERROR_CONNECTION_REFUSED, e);
      throw new OptimizeRuntimeException(ERROR_CONNECTION_REFUSED);
    }

    final int status = response.getStatus();
    if (status != Response.Status.OK.getStatusCode()) {
      final String errorMessageTemplate = "While checking the Engine version, following error occurred:";
      if (status == Response.Status.NOT_FOUND.getStatusCode()) {
        final String errorMessage = "While checking the Engine version, following error occurred: Status code: 404,"
          + " this means you either configured a wrong endpoint or you have an unsupported engine version < "
          + supportedEngines.get(0);
        throw new OptimizeRuntimeException(errorMessage);
      } else {
        throw new OptimizeRuntimeException(
          errorMessageTemplate + "\nStatus code:" + status + "\nResponse body:" + response.readEntity(String.class)
        );
      }
    }

    final String currentVersion = response.readEntity(EngineVersionDto.class).getVersion();
    if (!isVersionSupported(currentVersion, supportedEngines)) {
      throw new OptimizeRuntimeException(buildUnsupportedEngineErrorMessage(currentVersion));
    }
  }

  public static boolean isVersionSupported(String currentVersion, List<String> supportedVersions) {
    if (isAlphaVersion(currentVersion)) {
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

}
