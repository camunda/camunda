/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema;

import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface IndexSchemaValidator {

  boolean isHealthCheckEnabled();

  boolean hasAnyTasklistIndices();

  boolean schemaExists();

  void validateIndexVersions();

  boolean isValidNumberOfReplicas();

  Map<IndexDescriptor, Set<IndexMappingProperty>> validateIndexMappings() throws IOException;

  Set<String> olderVersionsForIndex(final IndexDescriptor indexDescriptor);

  Set<String> newerVersionsForIndex(final IndexDescriptor indexDescriptor);
}
