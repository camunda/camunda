/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;

public class OpensearchRestClientBased {

  protected final RestClient client;
  protected final ObjectMapper objectMapper;

  public OpensearchRestClientBased(final RestClient client, final ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  protected <T> T sendRequest(final Request request, final Class<T> responseType)
      throws IOException {
    final var response = client.performRequest(request);
    final var responseBody = response.getEntity().getContent().readAllBytes();
    return objectMapper.readValue(responseBody, responseType);
  }

  public OpensearchIndexStateManagementClient ism() {
    return new OpensearchIndexStateManagementClient(client, objectMapper);
  }

  public OpensearchSnapshotClient snapshot() {
    return new OpensearchSnapshotClient(client, objectMapper);
  }
}
