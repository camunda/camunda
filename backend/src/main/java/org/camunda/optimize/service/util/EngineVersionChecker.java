/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.EngineVersionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.service.util.importing.EngineConstants;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EngineVersionChecker {

  private static final String ERROR_CONNECTION_REFUSED =
      "Engine didn't respond. Can not verify this engine's version";

  @Getter private static final List<String> supportedEngines = new ArrayList<>();

  // Any minor or major versions newer than specified here will also be accepted
  static {
    supportedEngines.add("7.19.0");
    supportedEngines.add("7.20.0");
    supportedEngines.add("7.21.0");
  }

  public static void checkEngineVersionSupport(
      final Client engineClient, final String engineRestPath) {
    final Response response;
    try {
      response =
          engineClient.target(engineRestPath + EngineConstants.VERSION_ENDPOINT).request().get();
    } catch (final Exception e) {
      log.error(ERROR_CONNECTION_REFUSED, e);
      throw new OptimizeRuntimeException(ERROR_CONNECTION_REFUSED, e);
    }

    final int status = response.getStatus();
    if (status != Response.Status.OK.getStatusCode()) {
      final String errorMessageTemplate =
          "While checking the Engine version, following error occurred:";
      if (status == Response.Status.NOT_FOUND.getStatusCode()) {
        final String errorMessage =
            "While checking the Engine version, following error occurred: Status code: 404,"
                + " this means you either configured a wrong endpoint or you have an unsupported engine version < "
                + supportedEngines.get(0);
        throw new OptimizeRuntimeException(errorMessage);
      } else {
        throw new OptimizeRuntimeException(
            errorMessageTemplate
                + "\nStatus code:"
                + status
                + "\nResponse body:"
                + response.readEntity(String.class));
      }
    }

    final String currentVersion = response.readEntity(EngineVersionDto.class).getVersion();
    if (!isVersionSupported(currentVersion, supportedEngines)) {
      throw new OptimizeRuntimeException(buildUnsupportedEngineErrorMessage(currentVersion));
    }
  }

  public static boolean isVersionSupported(
      final String currentVersion, final List<String> supportedVersions) {
    if (Version.isAlphaVersion(currentVersion)) {
      log.warn("You are using a development version of the engine");
    }

    final String plainVersion = Version.stripToPlainVersion(currentVersion);
    final String currentMajorAndMinor = Version.getMajorAndMinor(currentVersion);
    return supportedVersions.stream()
        .filter(v -> currentMajorAndMinor.equals(Version.getMajorAndMinor(v)))
        .findFirst()
        .map(
            s ->
                Integer.parseInt(Version.getPatchVersionFrom(plainVersion))
                    >= Integer.parseInt(Version.getPatchVersionFrom(s)))
        .orElseGet(() -> isCurrentBiggerThanSupported(plainVersion, supportedVersions));
  }

  private static boolean isCurrentBiggerThanSupported(
      final String currentVersion, final List<String> supportedVersions) {
    final boolean match;
    final Comparator<String> versionComparator =
        (String a, String b) ->
            Integer.parseInt(Version.getMajorVersionFrom(a))
                        - Integer.parseInt(Version.getMajorVersionFrom(b))
                    != 0
                ? Integer.parseInt(Version.getMajorVersionFrom(a))
                    - Integer.parseInt(Version.getMajorVersionFrom(b))
                : Integer.parseInt(Version.getMinorVersionFrom(a))
                    - Integer.parseInt(Version.getMinorVersionFrom(b));

    supportedEngines.sort(versionComparator);

    final String biggestVersion = supportedVersions.get(0);
    match = versionComparator.compare(currentVersion, biggestVersion) > 0;
    return match;
  }

  private static String buildUnsupportedEngineErrorMessage(final String engineVersion) {
    final StringBuilder message =
        new StringBuilder("Engine version is not supported by Optimize.\n");

    message.append("Current version of Optimize supports following engine versions:\n");
    for (final String version : supportedEngines) {
      message.append(version).append("+\n");
    }

    message.append("Your current engine version is: ").append(engineVersion);
    return message.toString();
  }
}
