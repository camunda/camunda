/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * This class represents a stub or placeholder implementation of the AssigneeMigrator. Its main
 * purpose is to serve as a placeholder until a concrete implementation is provided.
 *
 * <p>TODO: Investigate if an actual implementation for Assignee migration is necessary for
 * integration with OpenSearch.
 */
@Component
@Conditional(OpenSearchCondition.class)
public class AssigneeMigratorNoImpl implements AssigneeMigrator {

  @Override
  public void migrateUsageMetrics(String newAssignee) {
    // No implementation currently.
  }
}
