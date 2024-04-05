/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
