/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import lombok.Getter;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class OptimizeIndexNameService implements ConfigurationReloadable {

  @Getter
  private String indexPrefix;

  @Autowired
  public OptimizeIndexNameService(final ConfigurationService configurationService) {
    this.indexPrefix = configurationService.getEsIndexPrefix();
  }

  public OptimizeIndexNameService(final String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public String getOptimizeIndexAliasForType(String type) {
    return getOptimizeIndexAliasForTypeAndPrefix(type, indexPrefix);
  }

  public String getVersionedOptimizeIndexNameForTypeMapping(final TypeMappingCreator typeMappingCreator) {
    return getOptimizeIndexNameForAliasAndVersion(
      getOptimizeIndexAliasForType(typeMappingCreator.getType()),
      String.valueOf(typeMappingCreator.getVersion())
    );
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    this.indexPrefix = context.getBean(ConfigurationService.class).getEsIndexPrefix();
  }

  public static String getOptimizeIndexNameForAliasAndVersion(final String indexAlias, final String version) {
    final String versionSuffix = version != null ? "_v" + version : "";
    return indexAlias + versionSuffix;
  }

  public static String getOptimizeIndexAliasForTypeAndPrefix(final String type, final String indexPrefix) {
    String original = String.join("-", indexPrefix, type);
    return original.toLowerCase();
  }
}