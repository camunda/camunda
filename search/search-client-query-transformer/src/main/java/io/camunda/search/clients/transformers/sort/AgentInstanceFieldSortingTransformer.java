/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.COMPLETION_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.CREATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.LAST_UPDATED_DATE;
import static io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate.STATUS;

public class AgentInstanceFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "creationDate" -> CREATION_DATE;
      case "lastUpdatedDate" -> LAST_UPDATED_DATE;
      case "completionDate" -> COMPLETION_DATE;
      case "status" -> STATUS;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }
}
