/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler.session;

import io.camunda.search.security.SessionDocumentStorageClient;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NoOpSessionDocumentStorageClient implements SessionDocumentStorageClient {
  private final Map<String, Map<String, Object>> sessionDocuments =
      Collections.synchronizedMap(new HashMap<>());

  @Override
  public boolean createOrUpdateSessionDocument(final String id, final Map<String, Object> source) {
    sessionDocuments.put(id, source);
    return true;
  }

  @Override
  public Map<String, Object> getSessionDocument(final String id) {
    return sessionDocuments.get(id);
  }

  @Override
  public void deleteSessionDocument(final String id) {
    sessionDocuments.remove(id);
  }
}
