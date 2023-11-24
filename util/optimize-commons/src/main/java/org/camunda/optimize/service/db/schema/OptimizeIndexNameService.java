/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.util.DatabaseProfilerUtil.getDatabaseProfile;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_PROFILE;

@Component
public class OptimizeIndexNameService implements ConfigurationReloadable {

  @Getter
  private String indexPrefix;

  @Autowired
  public OptimizeIndexNameService(final ConfigurationService configurationService,
                                  final Environment environment) {
    setIndexPrefix(configurationService, getDatabaseProfile(environment));
  }

  public OptimizeIndexNameService(final ConfigurationService configurationService,
                                  final String databaseProfile) {
    setIndexPrefix(configurationService, databaseProfile);
  }

  public OptimizeIndexNameService(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public String getOptimizeIndexAliasForIndex(String index) {
    return getOptimizeIndexAliasForIndexNameAndPrefix(index, indexPrefix);
  }

  public String getOptimizeIndexAliasForIndex(final IndexMappingCreator indexMappingCreator) {
    return getOptimizeIndexAliasForIndexNameAndPrefix(indexMappingCreator.getIndexName(), indexPrefix);
  }

  public String getOptimizeIndexTemplateNameWithVersion(final IndexMappingCreator indexMappingCreator) {
    if (!indexMappingCreator.isCreateFromTemplate()) {
      throw new IllegalArgumentException("Given indexMappingCreator is not templated!");
    }
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator);
  }

  /**
   * This will suffix the indices that are created from templates with their initial suffix
   */
  public String getOptimizeIndexNameWithVersion(final IndexMappingCreator indexMappingCreator) {
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator) + indexMappingCreator.getIndexNameInitialSuffix();
  }

  /**
   * This will suffix the wildcard for any indices that get rolled over, which is not compatible with all ES APIs. This cannot be
   * used for Index deletion as wildcard index deletion is prohibited from ES8+
   */
  public String getOptimizeIndexNameWithVersionForAllIndicesOf(final IndexMappingCreator indexMappingCreator) {
    if (StringUtils.isNotEmpty(indexMappingCreator.getIndexNameInitialSuffix())) {
      return getOptimizeIndexNameWithVersionWithWildcardSuffix(indexMappingCreator);
    }
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator);
  }

  public String getOptimizeIndexNameWithVersionWithWildcardSuffix(final IndexMappingCreator indexMappingCreator) {
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator)
      // match all indices of that version with this wildcard, which also catches potentially rolled over indices
      + "*";
  }

  public String getOptimizeIndexNameWithVersionWithoutSuffix(final IndexMappingCreator indexMappingCreator) {
    return getOptimizeIndexOrTemplateNameForAliasAndVersion(
      getOptimizeIndexAliasForIndex(indexMappingCreator.getIndexName()),
      String.valueOf(indexMappingCreator.getVersion())
    );
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    ConfigurationService configurationService = context.getBean(ConfigurationService.class);
    setIndexPrefix(configurationService, getDatabaseProfile(context.getEnvironment()));
  }

  private void setIndexPrefix(ConfigurationService configurationService, String databaseProfile) {
    if (databaseProfile.equals(OPENSEARCH_PROFILE)) {
      this.indexPrefix = configurationService.getOpenSearchConfiguration().getIndexPrefix();
    } else {
      this.indexPrefix = configurationService.getElasticSearchConfiguration().getIndexPrefix();
    }
  }

  public static String getOptimizeIndexOrTemplateNameForAliasAndVersion(final String indexAlias, final String version) {
    final String versionSuffix = version != null ? "_v" + version : "";
    return indexAlias + versionSuffix;
  }

  public static String getOptimizeIndexAliasForIndexNameAndPrefix(final String indexName, final String indexPrefix) {
    String original = indexName;
    if (!indexName.startsWith(indexPrefix)) {
      original = String.join("-", indexPrefix, indexName);
    }
    return original.toLowerCase();
  }

}