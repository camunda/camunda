/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex.AFTER_NON_GLOBAL;
import static io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex.ID;
import static io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex.LISTENER_ID;
import static io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex.LISTENER_TYPE;
import static io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex.PRIORITY;
import static io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex.SOURCE;
import static io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex.TYPE;

public class GlobalListenerFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "id" -> ID;
      case "listenerId" -> LISTENER_ID;
      case "type" -> TYPE;
      case "afterNonGlobal" -> AFTER_NON_GLOBAL;
      case "priority" -> PRIORITY;
      case "source" -> SOURCE;
      case "listenerType" -> LISTENER_TYPE;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return ID;
  }
}
