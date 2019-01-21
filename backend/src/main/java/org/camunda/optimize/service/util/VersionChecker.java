package org.camunda.optimize.service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.dto.engine.EngineVersionDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.metadata.Version.getMajorAndMinor;
import static org.camunda.optimize.service.metadata.Version.getPatchVersionFrom;
import static org.camunda.optimize.service.metadata.Version.stripToPlainVersion;

public class VersionChecker {
  private static List<String> supportedEngines = new ArrayList<>();
  private static List<String> supportedES = new ArrayList<>();
  private static List<String> warningES = new ArrayList<>();
  private static final Logger logger = LoggerFactory.getLogger(VersionChecker.class);

  static {
    supportedEngines.add("7.8.13");
    supportedEngines.add("7.9.7");
    supportedEngines.add("7.10.0");
    supportedEngines.add("7.11.0");
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

    Optional<String> matchedVersion = findMatchedVersion(currentVersion, supportedEngines);

    if (!matchedVersion.isPresent()) {
      throw new OptimizeRuntimeException(buildUnsupportedEngineErrorMessage(currentVersion));
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

