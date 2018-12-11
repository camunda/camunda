package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.engine.EngineVersionDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;

public class VersionChecker {
  private static List<String> supportedEngines = new ArrayList<>();
  private static final Logger logger = LoggerFactory.getLogger(VersionChecker.class);

  static {
    supportedEngines.add("7.8.13");
    supportedEngines.add("7.9.7");
    supportedEngines.add("7.10.0");
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
    String currentCutPatch = getMajorAndMinor(currentVersion);

    Optional<String> matchedVersion = supportedEngines.stream().filter(v -> {
      String neededVersion = stripToPlainVersion(v);
      String neededCutPatch = getMajorAndMinor(neededVersion);

      return currentCutPatch.equals(neededCutPatch)
        && Integer.parseInt(getPatchVersionFrom(currentVersion)) >= Integer.parseInt(getPatchVersionFrom
                                                                                       (neededVersion));
    }).findFirst();
    if (!matchedVersion.isPresent()) {
      throw new OptimizeRuntimeException(buildUnsupportedEngineErrorMessage(currentVersion));
    }
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

