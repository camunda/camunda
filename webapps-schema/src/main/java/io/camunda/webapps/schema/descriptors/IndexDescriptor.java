/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import java.util.Optional;

public interface IndexDescriptor {

  String TENANT_ID = "tenantId";

  String getFullQualifiedName();

  String getAlias();

  String getIndexName();

  String getMappingsClasspathFilename();

  @Deprecated
  default String getSchemaClasspathFilename() {
    return getMappingsClasspathFilename();
  }

  @Deprecated
  default String getDerivedIndexNamePattern() {
    return getFullQualifiedName() + "*";
  }

  String getAllVersionsIndexNameRegexPattern();

  String getIndexNameWithoutVersion();

  String getVersion();

  default Optional<String> getTenantIdField() {
    return Optional.empty();
  }
}
