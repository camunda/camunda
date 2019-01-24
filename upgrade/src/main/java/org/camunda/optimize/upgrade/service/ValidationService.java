package org.camunda.optimize.upgrade.service;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_TYPE_SCHEMA_VERSION;


public class ValidationService {
  private static final String ENVIRONMENT_CONFIG_FILE = "environment-config.yaml";
  private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

  private ConfigurationService configurationService;

  public ValidationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public void validateConfiguration() {
    configurationService.validateNoDeprecatedConfigKeysUsed();
  }

  public void validateVersions(RestClient restClient, String fromVersion, String toVersion) {

    try {
      String metaDataIndex = getOptimizeIndexAliasForType(ElasticsearchConstants.METADATA_TYPE);
      Response metadataResponse = restClient
        .performRequest("GET", metaDataIndex + "/_search", Collections.emptyMap());

      String schemaVersion = null;
      if (metadataResponse.getStatusLine().getStatusCode() == 200) {
        String entityString = EntityUtils.toString(metadataResponse.getEntity());
        Integer readTotal = JsonPath.read(entityString, "$.hits.total");
        if (readTotal == 1) {
          schemaVersion = JsonPath.read(entityString, "$.hits.hits[0]._source." + METADATA_TYPE_SCHEMA_VERSION);
        }
      }

      if (!fromVersion.equals(schemaVersion)) {
        throw new UpgradeRuntimeException(
          "Schema version saved in Metadata [" + schemaVersion + "] does not match required [" + fromVersion + "]"
        );
      }
    } catch (IOException e) {
      logger.error("can't get metadata", e);
      throw new UpgradeRuntimeException("can't get metadata");
    }

    if (toVersion == null || toVersion.isEmpty()) {
      throw new UpgradeRuntimeException(
        "New schema version is not allowed to be empty or null!");
    }
  }

  public void validateEnvironmentConfigInClasspath() {
    boolean configAvailable = false;
    try (InputStream resourceAsStream =
           ValidationService.class.getResourceAsStream("/" + ENVIRONMENT_CONFIG_FILE)) {
      if (resourceAsStream != null) {
        configAvailable = true;
      }
    } catch (IOException e) {
      logger.error("can't resolve " + ENVIRONMENT_CONFIG_FILE, e);
    }

    if (!configAvailable) {
      throw new UpgradeRuntimeException(
        "The upgrade has to be executed from \"upgrade\" folder in the Optimize root directory!"
      );
    }
  }
}
