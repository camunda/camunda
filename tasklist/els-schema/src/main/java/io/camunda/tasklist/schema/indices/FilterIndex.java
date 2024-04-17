/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.tasklist.schema.indices;

import io.camunda.tasklist.schema.backup.Prio4Backup;
import org.springframework.stereotype.Component;

@Component
public class FilterIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "filter";
  public static final String INDEX_VERSION = "8.6.0";
  public static final String NAME = "name";
  public static final String CREATED_BY = "createdBy";
  public static final String FILTER = "filter";
  public static final String CANDIDATE_USERS = "candidateUsers";
  public static final String CANDIDATE_GROUPS = "candidateGroups";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

}
