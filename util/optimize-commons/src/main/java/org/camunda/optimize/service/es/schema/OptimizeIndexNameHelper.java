/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

public class OptimizeIndexNameHelper {
  public static final String OPTIMIZE_INDEX_PREFIX = "optimize-";

  private OptimizeIndexNameHelper() {
  }

  public static String getOptimizeIndexAliasForType(String type) {
    String original = OPTIMIZE_INDEX_PREFIX + type;
    return original.toLowerCase();
  }

  public static String getVersionedOptimizeIndexNameForTypeMapping(final TypeMappingCreator typeMappingCreator) {
    return getOptimizeIndexNameForAliasAndVersion(
      getOptimizeIndexAliasForType(typeMappingCreator.getType()),
      String.valueOf(typeMappingCreator.getVersion())
    );
  }

  public static String getOptimizeIndexNameForAliasAndVersion(final String indexAlias, final String version) {
    final String versionSuffix = version != null ? "_v" + version : "";
    return indexAlias + versionSuffix;
  }
}