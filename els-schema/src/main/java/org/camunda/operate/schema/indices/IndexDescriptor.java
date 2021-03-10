/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.indices;

import org.camunda.operate.schema.Versionable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface IndexDescriptor extends Versionable {

  String getIndexName();

  String getFullQualifiedName();

  default String getDerivedIndexNamePattern() { return getFullQualifiedName() + "*"; }

  default String getAlias() {
    return getFullQualifiedName() + "alias";
  }

}
