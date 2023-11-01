/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db;

import lombok.Getter;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public abstract class DatabaseClient implements ConfigurationReloadable {

  public abstract Map<String, Set<String>> getAliasesForIndex(final String indexName) throws IOException;

  public abstract Set<String> getAllIndicesForAlias(final String aliasName) throws IOException;

  public abstract boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB);

  @Getter
  protected OptimizeIndexNameService indexNameService;

  protected String[] convertToPrefixedAliasNames(final String[] indices) {
    return Arrays.stream(indices)
      .map(this::convertToPrefixedAliasName)
      .toArray(String[]::new);
  }

  protected String convertToPrefixedAliasName(final String index) {
    final boolean hasExcludePrefix = '-' == index.charAt(0);
    final String rawIndexName = hasExcludePrefix ? index.substring(1) : index;
    final String prefixedIndexName = indexNameService.getOptimizeIndexAliasForIndex(rawIndexName);
    return hasExcludePrefix ? "-" + prefixedIndexName : prefixedIndexName;
  }

}
