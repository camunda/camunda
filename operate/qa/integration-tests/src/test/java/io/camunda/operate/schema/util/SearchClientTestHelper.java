/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util;

import java.util.Map;

public interface SearchClientTestHelper {

  void setClientRetries(int retries);

  void createDocument(String indexName, String id, Map<String, Object> document);

  void createDocument(String indexName, String id, String routing, Map<String, Object> document);
}
