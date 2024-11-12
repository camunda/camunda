/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store;

import java.io.IOException;
import java.util.Optional;

public interface DecisionStore {

  Optional<Long> getDistinctCountFor(final String fieldName);

  BatchRequest newBatchRequest();

  long deleteDocuments(final String indexName, final String idField, String id) throws IOException;
}
