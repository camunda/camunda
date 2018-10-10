package org.camunda.optimize.upgrade.service;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_TYPE_SCHEMA_VERSION;


public class ValidationService {
  private static final String ENVIRONMENT_CONFIG_YAML_REL_PATH = "/../environment/environment-config.yaml";
  private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

  private ConfigurationService configurationService;

  public ValidationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public void validateVersions(RestClient restClient, String fromVersion, String toVersion) {

    try {
      String metaDataIndex = configurationService.getOptimizeIndex(configurationService.getMetaDataType());
      Response metadataResponse = restClient
        .performRequest("GET", metaDataIndex + "/_search", Collections.emptyMap());

      boolean schemaMatches = false;
      if (metadataResponse.getStatusLine().getStatusCode() == 200) {
        String entityString = EntityUtils.toString(metadataResponse.getEntity());
        Integer readTotal = JsonPath.read(entityString, "$.hits.total");
        if (readTotal == 1) {
          String schemaVersion =
            JsonPath.read(entityString, "$.hits.hits[0]._source." + METADATA_TYPE_SCHEMA_VERSION);
          if (fromVersion.equals(schemaVersion)) {
            schemaMatches = true;
          }
        }
      }

      if (!schemaMatches) {
        throw new UpgradeRuntimeException(
          "Schema version saved in Metadata does not match required [" + fromVersion + "]") ;
      }
    } catch (IOException e) {
      logger.error("can't get metadata", e);
      throw new UpgradeRuntimeException("can't get metadata");
    }

    if (toVersion == null || toVersion.isEmpty()) {
      throw new UpgradeRuntimeException(
          "New schema version is not allowed to be empty or null!") ;
    }
  }

  public void validateExecutionPath() {
    File config = null;
    try {
      String executionFolderPath =
        ValidationService.class.
          getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .toURI()
          .getPath();
      executionFolderPath = executionFolderPath.substring(0, executionFolderPath.lastIndexOf("/"));
      executionFolderPath = executionFolderPath.replaceAll("%20"," ");
      String fullPath = executionFolderPath + ENVIRONMENT_CONFIG_YAML_REL_PATH;
      logger.debug("reading from [{}]", fullPath);
      config = new File(fullPath);
    } catch (URISyntaxException e) {
      logger.error("can't resolve current path", e);
    }

    if (config == null || !config.exists() || !config.canRead()) {
      throw new UpgradeRuntimeException(
        "The upgrade has to be executed from \"upgrade\" folder in the Optimize root directory!"
      );
    }
  }
}
