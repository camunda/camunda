package org.camunda.optimize.upgrade.service;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * @author Askar Akhmerov
 */
public class ValidationService {
  private static final String ENVIRONMENT_CONFIG_YAML_REL_PATH = "/../environment/environment-config.yaml";
  protected Logger logger = LoggerFactory.getLogger(getClass());
  protected String FROM_VERSION = org.camunda.optimize.upgrade.metadata.TargetSchemaVersion.VERSION;

  public void validate() {
    validateExecutionPath();
  }

  protected void validateVersions(RestClient restClient) {
    try {
      Response metadataResponse = restClient
        .performRequest("GET", "optimize-metadata/_search", Collections.emptyMap());

      boolean schemaMatches = false;
      if (metadataResponse.getStatusLine().getStatusCode() == 200) {
        String entityString = EntityUtils.toString(metadataResponse.getEntity());
        Integer readTotal = JsonPath.read(entityString, "$.hits.total");
        if (readTotal == 1) {
          String schemaVersion = JsonPath.read(entityString, "$.hits.hits[0]._source.schemaVersion");
          if (FROM_VERSION.equals(schemaVersion)) {
            schemaMatches = true;
          }
        }
      }

      if (!schemaMatches) {
        throw new UpgradeRuntimeException(
          "SchemaVersion saved in Metadata does not match required [" + FROM_VERSION + "]") ;
      }
    } catch (IOException e) {
      logger.error("can't get metadata", e);
      throw new UpgradeRuntimeException("can't get metadata");
    }
  }

  private void validateExecutionPath() {
    File config = null;
    try {
      String executionFolderPath = ValidationService.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
      executionFolderPath = executionFolderPath.substring(0, executionFolderPath.lastIndexOf("/"));
      executionFolderPath = executionFolderPath.replaceAll("%20"," ");
      String fullPath = executionFolderPath + ENVIRONMENT_CONFIG_YAML_REL_PATH;
      logger.debug("reading from [{}]", fullPath);
      config = new File(fullPath);
    } catch (URISyntaxException e) {
      logger.error("can't resolve current path", e);
    }

    if (config == null || !config.exists() || !config.canRead()) {
      throw new RuntimeException("Upgrade has to be executed from \"upgrade\" folder in $OPTIMIZE_HOME");
    }
  }
}
