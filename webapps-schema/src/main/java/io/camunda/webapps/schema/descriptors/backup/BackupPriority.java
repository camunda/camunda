/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.backup;

public interface BackupPriority {
  String getFullQualifiedName();

  /**
   * Some indices can be skipped if not found, such as optimize indices if optimize is not deployed
   *
   * @return if this index can be skipped if it's not created in the DB
   */
  default boolean required() {
    return true;
  }
}
