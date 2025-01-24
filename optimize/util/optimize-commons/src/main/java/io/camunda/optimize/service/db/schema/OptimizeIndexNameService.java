/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.COMPONENT_NAME;

import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OptimizeIndexNameService implements ConfigurationReloadable {

  public static String defaultIndexPrefix = "";
  private String indexPrefix;
  private String completeIndexPrefix;

  @Autowired
  public OptimizeIndexNameService(
      final ConfigurationService configurationService, final Environment environment) {
    setIndexPrefix(configurationService, ConfigurationService.getDatabaseType(environment));
  }

  public OptimizeIndexNameService(
      final ConfigurationService configurationService, final DatabaseType databaseType) {
    setIndexPrefix(configurationService, databaseType);
  }

  public OptimizeIndexNameService(final String indexPrefix) {
    setIndexPrefix(indexPrefix);
  }

  public String getOptimizeIndexAliasForIndex(final String index) {
    return getOptimizeIndexAliasForIndexNameAndPrefix(index);
  }

  public String getOptimizeIndexAliasForIndex(final IndexMappingCreator<?> indexMappingCreator) {
    return getOptimizeIndexAliasForIndexNameAndPrefix(indexMappingCreator.getIndexName());
  }

  public String getOptimizeIndexTemplateNameWithVersion(
      final IndexMappingCreator<?> indexMappingCreator) {
    if (!indexMappingCreator.isCreateFromTemplate()) {
      throw new IllegalArgumentException("Given indexMappingCreator is not templated!");
    }
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator);
  }

  /** This will suffix the indices that are created from templates with their initial suffix */
  public String getOptimizeIndexNameWithVersion(final IndexMappingCreator<?> indexMappingCreator) {
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator)
        + indexMappingCreator.getIndexNameInitialSuffix();
  }

  /**
   * This will suffix the wildcard for any indices that get rolled over, which is not compatible
   * with all ES APIs. This cannot be used for Index deletion as wildcard index deletion is
   * prohibited from ES8+
   */
  public String getOptimizeIndexNameWithVersionForAllIndicesOf(
      final IndexMappingCreator<?> indexMappingCreator) {
    if (StringUtils.isNotEmpty(indexMappingCreator.getIndexNameInitialSuffix())) {
      return getOptimizeIndexNameWithVersionWithWildcardSuffix(indexMappingCreator);
    }
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator);
  }

  public String getOptimizeIndexNameWithVersionWithWildcardSuffix(
      final IndexMappingCreator<?> indexMappingCreator) {
    return getOptimizeIndexNameWithVersionWithoutSuffix(indexMappingCreator)
        // match all indices of that version with this wildcard, which also catches potentially
        // rolled over indices
        + "*";
  }

  public String getOptimizeIndexNameWithVersionWithoutSuffix(
      final IndexMappingCreator<?> indexMappingCreator) {
    return getOptimizeIndexOrTemplateNameForAliasAndVersion(
        getOptimizeIndexAliasForIndex(indexMappingCreator.getIndexName()),
        String.valueOf(indexMappingCreator.getVersion()));
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
    setIndexPrefix(
        configurationService, ConfigurationService.getDatabaseType(context.getEnvironment()));
  }

  private void setIndexPrefix(
      final ConfigurationService configurationService, final DatabaseType databaseType) {
    if (databaseType.equals(DatabaseType.OPENSEARCH)) {
      indexPrefix = configurationService.getOpenSearchConfiguration().getIndexPrefix();
    } else {
      indexPrefix = configurationService.getElasticSearchConfiguration().getIndexPrefix();
    }
    setIndexPrefix(indexPrefix);
  }

  public static String getOptimizeIndexOrTemplateNameForAliasAndVersion(
      final String indexAlias, final String version) {
    final String versionSuffix = version != null ? "_v" + version : "";
    return indexAlias + versionSuffix;
  }

  public String getOptimizeIndexOrTemplateNameForAliasAndVersionWithPrefix(
      final String indexOrTemplateNameWithoutPrefix, final String version) {
    return getOptimizeIndexAliasForIndexNameAndPrefix(
        getOptimizeIndexOrTemplateNameForAliasAndVersion(
            indexOrTemplateNameWithoutPrefix, version));
  }

  /**
   * Adds the "optimize" prefix and the indexPrefix separated by a dash. The format if indexPrefix
   * is "" is: 'optimize-{indexName}' The format if indexPrefix is not blank is:
   * '{indexPrefix}-optimize-{indexName}'.
   *
   * <p>This function is idempotent because it is sometime called on an string that's already been
   * "fixed", such as in {@link
   * io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations}
   *
   * @param indexName the name of the index, potentially already "escaped"
   * @return the escaped index name
   */
  public String getOptimizeIndexAliasForIndexNameAndPrefix(String indexName) {
    if (!indexPrefix.isEmpty() && indexName.startsWith(indexPrefix)) {
      return indexName;
    }
    if (!indexName.startsWith(COMPONENT_NAME)) {
      indexName = String.join("-", COMPONENT_NAME, indexName);
    }
    if (!indexPrefix.isEmpty() && !indexName.startsWith(indexPrefix)) {
      indexName = String.join("-", indexPrefix, indexName);
    }
    return indexName.toLowerCase(Locale.ENGLISH);
  }

  private static String makeCompleteIndexPrefix(final String indexPrefix) {
    if (indexPrefix == null || indexPrefix.isEmpty()) {
      return COMPONENT_NAME;
    } else {
      return String.format("%s-%s", indexPrefix, COMPONENT_NAME);
    }
  }

  public String getIndexPrefix() {
    // the completePrefix is return for "backward-compatibility:
    // this method is used to search all optimize indices, by returning the complete prefix
    // we avoid returning just "", which could potentially return all indices in the DB if it's used
    // as getIndexPrefix + "*".
    return completeIndexPrefix;
  }

  public String getShortIndexPrefix() {
    // TODO: Reconcile nomenclature in the callers and in this callee, by not returning
    //  completeIndexPrefix in the getIndexPrefix(), once Optimize is fully identical to other
    //  applications.
    return indexPrefix;
  }

  @VisibleForTesting
  void setIndexPrefix(final String indexPrefix) {
    if (indexPrefix.equals(COMPONENT_NAME)) {
      throw new IllegalArgumentException("Invalid prefix " + indexPrefix);
    }
    this.indexPrefix = indexPrefix;
    completeIndexPrefix = makeCompleteIndexPrefix(indexPrefix);
  }
}
