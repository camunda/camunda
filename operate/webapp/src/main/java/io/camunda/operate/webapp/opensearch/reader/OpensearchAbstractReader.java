/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class OpensearchAbstractReader {

  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;
}
