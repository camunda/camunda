/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.TaskTemplate.COMPLETION_TIME;
import static io.camunda.webapps.schema.descriptors.template.TaskTemplate.CREATION_TIME;
import static io.camunda.webapps.schema.descriptors.template.TaskTemplate.DUE_DATE;
import static io.camunda.webapps.schema.descriptors.template.TaskTemplate.FOLLOW_UP_DATE;
import static io.camunda.webapps.schema.descriptors.template.TaskTemplate.PRIORITY;

public class UserTaskFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "creationDate" -> CREATION_TIME;
      case "completionDate" -> COMPLETION_TIME;
      case "priority" -> PRIORITY;
      case "dueDate" -> DUE_DATE;
      case "followUpDate" -> FOLLOW_UP_DATE;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }
}
