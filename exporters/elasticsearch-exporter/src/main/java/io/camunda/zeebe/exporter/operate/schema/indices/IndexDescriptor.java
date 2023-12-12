/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.schema.indices;

import io.camunda.operate.schema.Versionable;

public interface IndexDescriptor extends Versionable {

  String TENANT_ID = "tenantId";
  String DEFAULT_TENANT_ID = "<default>";

  String getIndexName();

  String getFullQualifiedName();

  default String getDerivedIndexNamePattern() {
    return getFullQualifiedName() + "*";
  }

  default String getAlias() {
    return getFullQualifiedName() + "alias";
  }
}
