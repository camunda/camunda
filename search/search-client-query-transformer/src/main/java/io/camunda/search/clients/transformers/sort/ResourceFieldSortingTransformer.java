/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.RESOURCE_ID;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.RESOURCE_KEY;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.RESOURCE_NAME;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.VERSION;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.VERSION_TAG;

public class ResourceFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "resourceKey" -> RESOURCE_KEY;
      case "resourceId" -> RESOURCE_ID;
      case "resourceName" -> RESOURCE_NAME;
      case "version" -> VERSION;
      case "versionTag" -> VERSION_TAG;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return RESOURCE_KEY;
  }
}
