/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.SemanticVersion;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class IndexSchemaValidatorUtil {
  public static String getIndexPrefix(final TasklistProperties tasklistProperties) {
    return TasklistProperties.OPEN_SEARCH.equals(tasklistProperties.getDatabaseType()) ? tasklistProperties.getOpenSearch().getIndexPrefix()
        : tasklistProperties.getElasticsearch().getIndexPrefix();
  }

  public static Set<String> newerVersionsForIndex(final IndexDescriptor indexDescriptor, final Set<String> versions) {
    final SemanticVersion currentVersion = SemanticVersion.fromVersion(indexDescriptor.getVersion());
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  public static Set<String> olderVersionsForIndex(final IndexDescriptor indexDescriptor, final Set<String> versions) {
    final SemanticVersion currentVersion = SemanticVersion.fromVersion(indexDescriptor.getVersion());
    return versions.stream()
        .filter(version -> currentVersion.isNewerThan(SemanticVersion.fromVersion(version)))
        .collect(Collectors.toSet());
  }
}
