/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.indices;

import io.camunda.tasklist.schema.v86.backup.Prio4Backup;
import org.springframework.stereotype.Component;

@Component
public class MigrationRepositoryIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "migration-steps-repository";
  public static final String INDEX_VERSION = "1.1.0";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }
}
