/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.security;

import java.util.Map;

public interface SessionDocumentStorageClient {

  boolean createOrUpdateSessionDocument(final String id, final Map<String, Object> source);

  Map<String, Object> getSessionDocument(String id);

  void deleteSessionDocument(String id);
}
