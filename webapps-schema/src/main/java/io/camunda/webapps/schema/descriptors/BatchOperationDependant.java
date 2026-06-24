/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import java.util.Map;

/**
 * Marker interface for descriptors that are dependent on batch operations. Used to identify related
 * documents that need to be archived together with batch operations.
 *
 * <p>When implementing this interface, the {@code getBatchOperationDependantField} method must
 * return the field name that refers to the batch operation key.
 */
public interface BatchOperationDependant extends IndexTemplateDescriptor {

  /**
   * @return the field name that refers to the batch operation key
   */
  String getBatchOperationDependantField();

  /**
   * Returns additional filter criteria when archiving dependant documents. This is useful when only
   * a subset of documents for a given batch operation key should be archived.
   *
   * @return a map of field names to values that must match for a document to be archived
   */
  default Map<String, String> getBatchOperationDependantFilters() {
    return Map.of();
  }
}
