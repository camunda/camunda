/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.core.SearchDeleteRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchWriteResponse;

public interface DocumentBasedWriteClient {

  <T> SearchWriteResponse index(final SearchIndexRequest<T> indexRequest);

  SearchWriteResponse delete(final SearchDeleteRequest deleteRequest);
}
