/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store;

import java.io.IOException;
import java.util.Optional;

public interface DecisionStore {

  Optional<Long> getDistinctCountFor(final String fieldName);

  BatchRequest newBatchRequest();

  long deleteDocuments(final String indexName, final String idField, String id) throws IOException;
}
