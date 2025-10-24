/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.indices;

import io.camunda.operate.schema.backup.Prio1Backup;
import org.springframework.stereotype.Component;

@Component
public final class MetadataIndex extends AbstractIndexDescriptor implements Prio1Backup {

  public static final String INDEX_NAME = "metadata";

  public static final String ID = "id";

  public static final String VALUE = "value";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.8.0";
  }
}
