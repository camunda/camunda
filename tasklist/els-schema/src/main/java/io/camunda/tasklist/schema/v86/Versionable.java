/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86;

public interface Versionable {

  String DEFAULT_SCHEMA_VERSION = "1.0.0";

  default String getVersion() {
    return DEFAULT_SCHEMA_VERSION;
  }
}
