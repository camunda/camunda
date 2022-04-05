/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.indices;

import io.camunda.tasklist.schema.Versionable;

public interface IndexDescriptor extends Versionable {

  String getIndexName();

  String getFullQualifiedName();

  default String getDerivedIndexNamePattern() {
    return getFullQualifiedName() + "*";
  }

  default String getAlias() {
    return getFullQualifiedName() + "alias";
  }
}
