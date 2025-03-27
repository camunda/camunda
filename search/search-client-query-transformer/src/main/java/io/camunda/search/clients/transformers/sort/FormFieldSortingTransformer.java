/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.index.FormIndex.VERSION;

public class FormFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "version" -> VERSION;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }
}
