/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.schema.indices;

import io.camunda.operate.schema.Versionable;

public interface IndexDescriptor extends Versionable {

  String getIndexName();

  String getFullQualifiedName();

  default String getDerivedIndexNamePattern() { return getFullQualifiedName() + "*"; }

  default String getAlias() {
    return getFullQualifiedName() + "alias";
  }

}
