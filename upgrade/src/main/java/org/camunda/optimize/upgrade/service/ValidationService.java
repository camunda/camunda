package org.camunda.optimize.upgrade.service;

import org.camunda.optimize.service.es.schema.type.MetadataType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;


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

  public void validateVersions(RestHighLevelClient restClient, String fromVersion, String toVersion) {

    try {
      final SearchResponse metadataSearchResponse = restClient.search(
        new SearchRequest(getOptimizeIndexAliasForType(ElasticsearchConstants.METADATA_TYPE)),
        RequestOptions.DEFAULT
      );

      String schemaVersion = null;
      if (metadataSearchResponse.getHits().getHits().length > 0) {
        schemaVersion = (String) metadataSearchResponse.getHits().getHits()[0]
          .getSourceAsMap()
          .get(MetadataType.SCHEMA_VERSION);
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
      logger.error("Can't resolve " + ENVIRONMENT_CONFIG_FILE, e);
    }

    if (!configAvailable) {
      throw new UpgradeRuntimeException(
        "Couldn't read " + ENVIRONMENT_CONFIG_FILE + " from environment folder in Optimize root!"
      );
    }
  }
}
